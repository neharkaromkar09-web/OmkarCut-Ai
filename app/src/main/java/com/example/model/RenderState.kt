package com.example.model

import android.net.Uri
import java.io.File

enum class RenderStage(val displayName: String) {
    IDLE("Ready to render"),
    PREPARING("Preparing source media & keyframes..."),
    PROCESSING_FRAMES("Processing video frames & zoom curves..."),
    ENCODING_MP4("Encoding H.264 / AAC MP4..."),
    VALIDATING_EXPORT("Running deep black-frame & stream validation..."),
    FALLBACK_RENDERING("Safe fallback rendering engaged..."),
    SAVING_MEDIASTORE("Exporting to Android MediaStore..."),
    COMPLETED("Export complete & validated"),
    FAILED("Render failed")
}

data class ValidationResult(
    val isValid: Boolean,
    val hasVideoStream: Boolean,
    val hasAudioStream: Boolean,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val decodedFrameCount: Int,
    val isBlackVideo: Boolean,
    val averageLuminance: Float,
    val errorMessage: String? = null
)

data class ExportResult(
    val localFile: File,
    val mediaStoreUri: Uri?,
    val contentUri: Uri,
    val fileSizeFormatted: String,
    val durationFormatted: String,
    val resolution: String,
    val isSafeFallbackUsed: Boolean = false,
    val validation: ValidationResult
)

data class RenderProgress(
    val stage: RenderStage = RenderStage.IDLE,
    val progressFraction: Float = 0f,
    val currentFrame: Long = 0L,
    val totalFrames: Long = 0L,
    val detailMessage: String = "",
    val error: String? = null,
    val exportResult: ExportResult? = null
)
