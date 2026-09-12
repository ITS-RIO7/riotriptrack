package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class UserRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private fun loadProfile(): UserProfile {
        return UserProfile(
            isLoggedIn = prefs.getBoolean("isLoggedIn", false),
            displayName = prefs.getString("displayName", "Guest Explorer") ?: "Guest Explorer",
            email = prefs.getString("email", "guest@tracker.offline") ?: "guest@tracker.offline",
            photoUrl = prefs.getString("photoUrl", null),
            authProvider = prefs.getString("authProvider", "guest") ?: "guest",
            cloudSyncEnabled = prefs.getBoolean("cloudSyncEnabled", true),
            lastSyncTimestamp = prefs.getLong("lastSyncTimestamp", 0L),
            totalCloudSavedTrips = prefs.getInt("totalCloudSavedTrips", 0),
            preferredSpeedUnit = prefs.getString("preferredSpeedUnit", "km/h") ?: "km/h",
            stayDetectionMinutes = prefs.getInt("stayDetectionMinutes", 2)
        )
    }

    suspend fun signInWithGoogle(email: String, name: String) = withContext(Dispatchers.IO) {
        val updated = _userProfile.value.copy(
            isLoggedIn = true,
            displayName = name.ifBlank { "Google User" },
            email = email.ifBlank { "user@gmail.com" },
            authProvider = "google",
            lastSyncTimestamp = System.currentTimeMillis()
        )
        saveProfile(updated)
    }

    suspend fun signInWithEmail(email: String, name: String) = withContext(Dispatchers.IO) {
        val updated = _userProfile.value.copy(
            isLoggedIn = true,
            displayName = name.ifBlank { email.substringBefore("@") },
            email = email,
            authProvider = "email",
            lastSyncTimestamp = System.currentTimeMillis()
        )
        saveProfile(updated)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val updated = UserProfile(
            isLoggedIn = false,
            displayName = "Guest Explorer",
            email = "guest@tracker.offline",
            authProvider = "guest",
            lastSyncTimestamp = 0L,
            totalCloudSavedTrips = 0
        )
        saveProfile(updated)
    }

    suspend fun syncWithCloud(tripCount: Int) = withContext(Dispatchers.IO) {
        val updated = _userProfile.value.copy(
            lastSyncTimestamp = System.currentTimeMillis(),
            totalCloudSavedTrips = tripCount
        )
        saveProfile(updated)
    }

    suspend fun updateSettings(speedUnit: String, stayMinutes: Int) = withContext(Dispatchers.IO) {
        val updated = _userProfile.value.copy(
            preferredSpeedUnit = speedUnit,
            stayDetectionMinutes = stayMinutes
        )
        saveProfile(updated)
    }

    private fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putBoolean("isLoggedIn", profile.isLoggedIn)
            .putString("displayName", profile.displayName)
            .putString("email", profile.email)
            .putString("photoUrl", profile.photoUrl)
            .putString("authProvider", profile.authProvider)
            .putBoolean("cloudSyncEnabled", profile.cloudSyncEnabled)
            .putLong("lastSyncTimestamp", profile.lastSyncTimestamp)
            .putInt("totalCloudSavedTrips", profile.totalCloudSavedTrips)
            .putString("preferredSpeedUnit", profile.preferredSpeedUnit)
            .putInt("stayDetectionMinutes", profile.stayDetectionMinutes)
            .apply()
        _userProfile.value = profile
    }
}
