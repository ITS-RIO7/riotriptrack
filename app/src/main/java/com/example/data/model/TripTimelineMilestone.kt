package com.example.data.model

data class TripTimelineMilestone(
    val id: String,
    val sequenceNumber: Int,
    val point: LocationPointEntity,
    val title: String,
    val subtitle: String,
    val timeFormatted: String,
    val durationFormatted: String? = null,
    val speedKmh: Double = 0.0,
    val altitudeM: Double = 0.0,
    val distanceKmFromStart: Double = 0.0,
    val type: MilestoneType = MilestoneType.CHECKPOINT
)

enum class MilestoneType {
    START,
    STAY_STOP,
    CHECKPOINT,
    FASTEST_POINT,
    FINISH
}
