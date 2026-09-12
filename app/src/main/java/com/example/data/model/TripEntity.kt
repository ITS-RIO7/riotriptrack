package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val distanceMeters: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val category: String = "Trip", // Drive, Walk, Run, Cycling, Transit, Trip
    val currentMode: String = "Drive", // Walk, Run, Cycling, Drive, Transit, Airplane, Boat
    val modesUsed: String = "Drive", // Comma-separated history of modes used
    val notes: String = "",
    val isCompleted: Boolean = false,
    val stayCount: Int = 0,
    val totalStayDurationMs: Long = 0L,
    val startLocationName: String = "",
    val endLocationName: String = ""
)
