package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class ZoomKeyframe(
    val timeSeconds: Double,
    val timeMs: Long,
    val frameIndex: Long,
    val scale: Float,
    val isBoundaryRoot: Boolean = false,
    val boundaryReason: String = "",
    val relativeFrameOffset: Int = 0 // e.g. -1, 0, 2, 4, 6, 8, 10
)

enum class EasingCurve(val displayName: String) {
    CUBIC_EASE_OUT("Cubic Ease-Out"),
    QUAD_EASE_OUT("Quad Ease-Out"),
    SMOOTH_STEP("Smooth Step"),
    LINEAR("Linear")
}

enum class FramingMode(val displayName: String, val description: String) {
    FIT("Fit with Blurred Background", "Preserves full frame with cinematic frosted background"),
    COVER("Dynamic Center Fill", "Fills the viewport dynamically centered"),
    LETTERBOX("Cinematic Letterbox", "Adds clean pillar/letterbox borders")
}

enum class TargetAspectRatio(val displayName: String, val widthRatio: Int, val heightRatio: Int) {
    ORIGINAL("Original Source", 0, 0),
    VERTICAL_9_16("9:16 Vertical (Reels / TikTok / Shorts)", 9, 16),
    HORIZONTAL_16_9("16:9 Landscape (YouTube / Standard)", 16, 9),
    SQUARE_1_1("1:1 Square (Instagram Post)", 1, 1)
}

@Serializable
data class ReferenceStyleProfile(
    val id: String = "cutszoom_default",
    val name: String = "CutsZoom Signature Punch-Out",
    val normalScale: Float = 1.00f,
    val wideScale: Float = 0.70f,
    val recoveryOffsets: List<Pair<Int, Float>> = listOf(
        -1 to 1.00f,
        0 to 0.70f,
        2 to 0.75f,
        4 to 0.82f,
        6 to 0.90f,
        8 to 0.96f,
        10 to 1.00f
    ),
    val recoveryDurationFrames: Int = 10,
    val easing: EasingCurve = EasingCurve.CUBIC_EASE_OUT,
    val framingBehavior: FramingMode = FramingMode.FIT,
    val targetAspectRatio: TargetAspectRatio = TargetAspectRatio.ORIGINAL,
    val blurBackgroundRadius: Float = 25f,
    val isLearnedFromReference: Boolean = false,
    val referenceVideoName: String? = null
)
