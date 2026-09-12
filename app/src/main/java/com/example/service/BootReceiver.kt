package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("BootReceiver", "Received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = context.getSharedPreferences(LocationTrackingService.PREFS_NAME, Context.MODE_PRIVATE)
            val isTracking = prefs.getBoolean(LocationTrackingService.KEY_IS_TRACKING, false)
            val activeTripId = prefs.getLong(LocationTrackingService.KEY_ACTIVE_TRIP_ID, -1L)
            val isSimulated = prefs.getBoolean(LocationTrackingService.KEY_IS_SIMULATED, false)
            val mode = prefs.getString(LocationTrackingService.KEY_CURRENT_MODE, "Drive") ?: "Drive"

            if (isTracking && activeTripId > 0) {
                Log.i("BootReceiver", "Resuming continuous tracking for trip $activeTripId after power on/reboot")
                LocationTrackingService.resumeAfterReboot(context, activeTripId, mode, isSimulated)
            }
        }
    }
}
