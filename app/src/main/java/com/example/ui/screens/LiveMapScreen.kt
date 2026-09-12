package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationPointEntity
import com.example.service.LocationTrackingService
import com.example.ui.components.MapThemeMode
import com.example.ui.components.OfflineMapView
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.MainViewModel

@Composable
fun LiveMapScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val liveState by viewModel.liveTrackingState.collectAsState()
    val context = LocalContext.current
    var selectedMapTheme by remember { mutableStateOf(MapThemeMode.ROADS_AND_NAMES_ONLY) }

    val travelModes = listOf(
        TravelModeItem("Walk", "🚶", "Walk", "4-6 km/h"),
        TravelModeItem("Run", "🏃", "Run", "10-15 km/h"),
        TravelModeItem("Cycling", "🚴", "Bike", "20-30 km/h"),
        TravelModeItem("Drive", "🚗", "Drive", "50-100 km/h"),
        TravelModeItem("Transit", "🚆", "Transit", "60-120 km/h"),
        TravelModeItem("Airplane", "✈️", "Flight", "700-900 km/h"),
        TravelModeItem("Boat", "⛵", "Boat", "20-40 km/h")
    )

    val currentPoint = if (liveState.currentLat != null && liveState.currentLng != null) {
        LocationPointEntity(
            tripId = liveState.tripId ?: 0L,
            latitude = liveState.currentLat!!,
            longitude = liveState.currentLng!!,
            altitude = liveState.currentAlt,
            speedKmh = liveState.currentSpeedKmh,
            accuracy = liveState.accuracy,
            timestamp = System.currentTimeMillis(),
            isStayPoint = liveState.isCurrentlyStationary,
            stayDurationMs = liveState.currentStayDurationMs,
            mode = liveState.currentMode
        )
    } else null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Fullscreen Real Slippy Tile Map with Satellite, Roads, Topo, and Hybrid modes
        OfflineMapView(
            points = liveState.recentPoints,
            activeLivePoint = currentPoint,
            modifier = Modifier.fillMaxSize(),
            showControls = true,
            isInteractive = true,
            initialTheme = selectedMapTheme,
            topPadding = 48.dp
        )

        // Top Continuous Tracking & Mode Status Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Power / Reboot Continuous Tracking Shield Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(if (liveState.isTracking) SuccessGreen else Color.Gray, CircleShape)
                        )
                        Text(
                            text = if (liveState.isTracking) "CONTINUOUS TRACKING ON" else "READY TO TRACK",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (liveState.isTracking) SuccessGreen else Color.LightGray,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Auto-resumes on reboot / discharge",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentTeal
                        )
                    }
                }
            }
        }

        // Floating Bottom HUD with Multi-mode Switching & Telemetry
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.97f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {

                // Mode Selector Bar (Multi-select / Mode Switch in trip: Walk, Drive, Airplane, etc.)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SWITCH TRAVEL MODE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            letterSpacing = 0.8.sp
                        )

                        Text(
                            text = "Active: ${LocationTrackingService.getModeEmoji(liveState.currentMode)} ${liveState.currentMode}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        travelModes.forEach { modeItem ->
                            val isSelected = liveState.currentMode.equals(modeItem.id, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) PrimaryBlue else DarkSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) AccentCyan else Color(0xFF334155),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        viewModel.switchMode(modeItem.id)
                                    }
                                    .testTag("mode_chip_${modeItem.id.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(text = modeItem.emoji, fontSize = 13.sp)
                                    Column {
                                        Text(
                                            text = modeItem.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) Color.White else Color(0xFFE2E8F0)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Real-time Metrics & Speed / Stay indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (liveState.isCurrentlyStationary) PurpleAccent.copy(alpha = 0.25f) else PrimaryBlue.copy(alpha = 0.25f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (liveState.isCurrentlyStationary) Icons.Default.Timer else Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = if (liveState.isCurrentlyStationary) PurpleAccent else AccentCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "%.1f km/h".format(liveState.currentSpeedKmh),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (liveState.isCurrentlyStationary) {
                                    val stayMins = (liveState.currentStayDurationMs / 60000)
                                    val staySecs = (liveState.currentStayDurationMs % 60000) / 1000
                                    "☕ Resting ($stayMins m $staySecs s)"
                                } else "${liveState.currentMode} Speed",
                                fontSize = 11.sp,
                                color = if (liveState.isCurrentlyStationary) PurpleAccent else AccentCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Distance, Time & Stays
                    Column(horizontalAlignment = Alignment.End) {
                        val hrs = liveState.elapsedSeconds / 3600
                        val mins = (liveState.elapsedSeconds % 3600) / 60
                        val secs = liveState.elapsedSeconds % 60
                        val timeStr = "%02d:%02d:%02d".format(hrs, mins, secs)

                        Text(
                            text = "%.2f km".format(liveState.distanceMeters / 1000.0),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlueLight
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = timeStr,
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                            if (liveState.stayCount > 0) {
                                Text(
                                    text = "• ${liveState.stayCount} stops",
                                    fontSize = 11.sp,
                                    color = PurpleAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Action Controls (Start / Pause / Resume / Stop)
                if (liveState.isTracking) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (liveState.isPaused) viewModel.resumeTracking() else viewModel.pauseTracking()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (liveState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (liveState.isPaused) SuccessGreen else WarningAmber
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (liveState.isPaused) "Resume" else "Pause",
                                color = if (liveState.isPaused) SuccessGreen else WarningAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { viewModel.stopTracking() },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stop_tracking_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop & Save", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startTracking(initialMode = liveState.currentMode, simulate = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("start_tracking_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Tracking", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.startTracking(initialMode = liveState.currentMode, simulate = true) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("demo_route_button")
                        ) {
                            Text("Demo Route", fontSize = 12.sp, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private data class TravelModeItem(
    val id: String,
    val emoji: String,
    val label: String,
    val speedHint: String
)
