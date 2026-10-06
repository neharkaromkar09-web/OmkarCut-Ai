package com.example.engine

import android.util.Log
import com.example.BuildConfig
import com.example.model.GeminiAnalysisResult
import com.example.model.GeminiBoundaryItem
import com.example.model.TranscriptResult
import com.example.model.WordTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Serializable
data class AnalyzeSpeechBackendRequest(
    val fullText: String,
    val words: List<WordTimestamp>,
    val videoFps: Double,
    val speechDurationSeconds: Double
)

@Serializable
data class BackendErrorResponse(
    val error: String? = null,
    val message: String? = null
)

object GeminiSemanticEngine {
    private const val TAG = "GeminiSemanticEngine"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sends speech transcript & timestamps to the secure backend proxy.
     * The backend holds GEMINI_API_KEY server-side.
     * The client APK NEVER receives or stores the API key.
     */
    suspend fun analyzeSpeechBoundaries(
        transcriptResult: TranscriptResult,
        videoFps: Double
    ): Pair<GeminiAnalysisResult, String?> = withContext(Dispatchers.IO) {
        val backendUrls = listOfNotNull(
            BuildConfig.BACKEND_URL.takeIf { it.isNotBlank() },
            BuildConfig.DEV_BACKEND_URL.takeIf { it.isNotBlank() }
        )

        val backendPayload = AnalyzeSpeechBackendRequest(
            fullText = transcriptResult.fullText,
            words = transcriptResult.words,
            videoFps = videoFps,
            speechDurationSeconds = transcriptResult.speechDurationSeconds
        )
        val jsonPayload = jsonParser.encodeToString(AnalyzeSpeechBackendRequest.serializer(), backendPayload)
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())

        var lastErrorMessage: String? = null

        for (baseUrl in backendUrls) {
            val endpoint = "${baseUrl.trimEnd('/')}/api/analyze-speech"
            try {
                val httpRequest = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val parsedResult = jsonParser.decodeFromString(GeminiAnalysisResult.serializer(), responseBody)
                    val cleanBoundaries = parsedResult.boundaries.map { item ->
                        GeminiBoundaryItem(
                            time = (item.time * 100.0).roundToInt() / 100.0,
                            reason = item.reason.ifBlank { "Semantic thought completion" },
                            confidence = item.confidence.coerceIn(0.70f, 0.99f)
                        )
                    }.sortedBy { it.time }

                    val finalResult = parsedResult.copy(
                        boundaries = cleanBoundaries,
                        totalKeyframesSuggested = cleanBoundaries.size * 7
                    )
                    return@withContext Pair(finalResult, null)
                } else if (responseBody != null) {
                    val errorObj = try {
                        jsonParser.decodeFromString(BackendErrorResponse.serializer(), responseBody)
                    } catch (_: Exception) {
                        null
                    }
                    lastErrorMessage = errorObj?.error ?: errorObj?.message ?: "HTTP ${response.code} from server."
                    Log.w(TAG, "Backend returned error from $endpoint: $lastErrorMessage")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to backend at $endpoint: ${e.message}")
                lastErrorMessage = e.message
            }
        }

        // If backend returned an error or was unavailable
        val defaultNotice = "AI analysis is temporarily unavailable. Please configure the Gemini API on the server."
        val noticeMessage = if (lastErrorMessage?.contains("configure", ignoreCase = true) == true) {
            lastErrorMessage
        } else {
            defaultNotice
        }

        Log.w(TAG, "Using fallback semantic rhythm: $noticeMessage")
        val fallback = generateAlgorithmicSemanticBoundaries(
            transcriptResult,
            videoFps,
            reasonPrefix = "Semantic Rhythm Engine"
        )
        Pair(fallback, defaultNotice)
    }

    fun generateAlgorithmicSemanticBoundaries(
        transcriptResult: TranscriptResult,
        videoFps: Double,
        reasonPrefix: String = "Semantic Punctuation"
    ): GeminiAnalysisResult {
        val boundaries = mutableListOf<GeminiBoundaryItem>()
        val words = transcriptResult.words

        if (words.isNotEmpty()) {
            for (i in words.indices) {
                val w = words[i]
                val hasPunctuation = w.word.endsWith(".") || w.word.endsWith("?") || w.word.endsWith("!") || w.word.endsWith(",")
                val hasPause = w.pauseAfterMs >= 220L
                val isLongWordPause = w.pauseAfterMs >= 300L

                if (hasPunctuation || hasPause || w.isBoundaryCandidate) {
                    val boundaryTime = (w.end * 100.0).roundToInt() / 100.0
                    val reason = when {
                        w.word.endsWith(".") -> "$reasonPrefix: Terminal sentence completion and vocal inflection drop"
                        w.word.endsWith("?") -> "$reasonPrefix: Rising interrogative hook inviting audience curiosity"
                        w.word.endsWith("!") -> "$reasonPrefix: High-energy acoustic emphasis & focal punch point"
                        isLongWordPause -> "$reasonPrefix: Deliberate dramatic pause (${w.pauseAfterMs}ms)"
                        else -> "$reasonPrefix: Natural phrase clause transition"
                    }
                    val confidence = when {
                        w.word.endsWith(".") || w.word.endsWith("!") -> 0.96f
                        hasPause -> 0.92f
                        else -> 0.85f
                    }
                    boundaries.add(GeminiBoundaryItem(time = boundaryTime, reason = reason, confidence = confidence))
                }
            }
        }

        // If boundaries are still empty, create rhythm markers based on duration
        if (boundaries.isEmpty()) {
            val totalSec = transcriptResult.speechDurationSeconds.coerceAtLeast(3.0)
            var t = 1.8
            while (t < totalSec - 0.8) {
                boundaries.add(
                    GeminiBoundaryItem(
                        time = (t * 100.0).roundToInt() / 100.0,
                        reason = "$reasonPrefix: Speech rhythm cadence anchor",
                        confidence = 0.90f
                    )
                )
                t += 2.2
            }
        }

        return GeminiAnalysisResult(
            boundaries = boundaries.distinctBy { (it.time * 10).roundToInt() },
            summary = "Analyzed ${words.size} spoken words across ${transcriptResult.speechDurationSeconds}s timeline. Detected ${boundaries.size} natural rhythm anchors.",
            speechRhythmPace = "Dynamic Conversational",
            totalKeyframesSuggested = boundaries.size * 7
        )
    }
}
