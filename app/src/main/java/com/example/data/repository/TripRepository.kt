package com.example.data.repository

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.example.data.dao.LocationPointDao
import com.example.data.dao.TripDao
import com.example.data.model.LocationPointEntity
import com.example.data.model.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TripRepository(
    private val tripDao: TripDao,
    private val locationPointDao: LocationPointDao,
    private val context: Context
) {
    val allTrips: Flow<List<TripEntity>> = tripDao.getAllTrips()
    val activeTrip: Flow<TripEntity?> = tripDao.getActiveTrip()

    suspend fun getActiveTripSync(): TripEntity? = withContext(Dispatchers.IO) {
        tripDao.getActiveTripSync()
    }

    fun getTripById(tripId: Long): Flow<TripEntity?> = tripDao.getTripById(tripId)

    suspend fun getTripByIdSync(tripId: Long): TripEntity? = withContext(Dispatchers.IO) {
        tripDao.getTripByIdSync(tripId)
    }

    fun getPointsForTrip(tripId: Long): Flow<List<LocationPointEntity>> =
        locationPointDao.getPointsForTrip(tripId)

    suspend fun getPointsForTripSync(tripId: Long): List<LocationPointEntity> =
        withContext(Dispatchers.IO) {
            locationPointDao.getPointsForTripSync(tripId)
        }

    fun getStayPointsForTrip(tripId: Long): Flow<List<LocationPointEntity>> =
        locationPointDao.getStayPointsForTrip(tripId)

    suspend fun startNewTrip(
        title: String = "",
        category: String = "Trip",
        initialMode: String = "Drive",
        notes: String = ""
    ): Long = withContext(Dispatchers.IO) {
        // Complete any stale active trip
        val existingActive = tripDao.getActiveTripSync()
        if (existingActive != null) {
            val now = System.currentTimeMillis()
            tripDao.updateTrip(existingActive.copy(endTime = now, isCompleted = true))
        }

        val timestamp = System.currentTimeMillis()
        val formattedDate = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
        val tripTitle = if (title.isNotBlank()) title else "Trip on $formattedDate"

        val newTrip = TripEntity(
            title = tripTitle,
            startTime = timestamp,
            category = category,
            currentMode = initialMode,
            modesUsed = initialMode,
            notes = notes,
            isCompleted = false
        )
        tripDao.insertTrip(newTrip)
    }

    suspend fun switchTripMode(tripId: Long, newMode: String) = withContext(Dispatchers.IO) {
        val trip = tripDao.getTripByIdSync(tripId) ?: return@withContext
        val modesList = trip.modesUsed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val updatedModes = if (modesList.contains(newMode)) modesList else modesList + newMode
        tripDao.updateTrip(
            trip.copy(
                currentMode = newMode,
                modesUsed = updatedModes.joinToString(", ")
            )
        )
    }

    suspend fun stopTrip(tripId: Long): Unit = withContext(Dispatchers.IO) {
        val trip = tripDao.getTripByIdSync(tripId) ?: return@withContext
        val points = locationPointDao.getPointsForTripSync(tripId)
        val now = System.currentTimeMillis()

        var distance = 0.0
        var maxSpeed = 0.0
        var speedSum = 0.0
        var speedCount = 0
        var stayCount = 0
        var totalStayMs = 0L
        val modesCollected = mutableSetOf<String>()
        if (trip.currentMode.isNotBlank()) modesCollected.add(trip.currentMode)

        for (i in points.indices) {
            val pt = points[i]
            if (pt.mode.isNotBlank()) {
                modesCollected.add(pt.mode)
            }
            if (pt.speedKmh > maxSpeed) {
                maxSpeed = pt.speedKmh
            }
            if (pt.speedKmh > 0.5) {
                speedSum += pt.speedKmh
                speedCount++
            }
            if (pt.isStayPoint) {
                stayCount++
                totalStayMs += pt.stayDurationMs
            }
            if (i > 0) {
                val prev = points[i - 1]
                distance += calculateDistanceMeters(
                    prev.latitude, prev.longitude,
                    pt.latitude, pt.longitude
                )
            }
        }

        val avgSpeed = if (speedCount > 0) speedSum / speedCount else 0.0
        val startLoc = if (points.isNotEmpty()) {
            points.first().locationName ?: getApproximateAddress(points.first().latitude, points.first().longitude)
        } else ""
        val endLoc = if (points.isNotEmpty()) {
            points.last().locationName ?: getApproximateAddress(points.last().latitude, points.last().longitude)
        } else ""

        val updated = trip.copy(
            endTime = now,
            isCompleted = true,
            distanceMeters = distance,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeed,
            stayCount = stayCount,
            totalStayDurationMs = totalStayMs,
            modesUsed = if (modesCollected.isNotEmpty()) modesCollected.joinToString(", ") else trip.modesUsed,
            startLocationName = startLoc,
            endLocationName = endLoc
        )
        tripDao.updateTrip(updated)
    }

    suspend fun updateTripDetails(
        tripId: Long,
        newTitle: String,
        newCategory: String,
        newNotes: String
    ) = withContext(Dispatchers.IO) {
        val trip = tripDao.getTripByIdSync(tripId) ?: return@withContext
        tripDao.updateTrip(
            trip.copy(
                title = newTitle,
                category = newCategory,
                notes = newNotes
            )
        )
    }

    suspend fun updateTrip(trip: TripEntity) = withContext(Dispatchers.IO) {
        tripDao.updateTrip(trip)
    }

    suspend fun deleteTrip(tripId: Long) = withContext(Dispatchers.IO) {
        locationPointDao.deletePointsForTrip(tripId)
        tripDao.deleteTripById(tripId)
    }

    suspend fun recordLocationPoint(
        tripId: Long,
        lat: Double,
        lng: Double,
        alt: Double,
        speedKmh: Double,
        accuracy: Float,
        isStayPoint: Boolean = false,
        stayDurationMs: Long = 0L,
        locationName: String? = null,
        mode: String = "Drive"
    ): Long = withContext(Dispatchers.IO) {
        val point = LocationPointEntity(
            tripId = tripId,
            latitude = lat,
            longitude = lng,
            altitude = alt,
            speedKmh = speedKmh,
            accuracy = accuracy,
            timestamp = System.currentTimeMillis(),
            isStayPoint = isStayPoint,
            stayDurationMs = stayDurationMs,
            locationName = locationName,
            mode = mode
        )
        val pointId = locationPointDao.insertPoint(point)

        // Live update active trip stats
        val trip = tripDao.getTripByIdSync(tripId)
        if (trip != null) {
            val newMax = if (speedKmh > trip.maxSpeedKmh) speedKmh else trip.maxSpeedKmh
            val prevPoint = locationPointDao.getLatestPointForTrip(tripId)
            val addedDistance = if (prevPoint != null && prevPoint.id != pointId) {
                calculateDistanceMeters(prevPoint.latitude, prevPoint.longitude, lat, lng)
            } else 0.0

            tripDao.updateTrip(
                trip.copy(
                    distanceMeters = trip.distanceMeters + addedDistance,
                    maxSpeedKmh = newMax,
                    stayCount = if (isStayPoint) trip.stayCount + 1 else trip.stayCount,
                    totalStayDurationMs = trip.totalStayDurationMs + stayDurationMs
                )
            )
        }
        pointId
    }

    suspend fun getApproximateAddress(latitude: Double, longitude: Double): String =
        withContext(Dispatchers.IO) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val thoroughfare = addr.thoroughfare ?: addr.featureName
                        val locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                        return@withContext listOfNotNull(thoroughfare, locality).joinToString(", ")
                            .ifBlank { "Lat: %.4f, Lng: %.4f".format(latitude, longitude) }
                    }
                }
            } catch (_: Exception) {}
            "Lat: %.4f, Lng: %.4f".format(latitude, longitude)
        }

    fun exportTripAsGpx(trip: TripEntity, points: List<LocationPointEntity>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Trip Tracker Android\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>${trip.title.replace("&", "&amp;").replace("<", "&lt;")}</name>\n")
        sb.append("    <time>${sdf.format(Date(trip.startTime))}</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>${trip.title.replace("&", "&amp;").replace("<", "&lt;")}</name>\n")
        sb.append("    <type>${trip.category}</type>\n")
        sb.append("    <trkseg>\n")

        for (pt in points) {
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            sb.append("        <ele>${pt.altitude}</ele>\n")
            sb.append("        <time>${sdf.format(Date(pt.timestamp))}</time>\n")
            sb.append("        <speed>${pt.speedKmh / 3.6}</speed>\n")
            if (pt.isStayPoint) {
                sb.append("        <name>Stay: ${pt.stayDurationMs / 60000} mins</name>\n")
            }
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")
        return sb.toString()
    }

    fun exportTripAsJson(trip: TripEntity, points: List<LocationPointEntity>): String {
        val root = JSONObject()
        root.put("id", trip.id)
        root.put("title", trip.title)
        root.put("category", trip.category)
        root.put("notes", trip.notes)
        root.put("startTime", trip.startTime)
        root.put("endTime", trip.endTime ?: System.currentTimeMillis())
        root.put("distanceMeters", trip.distanceMeters)
        root.put("avgSpeedKmh", trip.avgSpeedKmh)
        root.put("maxSpeedKmh", trip.maxSpeedKmh)
        root.put("stayCount", trip.stayCount)
        root.put("totalStayDurationMs", trip.totalStayDurationMs)
        root.put("startLocationName", trip.startLocationName)
        root.put("endLocationName", trip.endLocationName)

        val ptsArray = JSONArray()
        for (pt in points) {
            val pObj = JSONObject()
            pObj.put("lat", pt.latitude)
            pObj.put("lng", pt.longitude)
            pObj.put("alt", pt.altitude)
            pObj.put("speedKmh", pt.speedKmh)
            pObj.put("time", pt.timestamp)
            pObj.put("isStay", pt.isStayPoint)
            pObj.put("stayDurationMs", pt.stayDurationMs)
            pObj.put("name", pt.locationName ?: "")
            ptsArray.put(pObj)
        }
        root.put("points", ptsArray)
        return root.toString(2)
    }

    private fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
