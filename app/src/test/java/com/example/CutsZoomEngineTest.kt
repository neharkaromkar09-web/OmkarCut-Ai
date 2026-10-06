package com.example

import com.example.engine.KeyframeEngine
import com.example.model.EasingCurve
import com.example.model.ReferenceStyleProfile
import com.example.model.SpeechBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutsZoomEngineTest {

    @Test
    fun testKeyframeGenerationPunchOut() {
        val boundaries = listOf(
            SpeechBoundary(
                time = 2.0, // 2.0s
                reason = "Natural sentence completion",
                confidence = 0.95f
            )
        )
        val fps = 30.0
        val durationMs = 6000L
        val profile = ReferenceStyleProfile(
            normalScale = 1.00f,
            wideScale = 0.70f,
            easing = EasingCurve.CUBIC_EASE_OUT
        )

        val keyframes = KeyframeEngine.generateKeyframes(boundaries, fps, durationMs, profile)

        // Must have start, punch-out sequence, and end
        assertTrue("Keyframes should not be empty", keyframes.isNotEmpty())

        // Frame at 2.0s with 30fps is frame 60
        val punchOutKf = keyframes.firstOrNull { it.frameIndex == 60L }
        assertNotNull("Should contain root punch-out keyframe at frame 60", punchOutKf)
        assertEquals(0.70f, punchOutKf!!.scale, 0.01f)
        assertTrue(punchOutKf.isBoundaryRoot)

        // F - 1 is frame 59, scale should be 1.00f
        val beforeKf = keyframes.firstOrNull { it.frameIndex == 59L }
        assertNotNull("Should contain keyframe at frame 59", beforeKf)
        assertEquals(1.00f, beforeKf!!.scale, 0.01f)

        // F + 10 is frame 70, scale should be fully recovered to 1.00f
        val recoveredKf = keyframes.firstOrNull { it.frameIndex == 70L }
        assertNotNull("Should contain recovery keyframe at frame 70", recoveredKf)
        assertEquals(1.00f, recoveredKf!!.scale, 0.01f)
    }

    @Test
    fun testFrameRateSafetyDifferentFps() {
        val boundary = SpeechBoundary(time = 1.0, reason = "Cadence drop", confidence = 0.9f)
        val durationMs = 5000L

        // 24 FPS video
        val kfs24 = KeyframeEngine.generateKeyframes(listOf(boundary), 24.0, durationMs)
        val punch24 = kfs24.first { it.isBoundaryRoot }
        assertEquals(24L, punch24.frameIndex)
        assertEquals(1.0, punch24.timeSeconds, 0.01)

        // 60 FPS video
        val kfs60 = KeyframeEngine.generateKeyframes(listOf(boundary), 60.0, durationMs)
        val punch60 = kfs60.first { it.isBoundaryRoot }
        assertEquals(60L, punch60.frameIndex)
        assertEquals(1.0, punch60.timeSeconds, 0.01)
    }

    @Test
    fun testInterpolationSmoothness() {
        val boundaries = listOf(
            SpeechBoundary(time = 2.0, reason = "Test", confidence = 0.95f)
        )
        val profile = ReferenceStyleProfile(normalScale = 1.00f, wideScale = 0.70f)
        val keyframes = KeyframeEngine.generateKeyframes(boundaries, 30.0, 5000L, profile)

        // At exact punch-out timestamp (2000 ms), scale should be ~0.70f
        val scaleAtPunch = KeyframeEngine.calculateInterpolatedScale(2000L, keyframes, profile)
        assertEquals(0.70f, scaleAtPunch, 0.02f)

        // At 2100 ms (during recovery), scale should be between 0.70f and 1.00f
        val scaleMid = KeyframeEngine.calculateInterpolatedScale(2100L, keyframes, profile)
        assertTrue("Scale during recovery should be > 0.70f", scaleMid > 0.70f)
        assertTrue("Scale during recovery should be <= 1.00f", scaleMid <= 1.00f)

        // At 3000 ms (well after recovery), scale should be back to 1.00f
        val scaleAfter = KeyframeEngine.calculateInterpolatedScale(3000L, keyframes, profile)
        assertEquals(1.00f, scaleAfter, 0.01f)
    }

    @Test
    fun testCloselySpacedBoundariesResolution() {
        // Two boundaries only 300ms apart (e.g. at 1.0s and 1.3s)
        val boundaries = listOf(
            SpeechBoundary(time = 1.0, reason = "Boundary 1", confidence = 0.9f),
            SpeechBoundary(time = 1.3, reason = "Boundary 2", confidence = 0.95f)
        )
        val keyframes = KeyframeEngine.generateKeyframes(boundaries, 30.0, 5000L)
        assertTrue("Should handle closely spaced boundaries gracefully", keyframes.isNotEmpty())

        // Verify keyframes are strictly monotonic by frameIndex and scale values are valid
        for (i in 1 until keyframes.size) {
            assertTrue("Keyframe frame indices must be strictly increasing", keyframes[i].frameIndex > keyframes[i - 1].frameIndex)
            assertTrue("Scale must be in valid range", keyframes[i].scale in 0.60f..1.10f)
        }
    }
}
