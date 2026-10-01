package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.LocationPointEntity
import com.example.data.model.MilestoneType
import com.example.data.model.TripEntity
import com.example.data.model.TripTimelineMilestone
import com.example.data.repository.TripRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Complete Chronological Journey Milestones ("Kahan Kahan Gaya")
    val timelineMilestones: StateFlow<List<TripTimelineMilestone>> = combine(points, stayPoints, trip) { pts, stays, t ->
        if (pts.isEmpty()) return@combine emptyList()
        val list = mutableListOf<TripTimelineMilestone>()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

        // 1. Departure / Start
        val first = pts.first()
        val startSubtitle = first.locationName ?: t?.startLocationName?.ifBlank { null } ?: "Lat: %.4f, Lng: %.4f".format(first.latitude, first.longitude)
        list.add(
            TripTimelineMilestone(
                id = "start_${first.id}",
                sequenceNumber = 1,
                point = first,
                title = "Departure Point",
                subtitle = startSubtitle,
                timeFormatted = timeFmt.format(Date(first.timestamp)),
                durationFormatted = "Trip Started",
                speedKmh = first.speedKmh,
                altitudeM = first.altitude,
                distanceKmFromStart = 0.0,
                type = MilestoneType.START
            )
        )

        var seq = 2
        val stayIds = mutableSetOf<Long>()
        stays.forEachIndexed { index, stay ->
            stayIds.add(stay.id)
            val stayMins = (stay.stayDurationMs / 60000).coerceAtLeast(1)
            val subtitle = stay.locationName ?: "Lat: %.4f, Lng: %.4f".format(stay.latitude, stay.longitude)
            list.add(
                TripTimelineMilestone(
                    id = "stay_${stay.id}",
                    sequenceNumber = seq++,
                    point = stay,
                    title = "Stop #%d: Rested %d min%s".format(index + 1, stayMins, if (stayMins > 1) "s" else ""),
                    subtitle = subtitle,
                    timeFormatted = timeFmt.format(Date(stay.timestamp)),
                    durationFormatted = "Stayed for $stayMins mins",
                    speedKmh = stay.speedKmh,
                    altitudeM = stay.altitude,
                    distanceKmFromStart = 0.0,
                    type = MilestoneType.STAY_STOP
                )
            )
        }

        // Top speed sprint point
        if (pts.size > 10) {
            val maxPt = pts.maxByOrNull { it.speedKmh }
            if (maxPt != null && maxPt.speedKmh > 15.0 && !stayIds.contains(maxPt.id) && maxPt.id != first.id && maxPt.id != pts.last().id) {
                list.add(
                    TripTimelineMilestone(
                        id = "speed_${maxPt.id}",
                        sequenceNumber = seq++,
                        point = maxPt,
                        title = "Speed Sprint: %.1f km/h".format(maxPt.speedKmh),
                        subtitle = maxPt.locationName ?: "Fastest Segment",
                        timeFormatted = timeFmt.format(Date(maxPt.timestamp)),
                        durationFormatted = "Peak Speed",
                        speedKmh = maxPt.speedKmh,
                        altitudeM = maxPt.altitude,
                        distanceKmFromStart = 0.0,
                        type = MilestoneType.FASTEST_POINT
                    )
                )
            }
        }

        // 3. Final Destination
        if (pts.size >= 2) {
            val last = pts.last()
            val endSubtitle = last.locationName ?: t?.endLocationName?.ifBlank { null } ?: "Lat: %.4f, Lng: %.4f".format(last.latitude, last.longitude)
            val totalDistKm = (t?.distanceMeters ?: 0.0) / 1000.0
            list.add(
                TripTimelineMilestone(
                    id = "finish_${last.id}",
                    sequenceNumber = seq,
                    point = last,
                    title = "Destination Reached",
                    subtitle = endSubtitle,
                    timeFormatted = timeFmt.format(Date(last.timestamp)),
                    durationFormatted = "Total %.2f km".format(totalDistKm),
                    speedKmh = last.speedKmh,
                    altitudeM = last.altitude,
                    distanceKmFromStart = totalDistKm,
                    type = MilestoneType.FINISH
                )
            )
        }

        list.sortedBy { it.point.timestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedMilestone = MutableStateFlow<TripTimelineMilestone?>(null)
    val selectedMilestone: StateFlow<TripTimelineMilestone?> = _selectedMilestone.asStateFlow()

    fun selectMilestone(milestone: TripTimelineMilestone?) {
        _selectedMilestone.value = milestone
        if (milestone != null) {
            _selectedPoint.value = milestone.point
            val pts = points.value
            val idx = pts.indexOfFirst { it.id == milestone.point.id }
            if (idx >= 0 && pts.isNotEmpty()) {
                _scrubberProgress.value = idx.toFloat() / (pts.size - 1).coerceAtLeast(1)
            }
        }
    }

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

    private val _isAiAnalyzing = MutableStateFlow(false)
    val isAiAnalyzing: StateFlow<Boolean> = _isAiAnalyzing.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _customAiAnswer = MutableStateFlow<String?>(null)
    val customAiAnswer: StateFlow<String?> = _customAiAnswer.asStateFlow()

    private val _isAnswering = MutableStateFlow(false)
    val isAnswering: StateFlow<Boolean> = _isAnswering.asStateFlow()

    fun generateAiInsights() {
        val currentTrip = trip.value ?: return
        _isAiAnalyzing.value = true
        _aiError.value = null
        viewModelScope.launch {
            val result = com.example.data.api.GeminiTripAiService.analyzeTrip(
                trip = currentTrip,
                milestones = timelineMilestones.value
            )
            result.onSuccess { aiResult ->
                val updated = currentTrip.copy(
                    aiSummary = aiResult.summary,
                    aiEcoScore = aiResult.ecoScore,
                    aiStyleTag = aiResult.styleTag,
                    aiTips = aiResult.drivingTips
                )
                tripRepository.updateTrip(updated)
                _isAiAnalyzing.value = false
            }.onFailure { ex ->
                _aiError.value = ex.message ?: "Failed to generate AI insights."
                _isAiAnalyzing.value = false
            }
        }
    }

    fun askAiQuestion(question: String) {
        val currentTrip = trip.value ?: return
        if (question.isBlank()) return
        _isAnswering.value = true
        _aiError.value = null
        viewModelScope.launch {
            val result = com.example.data.api.GeminiTripAiService.askTripQuestion(currentTrip, question)
            result.onSuccess { answer ->
                _customAiAnswer.value = answer
                _isAnswering.value = false
            }.onFailure { ex ->
                _aiError.value = ex.message ?: "Failed to answer question."
                _isAnswering.value = false
            }
        }
    }

    fun clearAiError() {
        _aiError.value = null
    }

    fun clearCustomAnswer() {
        _customAiAnswer.value = null
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }
}
