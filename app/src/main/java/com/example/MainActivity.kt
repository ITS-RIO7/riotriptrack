package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveMapScreen
import com.example.ui.screens.TripDetailScreen
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.TripDetailViewModel

enum class MainTab(val title: String, val icon: ImageVector, val tag: String) {
    TRACKER("Trips & Track", Icons.Default.Timeline, "tab_tracker"),
    LIVE_MAP("Live Map", Icons.Default.Map, "tab_live_map"),
    ACCOUNT("Account & Cloud", Icons.Default.AccountCircle, "tab_account")
}

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TripTrackerApp(mainViewModel = mainViewModel)
            }
        }
    }
}

@Composable
fun TripTrackerApp(mainViewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(MainTab.TRACKER) }
    var viewingTripId by remember { mutableStateOf<Long?>(null) }

    val liveState by mainViewModel.liveTrackingState.collectAsState()
    val context = LocalContext.current

    if (viewingTripId != null) {
        val app = context.applicationContext as Application
        val tripDetailVm: TripDetailViewModel = viewModel(
            key = "trip_${viewingTripId}",
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TripDetailViewModel(app, viewingTripId!!) as T
                }
            }
        )

        TripDetailScreen(
            viewModel = tripDetailVm,
            onBack = { viewingTripId = null },
            onDeleteTrip = { id ->
                mainViewModel.deleteTrip(id)
                viewingTripId = null
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    modifier = Modifier.testTag("main_bottom_nav")
                ) {
                    MainTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                if (tab == MainTab.TRACKER && liveState.isTracking) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = if (liveState.isPaused) WarningAmber else SuccessGreen,
                                                modifier = Modifier.size(8.dp)
                                            )
                                        }
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (isSelected) PrimaryBlueLight else Color.Gray
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) PrimaryBlueLight else Color.Gray
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) PrimaryBlueLight else Color.Gray
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color(0xFF1E2D48)
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            },
            containerColor = DarkBackground
        ) { innerPadding ->
            when (selectedTab) {
                MainTab.TRACKER -> {
                    HomeScreen(
                        viewModel = mainViewModel,
                        onTripSelected = { tripId -> viewingTripId = tripId },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainTab.LIVE_MAP -> {
                    LiveMapScreen(
                        viewModel = mainViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                MainTab.ACCOUNT -> {
                    AccountScreen(
                        viewModel = mainViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
