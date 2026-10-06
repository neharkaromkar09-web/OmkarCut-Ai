package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaInspection(
    val filePath: String = "",
    val fileName: String = "",
    val fileSizeFormatted: String = "",
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val aspectRatio: String = "16:9",
    val isPortrait: Boolean = false,
    val durationMs: Long = 0L,
    val durationFormatted: String = "0:00",
    val detectedFps: Double = 30.0,
    val rFrameRate: String = "30/1",
    val avgFrameRate: String = "30/1",
    val timeBase: String = "1/1000",
    val estimatedFrameCount: Long = 0L,
    val videoCodec: String = "H.264 / AVC",
    val videoMimeType: String = "video/avc",
    val videoBitrate: Long = 0L,
    val hasAudio: Boolean = false,
    val audioCodec: String = "AAC",
    val audioMimeType: String = "audio/mp4a-latm",
    val audioChannels: Int = 2,
    val audioSampleRate: Int = 44100,
    val audioBitrate: Long = 0L,
    val isVfr: Boolean = false,
    val isHighFps: Boolean = false,
    val validationStatus: String = "Valid Media"
)
