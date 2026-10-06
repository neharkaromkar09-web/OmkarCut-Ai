package com.example.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.model.MediaInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

object MediaInspector {

    suspend fun inspectMedia(context: Context, videoFile: File): MediaInspection = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        try {
            retriever.setDataSource(videoFile.absolutePath)
            extractor.setDataSource(videoFile.absolutePath)

            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L

            val isRotated = rotation == 90 || rotation == 270
            val displayWidth = if (isRotated) rawHeight else rawWidth
            val displayHeight = if (isRotated) rawWidth else rawHeight
            val isPortrait = displayHeight > displayWidth

            val aspectRatio = calculateAspectRatio(displayWidth, displayHeight)

            // Inspect tracks with MediaExtractor
            var hasVideo = false
            var hasAudio = false
            var videoMime = "video/avc"
            var videoCodec = "H.264 / AVC"
            var audioMime = "audio/mp4a-latm"
            var audioCodec = "AAC"
            var audioChannels = 2
            var audioSampleRate = 44100
            var audioBitrate = 128000L
            var reportedFps = 0.0

            val trackCount = extractor.trackCount
            var videoTrackIndex = -1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    hasVideo = true
                    videoTrackIndex = i
                    videoMime = mime
                    videoCodec = formatMimeToReadable(mime)
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        reportedFps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true
                    audioMime = mime
                    audioCodec = formatMimeToReadable(mime)
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        audioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        audioBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                    }
                }
            }

            // Accurate Frame Rate calculation by sampling PTS (Presentation Timestamps)
            var detectedFps = reportedFps
            var isVfr = false
            if (hasVideo && videoTrackIndex >= 0) {
                extractor.selectTrack(videoTrackIndex)
                val timestamps = mutableListOf<Long>()
                var sampleCount = 0
                while (sampleCount < 40 && extractor.sampleTime >= 0) {
                    timestamps.add(extractor.sampleTime)
                    extractor.advance()
                    sampleCount++
                }

                if (timestamps.size > 2) {
                    val intervals = mutableListOf<Long>()
                    for (k in 1 until timestamps.size) {
                        val diff = timestamps[k] - timestamps[k - 1]
                        if (diff > 0) intervals.add(diff)
                    }
                    if (intervals.isNotEmpty()) {
                        val avgDeltaUs = intervals.average()
                        if (avgDeltaUs > 0) {
                            val computedFps = 1_000_000.0 / avgDeltaUs
                            if (detectedFps <= 0.0 || detectedFps > 120.0 || detectedFps < 10.0) {
                                detectedFps = computedFps
                            }
                            // Detect variable frame rate (variance in delta > 25%)
                            val variance = intervals.map { kotlin.math.abs(it - avgDeltaUs) }.average()
                            isVfr = (variance / avgDeltaUs) > 0.25
                        }
                    }
                }
            }

            if (detectedFps <= 0.0) {
                detectedFps = 30.0
            }
            // Clamp reasonable FPS
            val roundedFps = (detectedFps * 100.0).roundToInt() / 100.0
            val estimatedFrames = if (durationMs > 0) {
                ((durationMs / 1000.0) * roundedFps).toLong().coerceAtLeast(1L)
            } else 1L

            val isHighFps = roundedFps >= 45.0
            val durationSec = durationMs / 1000
            val durationMin = durationSec / 60
            val formattedDuration = String.format(Locale.US, "%d:%02d", durationMin, durationSec % 60)
            val fileSizeFormatted = formatFileSize(videoFile.length())

            val rFrameRate = String.format(Locale.US, "%.2f/1", roundedFps)
            val avgFrameRate = String.format(Locale.US, "%.2f/1", roundedFps)
            val timeBase = "1/1000000"

            MediaInspection(
                filePath = videoFile.absolutePath,
                fileName = videoFile.name,
                fileSizeFormatted = fileSizeFormatted,
                fileSizeBytes = videoFile.length(),
                width = rawWidth,
                height = rawHeight,
                rotation = rotation,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                aspectRatio = aspectRatio,
                isPortrait = isPortrait,
                durationMs = durationMs,
                durationFormatted = formattedDuration,
                detectedFps = roundedFps,
                rFrameRate = rFrameRate,
                avgFrameRate = avgFrameRate,
                timeBase = timeBase,
                estimatedFrameCount = estimatedFrames,
                videoCodec = videoCodec,
                videoMimeType = videoMime,
                videoBitrate = bitrate,
                hasAudio = hasAudio,
                audioCodec = audioCodec,
                audioMimeType = audioMime,
                audioChannels = audioChannels,
                audioSampleRate = audioSampleRate,
                audioBitrate = audioBitrate,
                isVfr = isVfr,
                isHighFps = isHighFps,
                validationStatus = if (hasVideo && durationMs > 0) "Valid Stream (Pass)" else "Inspection Warning"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            MediaInspection(
                filePath = videoFile.absolutePath,
                fileName = videoFile.name,
                validationStatus = "Metadata Error: ${e.message}"
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

    private fun calculateAspectRatio(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return "16:9"
        val ratio = w.toDouble() / h.toDouble()
        return when {
            kotlin.math.abs(ratio - (16.0 / 9.0)) < 0.05 -> "16:9"
            kotlin.math.abs(ratio - (9.0 / 16.0)) < 0.05 -> "9:16"
            kotlin.math.abs(ratio - 1.0) < 0.05 -> "1:1"
            kotlin.math.abs(ratio - (4.0 / 3.0)) < 0.05 -> "4:3"
            kotlin.math.abs(ratio - (3.0 / 4.0)) < 0.05 -> "3:4"
            else -> String.format(Locale.US, "%.2f:1", ratio)
        }
    }

    private fun formatMimeToReadable(mime: String): String {
        return when {
            mime.contains("avc", ignoreCase = true) || mime.contains("h264", ignoreCase = true) -> "H.264 / AVC"
            mime.contains("hevc", ignoreCase = true) || mime.contains("h265", ignoreCase = true) -> "H.265 / HEVC"
            mime.contains("mp4a-latm", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC LC"
            mime.contains("opus", ignoreCase = true) -> "Opus"
            mime.contains("vp9", ignoreCase = true) -> "VP9"
            mime.contains("av01", ignoreCase = true) -> "AV1"
            else -> mime
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.1f KB", kb)
        }
    }
}
