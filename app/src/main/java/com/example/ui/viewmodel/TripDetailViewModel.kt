package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.LocationPointEntity
import com.example.data.model.TripEntity
import com.example.data.repository.TripRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TripDetailViewModel(
    application: Application,
    val tripId: Long
) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val tripRepository = TripRepository(db.tripDao(), db.locationPointDao(), application)

    val trip: StateFlow<TripEntity?> = tripRepository.getTripById(tripId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val points: StateFlow<List<LocationPointEntity>> = tripRepository.getPointsForTrip(tripId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val stayPoints: StateFlow<List<LocationPointEntity>> = tripRepository.getStayPointsForTrip(tripId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Interactive timeline playback / scrubber state
    private val _scrubberProgress = MutableStateFlow<Float?>(null)
    val scrubberProgress = _scrubberProgress.asStateFlow()

    private val _isPlayingTimeline = MutableStateFlow(false)
    val isPlayingTimeline = _isPlayingTimeline.asStateFlow()

    private val _selectedPoint = MutableStateFlow<LocationPointEntity?>(null)
    val selectedPoint = _selectedPoint.asStateFlow()

    private var playbackJob: Job? = null

    fun setScrubberProgress(progress: Float) {
        _scrubberProgress.value = progress.coerceIn(0f, 1f)
        val pts = points.value
        if (pts.isNotEmpty()) {
            val idx = ((pts.size - 1) * progress).toInt().coerceIn(0, pts.size - 1)
            _selectedPoint.value = pts[idx]
        }
    }

    fun setSelectedPoint(point: LocationPointEntity?) {
        _selectedPoint.value = point
    }

    fun toggleTimelinePlayback() {
        if (_isPlayingTimeline.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        _isPlayingTimeline.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var curr = _scrubberProgress.value ?: 0f
            if (curr >= 0.98f) curr = 0f

            while (isActive && curr < 1.0f) {
                curr += 0.015f
                if (curr > 1.0f) curr = 1.0f
                setScrubberProgress(curr)
                delay(80)
            }
            _isPlayingTimeline.value = false
        }
    }

    private fun stopPlayback() {
        _isPlayingTimeline.value = false
        playbackJob?.cancel()
    }

    fun updateTripDetails(newTitle: String, newCategory: String, newNotes: String) {
        viewModelScope.launch {
            tripRepository.updateTripDetails(tripId, newTitle, newCategory, newNotes)
        }
    }

    fun exportGpx(): String {
        val t = trip.value ?: return ""
        val pts = points.value
        return tripRepository.exportTripAsGpx(t, pts)
    }

    fun exportJson(): String {
        val t = trip.value ?: return ""
        val pts = points.value
        return tripRepository.exportTripAsJson(t, pts)
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
