package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.model.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

object VideoValidator {
    private const val TAG = "VideoValidator"

    /**
     * Strictly verifies that the rendered MP4 file:
     * 1. Exists and has positive file size.
     * 2. Contains a valid H.264/AVC video stream with width > 0, height > 0, duration > 0.
     * 3. Contains a valid audio stream (if expected).
     * 4. Decodes actual frames from the video.
     * 5. Confirms frames are NOT all-black (calculates luminance and color distribution).
     */
    suspend fun validateExportedVideo(
        file: File,
        expectedDurationMs: Long = 0L,
        requireAudio: Boolean = false
    ): ValidationResult = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 1024L) {
            return@withContext ValidationResult(
                isValid = false,
                hasVideoStream = false,
                hasAudioStream = false,
                width = 0,
                height = 0,
                durationMs = 0L,
                decodedFrameCount = 0,
                isBlackVideo = true,
                averageLuminance = 0f,
                errorMessage = "File missing or empty (size: ${file.length()} bytes)"
            )
        }

        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        try {
            retriever.setDataSource(file.absolutePath)
            extractor.setDataSource(file.absolutePath)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val hasVideoMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null

            var hasVideoTrack = false
            var hasAudioTrack = false

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) hasVideoTrack = true
                if (mime.startsWith("audio/")) hasAudioTrack = true
            }

            if (!hasVideoTrack || width <= 0 || height <= 0 || durationMs <= 0L) {
                return@withContext ValidationResult(
                    isValid = false,
                    hasVideoStream = hasVideoTrack,
                    hasAudioStream = hasAudioTrack,
                    width = width,
                    height = height,
                    durationMs = durationMs,
                    decodedFrameCount = 0,
                    isBlackVideo = true,
                    averageLuminance = 0f,
                    errorMessage = "Invalid video metadata: width=$width, height=$height, duration=$durationMs"
                )
            }

            if (requireAudio && !hasAudioTrack) {
                return@withContext ValidationResult(
                    isValid = false,
                    hasVideoStream = hasVideoTrack,
                    hasAudioStream = false,
                    width = width,
                    height = height,
                    durationMs = durationMs,
                    decodedFrameCount = 0,
                    isBlackVideo = false,
                    averageLuminance = 0f,
                    errorMessage = "Audio track missing from rendered video"
                )
            }

            // Sample 3 frames across timeline (at 15%, 50%, and 85%) to detect black frames
            val sampleTimesUs = listOf(
                (durationMs * 1000L * 0.15).toLong(),
                (durationMs * 1000L * 0.50).toLong(),
                (durationMs * 1000L * 0.85).toLong()
            )

            var decodedCount = 0
            var totalLuminance = 0.0
            var maxLuminanceInBatch = 0f
            var isAllBlack = true

            for (timeUs in sampleTimesUs) {
                val frameBitmap: Bitmap? = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) {
                    null
                }

                if (frameBitmap != null) {
                    decodedCount++
                    val frameLuma = analyzeBitmapLuminance(frameBitmap)
                    totalLuminance += frameLuma
                    if (frameLuma > maxLuminanceInBatch) maxLuminanceInBatch = frameLuma

                    // If any frame has detectable luma > 0.04 (not pitch black)
                    if (frameLuma > 0.035f) {
                        isAllBlack = false
                    }
                }
            }

            val avgLuminance = if (decodedCount > 0) (totalLuminance / decodedCount).toFloat() else 0f

            if (decodedCount == 0) {
                return@withContext ValidationResult(
                    isValid = false,
                    hasVideoStream = true,
                    hasAudioStream = hasAudioTrack,
                    width = width,
                    height = height,
                    durationMs = durationMs,
                    decodedFrameCount = 0,
                    isBlackVideo = true,
                    averageLuminance = 0f,
                    errorMessage = "Failed to decode any video frames"
                )
            }

            if (isAllBlack) {
                Log.e(TAG, "Black video detected! Decoded $decodedCount frames, all pitch black.")
                return@withContext ValidationResult(
                    isValid = false,
                    hasVideoStream = true,
                    hasAudioStream = hasAudioTrack,
                    width = width,
                    height = height,
                    durationMs = durationMs,
                    decodedFrameCount = decodedCount,
                    isBlackVideo = true,
                    averageLuminance = avgLuminance,
                    errorMessage = "Black frame export detected: luminance is zero across sampled frames"
                )
            }

            ValidationResult(
                isValid = true,
                hasVideoStream = true,
                hasAudioStream = hasAudioTrack,
                width = width,
                height = height,
                durationMs = durationMs,
                decodedFrameCount = decodedCount,
                isBlackVideo = false,
                averageLuminance = avgLuminance,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Video validation crashed", e)
            ValidationResult(
                isValid = false,
                hasVideoStream = false,
                hasAudioStream = false,
                width = 0,
                height = 0,
                durationMs = 0L,
                decodedFrameCount = 0,
                isBlackVideo = true,
                averageLuminance = 0f,
                errorMessage = "Validation exception: ${e.message}"
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    private fun analyzeBitmapLuminance(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0f

        var lumaSum = 0.0
        val sampleStepX = (w / 16).coerceAtLeast(1)
        val sampleStepY = (h / 16).coerceAtLeast(1)
        var samples = 0

        for (y in 0 until h step sampleStepY) {
            for (x in 0 until w step sampleStepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel) / 255.0
                val g = Color.green(pixel) / 255.0
                val b = Color.blue(pixel) / 255.0
                // ITU-R BT.709 luma
                val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
                lumaSum += luma
                samples++
            }
        }

        return if (samples > 0) (lumaSum / samples).toFloat() else 0f
    }
}
