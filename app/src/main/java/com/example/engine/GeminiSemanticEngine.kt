package com.example.engine

import android.util.Log
import com.example.BuildConfig
import com.example.model.GeminiAnalysisResult
import com.example.model.GeminiBoundaryItem
import com.example.model.SpeechBoundary
import com.example.model.TranscriptResult
import com.example.model.WordTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Serializable
data class GeminiContentPart(val text: String? = null)

@Serializable
data class GeminiContent(val parts: List<GeminiContentPart>)

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String = "application/json",
    val temperature: Float = 0.2f
)

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate>? = null)

@Serializable
data class BoundaryJsonResponse(
    val boundaries: List<GeminiBoundaryItem> = emptyList(),
    val summary: String = "",
    val speechRhythmPace: String = "Dynamic"
)

object GeminiSemanticEngine {
    private const val TAG = "GeminiSemanticEngine"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeSpeechBoundaries(
        transcriptResult: TranscriptResult,
        videoFps: Double
    ): Pair<GeminiAnalysisResult, String?> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        // Server-side check
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val statusMessage = "AI analysis is temporarily unavailable. Please configure the Gemini API on the server."
            Log.w(TAG, statusMessage)
            val fallback = generateAlgorithmicSemanticBoundaries(transcriptResult, videoFps, reasonPrefix = "Acoustic & Semantic Cadence")
            return@withContext Pair(fallback, statusMessage)
        }

        try {
            val prompt = buildPrompt(transcriptResult)
            val requestBodyObj = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiContentPart(text = prompt)))
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(
                        GeminiContentPart(
                            text = "You are an expert film director and AI video rhythm editor for CutsZoom AI. " +
                                    "Analyze the speech transcript, word timestamps, pauses, and syntax. " +
                                    "Detect natural semantic phrase/sentence boundaries where dynamic camera zoom-out (punch-out) accentuates meaning. " +
                                    "Output strictly valid JSON conforming to the requested schema. " +
                                    "Do NOT impose an arbitrary minimum spacing. Closely spaced natural boundaries are allowed."
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.2f
                )
            )

            val jsonBody = jsonParser.encodeToString(GeminiRequest.serializer(), requestBodyObj)
            val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"

            val httpRequest = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                Log.e(TAG, "Gemini API HTTP Error ${response.code}: $responseBody")
                val statusMessage = "AI analysis is temporarily unavailable. Please configure the Gemini API on the server."
                val fallback = generateAlgorithmicSemanticBoundaries(transcriptResult, videoFps, reasonPrefix = "Semantic Rhythm Engine")
                return@withContext Pair(fallback, statusMessage)
            }

            val geminiResponse = jsonParser.decodeFromString(GeminiResponse.serializer(), responseBody)
            val textContent = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (textContent.isNullOrBlank()) {
                val fallback = generateAlgorithmicSemanticBoundaries(transcriptResult, videoFps)
                return@withContext Pair(fallback, "AI analysis completed using semantic rhythmic fallbacks.")
            }

            val parsedResult = jsonParser.decodeFromString(BoundaryJsonResponse.serializer(), textContent)
            val cleanBoundaries = parsedResult.boundaries.map { item ->
                GeminiBoundaryItem(
                    time = (item.time * 100.0).roundToInt() / 100.0,
                    reason = item.reason.ifBlank { "Semantic thought completion" },
                    confidence = item.confidence.coerceIn(0.70f, 0.99f)
                )
            }.sortedBy { it.time }

            val result = GeminiAnalysisResult(
                boundaries = cleanBoundaries,
                summary = parsedResult.summary.ifBlank { "Detected ${cleanBoundaries.size} natural semantic zoom boundaries." },
                speechRhythmPace = parsedResult.speechRhythmPace,
                totalKeyframesSuggested = cleanBoundaries.size * 7
            )

            Pair(result, null)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini semantic analysis", e)
            val statusMessage = "AI analysis is temporarily unavailable. Please configure the Gemini API on the server."
            val fallback = generateAlgorithmicSemanticBoundaries(transcriptResult, videoFps, reasonPrefix = "Semantic Structural Analysis")
            Pair(fallback, statusMessage)
        }
    }

    private fun buildPrompt(transcript: TranscriptResult): String {
        val wordListDump = transcript.words.take(150).joinToString("\n") { w ->
            "- \"${w.word}\" [start=${w.start}s, end=${w.end}s, pauseAfter=${w.pauseAfterMs}ms]"
        }

        return """
            Identify all natural speech, sentence, and semantic thought boundaries for video zoom keyframes.
            
            Video Duration: ${transcript.speechDurationSeconds} seconds
            Full Transcript: "${transcript.fullText}"
            
            Word-level timings:
            $wordListDump
            
            Requirements:
            1. Identify natural phrase/sentence boundaries.
            2. Each boundary must be formatted as:
               {
                 "time": number (seconds),
                 "reason": string (why this boundary punctuates the speech),
                 "confidence": number (between 0.75 and 0.99)
               }
            3. Closely spaced natural boundaries are allowed. Do NOT impose an arbitrary 2-3 second limit.
            
            Return a JSON object:
            {
               "summary": "Brief executive analysis of speaker cadence",
               "speechRhythmPace": "Fast / Natural / Deliberate",
               "boundaries": [
                 { "time": 2.45, "reason": "Complete introductory thought and vocal cadence drop", "confidence": 0.94 }
               ]
            }
        """.trimIndent()
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

        // If boundaries are still empty or video has no words, create rhythm markers based on duration
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
