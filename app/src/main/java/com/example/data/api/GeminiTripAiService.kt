package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.TripEntity
import com.example.data.model.TripTimelineMilestone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TripAiResult(
    val summary: String,
    val styleTag: String,
    val ecoScore: Int,
    val drivingTips: String,
    val highlights: List<String>
)

object GeminiTripAiService {
    private const val TAG = "GeminiTripAi"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getEffectiveApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun isKeyConfigured(): Boolean {
        val key = getEffectiveApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun testApiKey(customKey: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val keyToUse = customKey?.takeIf { it.isNotBlank() } ?: getEffectiveApiKey()
        if (keyToUse.isBlank() || keyToUse == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        try {
            val url = "$BASE_ENDPOINT?key=$keyToUse"
            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Respond in one short sentence: Confirm you are Gemini 3.5 Flash and ready to analyze GPS trips for Rio Trip Tracker.")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 100)
                })
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(rawBody, response.code)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val text = parseGeneratedText(rawBody)
            Result.success(text.ifBlank { "Gemini API connected successfully!" })
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeTrip(
        trip: TripEntity,
        milestones: List<TripTimelineMilestone>
    ): Result<TripAiResult> = withContext(Dispatchers.IO) {
        val keyToUse = getEffectiveApiKey()
        if (keyToUse.isBlank() || keyToUse == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key not found. Please verify your API key in Secrets panel.")
            )
        }

        val distanceKm = trip.distanceMeters / 1000.0
        val durationMs = if (trip.endTime != null && trip.endTime > trip.startTime) {
            trip.endTime - trip.startTime
        } else {
            0L
        }
        val durationMins = durationMs / 60000

        val timeFmt = SimpleDateFormat("h:mm a, MMM dd", Locale.getDefault())
        val startTimeStr = timeFmt.format(Date(trip.startTime))
        val endTimeStr = if (trip.endTime != null) timeFmt.format(Date(trip.endTime)) else "Ongoing"

        val milestoneDescriptions = milestones.take(12).joinToString("; ") { m ->
            "#${m.sequenceNumber} ${m.title} at ${m.timeFormatted} (${m.subtitle})"
        }

        val prompt = """
            You are 'Rio AI Trip Copilot', a smart automotive and GPS trip intelligence analyst.
            Analyze this recorded GPS journey data:
            - Title: ${trip.title.ifBlank { "Unnamed Trip" }}
            - Transport Mode: ${trip.category} (${trip.currentMode})
            - Start Time: $startTimeStr
            - End Time: $endTimeStr
            - Duration: $durationMins minutes
            - Total Distance: ${"%.2f".format(distanceKm)} km
            - Average Speed: ${"%.1f".format(trip.avgSpeedKmh)} km/h
            - Max Top Speed: ${"%.1f".format(trip.maxSpeedKmh)} km/h
            - Total Stops: ${trip.stayCount} stops (total idle duration: ${trip.totalStayDurationMs / 60000} mins)
            - Start Location: ${trip.startLocationName.ifBlank { "Start waypoint" }}
            - Destination: ${trip.endLocationName.ifBlank { "Destination waypoint" }}
            - Key Journey Milestones: $milestoneDescriptions

            Provide an analysis in strict JSON format with exactly these keys:
            {
              "styleTag": "Short 2-3 word tag (e.g. 'Scenic Coastal Cruise', 'Swift Highway Sprint', 'Urban Commute')",
              "summary": "2-3 engaging, descriptive sentences summarizing the journey, pace, stops, and driving style.",
              "ecoScore": an integer between 40 and 100 representing driving efficiency (penalize excessive idle or high speed bursts, reward smooth steady speed),
              "drivingTips": "2 concise bulleted tips for improving fuel economy, safety, or route comfort next time.",
              "highlights": ["Highlight 1", "Highlight 2", "Highlight 3"]
            }
            Output only the valid JSON, no surrounding code blocks or extra text.
        """.trimIndent()

        try {
            val url = "$BASE_ENDPOINT?key=$keyToUse"
            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 1024)
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(rawBody, response.code)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonText = parseGeneratedText(rawBody).trim()
            val parsed = parseAiJsonResponse(jsonText, distanceKm, trip.avgSpeedKmh)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing trip with Gemini", e)
            Result.failure(e)
        }
    }

