package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiTripAiService
import com.example.ui.dialogs.SignInDialog
import com.example.ui.theme.AccentCyan
import kotlinx.coroutines.launch
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
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val allTrips by viewModel.allTrips.collectAsState()

    var showSignInDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isTestingGemini by remember { mutableStateOf(false) }
    var geminiPingResponse by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Profile Header Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (userProfile.isLoggedIn) PrimaryBlue else Color(0xFF1E2D48),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (userProfile.authProvider == "google") Icons.Default.Email else Icons.Default.AccountCircle,
                                    contentDescription = "Profile Avatar",
                                    tint = if (userProfile.isLoggedIn) Color.White else Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = userProfile.displayName,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (userProfile.isLoggedIn) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SuccessGreen.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (userProfile.authProvider == "google") "GOOGLE" else "SIGNED IN",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = userProfile.email,
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!userProfile.isLoggedIn) {
                        Button(
                            onClick = { showSignInDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_signin_dialog_button")
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In with Google / Account to Save Trips", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.syncToCloud() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isCloudSyncing,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("backup_sync_now_button")
                            ) {
                                if (isCloudSyncing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Syncing...")
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Backup & Sync")
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.signOut() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("signout_button")
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp), tint = DangerRed)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sign Out", color = DangerRed)
                            }
                        }
                    }
                }
            }
        }

        // 2. Cloud Backup & Sync Status Card
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
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Cloud Backup & Sync Status",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cloud Storage Status", fontSize = 13.sp, color = Color.LightGray)
                        Text(
                            text = if (userProfile.isLoggedIn) "Connected & Protected" else "Local Offline Mode Only",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (userProfile.isLoggedIn) SuccessGreen else WarningAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Local Trips in Database", fontSize = 13.sp, color = Color.LightGray)
                        Text(
                            text = "${allTrips.size} trips",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Last Cloud Backup", fontSize = 13.sp, color = Color.LightGray)
                        val lastSyncStr = if (userProfile.lastSyncTimestamp > 0) {
                            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(userProfile.lastSyncTimestamp))
                        } else "Not synced yet"
                        Text(
                            text = lastSyncStr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlueLight
                        )
                    }

                    if (syncMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = syncMessage!!,
                                fontSize = 12.sp,
                                color = SuccessGreen,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2b. Gemini 3.5 Flash AI Engine Status & Diagnostics Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gemini AI",
                                tint = AccentCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Gemini AI Copilot",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (GeminiTripAiService.isKeyConfigured()) SuccessGreen else WarningAmber).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (GeminiTripAiService.isKeyConfigured()) "CONNECTED" else "NEEDS KEY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (GeminiTripAiService.isKeyConfigured()) SuccessGreen else WarningAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Real-time AI telemetry analysis powered by Google Gemini 3.5 Flash. Analyzes driving style, fuel eco-efficiency, stops, and generates smart journey narratives.",
                        fontSize = 12.5.sp,
                        color = Color.LightGray,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Model", fontSize = 12.5.sp, color = Color.Gray)
                        Text("gemini-3.5-flash", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("API Key Source", fontSize = 12.5.sp, color = Color.Gray)
                        Text(
                            text = if (GeminiTripAiService.isKeyConfigured()) "Configured (.env / Secrets)" else "Not Set",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (GeminiTripAiService.isKeyConfigured()) Color.White else WarningAmber
                        )
                    }

                    if (geminiPingResponse != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentCyan.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = geminiPingResponse!!,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            isTestingGemini = true
                            geminiPingResponse = null
                            coroutineScope.launch {
                                val res = GeminiTripAiService.testApiKey()
                                res.onSuccess { msg ->
                                    geminiPingResponse = msg
                                    Toast.makeText(context, "Gemini 3.5 Flash: Connected & Active!", Toast.LENGTH_SHORT).show()
                                }.onFailure { ex ->
                                    geminiPingResponse = "Connection Error: ${ex.message}"
                                    Toast.makeText(context, "Gemini Test Failed: ${ex.message}", Toast.LENGTH_LONG).show()
                                }
                                isTestingGemini = false
                            }
                        },
                        enabled = !isTestingGemini,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_gemini_connection_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                    ) {
                        if (isTestingGemini) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentCyan, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Gemini Connection...", fontSize = 12.5.sp)
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ping Gemini 3.5 Flash", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 3. Tracking Preferences & Settings Card
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
                        Icon(Icons.Default.Settings, contentDescription = null, tint = PrimaryBlueLight, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Tracking Preferences",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Speed & Distance Units", fontSize = 12.sp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = userProfile.preferredSpeedUnit == "km/h",
                            onClick = {
                                Toast.makeText(context, "Set units to km/h", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Metric (km/h, km)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = Color.LightGray
                            )
                        )
                        FilterChip(
                            selected = userProfile.preferredSpeedUnit == "mph",
                            onClick = {
                                Toast.makeText(context, "Set units to mph", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Imperial (mph, miles)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = Color.LightGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Stay Detection Threshold", fontSize = 12.sp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 5).forEach { mins ->
                            FilterChip(
                                selected = userProfile.stayDetectionMinutes == mins,
                                onClick = {
                                    Toast.makeText(context, "Stay detection set to $mins min", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("$mins mins stationary") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurpleAccent,
                                    selectedLabelColor = Color.White,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = Color.LightGray
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. Privacy & Offline Guarantee Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Offline-First Privacy Guarantee",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your GPS coordinates, travel paths, visited stops, and speed profiles are stored safely on this device. You can view all map timelines completely offline without an internet connection.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }

    if (showSignInDialog) {
        SignInDialog(
            onDismiss = { showSignInDialog = false },
            onGoogleSignIn = { email, name ->
                viewModel.signInWithGoogle(email, name)
                showSignInDialog = false
                Toast.makeText(context, "Signed in with Google ($email)!", Toast.LENGTH_SHORT).show()
            },
            onEmailSignIn = { email, name ->
                viewModel.signInWithEmail(email, name)
                showSignInDialog = false
                Toast.makeText(context, "Signed in as $name ($email)!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
