package com.example.service

import com.example.data.model.LocationPointEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveTrackingState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val tripId: Long? = null,
    val currentMode: String = "Drive", // Walk, Run, Cycling, Drive, Transit, Airplane, Boat
    val modesUsed: List<String> = listOf("Drive"),
    val currentSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val currentAlt: Double = 0.0,
    val accuracy: Float = 0f,
    val stayCount: Int = 0,
    val currentStayDurationMs: Long = 0L,
    val isCurrentlyStationary: Boolean = false,
    val isSimulated: Boolean = false,
    val isAutoResumedFromReboot: Boolean = false,
    val recentPoints: List<LocationPointEntity> = emptyList()
)

object TrackingStateManager {
    private val _trackingState = MutableStateFlow(LiveTrackingState())
    val trackingState: StateFlow<LiveTrackingState> = _trackingState.asStateFlow()

    fun updateState(transform: (LiveTrackingState) -> LiveTrackingState) {
        _trackingState.value = transform(_trackingState.value)
    }

    fun startTracking(tripId: Long, initialMode: String = "Drive", isSimulated: Boolean = false, isAutoResumed: Boolean = false) {
        _trackingState.value = LiveTrackingState(
            isTracking = true,
            isPaused = false,
            tripId = tripId,
            currentMode = initialMode,
            modesUsed = listOf(initialMode),
            isSimulated = isSimulated,
            isAutoResumedFromReboot = isAutoResumed
        )
    }

    fun switchMode(newMode: String) {
        _trackingState.value = _trackingState.value.let { state ->
            val updatedModes = if (state.modesUsed.contains(newMode)) state.modesUsed else state.modesUsed + newMode
            state.copy(
                currentMode = newMode,
                modesUsed = updatedModes
            )
        }
    }

    fun pauseTracking() {
        _trackingState.value = _trackingState.value.copy(isPaused = true)
    }

    fun resumeTracking() {
        _trackingState.value = _trackingState.value.copy(isPaused = false)
    }

    fun stopTracking() {
        _trackingState.value = LiveTrackingState()
    }
}
