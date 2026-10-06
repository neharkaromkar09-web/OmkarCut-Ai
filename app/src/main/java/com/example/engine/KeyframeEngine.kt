package com.example.engine

import com.example.model.EasingCurve
import com.example.model.ReferenceStyleProfile
import com.example.model.SpeechBoundary
import com.example.model.ZoomKeyframe
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

object KeyframeEngine {

    /**
     * Converts speech boundaries into dynamic frame-accurate zoom keyframes
     * using the actual video FPS and ReferenceStyleProfile.
     */
    fun generateKeyframes(
        boundaries: List<SpeechBoundary>,
        fps: Double,
        durationMs: Long,
        profile: ReferenceStyleProfile = ReferenceStyleProfile()
    ): List<ZoomKeyframe> {
        val safeFps = if (fps <= 0.0 || fps > 240.0) 30.0 else fps
        val totalDurationSec = if (durationMs > 0) durationMs / 1000.0 else 10.0
        val maxFrame = (totalDurationSec * safeFps).roundToLong()

        val rawKeyframes = mutableListOf<ZoomKeyframe>()

        // Initial normal framing at start of timeline
        rawKeyframes.add(
            ZoomKeyframe(
                timeSeconds = 0.0,
                timeMs = 0L,
                frameIndex = 0L,
                scale = profile.normalScale,
                boundaryReason = "Timeline Start"
            )
        )

        for (boundary in boundaries) {
            val rootFrame = (boundary.time * safeFps).roundToLong().coerceIn(0L, maxFrame)

            for ((frameOffset, targetScale) in profile.recoveryOffsets) {
                val targetFrame = (rootFrame + frameOffset).coerceIn(0L, maxFrame)
                val targetTimeSec = targetFrame / safeFps
                val targetTimeMs = (targetTimeSec * 1000.0).roundToLong().coerceAtLeast(0L)

                // Scale adjustment based on profile
                val adjustedScale = when (frameOffset) {
                    -1 -> profile.normalScale
                    0 -> profile.wideScale
                    else -> {
                        // Smoothly scale between wideScale and normalScale based on relative progress
                        val progress = frameOffset / profile.recoveryDurationFrames.toFloat()
                        profile.wideScale + (profile.normalScale - profile.wideScale) * applyEaseOut(progress, profile.easing)
                    }
                }

                rawKeyframes.add(
                    ZoomKeyframe(
                        timeSeconds = targetTimeSec,
                        timeMs = targetTimeMs,
                        frameIndex = targetFrame,
                        scale = adjustedScale,
                        isBoundaryRoot = (frameOffset == 0),
                        boundaryReason = if (frameOffset == 0) boundary.reason else "",
                        relativeFrameOffset = frameOffset
                    )
                )
            }
        }

        // Final normal framing at end of timeline
        rawKeyframes.add(
            ZoomKeyframe(
                timeSeconds = totalDurationSec,
                timeMs = durationMs,
                frameIndex = maxFrame,
                scale = profile.normalScale,
                boundaryReason = "Timeline End"
            )
        )

        // Sort by time and remove duplicate frame entries keeping the punch-out (minimum scale)
        val sorted = rawKeyframes.sortedWith(compareBy({ it.frameIndex }, { it.scale }))
        val merged = mutableListOf<ZoomKeyframe>()

        for (kf in sorted) {
            val last = merged.lastOrNull()
            if (last != null && last.frameIndex == kf.frameIndex) {
                // If collision at the same frame, prefer the punch-out (lower scale) or root boundary
                if (kf.scale < last.scale || kf.isBoundaryRoot) {
                    merged[merged.lastIndex] = kf
                }
            } else {
                merged.add(kf)
            }
        }

        return merged
    }

    /**
     * Calculates the real-time interpolated zoom scale at a given millisecond presentation timestamp.
     * Uses Cubic Ease-Out interpolation for silky-smooth 60fps GPU preview.
     */
    fun calculateInterpolatedScale(
        currentTimeMs: Long,
        keyframes: List<ZoomKeyframe>,
        profile: ReferenceStyleProfile = ReferenceStyleProfile()
    ): Float {
        if (keyframes.isEmpty()) return profile.normalScale
        if (keyframes.size == 1) return keyframes[0].scale

        if (currentTimeMs <= keyframes.first().timeMs) {
            return keyframes.first().scale
        }
        if (currentTimeMs >= keyframes.last().timeMs) {
            return keyframes.last().scale
        }

        // Binary search for enclosing keyframe interval
        var low = 0
        var high = keyframes.size - 1
        var prevIndex = 0

        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (keyframes[mid].timeMs <= currentTimeMs) {
                prevIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        val nextIndex = (prevIndex + 1).coerceAtMost(keyframes.size - 1)
        val prevKf = keyframes[prevIndex]
        val nextKf = keyframes[nextIndex]

        val intervalMs = nextKf.timeMs - prevKf.timeMs
        if (intervalMs <= 0L) {
            return nextKf.scale
        }

        val rawProgress = ((currentTimeMs - prevKf.timeMs).toDouble() / intervalMs.toDouble()).toFloat().coerceIn(0f, 1f)
        val easedProgress = applyEaseOut(rawProgress, profile.easing)

        val interpolated = prevKf.scale + (nextKf.scale - prevKf.scale) * easedProgress
        return interpolated.coerceIn(0.65f, 1.25f)
    }

    private fun applyEaseOut(t: Float, curve: EasingCurve): Float {
        val clamped = t.coerceIn(0f, 1f)
        return when (curve) {
            EasingCurve.CUBIC_EASE_OUT -> {
                // 1 - (1 - t)^3
                1f - (1f - clamped).pow(3)
            }
            EasingCurve.QUAD_EASE_OUT -> {
                // 1 - (1 - t)^2
                1f - (1f - clamped).pow(2)
            }
            EasingCurve.SMOOTH_STEP -> {
                // 3t^2 - 2t^3
                clamped * clamped * (3f - 2f * clamped)
            }
            EasingCurve.LINEAR -> clamped
        }
    }
}
