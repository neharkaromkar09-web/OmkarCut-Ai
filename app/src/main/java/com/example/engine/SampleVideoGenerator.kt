package com.example.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sin

object SampleVideoGenerator {
    private const val TAG = "SampleVideoGenerator"

    /**
     * Generates a real, valid H.264/AVC portrait talking-head video on-device
     * for immediate testing, speech analysis, and rendering.
     */
    suspend fun createSampleVideo(
        context: Context,
        isPortrait: Boolean = true,
        durationSeconds: Int = 8
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.cacheDir, "sample_videos").apply { mkdirs() }
        val outputFile = File(outputDir, "sample_talk_${if (isPortrait) "portrait" else "landscape"}.mp4")

        if (outputFile.exists() && outputFile.length() > 50000L) {
            return@withContext outputFile
        }

        val width = if (isPortrait) 720 else 1280
        val height = if (isPortrait) 1280 else 720
        val fps = 30
        val totalFrames = durationSeconds * fps
        val bitRate = 2_500_000

        var mediaCodec: MediaCodec? = null
        var mediaMuxer: MediaMuxer? = null
        var videoTrackIndex = -1

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = mediaCodec.createInputSurface()
            mediaCodec.start()

            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            for (frame in 0 until totalFrames) {
                // Draw frame to input surface
                val canvas: Canvas = inputSurface.lockCanvas(null)
                try {
                    drawSampleFrame(canvas, width, height, frame, totalFrames, paint, isPortrait)
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Drain encoder
                while (true) {
                    val status = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            videoTrackIndex = mediaMuxer.addTrack(mediaCodec.outputFormat)
                            mediaMuxer.start()
                            muxerStarted = true
                        }
                    } else if (status >= 0) {
                        val encodedBuffer = mediaCodec.getOutputBuffer(status)
                        if (encodedBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                            encodedBuffer.position(bufferInfo.offset)
                            encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            // Presentation timestamp
                            bufferInfo.presentationTimeUs = (frame * 1_000_000L) / fps
                            mediaMuxer.writeSampleData(videoTrackIndex, encodedBuffer, bufferInfo)
                        }
                        mediaCodec.releaseOutputBuffer(status, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    } else {
                        break
                    }
                }
            }

            // Signal EOS
            mediaCodec.signalEndOfInputStream()
            var eosDone = false
            var tries = 0
            while (!eosDone && tries++ < 30) {
                val status = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000)
                if (status >= 0) {
                    val encodedBuffer = mediaCodec.getOutputBuffer(status)
                    if (encodedBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                        encodedBuffer.position(bufferInfo.offset)
                        encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        mediaMuxer.writeSampleData(videoTrackIndex, encodedBuffer, bufferInfo)
                    }
                    mediaCodec.releaseOutputBuffer(status, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eosDone = true
                    }
                } else if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sample video generation failed", e)
        } finally {
            try {
                mediaCodec?.stop()
                mediaCodec?.release()
            } catch (_: Exception) {}
            try {
                mediaMuxer?.stop()
                mediaMuxer?.release()
            } catch (_: Exception) {}
        }

        outputFile
    }

    private fun drawSampleFrame(
        canvas: Canvas,
        w: Int,
        h: Int,
        frame: Int,
        total: Int,
        paint: Paint,
        isPortrait: Boolean
    ) {
        val t = frame.toFloat() / total.toFloat()

        // Background studio gradient
        canvas.drawColor(Color.parseColor("#0C0A1A"))

        // Glowing studio spotlight
        paint.color = Color.parseColor("#261D4C")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(w * 0.5f, h * 0.45f, w * 0.45f, paint)

        paint.color = Color.parseColor("#3C2978")
        canvas.drawCircle(w * 0.5f, h * 0.45f, w * 0.32f, paint)

        // Talking head presenter avatar
        val headY = h * 0.42f
        val headX = w * 0.5f
        val headR = w * 0.16f

        // Head shadow/glow
        paint.color = Color.parseColor("#150F2C")
        canvas.drawCircle(headX, headY, headR * 1.15f, paint)

        // Face
        paint.color = Color.parseColor("#F5D0B0")
        canvas.drawCircle(headX, headY, headR, paint)

        // Hair
        paint.color = Color.parseColor("#2A1E38")
        canvas.drawArc(
            RectF(headX - headR, headY - headR, headX + headR, headY),
            180f,
            180f,
            true,
            paint
        )

        // Eyes
        paint.color = Color.parseColor("#1A1428")
        val eyeSpacing = headR * 0.45f
        val eyeY = headY - (headR * 0.1f)
        canvas.drawCircle(headX - eyeSpacing, eyeY, headR * 0.12f, paint)
        canvas.drawCircle(headX + eyeSpacing, eyeY, headR * 0.12f, paint)

        // Animated talking mouth (opening and closing with speech cadence)
        val mouthY = headY + (headR * 0.45f)
        val mouthOpen = (sin(frame * 0.45) * 0.5 + 0.5).toFloat() * (headR * 0.28f)
        paint.color = Color.parseColor("#A83244")
        canvas.drawOval(
            RectF(
                headX - (headR * 0.35f),
                mouthY - (mouthOpen * 0.5f),
                headX + (headR * 0.35f),
                mouthY + (mouthOpen * 0.8f) + 6f
            ),
            paint
        )

        // Torso/Presenter clothes
        paint.color = Color.parseColor("#1E163B")
        val bodyRect = RectF(
            headX - (headR * 1.8f),
            headY + (headR * 0.9f),
            headX + (headR * 1.8f),
            h.toFloat()
        )
        canvas.drawRoundRect(bodyRect, 40f, 40f, paint)

        // Neon Framing Box / Zoom Target
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.parseColor("#00E5FF")
        val margin = w * 0.08f
        canvas.drawRoundRect(
            RectF(margin, margin, w - margin, h - margin),
            24f,
            24f,
            paint
        )

        // HUD Text Overlay
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = if (isPortrait) 36f else 32f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("CUTSZOOM AI - TALKING HEAD", w * 0.5f, h * 0.12f, paint)

        paint.color = Color.parseColor("#B388FF")
        paint.textSize = if (isPortrait) 26f else 24f
        val sec = frame / 30f
        canvas.drawText(String.format("FRAME: %d | TIME: %.2fs", frame, sec), w * 0.5f, h * 0.86f, paint)

        paint.color = Color.parseColor("#00E5FF")
        canvas.drawText("SPEECH CADENCE SYNCHRONIZED", w * 0.5f, h * 0.91f, paint)
    }
}