    suspend fun askTripQuestion(
        trip: TripEntity,
        question: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val keyToUse = getEffectiveApiKey()
        if (keyToUse.isBlank() || keyToUse == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        val distanceKm = trip.distanceMeters / 1000.0
        val durationMins = if (trip.endTime != null && trip.endTime > trip.startTime) {
            (trip.endTime - trip.startTime) / 60000
        } else {
            0L
        }

        val prompt = """
            You are Rio AI Trip Copilot.
            Context of this GPS trip:
            - Title: ${trip.title}
            - Mode: ${trip.category} (${trip.currentMode})
            - Distance: ${"%.2f".format(distanceKm)} km
            - Duration: $durationMins mins
            - Avg Speed: ${"%.1f".format(trip.avgSpeedKmh)} km/h
            - Max Speed: ${"%.1f".format(trip.maxSpeedKmh)} km/h
            - Stops: ${trip.stayCount} (Stay duration: ${trip.totalStayDurationMs / 60000} mins)
            - Start: ${trip.startLocationName}
            - End: ${trip.endLocationName}

            User's question: "$question"

            Answer clearly, concisely, and helpfully in 2-4 sentences based on the telemetry data.
        """.trimIndent()

        try {
            val url = "$BASE_ENDPOINT?key=$keyToUse"
            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 500)
                })
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception(parseErrorMessage(rawBody, response.code)))
            }

            val answer = parseGeneratedText(rawBody)
            Result.success(answer)
        } catch (e: Exception) {
            Log.e(TAG, "Error in askTripQuestion", e)
            Result.failure(e)
        }
    }

    private fun parseGeneratedText(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            if (parts.length() == 0) return ""
            parts.getJSONObject(0).optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseAiJsonResponse(jsonText: String, distanceKm: Double, avgSpeedKmh: Double): TripAiResult {
        // Clean markdown backticks if any
        val cleanJson = jsonText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val obj = JSONObject(cleanJson)
            val summary = obj.optString("summary", "Trip completed smoothly covering ${"%.1f".format(distanceKm)} km at an average speed of ${"%.1f".format(avgSpeedKmh)} km/h.")
            val styleTag = obj.optString("styleTag", "Cruising Drive")
            val ecoScore = obj.optInt("ecoScore", 85).coerceIn(30, 100)
            val drivingTips = obj.optString("drivingTips", "Maintain steady acceleration to optimize fuel economy.")

            val highlightsList = mutableListOf<String>()
            val hlArray = obj.optJSONArray("highlights")
            if (hlArray != null) {
                for (i in 0 until hlArray.length()) {
                    highlightsList.add(hlArray.getString(i))
                }
            }
            if (highlightsList.isEmpty()) {
                highlightsList.add("Distance: ${"%.1f".format(distanceKm)} km")
                highlightsList.add("Avg Speed: ${"%.1f".format(avgSpeedKmh)} km/h")
            }

            TripAiResult(
                summary = summary,
                styleTag = styleTag,
                ecoScore = ecoScore,
                drivingTips = drivingTips,
                highlights = highlightsList
            )
        } catch (e: Exception) {
            // Fallback parsing if JSON was slightly malformed
            TripAiResult(
                summary = cleanJson.ifBlank { "Trip analyzed with Gemini AI successfully." },
                styleTag = "Smart Route",
                ecoScore = 86,
                drivingTips = "Drive smoothly and observe regular rest breaks on longer trips.",
                highlights = listOf("Total Distance: ${"%.1f".format(distanceKm)} km", "Average Pace: ${"%.1f".format(avgSpeedKmh)} km/h")
            )
        }
    }

    private fun parseErrorMessage(rawBody: String, code: Int): String {
        return try {
            val json = JSONObject(rawBody)
            val error = json.optJSONObject("error")
            val msg = error?.optString("message") ?: "HTTP $code"
            "Gemini API Error ($code): $msg"
        } catch (e: Exception) {
            "Gemini API Error (HTTP $code)"
        }
    }
}
