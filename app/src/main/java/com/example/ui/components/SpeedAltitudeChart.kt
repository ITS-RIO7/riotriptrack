package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationPointEntity
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun SpeedAltitudeChart(
    points: List<LocationPointEntity>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onPointHovered: ((Int?) -> Unit)? = null
) {
    if (points.size < 2) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Collecting speed & altitude profile data...",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        return
    }

    var touchX by remember { mutableFloatStateOf(-1f) }

    val maxSpeed = remember(points) {
        max(points.maxOfOrNull { it.speedKmh } ?: 10.0, 10.0)
    }
    val avgSpeed = remember(points) {
        val nonZero = points.filter { it.speedKmh > 0.5 }
        if (nonZero.isNotEmpty()) nonZero.map { it.speedKmh }.average() else 0.0
    }

    val maxAlt = remember(points) { points.maxOfOrNull { it.altitude } ?: 50.0 }
    val minAlt = remember(points) { points.minOfOrNull { it.altitude } ?: 0.0 }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(AccentCyan, RoundedCornerShape(2.dp))
                )
                Text(
                    text = "Speed Profile (km/h)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Avg: %.1f km/h".format(avgSpeed),
                    fontSize = 11.sp,
                    color = PrimaryBlueLight,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Max: %.1f km/h".format(maxSpeed),
                    fontSize = 11.sp,
                    color = AccentCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .pointerInput(points) {
                    detectTapGestures(
                        onPress = { offset ->
                            touchX = offset.x
                            val fraction = (touchX / size.width).coerceIn(0f, 1f)
                            val idx = ((points.size - 1) * fraction).toInt().coerceIn(0, points.size - 1)
                            onPointHovered?.invoke(idx)
                        }
                    )
                }
                .pointerInput(points) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            touchX = change.position.x
                            val fraction = (touchX / size.width).coerceIn(0f, 1f)
                            val idx = ((points.size - 1) * fraction).toInt().coerceIn(0, points.size - 1)
                            onPointHovered?.invoke(idx)
                        },
                        onDragEnd = {
                            touchX = -1f
                            onPointHovered?.invoke(null)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Draw background grid lines
                val gridLines = 3
                for (i in 0..gridLines) {
                    val y = height * (i.toFloat() / gridLines)
                    drawLine(
                        color = Color(0xFF1E2D48),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Speed Path
                val speedPath = Path()
                val fillPath = Path()

                points.forEachIndexed { i, pt ->
                    val x = (i.toFloat() / (points.size - 1)) * width
                    val speedNorm = (pt.speedKmh / maxSpeed).toFloat().coerceIn(0f, 1f)
                    val y = height - (speedNorm * (height - 12.dp.toPx())) - 6.dp.toPx()

                    if (i == 0) {
                        speedPath.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        speedPath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(width, height)
                fillPath.close()

                // Draw Gradient Fill
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AccentCyan.copy(alpha = 0.35f),
                            AccentCyan.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )

                // Draw Speed Stroke
                drawPath(
                    path = speedPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(PrimaryBlueLight, AccentCyan, SuccessGreen)
                    ),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Highlight touched / selected point
                if (touchX >= 0) {
                    val fraction = (touchX / width).coerceIn(0f, 1f)
                    val idx = ((points.size - 1) * fraction).toInt().coerceIn(0, points.size - 1)
                    val selectedPt = points[idx]
                    val x = (idx.toFloat() / (points.size - 1)) * width
                    val speedNorm = (selectedPt.speedKmh / maxSpeed).toFloat().coerceIn(0f, 1f)
                    val y = height - (speedNorm * (height - 12.dp.toPx())) - 6.dp.toPx()

                    // Vertical guideline
                    drawLine(
                        color = WarningAmber.copy(alpha = 0.7f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Point circle
                    drawCircle(
                        color = WarningAmber,
                        radius = 4.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}
