package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationPointEntity
import com.example.ui.components.OfflineMapView
import com.example.ui.components.SpeedAltitudeChart
import com.example.ui.dialogs.TripEditDialog
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.TripDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    viewModel: TripDetailViewModel,
    onBack: () -> Unit,
    onDeleteTrip: (Long) -> Unit
) {
    val trip by viewModel.trip.collectAsState()
    val points by viewModel.points.collectAsState()
    val stayPoints by viewModel.stayPoints.collectAsState()
    val scrubberProgress by viewModel.scrubberProgress.collectAsState()
    val isPlayingTimeline by viewModel.isPlayingTimeline.collectAsState()
    val selectedPoint by viewModel.selectedPoint.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val currentTrip = trip

    if (currentTrip == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading trip details...", color = Color.White)
        }
        return
    }

    val durationMs = if (currentTrip.endTime != null) {
        (currentTrip.endTime - currentTrip.startTime).coerceAtLeast(0)
    } else {
        (System.currentTimeMillis() - currentTrip.startTime).coerceAtLeast(0)
    }
    val durHours = durationMs / 3600000
    val durMins = (durationMs % 3600000) / 60000
    val durSecs = (durationMs % 60000) / 1000
    val formattedDuration = if (durHours > 0) "${durHours}h ${durMins}m ${durSecs}s" else "${durMins}m ${durSecs}s"

    val startDateStr = SimpleDateFormat("EEEE, MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(currentTrip.startTime))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentTrip.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = startDateStr,
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("trip_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.testTag("edit_trip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Trip",
                            tint = PrimaryBlueLight
                        )
                    }
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("export_trip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Data",
                            tint = AccentCyan
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_trip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Trip",
                            tint = DangerRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Offline Map View Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Route Map & Satellite",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )

                            IconButton(
                                onClick = { isMapExpanded = !isMapExpanded },
                                modifier = Modifier.size(28.dp).testTag("toggle_map_size_button")
                            ) {
                                Icon(
                                    imageVector = if (isMapExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isMapExpanded) "Collapse Map" else "Expand Map",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        OfflineMapView(
                            points = points,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isMapExpanded) 420.dp else 270.dp),
                            scrubberProgress = scrubberProgress,
                            onScrubPointSelected = { pt ->
                                viewModel.setSelectedPoint(pt)
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Timeline Scrubber Slider & Playback Controls
                        if (points.size > 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryBlue,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.toggleTimelinePlayback() },
                                        modifier = Modifier.size(36.dp).testTag("play_pause_scrubber_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingTimeline) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlayingTimeline) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Slider(
                                    value = scrubberProgress ?: 1f,
                                    onValueChange = { viewModel.setScrubberProgress(it) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = WarningAmber,
                                        activeTrackColor = AccentCyan,
                                        inactiveTrackColor = DarkBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("timeline_scrubber_slider")
                                )

                                Text(
                                    text = if (scrubberProgress != null && points.isNotEmpty()) {
                                        val idx = ((points.size - 1) * scrubberProgress!!).toInt().coerceIn(0, points.size - 1)
                                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(points[idx].timestamp))
                                    } else "Live",
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Trip Key Metrics Summary Banner
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        title = "DISTANCE",
                        value = "%.2f".format(currentTrip.distanceMeters / 1000.0),
                        unit = "km",
                        icon = Icons.Default.LocationOn,
                        tint = PrimaryBlueLight,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "DURATION",
                        value = formattedDuration,
                        unit = "",
                        icon = Icons.Default.Timer,
                        tint = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        title = "AVG SPEED",
                        value = "%.1f".format(currentTrip.avgSpeedKmh),
                        unit = "km/h",
                        icon = Icons.Default.Speed,
                        tint = AccentCyan,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "MAX SPEED",
                        value = "%.1f".format(currentTrip.maxSpeedKmh),
                        unit = "km/h",
                        icon = Icons.Default.Speed,
                        tint = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "STOPS / STAYS",
                        value = "${currentTrip.stayCount}",
                        unit = "places",
                        icon = Icons.Default.Timer,
                        tint = PurpleAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Speed & Altitude Profile Chart
            item {
                SpeedAltitudeChart(
                    points = points,
                    modifier = Modifier.fillMaxWidth(),
                    selectedIndex = if (scrubberProgress != null && points.isNotEmpty()) {
                        ((points.size - 1) * scrubberProgress!!).toInt().coerceIn(0, points.size - 1)
                    } else null,
                    onPointHovered = { idx ->
                        if (idx != null && idx in points.indices) {
                            viewModel.setScrubberProgress(idx.toFloat() / (points.size - 1))
                        }
                    }
                )
            }

            // 4. Stay / Visited Places Breakdown
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = PurpleAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Stay & Visited Places Timeline",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        val totalStayMins = currentTrip.totalStayDurationMs / 60000
                        Text(
                            text = if (currentTrip.stayCount > 0) {
                                "Recorded ${currentTrip.stayCount} stops with a total stationary duration of $totalStayMins mins."
                            } else {
                                "Non-stop continuous trip (no long stationary stops detected)."
                            },
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )

                        if (stayPoints.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            stayPoints.forEachIndexed { index, stay ->
                                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(stay.timestamp))
                                val stayMins = (stay.stayDurationMs / 60000).coerceAtLeast(1)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = PurpleAccent.copy(alpha = 0.2f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PurpleAccent
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stay.locationName ?: "Stop #${index + 1} (Lat: %.4f, Lng: %.4f)".format(stay.latitude, stay.longitude),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Arrived around $timeStr · Stayed for ~$stayMins mins",
                                            fontSize = 11.sp,
                                            color = PurpleAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Notes & Memo Section
            if (currentTrip.notes.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Trip Notes & Memo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlueLight
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentTrip.notes,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 6. Action Share Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareSummary = """
                                🗺️ Trip: ${currentTrip.title}
                                📅 Date: $startDateStr
                                📍 Distance: ${"%.2f".format(currentTrip.distanceMeters / 1000.0)} km
                                ⏱️ Duration: $formattedDuration
                                🚗 Max Speed: ${"%.1f".format(currentTrip.maxSpeedKmh)} km/h
                                ⏱️ Stops: ${currentTrip.stayCount} places visited
                                ${if (currentTrip.notes.isNotBlank()) "📝 Notes: " + currentTrip.notes else ""}
                            """.trimIndent()

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareSummary)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Trip Summary"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_trip_summary_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Summary", color = AccentCyan)
                    }

                    Button(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_data_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Data")
                    }
                }
            }
        }
    }

    // Edit Trip Dialog
    if (showEditDialog) {
        TripEditDialog(
            initialTitle = currentTrip.title,
            initialCategory = currentTrip.category,
            initialNotes = currentTrip.notes,
            onDismiss = { showEditDialog = false },
            onSave = { title, cat, notes ->
                viewModel.updateTripDetails(title, cat, notes)
                showEditDialog = false
                Toast.makeText(context, "Trip details updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkSurface,
            title = { Text("Delete Trip?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${currentTrip.title}'? This action cannot be undone.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteTrip(currentTrip.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.testTag("confirm_delete_trip_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.testTag("cancel_delete_trip_button")
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Export Dialog (GPX / JSON)
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text("Export Recorded Trip", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose format to copy or export your GPS route and stay timestamps:",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )

                    Button(
                        onClick = {
                            val gpx = viewModel.exportGpx()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Trip GPX", gpx))
                            Toast.makeText(context, "GPX XML copied to clipboard!", Toast.LENGTH_LONG).show()
                            showExportDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_gpx_button")
                    ) {
                        Text("Copy GPX Route (GPS Exchange Format)")
                    }

                    Button(
                        onClick = {
                            val json = viewModel.exportJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Trip JSON", json))
                            Toast.makeText(context, "JSON Data copied to clipboard!", Toast.LENGTH_LONG).show()
                            showExportDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_json_button")
                    ) {
                        Text("Copy Raw JSON Timeline", color = PrimaryBlueLight)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    modifier = Modifier.testTag("close_export_button")
                ) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        fontSize = 10.sp,
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        "Drive" -> Icons.Default.DirectionsCar
        "Walk" -> Icons.Default.DirectionsWalk
        "Cycling" -> Icons.Default.DirectionsBike
        "Run" -> Icons.Default.DirectionsRun
        "Transit" -> Icons.Default.Train
        else -> Icons.Default.Flight
    }
}
