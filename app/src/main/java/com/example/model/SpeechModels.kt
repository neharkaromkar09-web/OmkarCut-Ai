package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class WordTimestamp(
    val word: String,
    val start: Double, // in seconds
    val end: Double,   // in seconds
    val confidence: Float = 0.95f,
    val isBoundaryCandidate: Boolean = false,
    val pauseAfterMs: Long = 0L
)

@Serializable
data class SpeechBoundary(
    val time: Double, // in seconds
    val reason: String,
    val confidence: Float,
    val source: String = "Gemini AI",
    val frameIndex: Long = 0L
)

@Serializable
data class TranscriptResult(
    val fullText: String = "",
    val words: List<WordTimestamp> = emptyList(),
    val language: String = "en",
    val speechDurationSeconds: Double = 0.0,
    val speechSegmentsCount: Int = 0
)

@Serializable
data class GeminiBoundaryItem(
    val time: Double,
    val reason: String,
    val confidence: Float = 0.9f
)

@Serializable
data class GeminiAnalysisResult(
    val boundaries: List<GeminiBoundaryItem> = emptyList(),
    val summary: String = "",
    val speechRhythmPace: String = "Natural",
    val averagePauseMs: Long = 0L,
    val totalKeyframesSuggested: Int = 0
)
