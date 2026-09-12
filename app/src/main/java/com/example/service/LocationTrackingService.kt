package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.model.LocationPointEntity
import com.example.data.repository.TripRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: TripRepository
    private var wakeLock: PowerManager.WakeLock? = null

    private var activeTripId: Long = 0L
    private var isSimulating = false
    private var currentTravelMode: String = "Drive" // Walk, Run, Cycling, Drive, Transit, Airplane, Boat
    private var timerJob: Job? = null
    private var simulationJob: Job? = null

    // Stay / Rest detection state
    private var stationaryAnchorLat: Double? = null
    private var stationaryAnchorLng: Double? = null
    private var stationaryStartTime: Long = 0L
    private var isMarkedStationary = false
    private var lastRecordedStayDurationMs = 0L

    // Running distance & speed metrics
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var accumulatedDistanceMeters: Double = 0.0
    private var speedSum: Double = 0.0
    private var speedSamples: Int = 0
    private var maxRecordedSpeed: Double = 0.0
    private var elapsedSecondsCount: Long = 0L
    private var isAutoResumedSession = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        repository = TripRepository(db.tripDao(), db.locationPointDao(), this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TripTracker::LocationWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max safety
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                val tripId = intent?.getLongExtra(EXTRA_TRIP_ID, -1L) ?: -1L
                val initialMode = intent?.getStringExtra(EXTRA_INITIAL_MODE) ?: "Drive"
                val simulate = intent?.getBooleanExtra(EXTRA_SIMULATE, false) ?: false
                if (tripId > 0) {
                    startTrackingSession(tripId, initialMode, simulate, isResumed = false)
                }
            }
            ACTION_RESUME_AFTER_BOOT -> {
                val tripId = intent?.getLongExtra(EXTRA_TRIP_ID, -1L) ?: -1L
                val initialMode = intent?.getStringExtra(EXTRA_INITIAL_MODE) ?: "Drive"
                val simulate = intent?.getBooleanExtra(EXTRA_SIMULATE, false) ?: false
                if (tripId > 0) {
                    startTrackingSession(tripId, initialMode, simulate, isResumed = true)
                }
            }
            ACTION_SWITCH_MODE -> {
                val newMode = intent?.getStringExtra(EXTRA_MODE) ?: "Drive"
                performModeSwitch(newMode)
            }
            ACTION_PAUSE -> {
                TrackingStateManager.pauseTracking()
                updateNotification("Tracking Paused ($currentTravelMode)", "Trip is paused.")
            }
            ACTION_RESUME -> {
                TrackingStateManager.resumeTracking()
                updateNotification("Tracking Active ($currentTravelMode)", "Recording location & speed...")
            }
            ACTION_STOP -> {
                stopTrackingSession()
            }
        }
        return START_STICKY
    }

    private fun startTrackingSession(
        tripId: Long,
        initialMode: String,
        simulate: Boolean,
        isResumed: Boolean
    ) {
        activeTripId = tripId
        currentTravelMode = initialMode
        isSimulating = simulate
        isAutoResumedSession = isResumed

        // Save persistent active session state to SharedPreferences for survival across discharge/power-off/reboot
        savePersistentState(tripId, isTracking = true, simulate = simulate, mode = initialMode)

        accumulatedDistanceMeters = 0.0
        speedSum = 0.0
        speedSamples = 0
        maxRecordedSpeed = 0.0
        elapsedSecondsCount = 0L
        stationaryAnchorLat = null
        stationaryAnchorLng = null
        stationaryStartTime = 0L
        isMarkedStationary = false

        TrackingStateManager.startTracking(
            tripId = tripId,
            initialMode = initialMode,
            isSimulated = simulate,
            isAutoResumed = isResumed
        )

        // If resuming after phone power off/reboot, load historical data from Room
        if (isResumed) {
            serviceScope.launch {
                val points = repository.getPointsForTripSync(tripId)
                if (points.isNotEmpty()) {
                    var loadedDist = 0.0
                    var maxSpd = 0.0
                    var spdSum = 0.0
                    var spdCnt = 0
                    for (i in points.indices) {
                        val pt = points[i]
                        if (pt.speedKmh > maxSpd) maxSpd = pt.speedKmh
                        if (pt.speedKmh > 0.5) {
                            spdSum += pt.speedKmh
                            spdCnt++
                        }
                        if (i > 0) {
                            val prev = points[i - 1]
                            loadedDist += calculateDistanceMeters(prev.latitude, prev.longitude, pt.latitude, pt.longitude)
                        }
                    }
                    accumulatedDistanceMeters = loadedDist
                    maxRecordedSpeed = maxSpd
                    speedSum = spdSum
                    speedSamples = spdCnt
                    lastLat = points.last().latitude
                    lastLng = points.last().longitude

                    val trip = repository.getTripByIdSync(tripId)
                    if (trip != null) {
                        val elapsed = ((System.currentTimeMillis() - trip.startTime) / 1000).coerceAtLeast(0)
                        elapsedSecondsCount = elapsed
                    }

                    TrackingStateManager.updateState {
                        it.copy(
                            distanceMeters = accumulatedDistanceMeters,
                            maxSpeedKmh = maxRecordedSpeed,
                            avgSpeedKmh = if (speedSamples > 0) speedSum / speedSamples else 0.0,
                            elapsedSeconds = elapsedSecondsCount,
                            recentPoints = points.takeLast(100),
                            currentMode = currentTravelMode
                        )
                    }
                }
            }
        }

        val modeIcon = getModeEmoji(currentTravelMode)
        val notifTitle = if (isResumed) "Continuous Tracking Resumed $modeIcon" else "Trip Tracking Active $modeIcon"
        val notifSub = if (isResumed) "Auto-recovered after device restart. Tracking uninterrupted." else "Recording $currentTravelMode path, speed & rests..."

        val notification = buildNotification(notifTitle, notifSub)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start elapsed time timer
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (!TrackingStateManager.trackingState.value.isPaused) {
                    elapsedSecondsCount++
                    val currentStayMs = if (isMarkedStationary && stationaryStartTime > 0) {
                        System.currentTimeMillis() - stationaryStartTime
                    } else 0L

                    TrackingStateManager.updateState {
                        it.copy(
                            elapsedSeconds = elapsedSecondsCount,
                            currentStayDurationMs = currentStayMs
                        )
                    }

                    if (elapsedSecondsCount % 4 == 0L) {
                        val speedStr = "%.1f km/h".format(TrackingStateManager.trackingState.value.currentSpeedKmh)
                        val distStr = "%.2f km".format(accumulatedDistanceMeters / 1000.0)
                        val modeLabel = "${getModeEmoji(currentTravelMode)} $currentTravelMode"
                        val status = if (isMarkedStationary) {
                            "☕ Resting at stop (${currentStayMs / 60000}m) | $modeLabel"
                        } else {
                            "$modeLabel: $speedStr | $distStr"
                        }
                        updateNotification("Trip Tracking Active: $distStr", status)
                    }
                }
            }
        }

        if (simulate) {
            startSimulatedRoute()
        } else {
            startRealLocationUpdates()
        }
    }

    private fun performModeSwitch(newMode: String) {
        currentTravelMode = newMode
        TrackingStateManager.switchMode(newMode)
        savePersistentMode(newMode)

        serviceScope.launch {
            if (activeTripId > 0) {
                repository.switchTripMode(activeTripId, newMode)
            }
        }

        val modeLabel = "${getModeEmoji(newMode)} $newMode Mode Active"
        updateNotification(modeLabel, "Tracking speed, distance, and automatic rest stops")
    }

    private fun savePersistentState(tripId: Long, isTracking: Boolean, simulate: Boolean, mode: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_ACTIVE_TRIP_ID, tripId)
            .putBoolean(KEY_IS_TRACKING, isTracking)
            .putBoolean(KEY_IS_SIMULATED, simulate)
            .putString(KEY_CURRENT_MODE, mode)
            .putLong(KEY_START_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun savePersistentMode(mode: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CURRENT_MODE, mode).apply()
    }

    private fun clearPersistentState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_TRACKING, false)
            .putLong(KEY_ACTIVE_TRIP_ID, -1L)
            .apply()
    }

    @SuppressLint("MissingPermission")
    private fun startRealLocationUpdates() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(1.5f)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSimulatedRoute() {
        simulationJob?.cancel()
        simulationJob = serviceScope.launch {
            val baseLat = 37.7749
            val baseLng = -122.4194

            val waypoints = listOf(
                SimPoint(37.7749, -122.4194, 0.0, false, 0, "Civic Center Plaza", 45.0),
                SimPoint(37.7758, -122.4182, 28.5, false, 0, "Market St & 8th Ave", 48.0),
                SimPoint(37.7772, -122.4165, 42.0, false, 0, "Mid-Market Tech Corridor", 52.0),
                SimPoint(37.7790, -122.4140, 54.2, false, 0, "US-101 Northbound Expressway", 60.0),
                SimPoint(37.7815, -122.4110, 35.8, false, 0, "Downtown Transit Center", 55.0),
                SimPoint(37.7830, -122.4080, 18.0, false, 0, "Powell St & Geary Blvd", 50.0),
                // Rest / Stay at Coffee Shop
                SimPoint(37.7835, -122.4075, 0.0, true, 180000, "Blue Bottle Artisan Roastery", 48.0),
                SimPoint(37.7836, -122.4074, 0.2, true, 240000, "Blue Bottle Artisan Roastery", 48.0),
                SimPoint(37.7850, -122.4050, 32.0, false, 0, "Union Square Central", 52.0),
                SimPoint(37.7880, -122.4015, 48.6, false, 0, "Financial District Parkway", 40.0),
                SimPoint(37.7910, -122.3980, 52.3, false, 0, "Grand California Ave", 30.0),
                // Rest / Stay at Waterfront Plaza
                SimPoint(37.7935, -122.3960, 0.0, true, 300000, "Embarcadero Waterfront Plaza", 10.0),
                SimPoint(37.7950, -122.3930, 22.0, false, 0, "Ferry Building Promenade", 8.0),
                SimPoint(37.7980, -122.3890, 38.4, false, 0, "Bay View Pier Overlook", 5.0)
            )

            var index = 0
            while (isActive && activeTripId > 0) {
                if (!TrackingStateManager.trackingState.value.isPaused) {
                    val wp = waypoints[index % waypoints.size]
                    val jitterLat = (Math.random() - 0.5) * 0.0001
                    val jitterLng = (Math.random() - 0.5) * 0.0001
                    val lat = wp.lat + jitterLat
                    val lng = wp.lng + jitterLng

                    // Adjust simulated speed based on active mode
                    val adjustedSpeedKmh = when (currentTravelMode) {
                        "Walk" -> if (wp.isStay) 0.0 else (4.2 + (Math.random() * 1.5))
                        "Run" -> if (wp.isStay) 0.0 else (11.5 + (Math.random() * 3.0))
                        "Cycling" -> if (wp.isStay) 0.0 else (24.0 + (Math.random() * 8.0))
                        "Airplane" -> if (wp.isStay) 0.0 else (720.0 + (Math.random() * 90.0))
                        "Transit" -> if (wp.isStay) 0.0 else (85.0 + (Math.random() * 30.0))
                        "Boat" -> if (wp.isStay) 0.0 else (32.0 + (Math.random() * 10.0))
                        else -> wp.speedKmh // Drive
                    }

                    val adjustedAlt = when (currentTravelMode) {
                        "Airplane" -> 9850.0 + (index * 50)
                        else -> wp.alt
                    }

                    val location = Location("simulated").apply {
                        latitude = lat
                        longitude = lng
                        altitude = adjustedAlt
                        speed = (adjustedSpeedKmh / 3.6).toFloat()
                        accuracy = 3.5f
                        time = System.currentTimeMillis()
                    }

                    handleNewLocation(
                        location,
                        isStayOverride = wp.isStay,
                        stayMsOverride = wp.stayMs,
                        placeNameOverride = wp.placeName,
                        speedOverrideKmh = adjustedSpeedKmh
                    )
                    index++
                }
                delay(2800)
            }
        }
    }

    private data class SimPoint(
        val lat: Double,
        val lng: Double,
        val speedKmh: Double,
        val isStay: Boolean,
        val stayMs: Long,
        val placeName: String,
        val alt: Double
    )

    private fun handleNewLocation(
        location: Location,
        isStayOverride: Boolean = false,
        stayMsOverride: Long = 0L,
        placeNameOverride: String? = null,
        speedOverrideKmh: Double? = null
    ) {
        if (activeTripId <= 0) return
        if (TrackingStateManager.trackingState.value.isPaused) return

        val lat = location.latitude
        val lng = location.longitude
        val alt = location.altitude
        val speedKmh = speedOverrideKmh ?: (location.speed * 3.6).coerceAtLeast(0.0)
        val accuracy = location.accuracy

        // Distance increment
        var distDelta = 0.0
        if (lastLat != null && lastLng != null) {
            distDelta = calculateDistanceMeters(lastLat!!, lastLng!!, lat, lng)
            // Filter abnormal GPS jumps unless in airplane mode or simulating
            if (distDelta > 250.0 && !isSimulating && currentTravelMode != "Airplane") {
                distDelta = 0.0
            }
        }
        lastLat = lat
        lastLng = lng
        accumulatedDistanceMeters += distDelta

        if (speedKmh > maxRecordedSpeed) {
            maxRecordedSpeed = speedKmh
        }
        if (speedKmh > 0.5) {
            speedSum += speedKmh
            speedSamples++
        }
        val avgSpeed = if (speedSamples > 0) speedSum / speedSamples else 0.0

        // Adaptive Stay / Rest Detection Algorithm per mode
        var isStay = isStayOverride
        var stayDurationMs = stayMsOverride

        val staySpeedThreshold = when (currentTravelMode) {
            "Walk" -> 1.2
            "Run" -> 2.0
            "Cycling" -> 3.0
            "Airplane" -> 20.0
            else -> 3.5 // Drive / Transit / Boat
        }

        if (!isSimulating) {
            val now = System.currentTimeMillis()
            if (stationaryAnchorLat == null) {
                stationaryAnchorLat = lat
                stationaryAnchorLng = lng
                stationaryStartTime = now
            } else {
                val distFromAnchor = calculateDistanceMeters(
                    stationaryAnchorLat!!, stationaryAnchorLng!!,
                    lat, lng
                )
                if (distFromAnchor < 30.0 && speedKmh < staySpeedThreshold) {
                    // User has stayed inside 30m radius and speed is below rest threshold
                    val elapsedStationary = now - stationaryStartTime
                    if (elapsedStationary >= 60_000L) { // Stationary for > 1 minute
                        isMarkedStationary = true
                        isStay = true
                        stayDurationMs = elapsedStationary
                    }
                } else {
                    if (isMarkedStationary) {
                        // User was resting and is now moving again
                        isStay = true
                        stayDurationMs = now - stationaryStartTime
                    }
                    stationaryAnchorLat = lat
                    stationaryAnchorLng = lng
                    stationaryStartTime = now
                    isMarkedStationary = false
                }
            }
        } else {
            isMarkedStationary = isStayOverride
        }

        val pointMode = currentTravelMode

        serviceScope.launch {
            val placeName = placeNameOverride ?: if (isStay || (accumulatedDistanceMeters % 400 < 40)) {
                repository.getApproximateAddress(lat, lng)
            } else null

            val pointId = repository.recordLocationPoint(
                tripId = activeTripId,
                lat = lat,
                lng = lng,
                alt = alt,
                speedKmh = speedKmh,
                accuracy = accuracy,
                isStayPoint = isStay,
                stayDurationMs = stayDurationMs,
                locationName = placeName,
                mode = pointMode
            )

            val point = LocationPointEntity(
                id = pointId,
                tripId = activeTripId,
                latitude = lat,
                longitude = lng,
                altitude = alt,
                speedKmh = speedKmh,
                accuracy = accuracy,
                timestamp = System.currentTimeMillis(),
                isStayPoint = isStay,
                stayDurationMs = stayDurationMs,
                locationName = placeName,
                mode = pointMode
            )

            TrackingStateManager.updateState { current ->
                val updatedRecent = (current.recentPoints + point).takeLast(120)
                current.copy(
                    currentLat = lat,
                    currentLng = lng,
                    currentAlt = alt,
                    currentSpeedKmh = speedKmh,
                    maxSpeedKmh = maxRecordedSpeed,
                    avgSpeedKmh = avgSpeed,
                    distanceMeters = accumulatedDistanceMeters,
                    accuracy = accuracy,
                    isCurrentlyStationary = isMarkedStationary,
                    stayCount = if (isStay) current.stayCount + 1 else current.stayCount,
                    currentMode = pointMode,
                    recentPoints = updatedRecent
                )
            }
        }
    }

    private fun stopTrackingSession() {
        val tripId = activeTripId
        activeTripId = 0L
        clearPersistentState()
        releaseWakeLock()

        timerJob?.cancel()
        simulationJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)

        serviceScope.launch {
            if (tripId > 0) {
                repository.stopTrip(tripId)
            }
            TrackingStateManager.stopTracking()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Tracking Continuous Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors real-time trip GPS, multi-modal speeds, and stay locations"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.trip_tracker_icon_1788343891805)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()
        simulationJob?.cancel()
        serviceScope.cancel()
    }

    private fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    companion object {
        const val CHANNEL_ID = "trip_tracking_channel"
        const val NOTIFICATION_ID = 1001

        const val PREFS_NAME = "trip_tracker_persistent_prefs"
        const val KEY_ACTIVE_TRIP_ID = "pref_active_trip_id"
        const val KEY_IS_TRACKING = "pref_is_tracking"
        const val KEY_IS_SIMULATED = "pref_is_simulated"
        const val KEY_CURRENT_MODE = "pref_current_mode"
        const val KEY_START_TIME = "pref_start_time"

        const val ACTION_START = "com.example.action.START"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_RESUME = "com.example.action.RESUME"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_SWITCH_MODE = "com.example.action.SWITCH_MODE"
        const val ACTION_RESUME_AFTER_BOOT = "com.example.action.RESUME_AFTER_BOOT"

        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_SIMULATE = "extra_simulate"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_INITIAL_MODE = "extra_initial_mode"

        fun start(context: Context, tripId: Long, initialMode: String = "Drive", simulate: Boolean = false) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_ID, tripId)
                putExtra(EXTRA_INITIAL_MODE, initialMode)
                putExtra(EXTRA_SIMULATE, simulate)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun resumeAfterReboot(context: Context, tripId: Long, mode: String = "Drive", simulate: Boolean = false) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_RESUME_AFTER_BOOT
                putExtra(EXTRA_TRIP_ID, tripId)
                putExtra(EXTRA_INITIAL_MODE, mode)
                putExtra(EXTRA_SIMULATE, simulate)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun switchMode(context: Context, newMode: String) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_SWITCH_MODE
                putExtra(EXTRA_MODE, newMode)
            }
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun getModeEmoji(mode: String): String {
            return when (mode.lowercase()) {
                "walk" -> "🚶"
                "run" -> "🏃"
                "cycling", "bike", "bicycle" -> "🚴"
                "drive", "car" -> "🚗"
                "transit", "train", "metro" -> "🚆"
                "airplane", "flight" -> "✈️"
                "boat", "ferry", "ship" -> "⛵"
                else -> "🚗"
            }
        }
    }
}
