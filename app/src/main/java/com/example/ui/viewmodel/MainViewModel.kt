package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.LocationPointEntity
import com.example.data.model.TripEntity
import com.example.data.model.UserProfile
import com.example.data.repository.TripRepository
import com.example.data.repository.UserRepository
import com.example.service.LiveTrackingState
import com.example.service.LocationTrackingService
import com.example.service.TrackingStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OverallStats(
    val totalTrips: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalStayCount: Int = 0,
    val maxSpeedOverallKmh: Double = 0.0,
    val totalDurationSeconds: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val tripRepository = TripRepository(db.tripDao(), db.locationPointDao(), application)
    val userRepository = UserRepository(application)

    val liveTrackingState: StateFlow<LiveTrackingState> = TrackingStateManager.trackingState
    val userProfile: StateFlow<UserProfile> = userRepository.userProfile

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing = _isCloudSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    // Filtered trips flow
    val allTrips = tripRepository.allTrips.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredTrips: StateFlow<List<TripEntity>> = combine(
        allTrips,
        _searchQuery,
        _selectedCategory
    ) { trips, query, category ->
        trips.filter { trip ->
            val matchesQuery = query.isBlank() ||
                    trip.title.contains(query, ignoreCase = true) ||
                    trip.notes.contains(query, ignoreCase = true) ||
                    trip.startLocationName.contains(query, ignoreCase = true) ||
                    trip.endLocationName.contains(query, ignoreCase = true)

            val matchesCategory = category == "All" || trip.category.equals(category, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val overallStats: StateFlow<OverallStats> = allTrips.combine(liveTrackingState) { trips, live ->
        var totalDistMeters = 0.0
        var totalStays = 0
        var maxSpeed = 0.0
        var totalSeconds = 0L

        for (t in trips) {
            totalDistMeters += t.distanceMeters
            totalStays += t.stayCount
            if (t.maxSpeedKmh > maxSpeed) maxSpeed = t.maxSpeedKmh
            val start = t.startTime
            val end = t.endTime ?: start
            totalSeconds += ((end - start) / 1000).coerceAtLeast(0)
        }

        OverallStats(
            totalTrips = trips.size,
            totalDistanceKm = totalDistMeters / 1000.0,
            totalStayCount = totalStays,
            maxSpeedOverallKmh = maxSpeed,
            totalDurationSeconds = totalSeconds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OverallStats()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun startTracking(title: String = "", category: String = "Trip", initialMode: String = "Drive", simulate: Boolean = false) {
        viewModelScope.launch {
            val tripId = tripRepository.startNewTrip(
                title = title,
                category = category,
                initialMode = initialMode
            )
            LocationTrackingService.start(getApplication(), tripId, initialMode, simulate)
        }
    }

    fun switchMode(newMode: String) {
        TrackingStateManager.switchMode(newMode)
        LocationTrackingService.switchMode(getApplication(), newMode)
    }

    fun pauseTracking() {
        LocationTrackingService.pause(getApplication())
    }

    fun resumeTracking() {
        LocationTrackingService.resume(getApplication())
    }

    fun stopTracking() {
        LocationTrackingService.stop(getApplication())
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
        }
    }

    fun updateTrip(tripId: Long, title: String, category: String, notes: String) {
        viewModelScope.launch {
            tripRepository.updateTripDetails(tripId, title, category, notes)
        }
    }

    fun signInWithGoogle(email: String, name: String) {
        viewModelScope.launch {
            userRepository.signInWithGoogle(email, name)
            syncToCloud()
        }
    }

    fun signInWithEmail(email: String, name: String) {
        viewModelScope.launch {
            userRepository.signInWithEmail(email, name)
            syncToCloud()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            userRepository.signOut()
        }
    }

    fun syncToCloud() {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            val count = allTrips.value.size
            kotlinx.coroutines.delay(1200) // Realistic cloud sync feel
            userRepository.syncWithCloud(count)
            _isCloudSyncing.value = false
            _syncMessage.value = "Successfully synced $count trips to Cloud Backup!"
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
