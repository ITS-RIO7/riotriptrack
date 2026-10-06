package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationPointEntity
import com.example.data.model.MilestoneType
import com.example.data.model.TripTimelineMilestone
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.RouteLineFast
import com.example.ui.theme.RouteLineNormal
import com.example.ui.theme.RouteLineSlow
import com.example.ui.theme.RouteLineStopped
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class MapThemeMode(val title: String, val badge: String, val iconEmoji: String) {
    ROADS_AND_NAMES_ONLY("Streets & Highways", "🛣️ STREETS & ROADS", "🛣️"),
    SATELLITE("Satellite Imagery", "🛰️ SATELLITE", "🛰️"),
    SATELLITE_HYBRID("Satellite Hybrid", "🛰️ SATELLITE + ROADS", "🌍"),
    DARK_NAV("Dark Navigation", "🌌 DARK NAV", "🌌"),
    DAYLIGHT("Daylight Atlas", "☀️ DAYLIGHT", "☀️"),
    TOPO_TERRAIN("Topographic Terrain", "⛰️ TOPO TERRAIN", "⛰️"),
    HIGH_CONTRAST("OpenStreetMap", "🗺️ OPENSTREETMAP", "🗺️")
}

@Composable
fun OfflineMapView(
    points: List<LocationPointEntity>,
    modifier: Modifier = Modifier,
    activeLivePoint: LocationPointEntity? = null,
    currentLocationPoint: LocationPointEntity? = null,
    timelineMilestones: List<TripTimelineMilestone> = emptyList(),
    selectedMilestone: TripTimelineMilestone? = null,
    onMilestoneClick: ((TripTimelineMilestone) -> Unit)? = null,
    showRioLogo: Boolean = true,
    scrubberProgress: Float? = null,
    onScrubPointSelected: ((LocationPointEntity?) -> Unit)? = null,
    showControls: Boolean = true,
    isInteractive: Boolean = true,
    initialTheme: MapThemeMode = MapThemeMode.ROADS_AND_NAMES_ONLY,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val tileProvider = remember { MapTileProvider.getInstance(context) }
    val tileRecomposeTrigger by tileProvider.tileUpdateTrigger.collectAsState()

    var mapTheme by remember { mutableStateOf(initialTheme) }
    var selectedPoint by remember { mutableStateOf<LocationPointEntity?>(null) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var followUser by remember { mutableStateOf(true) }

    val allPoints = remember(points, activeLivePoint) {
        if (activeLivePoint != null) points + activeLivePoint else points
    }

    val stayPoints = remember(allPoints) {
        allPoints.filter { it.isStayPoint }
    }

    val targetLivePoint = activeLivePoint ?: currentLocationPoint

    // Viewport State (Center Latitude, Center Longitude, Zoom Level)
    var centerLat by remember { mutableDoubleStateOf(targetLivePoint?.latitude ?: 37.7749) }
    var centerLng by remember { mutableDoubleStateOf(targetLivePoint?.longitude ?: -122.4194) }
    var zoom by remember { mutableFloatStateOf(15.5f) }

    // Helper to calculate bounds and fit points
    val fitBoundsToPoints = {
        if (allPoints.isNotEmpty()) {
            var minLa = allPoints.first().latitude
            var maxLa = allPoints.first().latitude
            var minLn = allPoints.first().longitude
            var maxLn = allPoints.first().longitude
            for (p in allPoints) {
                if (p.latitude < minLa) minLa = p.latitude
                if (p.latitude > maxLa) maxLa = p.latitude
                if (p.longitude < minLn) minLn = p.longitude
                if (p.longitude > maxLn) maxLn = p.longitude
            }
            centerLat = (minLa + maxLa) / 2.0
            centerLng = (minLn + maxLn) / 2.0

            val latSpan = max(maxLa - minLa, 0.001)
            val lngSpan = max(maxLn - minLn, 0.001)
            val maxSpan = max(latSpan, lngSpan)

            // Approximate optimal zoom based on span
            val computedZoom = (ln(360.0 / (maxSpan * 2.8)) / ln(2.0)).toFloat()
            zoom = computedZoom.coerceIn(3.0f, 17.5f)
        } else if (targetLivePoint != null) {
            centerLat = targetLivePoint.latitude
            centerLng = targetLivePoint.longitude
            zoom = 15.5f
        }
    }

    // Initial setup on launch or points change
    var hasInitializedCenter by remember { mutableStateOf(false) }
    LaunchedEffect(allPoints.size, targetLivePoint?.latitude, targetLivePoint?.longitude) {
        if (!hasInitializedCenter) {
            if (allPoints.isNotEmpty()) {
                fitBoundsToPoints()
                hasInitializedCenter = true
            } else if (targetLivePoint != null) {
                centerLat = targetLivePoint.latitude
                centerLng = targetLivePoint.longitude
                zoom = 15.5f
                hasInitializedCenter = true
            }
        } else if (allPoints.isEmpty() && targetLivePoint != null && centerLat == 37.7749 && centerLng == -122.4194) {
            centerLat = targetLivePoint.latitude
            centerLng = targetLivePoint.longitude
            zoom = 15.5f
        }
    }

    // Follow user in live tracking or current location mode
    LaunchedEffect(targetLivePoint?.latitude, targetLivePoint?.longitude, followUser) {
        if (followUser && targetLivePoint != null) {
            centerLat = targetLivePoint.latitude
            centerLng = targetLivePoint.longitude
        }
    }

    // Center on selected milestone
    LaunchedEffect(selectedMilestone) {
        if (selectedMilestone != null) {
            followUser = false
            centerLat = selectedMilestone.point.latitude
            centerLng = selectedMilestone.point.longitude
            zoom = 16.5f
            selectedPoint = selectedMilestone.point
        }
    }

    // Scrubber point synchronization
    val scrubbedPoint = remember(scrubberProgress, allPoints) {
        if (scrubberProgress != null && allPoints.isNotEmpty()) {
            val idx = ((allPoints.size - 1) * scrubberProgress).toInt().coerceIn(0, allPoints.size - 1)
            allPoints[idx]
        } else null
    }

    LaunchedEffect(scrubbedPoint) {
        if (scrubbedPoint != null) {
            selectedPoint = scrubbedPoint
            onScrubPointSelected?.invoke(scrubbedPoint)
            centerLat = scrubbedPoint.latitude
            centerLng = scrubbedPoint.longitude
        }
    }

    // Multi-touch Gestures (Pinch-to-zoom & continuous pan)
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (isInteractive) {
            followUser = false
            // Smooth zoom
            val newZoom = (zoom + log2(zoomChange)).coerceIn(2.5f, 19.0f)
            zoom = newZoom

            // Smooth Pan
            val (curWx, curWy) = MercatorProjection.latLngToWorld(centerLat, centerLng, newZoom)
            val newWx = curWx - panChange.x
            val newWy = curWy - panChange.y
            val (newLat, newLng) = MercatorProjection.worldToLatLng(newWx, newWy, newZoom)
            centerLat = newLat
            centerLng = newLng
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(getMapBgColor(mapTheme))
            .pointerInput(isInteractive, allPoints, timelineMilestones, zoom, centerLat, centerLng) {
                if (isInteractive) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            followUser = false
                            zoom = (zoom + 1.2f).coerceAtMost(19.0f)
                        },
                        onTap = { tapOffset ->
                            // Check milestones first
                            if (timelineMilestones.isNotEmpty()) {
                                val (cwx, cwy) = MercatorProjection.latLngToWorld(centerLat, centerLng, zoom)
                                for (m in timelineMilestones) {
                                    val (wx, wy) = MercatorProjection.latLngToWorld(m.point.latitude, m.point.longitude, zoom)
                                    val sx = (size.width / 2f + (wx - cwx)).toFloat()
                                    val sy = (size.height / 2f + (wy - cwy)).toFloat()
                                    val distSq = (sx - tapOffset.x) * (sx - tapOffset.x) + (sy - tapOffset.y) * (sy - tapOffset.y)
                                    if (distSq < (44 * density) * (44 * density)) {
                                        selectedPoint = m.point
                                        onMilestoneClick?.invoke(m)
                                        return@detectTapGestures
                                    }
                                }
                            }

                            // Hit test closest point in route
                            val tappedPt = findClosestPoint(
                                tapOffset = tapOffset,
                                points = allPoints,
                                centerLat = centerLat,
                                centerLng = centerLng,
                                zoom = zoom,
                                width = size.width.toFloat(),
                                height = size.height.toFloat()
                            )
                            selectedPoint = tappedPt
                        }
                    )
                }
            }
            .then(if (isInteractive) Modifier.transformable(transformState) else Modifier)
    ) {
        // Compose Map Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read recomposition state to trigger redraw when new tile loaded
            @Suppress("UNUSED_VARIABLE")
            val trigger = tileRecomposeTrigger

            val width = size.width
            val height = size.height
            val currentZoom = zoom
            val cLat = centerLat
            val cLng = centerLng

            val (centerWorldX, centerWorldY) = MercatorProjection.latLngToWorld(cLat, cLng, currentZoom)

            fun latLngToScreen(lat: Double, lng: Double): Offset {
                val (wx, wy) = MercatorProjection.latLngToWorld(lat, lng, currentZoom)
                val sx = (width / 2f + (wx - centerWorldX)).toFloat()
                val sy = (height / 2f + (wy - centerWorldY)).toFloat()
                return Offset(sx, sy)
            }

            // 1. Draw Real Base Slippy Map Tiles (CartoDB / OpenStreetMap / Esri Satellite)
            drawSlippyMapTiles(
                tileProvider = tileProvider,
                theme = mapTheme,
                zoom = currentZoom,
                centerWorldX = centerWorldX,
                centerWorldY = centerWorldY,
                width = width,
                height = height
            )

            // 2. Draw Polyline Route Path with Neon Glow and Speed Colors
            if (allPoints.size >= 2) {
                // Route outer neon glow
                val glowColor = getMapThemeGlow(mapTheme)
                for (i in 0 until allPoints.size - 1) {
                    val p1 = allPoints[i]
                    val p2 = allPoints[i + 1]
                    val s1 = latLngToScreen(p1.latitude, p1.longitude)
                    val s2 = latLngToScreen(p2.latitude, p2.longitude)

                    // Skip lines outside screen bounds
                    if (isLineVisible(s1, s2, width, height)) {
                        drawLine(
                            color = glowColor,
                            start = s1,
                            end = s2,
                            strokeWidth = 10.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Core Route with dynamic speed-graded coloring
                for (i in 0 until allPoints.size - 1) {
                    val p1 = allPoints[i]
                    val p2 = allPoints[i + 1]
                    val s1 = latLngToScreen(p1.latitude, p1.longitude)
                    val s2 = latLngToScreen(p2.latitude, p2.longitude)

                    if (isLineVisible(s1, s2, width, height)) {
                        val segColor = getSpeedColor(p2.speedKmh, p2.isStayPoint)
                        drawLine(
                            color = segColor,
                            start = s1,
                            end = s2,
                            strokeWidth = 5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // 3. Draw Stay Points (Where & When resting stops)
            for (stay in stayPoints) {
                val center = latLngToScreen(stay.latitude, stay.longitude)
                if (center.x in -60f..(width + 60f) && center.y in -60f..(height + 60f)) {
                    // Pulsing Stay Aura
                    drawCircle(
                        color = PurpleAccent.copy(alpha = 0.35f),
                        radius = 18.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = PurpleAccent,
                        radius = 10.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = center
                    )

                    // Draw Stay badge callout with minutes
                    val stayMinutes = stay.stayDurationMs / 60000
                    drawStayCallout(
                        center = center,
                        label = "☕ ${if (stayMinutes > 0) "${stayMinutes}m" else "Stop"}",
                        subLabel = stay.locationName
                    )
                }
            }

            // 4. Draw Start Waypoint Pin (Green flag)
            if (allPoints.isNotEmpty()) {
                val start = allPoints.first()
                val center = latLngToScreen(start.latitude, start.longitude)
                if (center.x in -80f..(width + 80f) && center.y in -80f..(height + 80f)) {
                    drawCircle(
                        color = SuccessGreen.copy(alpha = 0.35f),
                        radius = 20.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = SuccessGreen,
                        radius = 9.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = center
                    )

                    drawPinBadge(
                        center = center,
                        label = "START",
                        badgeColor = SuccessGreen
                    )
                }
            }

            // 5. Draw End Waypoint or Active Live GPS Radar Pulse
            if (allPoints.size >= 2) {
                val end = allPoints.last()
                val center = latLngToScreen(end.latitude, end.longitude)

                if (center.x in -80f..(width + 80f) && center.y in -80f..(height + 80f)) {
                    if (activeLivePoint != null) {
                        // Live GPS Location Radar Beacon
                        drawCircle(
                            color = AccentCyan.copy(alpha = 0.22f),
                            radius = 32.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = PrimaryBlueLight.copy(alpha = 0.45f),
                            radius = 20.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = PrimaryBlue,
                            radius = 10.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4.5.dp.toPx(),
                            center = center
                        )

                        // Live Speed Badge
                        drawPinBadge(
                            center = center,
                            label = "LIVE: %.1f km/h".format(end.speedKmh),
                            badgeColor = AccentCyan
                        )
                    } else {
                        // Trip Finish Checkpoint
                        drawCircle(
                            color = DangerRed.copy(alpha = 0.35f),
                            radius = 20.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = DangerRed,
                            radius = 9.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = center
                        )

                        drawPinBadge(
                            center = center,
                            label = "FINISH",
                            badgeColor = DangerRed
                        )
                    }
                }
            }

            // 5b. Draw Standalone Live Current Location when not tracking active trip
            if (activeLivePoint == null && currentLocationPoint != null) {
                val curCenter = latLngToScreen(currentLocationPoint.latitude, currentLocationPoint.longitude)
                if (curCenter.x in -80f..(width + 80f) && curCenter.y in -80f..(height + 80f)) {
                    drawCircle(
                        color = AccentCyan.copy(alpha = 0.22f),
                        radius = 34.dp.toPx(),
                        center = curCenter
                    )
                    drawCircle(
                        color = PrimaryBlueLight.copy(alpha = 0.45f),
                        radius = 20.dp.toPx(),
                        center = curCenter
                    )
                    drawCircle(
                        color = PrimaryBlue,
                        radius = 10.dp.toPx(),
                        center = curCenter
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.5.dp.toPx(),
                        center = curCenter
                    )
                    drawPinBadge(
                        center = curCenter,
                        label = "📍 YOU ARE HERE",
                        badgeColor = AccentCyan
                    )
                }
            }

            // 5c. Draw Sequential Journey Milestones ("Kahan Kahan Gaya")
            if (timelineMilestones.isNotEmpty()) {
                for (m in timelineMilestones) {
                    val mCenter = latLngToScreen(m.point.latitude, m.point.longitude)
                    if (mCenter.x in -80f..(width + 80f) && mCenter.y in -80f..(height + 80f)) {
                        val isSelected = (m.id == selectedMilestone?.id)
                        val (pinColor, badgeLabel) = when (m.type) {
                            MilestoneType.START -> Pair(SuccessGreen, "START")
                            MilestoneType.FINISH -> Pair(DangerRed, "FINISH")
                            MilestoneType.STAY_STOP -> Pair(PurpleAccent, "STOP #${m.sequenceNumber - 1}")
                            MilestoneType.FASTEST_POINT -> Pair(AccentCyan, "%.0f km/h".format(m.speedKmh))
                            MilestoneType.CHECKPOINT -> Pair(PrimaryBlueLight, "#${m.sequenceNumber}")
                        }

                        if (isSelected) {
                            drawCircle(
                                color = AccentCyan.copy(alpha = 0.45f),
                                radius = 28.dp.toPx(),
                                center = mCenter
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.3f),
                                radius = 18.dp.toPx(),
                                center = mCenter
                            )
                        }

                        drawCircle(
                            color = pinColor.copy(alpha = 0.35f),
                            radius = 16.dp.toPx(),
                            center = mCenter
                        )
                        drawCircle(
                            color = pinColor,
                            radius = 8.5.dp.toPx(),
                            center = mCenter
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = mCenter
                        )

                        drawMilestoneBadge(
                            center = mCenter,
                            sequenceNumber = m.sequenceNumber,
                            label = badgeLabel,
                            badgeColor = pinColor,
                            isSelected = isSelected
                        )
                    }
                }
            }

            // 6. Draw Scrubber Timeline Indicator Pin
            if (scrubbedPoint != null) {
                val scrubCenter = latLngToScreen(scrubbedPoint.latitude, scrubbedPoint.longitude)
                drawCircle(
                    color = WarningAmber.copy(alpha = 0.45f),
                    radius = 24.dp.toPx(),
                    center = scrubCenter
                )
                drawCircle(
                    color = WarningAmber,
                    radius = 11.dp.toPx(),
                    center = scrubCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = scrubCenter
                )
            }

            // 7. Draw Dynamic Map Scale Bar (Bottom Left)
            drawMapScaleBar(
                centerLat = cLat,
                zoom = currentZoom,
                width = width,
                height = height
            )

            // 8. Draw RIO Watermark on Map Canvas (Bottom Right)
            if (showRioLogo) {
                drawRioWatermark(width = width, height = height)
            }
        }

        // Top Floating Map Style Quick Switcher Chips and RIO Logo Emblem
        if (showControls) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = topPadding + 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showRioLogo) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = DarkSurface.copy(alpha = 0.95f),
                        border = BorderStroke(1.2.dp, AccentCyan.copy(alpha = 0.75f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier.testTag("rio_map_logo_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF2563EB))),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "R",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "RIO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapThemeMode.values().forEach { mode ->
                        val isSelected = (mapTheme == mode)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) PrimaryBlue else DarkSurface.copy(alpha = 0.88f),
                            shadowElevation = if (isSelected) 4.dp else 2.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.8.dp,
                                    color = if (isSelected) AccentCyan else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { mapTheme = mode }
                                .testTag("map_style_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = mode.iconEmoji, fontSize = 11.sp)
                                Text(
                                    text = mode.title,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }

            // Floating Map Controls (Right Side: Recenter, Follow, Zoom In, Zoom Out)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // My Location / Center on Live GPS Location Button
                if (targetLivePoint != null) {
                    Surface(
                        shape = CircleShape,
                        color = if (followUser) PrimaryBlue else DarkSurface.copy(alpha = 0.92f),
                        shadowElevation = 5.dp
                    ) {
                        IconButton(
                            onClick = {
                                followUser = true
                                centerLat = targetLivePoint.latitude
                                centerLng = targetLivePoint.longitude
                                zoom = 16.5f
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("my_location_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Center on My Location",
                                tint = if (followUser) AccentCyan else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Follow Me / Lock GPS Location Button (in live mode or watching live location)
                if (targetLivePoint != null) {
                    Surface(
                        shape = CircleShape,
                        color = if (followUser) PrimaryBlue else DarkSurface.copy(alpha = 0.92f),
                        shadowElevation = 5.dp
                    ) {
                        IconButton(
                            onClick = {
                                followUser = !followUser
                                if (followUser) {
                                    centerLat = targetLivePoint.latitude
                                    centerLng = targetLivePoint.longitude
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("follow_me_button")
                        ) {
                            Icon(
                                imageVector = if (followUser) Icons.Default.NearMe else Icons.Default.Explore,
                                contentDescription = if (followUser) "Following Location" else "Free Map Pan",
                                tint = if (followUser) AccentCyan else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Recenter / Fit All Points
                Surface(
                    shape = CircleShape,
                    color = DarkSurface.copy(alpha = 0.92f),
                    shadowElevation = 5.dp
                ) {
                    IconButton(
                        onClick = {
                            followUser = false
                            fitBoundsToPoints()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("fit_route_bounds_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Fit Trip on Map",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Zoom In Button
                Surface(
                    shape = CircleShape,
                    color = DarkSurface.copy(alpha = 0.92f),
                    shadowElevation = 5.dp
                ) {
                    IconButton(
                        onClick = {
                            followUser = false
                            zoom = (zoom + 1.0f).coerceAtMost(19.0f)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Zoom Out Button
                Surface(
                    shape = CircleShape,
                    color = DarkSurface.copy(alpha = 0.92f),
                    shadowElevation = 5.dp
                ) {
                    IconButton(
                        onClick = {
                            followUser = false
                            zoom = (zoom - 1.0f).coerceAtLeast(2.5f)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom Left HUD Telemetry Pill (Zoom Level & Coordinates)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface.copy(alpha = 0.88f),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "z%.1f".format(zoom),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    Text(
                        text = "•",
                        fontSize = 9.5.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "%.4f, %.4f".format(centerLat, centerLng),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }

        // Floating Inspector Callout for Selected or Scrubbed Point
        AnimatedVisibility(
            visible = selectedPoint != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            selectedPoint?.let { pt ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.97f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val timeStr = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(pt.timestamp))
                            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(pt.timestamp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (pt.isStayPoint) "⏱️ Stayed Location (${pt.stayDurationMs / 60000} mins)" else "📍 Waypoint at $timeStr",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pt.isStayPoint) PurpleAccent else AccentCyan
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurfaceVariant
                                ) {
                                    Text(
                                        text = pt.mode,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.LightGray,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pt.locationName ?: "Lat: %.5f, Lng: %.5f".format(pt.latitude, pt.longitude),
                                fontSize = 11.5.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Speed: %.1f km/h".format(pt.speedKmh),
                                    fontSize = 11.sp,
                                    color = getSpeedColor(pt.speedKmh, pt.isStayPoint),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Alt: %.0f m".format(pt.altitude),
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = dateStr,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedPoint = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Tooltip",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SLIPPY MAP TILE CANVAS DRAW ENGINE
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawSlippyMapTiles(
    tileProvider: MapTileProvider,
    theme: MapThemeMode,
    zoom: Float,
    centerWorldX: Double,
    centerWorldY: Double,
    width: Float,
    height: Float
) {
    val maxNative = MapTileProvider.getMaxNativeZoom(theme)
    val tileZoom = zoom.toInt().coerceIn(0, maxNative)
    val scaleDiff = 2.0.pow((zoom - tileZoom).toDouble())
    val tileDisplaySize = (256.0 * scaleDiff).toFloat()

    // Bounding box in world coordinates
    val minWorldX = centerWorldX - width / 2.0
    val maxWorldX = centerWorldX + width / 2.0
    val minWorldY = centerWorldY - height / 2.0
    val maxWorldY = centerWorldY + height / 2.0

    // Coordinates at tile zoom level
    val minTileWorldX = minWorldX / scaleDiff
    val maxTileWorldX = maxWorldX / scaleDiff
    val minTileWorldY = minWorldY / scaleDiff
    val maxTileWorldY = maxWorldY / scaleDiff

    val startX = floor(minTileWorldX / 256.0).toInt()
    val endX = floor(maxTileWorldX / 256.0).toInt()
    val startY = floor(minTileWorldY / 256.0).toInt()
    val endY = floor(maxTileWorldY / 256.0).toInt()

    val maxTile = (1 shl tileZoom) - 1

    for (x in startX..endX) {
        val tileX = ((x % (maxTile + 1)) + (maxTile + 1)) % (maxTile + 1)
        for (y in startY..endY) {
            if (y < 0 || y > maxTile) continue

            val tileScreenX = (width / 2.0 + (x * 256.0 - (centerWorldX / scaleDiff)) * scaleDiff).toFloat()
            val tileScreenY = (height / 2.0 + (y * 256.0 - (centerWorldY / scaleDiff)) * scaleDiff).toFloat()

            val tileBitmap = tileProvider.getTile(theme, tileZoom, tileX, y)
            if (tileBitmap != null) {
                drawImage(
                    image = tileBitmap,
                    dstOffset = IntOffset(tileScreenX.toInt(), tileScreenY.toInt()),
                    dstSize = IntSize((tileDisplaySize + 1).toInt(), (tileDisplaySize + 1).toInt())
                )
            } else {
                // Background filler while tile is loading
                drawRect(
                    color = getMapBgColor(theme),
                    topLeft = Offset(tileScreenX, tileScreenY),
                    size = androidx.compose.ui.geometry.Size(tileDisplaySize, tileDisplaySize)
                )
                // Subtle road grid lines during offline or initial loading
                drawRect(
                    color = Color.White.copy(alpha = 0.04f),
                    topLeft = Offset(tileScreenX, tileScreenY),
                    size = androidx.compose.ui.geometry.Size(tileDisplaySize, tileDisplaySize),
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// MAP SCALE BAR
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawMapScaleBar(
    centerLat: Double,
    zoom: Float,
    width: Float,
    height: Float
) {
    val metersPerPx = MercatorProjection.getMetersPerPixel(centerLat, zoom)
    val targetBarPx = 80.dp.toPx()
    val rawMeters = targetBarPx * metersPerPx

    val (chosenMeters, label) = when {
        rawMeters >= 50000 -> Pair(50000.0, "50 km")
        rawMeters >= 20000 -> Pair(20000.0, "20 km")
        rawMeters >= 10000 -> Pair(10000.0, "10 km")
        rawMeters >= 5000 -> Pair(5000.0, "5 km")
        rawMeters >= 2000 -> Pair(2000.0, "2 km")
        rawMeters >= 1000 -> Pair(1000.0, "1 km")
        rawMeters >= 500 -> Pair(500.0, "500 m")
        rawMeters >= 200 -> Pair(200.0, "200 m")
        rawMeters >= 100 -> Pair(100.0, "100 m")
        rawMeters >= 50 -> Pair(50.0, "50 m")
        else -> Pair(20.0, "20 m")
    }

    val barWidthPx = (chosenMeters / metersPerPx).toFloat()
    val startX = 14.dp.toPx()
    val startY = height - 14.dp.toPx()

    // Draw scale bar line with end ticks
    drawLine(
        color = Color.White,
        start = Offset(startX, startY),
        end = Offset(startX + barWidthPx, startY),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = Color.White,
        start = Offset(startX, startY - 4.dp.toPx()),
        end = Offset(startX, startY + 4.dp.toPx()),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = Color.White,
        start = Offset(startX + barWidthPx, startY - 4.dp.toPx()),
        end = Offset(startX + barWidthPx, startY + 4.dp.toPx()),
        strokeWidth = 2.dp.toPx()
    )

    // Text Label
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 9.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val shadowPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        textSize = 9.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    nativeCanvas.drawText(label, startX + 4.dp.toPx(), startY - 5.dp.toPx(), shadowPaint)
    nativeCanvas.drawText(label, startX + 4.dp.toPx(), startY - 5.dp.toPx(), textPaint)
}

// -----------------------------------------------------------------------------------------
// PIN BADGES & LABELS
// -----------------------------------------------------------------------------------------
private fun DrawScope.drawPinBadge(
    center: Offset,
    label: String,
    badgeColor: Color
) {
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 10f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val shadowPaint = Paint().apply {
        isAntiAlias = true
        color = badgeColor.toArgb()
        textSize = 10f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    val badgeX = center.x + 12.dp.toPx()
    val badgeY = center.y - 8.dp.toPx()

    nativeCanvas.drawText(label, badgeX, badgeY, shadowPaint)
    nativeCanvas.drawText(label, badgeX, badgeY, textPaint)
}

private fun DrawScope.drawMilestoneBadge(
    center: Offset,
    sequenceNumber: Int,
    label: String,
    badgeColor: Color,
    isSelected: Boolean
) {
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val text = if (sequenceNumber > 1) "$sequenceNumber. $label" else label

    val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = (if (isSelected) 11f else 9.5f) * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val shadowPaint = Paint().apply {
        isAntiAlias = true
        color = if (isSelected) android.graphics.Color.argb(250, 0, 229, 255) else badgeColor.toArgb()
        textSize = (if (isSelected) 11f else 9.5f) * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = if (isSelected) 4.5f else 3.5f
    }

    val badgeX = center.x + 12.dp.toPx()
    val badgeY = center.y - 8.dp.toPx()

    nativeCanvas.drawText(text, badgeX, badgeY, shadowPaint)
    nativeCanvas.drawText(text, badgeX, badgeY, textPaint)
}

private fun DrawScope.drawRioWatermark(width: Float, height: Float) {
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val text = "RIO • TRIP TRACKER"

    val shadowPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(160, 0, 0, 0)
        textSize = 10f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        letterSpacing = 0.14f
    }
    val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(190, 255, 255, 255)
        textSize = 10f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.14f
    }

    val x = width - 145.dp.toPx()
    val y = height - 16.dp.toPx()

    nativeCanvas.drawText(text, x, y, shadowPaint)
    nativeCanvas.drawText(text, x, y, textPaint)
}

private fun DrawScope.drawStayCallout(
    center: Offset,
    label: String,
    subLabel: String?
) {
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val textPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = 9.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val shadowPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(230, 88, 28, 135)
        textSize = 9.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    val text = if (!subLabel.isNullOrBlank()) "$label: ${subLabel.take(16)}" else label
    val badgeX = center.x - 30.dp.toPx()
    val badgeY = center.y - 14.dp.toPx()

    nativeCanvas.drawText(text, badgeX, badgeY, shadowPaint)
    nativeCanvas.drawText(text, badgeX, badgeY, textPaint)
}

// -----------------------------------------------------------------------------------------
// HIT TESTING & GEOMETRY HELPERS
// -----------------------------------------------------------------------------------------
private fun findClosestPoint(
    tapOffset: Offset,
    points: List<LocationPointEntity>,
    centerLat: Double,
    centerLng: Double,
    zoom: Float,
    width: Float,
    height: Float
): LocationPointEntity? {
    if (points.isEmpty()) return null
    val (cwx, cwy) = MercatorProjection.latLngToWorld(centerLat, centerLng, zoom)

    var closest: LocationPointEntity? = null
    var minDistanceSq = 55.dp.value.pow(2) // Max tap radius

    for (p in points) {
        val (wx, wy) = MercatorProjection.latLngToWorld(p.latitude, p.longitude, zoom)
        val sx = (width / 2f + (wx - cwx)).toFloat()
        val sy = (height / 2f + (wy - cwy)).toFloat()
        val distSq = (sx - tapOffset.x).pow(2) + (sy - tapOffset.y).pow(2)
        if (distSq < minDistanceSq) {
            minDistanceSq = distSq
            closest = p
        }
    }
    return closest
}

private fun isLineVisible(s1: Offset, s2: Offset, width: Float, height: Float): Boolean {
    val margin = 50f
    val minX = min(s1.x, s2.x)
    val maxX = max(s1.x, s2.x)
    val minY = min(s1.y, s2.y)
    val maxY = max(s1.y, s2.y)
    return !(maxX < -margin || minX > width + margin || maxY < -margin || minY > height + margin)
}

private fun getMapBgColor(theme: MapThemeMode): Color = when (theme) {
    MapThemeMode.ROADS_AND_NAMES_ONLY -> Color(0xFF13171F)
    MapThemeMode.SATELLITE, MapThemeMode.SATELLITE_HYBRID -> Color(0xFF070F0B)
    MapThemeMode.DARK_NAV -> Color(0xFF0F172A)
    MapThemeMode.DAYLIGHT -> Color(0xFFF1F5F9)
    MapThemeMode.TOPO_TERRAIN -> Color(0xFF1E2819)
    MapThemeMode.HIGH_CONTRAST -> Color(0xFF0E1318)
}

private fun getMapThemeGlow(theme: MapThemeMode): Color = when (theme) {
    MapThemeMode.ROADS_AND_NAMES_ONLY -> Color(0xFF38BDF8).copy(alpha = 0.45f)
    MapThemeMode.SATELLITE, MapThemeMode.SATELLITE_HYBRID -> Color(0xFF06B6D4).copy(alpha = 0.45f)
    MapThemeMode.DARK_NAV -> Color(0xFF3B82F6).copy(alpha = 0.4f)
    MapThemeMode.DAYLIGHT -> Color(0xFF64748B).copy(alpha = 0.35f)
    MapThemeMode.TOPO_TERRAIN -> Color(0xFF84CC16).copy(alpha = 0.35f)
    MapThemeMode.HIGH_CONTRAST -> Color.White.copy(alpha = 0.3f)
}

fun getSpeedColor(speedKmh: Double, isStay: Boolean = false): Color {
    if (isStay) return PurpleAccent
    return when {
        speedKmh >= 60.0 -> Color(0xFF06B6D4) // Fast Cyan
        speedKmh >= 25.0 -> RouteLineFast // Green
        speedKmh >= 10.0 -> RouteLineNormal // Blue
        speedKmh >= 2.5 -> RouteLineSlow // Amber
        else -> RouteLineStopped // Red
    }
}
