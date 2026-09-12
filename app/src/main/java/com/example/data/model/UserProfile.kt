package com.example.data.model

data class UserProfile(
    val isLoggedIn: Boolean = false,
    val displayName: String = "Guest User",
    val email: String = "guest@offline.local",
    val photoUrl: String? = null,
    val authProvider: String = "guest", // "google", "email", "guest"
    val cloudSyncEnabled: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val totalCloudSavedTrips: Int = 0,
    val preferredSpeedUnit: String = "km/h", // "km/h" or "mph"
    val stayDetectionMinutes: Int = 2 // threshold for stay detection
)
