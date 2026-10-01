package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

object MercatorProjection {
    const val TILE_SIZE = 256

    fun latLngToWorld(lat: Double, lng: Double, zoom: Float): Pair<Double, Double> {
        val n = 2.0.pow(zoom.toDouble())
        val x = ((lng + 180.0) / 360.0) * n * TILE_SIZE
        val latClamped = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(latClamped)
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n * TILE_SIZE
        return Pair(x, y)
    }

    fun worldToLatLng(worldX: Double, worldY: Double, zoom: Float): Pair<Double, Double> {
        val n = 2.0.pow(zoom.toDouble())
        val lng = (worldX / (n * TILE_SIZE)) * 360.0 - 180.0
        val yNorm = 1.0 - (2.0 * worldY) / (n * TILE_SIZE)
        val latRad = atan(sinh(Math.PI * yNorm))
        val lat = Math.toDegrees(latRad)
        return Pair(lat, lng)
    }

    fun getMetersPerPixel(lat: Double, zoom: Float): Double {
        // Equatorial circumference / 256 ~ 156543.03392 m/px at zoom 0
        return (156543.03392 * cos(Math.toRadians(lat))) / 2.0.pow(zoom.toDouble())
    }
}

class MapTileProvider private constructor(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diskCacheDir = File(context.cacheDir, "slippy_map_tiles").apply { mkdirs() }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // RAM Cache: Holds up to 180 decoded ImageBitmaps
    private val memoryCache = object : LruCache<String, ImageBitmap>(180) {}

    // In-flight download tracking to avoid duplicate concurrent requests
    private val pendingRequests = ConcurrentHashMap<String, Job>()

    var onTileLoaded: (() -> Unit)? = null

    init {
        // Automatically purge any old cached tiles that may contain "API key required" watermarks
        scope.launch {
            try {
                diskCacheDir.listFiles()?.forEach { file ->
                    // Purge old carto tiles
                    if (file.name.contains("carto", ignoreCase = true) ||
                        file.name.startsWith("ROADS_AND_NAMES_ONLY_") ||
                        file.name.startsWith("DARK_NAV_") ||
                        file.name.startsWith("DAYLIGHT_")
                    ) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun getTile(
        theme: MapThemeMode,
        z: Int,
        x: Int,
        y: Int
    ): ImageBitmap? {
        val maxCoord = 1 shl z
        if (x < 0 || x >= maxCoord || y < 0 || y >= maxCoord) return null

        val cacheKey = "${theme.name}_${z}_${x}_${y}"

        // 1. Check RAM Cache
        val cached = memoryCache.get(cacheKey)
        if (cached != null) return cached

        // 2. Fetch from Disk or Network
        requestTileAsync(theme, z, x, y, cacheKey)

        // 3. Fallback: try to find lower-zoom parent tile in cache as placeholder
        if (z > 2) {
            val parentZ = z - 1
            val parentX = x / 2
            val parentY = y / 2
            val parentKey = "${theme.name}_${parentZ}_${parentX}_${parentY}"
            val parentTile = memoryCache.get(parentKey)
            if (parentTile != null) return parentTile
        }

        return null
    }

    private fun requestTileAsync(
        theme: MapThemeMode,
        z: Int,
        x: Int,
        y: Int,
        cacheKey: String
    ) {
        if (pendingRequests.containsKey(cacheKey)) return

        val job = scope.launch {
            try {
                val diskFile = File(diskCacheDir, "$cacheKey.png")
                var bitmap: Bitmap? = null

                if (diskFile.exists() && diskFile.length() > 200) {
                    bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                }

                if (bitmap == null) {
                    val url = buildTileUrl(theme, z, x, y) ?: return@launch
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "RioTripTracker/1.0 (Android; Slippy Map Client; support@example.com)")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    try {
                                        FileOutputStream(diskFile).use { fos ->
                                            fos.write(bytes)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }

                if (bitmap != null) {
                    val imageBitmap = bitmap!!.asImageBitmap()
                    memoryCache.put(cacheKey, imageBitmap)
                    onTileLoaded?.invoke()
                }
            } catch (_: Exception) {
                // Network unavailable or tile missing - will fallback gracefully to vector background
            } finally {
                pendingRequests.remove(cacheKey)
            }
        }

        pendingRequests[cacheKey] = job
    }

    private fun buildTileUrl(theme: MapThemeMode, z: Int, x: Int, y: Int): String? {
        return when (theme) {
            MapThemeMode.ROADS_AND_NAMES_ONLY -> {
                // Esri World Street Map: Global high-contrast roads, highways, and street names without any watermarks
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$z/$y/$x"
            }
            MapThemeMode.SATELLITE, MapThemeMode.SATELLITE_HYBRID -> {
                // Esri World Imagery: Real spaceborne satellite imagery of earth (no watermark)
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            }
            MapThemeMode.DARK_NAV -> {
                // Esri World Dark Gray Base: Beautiful OLED dark map with highlighted roads (no watermark)
                "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/$z/$y/$x"
            }
            MapThemeMode.DAYLIGHT -> {
                // OpenStreetMap Standard: Community road atlas (100% free, no watermark)
                "https://tile.openstreetmap.org/$z/$x/$y.png"
            }
            MapThemeMode.TOPO_TERRAIN -> {
                // Esri World Topographic Map: Real elevations, contour lines, trails (no watermark)
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/$z/$y/$x"
            }
            MapThemeMode.HIGH_CONTRAST -> {
                // OpenStreetMap Standard
                "https://tile.openstreetmap.org/$z/$x/$y.png"
            }
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
        scope.launch {
            try {
                diskCacheDir.listFiles()?.forEach { it.delete() }
            } catch (_: Exception) {}
        }
    }

    companion object {
        @Volatile
        private var instance: MapTileProvider? = null

        fun getInstance(context: Context): MapTileProvider {
            return instance ?: synchronized(this) {
                instance ?: MapTileProvider(context.applicationContext).also { instance = it }
            }
        }
    }
}
