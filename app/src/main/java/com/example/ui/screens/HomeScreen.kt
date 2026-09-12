package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.TripEntity
import com.example.ui.components.OfflineMapView
import com.example.ui.dialogs.TripEditDialog
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onTripSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val liveState by viewModel.liveTrackingState.collectAsState()
    val trips by viewModel.filteredTrips.collectAsState()
    val overallStats by viewModel.overallStats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var tripTitleInput by remember { mutableStateOf("") }
    var tripCategoryInput by remember { mutableStateOf("Drive") }
    var isSimulateMode by remember { mutableStateOf(false) }

    var tripToEdit by remember { mutableStateOf<TripEntity?>(null) }
    var tripToDelete by remember { mutableStateOf<TripEntity?>(null) }

    val context = LocalContext.current

    // Runtime Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.startTracking(
                title = tripTitleInput,
                category = tripCategoryInput,
                initialMode = tripCategoryInput,
                simulate = isSimulateMode
            )
            Toast.makeText(context, "Location tracking started!", Toast.LENGTH_SHORT).show()
        } else {
            // If user denies permission, suggest simulation mode for testing
            Toast.makeText(
                context,
                "Location permission is needed for live GPS. You can also toggle 'Test Simulation Mode' to test.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestTrackingStart = {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse || isSimulateMode) {
            viewModel.startTracking(
                title = tripTitleInput,
                category = tripCategoryInput,
                initialMode = tripCategoryInput,
                simulate = isSimulateMode
            )
            Toast.makeText(context, "Trip tracking activated!", Toast.LENGTH_SHORT).show()
        } else {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero / Main Live Tracking Control Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (liveState.isTracking) DarkSurfaceHighlight else DarkSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tracking_control_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = if (liveState.isTracking) {
                                            if (liveState.isPaused) WarningAmber else SuccessGreen
                                        } else DangerRed,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (liveState.isTracking) {
                                    if (liveState.isPaused) "TRACKING PAUSED" else "LIVE TRACKING ACTIVE"
                                } else "PHONE TRACKING READY",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (liveState.isTracking) {
                                    if (liveState.isPaused) WarningAmber else SuccessGreen
                                } else Color.LightGray,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (liveState.isTracking && liveState.isSimulated) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentCyan.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "SIMULATED GPS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (liveState.isTracking) {
                        // ACTIVE TRACKING DASHBOARD
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular Speedometer Gauge
                            Surface(
                                shape = CircleShape,
                                color = DarkBackground,
                                modifier = Modifier.size(92.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "%.1f".format(liveState.currentSpeedKmh),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (liveState.isCurrentlyStationary) PurpleAccent else AccentCyan
                                    )
                                    Text(
                                        text = "km/h",
                                        fontSize = 10.sp,
                                        color = Color.LightGray,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Live metrics list
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val hrs = liveState.elapsedSeconds / 3600
                                val mins = (liveState.elapsedSeconds % 3600) / 60
                                val secs = liveState.elapsedSeconds % 60
                                val timeStr = "%02d:%02d:%02d".format(hrs, mins, secs)

                                MetricRow(
                                    label = "Elapsed Time",
                                    value = timeStr,
                                    valueColor = Color.White
                                )
                                MetricRow(
                                    label = "Distance",
                                    value = "%.2f km".format(liveState.distanceMeters / 1000.0),
                                    valueColor = PrimaryBlueLight
                                )
                                MetricRow(
                                    label = "Max Speed",
                                    value = "%.1f km/h".format(liveState.maxSpeedKmh),
                                    valueColor = WarningAmber
                                )
                                MetricRow(
                                    label = "Places Visited",
                                    value = "${liveState.stayCount} stops",
                                    valueColor = PurpleAccent
                                )
                            }
                        }

                        // Stationary / Stay status alert if stationary
                        if (liveState.isCurrentlyStationary) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PurpleAccent.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Stationary Stay: ${liveState.currentStayDurationMs / 60000} mins at current location",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PurpleAccent
                                    )
                                }
                            }
                        }

                        // Mini Live Offline Map preview
                        if (liveState.recentPoints.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OfflineMapView(
                                points = liveState.recentPoints,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                showControls = false,
                                isInteractive = false
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls: Pause/Resume + Stop
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
                                    modifier = Modifier.size(18.dp),
                                    tint = if (liveState.isPaused) SuccessGreen else WarningAmber
                                )
                                Spacer(modifier = Modifier.width(6.dp))
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
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop & Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // IDLE / START TRACKING CONFIG
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = tripTitleInput,
                                onValueChange = { tripTitleInput = it },
                                label = { Text("Trip Name (Optional)") },
                                placeholder = { Text("e.g. Morning Ride, Route 66 Road Trip") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryBlueLight) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlueLight,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_trip_title_input")
                            )

                            // Transport / Activity selector chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Drive", "Walk", "Cycling", "Run", "Transit", "Trip").forEach { cat ->
                                    FilterChip(
                                        selected = tripCategoryInput == cat,
                                        onClick = { tripCategoryInput = cat },
                                        label = { Text(cat) },
                                        leadingIcon = {
                                            Icon(getCategoryIcon(cat), contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryBlue,
                                            selectedLabelColor = Color.White,
                                            containerColor = DarkSurfaceVariant,
                                            labelColor = Color.LightGray
                                        )
                                    )
                                }
                            }

                            // Simulation mode toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Test Simulation GPS Route", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    Text("Simulates vehicle movement & stops for testing", fontSize = 10.sp, color = Color.Gray)
                                }
                                Switch(
                                    checked = isSimulateMode,
                                    onCheckedChange = { isSimulateMode = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AccentCyan,
                                        checkedTrackColor = PrimaryBlueDark
                                    ),
                                    modifier = Modifier.testTag("simulate_gps_switch")
                                )
                            }

                            // Big Start Button
                            Button(
                                onClick = requestTrackingStart,
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("start_tracking_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSimulateMode) "START SIMULATED TRIP" else "START TRACKING THIS PHONE",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Lifetime Stats Ribbon
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatPill(
                        title = "TOTAL DISTANCE",
                        value = "%.1f".format(overallStats.totalDistanceKm),
                        unit = "km",
                        color = PrimaryBlueLight
                    )
                    StatPill(
                        title = "TRIPS",
                        value = "${overallStats.totalTrips}",
                        unit = "saved",
                        color = SuccessGreen
                    )
                    StatPill(
                        title = "PLACES VISITED",
                        value = "${overallStats.totalStayCount}",
                        unit = "stays",
                        color = PurpleAccent
                    )
                    StatPill(
                        title = "TOP SPEED",
                        value = "%.0f".format(overallStats.maxSpeedOverallKmh),
                        unit = "km/h",
                        color = AccentCyan
                    )
                }
            }
        }

        // 3. Search Bar & Category Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search trips by name, location, or notes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlueLight,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_trips_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Drive", "Walk", "Cycling", "Run", "Transit", "Trip").forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { viewModel.setCategory(cat) },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }
            }
        }

        // 4. Trip Timeline Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trip History & Timeline (${trips.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Offline Available",
                    fontSize = 11.sp,
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 5. Trip List / Empty State
        if (trips.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = PrimaryBlueLight,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No trips match '$searchQuery'" else "No recorded trips yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'START TRACKING THIS PHONE' above to record your route, speed, and visited stops!",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            items(trips, key = { it.id }) { trip ->
                TripCardItem(
                    trip = trip,
                    onTap = { onTripSelected(trip.id) },
                    onEdit = { tripToEdit = trip },
                    onDelete = { tripToDelete = trip }
                )
            }
        }
    }

    // Edit Trip Dialog
    tripToEdit?.let { trip ->
        TripEditDialog(
            initialTitle = trip.title,
            initialCategory = trip.category,
            initialNotes = trip.notes,
            onDismiss = { tripToEdit = null },
            onSave = { title, cat, notes ->
                viewModel.updateTrip(trip.id, title, cat, notes)
                tripToEdit = null
                Toast.makeText(context, "Trip updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialog
    tripToDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            containerColor = DarkSurface,
            title = { Text("Delete Trip?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${trip.title}'?", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrip(trip.id)
                        tripToDelete = null
                        Toast.makeText(context, "Trip deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun TripCardItem(
    trip: TripEntity,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val durationMs = if (trip.endTime != null) {
        (trip.endTime - trip.startTime).coerceAtLeast(0)
    } else {
        (System.currentTimeMillis() - trip.startTime).coerceAtLeast(0)
    }
    val durHours = durationMs / 3600000
    val durMins = (durationMs % 3600000) / 60000
    val durationStr = if (durHours > 0) "${durHours}h ${durMins}m" else "${durMins}m"

    val dateStr = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(trip.startTime))

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .testTag("trip_item_${trip.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PrimaryBlue.copy(alpha = 0.2f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = getCategoryIcon(trip.category),
                                contentDescription = null,
                                tint = PrimaryBlueLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = trip.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = dateStr,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats grid in card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("DISTANCE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("%.2f km".format(trip.distanceMeters / 1000.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryBlueLight)
                }
                Column {
                    Text("DURATION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(durationStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                }
                Column {
                    Text("AVG SPEED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("%.1f km/h".format(trip.avgSpeedKmh), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                }
                Column {
                    Text("STAYS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("${trip.stayCount} stops", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PurpleAccent)
                }
            }

            if (trip.startLocationName.isNotBlank() || trip.endLocationName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📍 " + listOfNotNull(trip.startLocationName.takeIf { it.isNotBlank() }, trip.endLocationName.takeIf { it.isNotBlank() }).joinToString(" ➔ "),
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row: View Timeline on Map
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Text("View Offline Map Timeline", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                    }
                    Text("Open ➔", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlueLight)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = Color.LightGray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun StatPill(title: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.LightGray, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(unit, fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 1.dp))
        }
    }
}
