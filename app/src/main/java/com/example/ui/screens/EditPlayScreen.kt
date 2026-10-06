package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonBadge
import com.example.ui.components.ZoomScaleGauge
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.EditorViewModel
import java.util.Locale

@UnstableApi
@Composable
fun EditPlayScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val currentZoomScale by viewModel.currentZoomScale.collectAsState()
    val activeBoundary by viewModel.activeBoundary.collectAsState()
    val inspection by viewModel.inspection.collectAsState()
    val boundaries by viewModel.boundaries.collectAsState()
    val keyframes by viewModel.keyframes.collectAsState()

    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val sliderValue = if (isUserScrubbing) {
        scrubPositionMs.coerceIn(0f, safeDuration.toFloat())
    } else {
        currentPositionMs.toFloat().coerceIn(0f, safeDuration.toFloat())
    }

    val isPortrait = inspection?.isPortrait ?: true
    val videoAspect = if (isPortrait) 9f / 16f else 16f / 9f

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top HUD Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Live Keyframe Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Hardware-accelerated GPU zoom transformation",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            ZoomScaleGauge(currentScale = currentZoomScale)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Video Player Viewport Container with GPU GraphicsLayer Scaling
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .aspectRatio(videoAspect, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, BorderHighlight, RoundedCornerShape(20.dp)),
                color = DarkBackground
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Stable AndroidX Media3 PlayerView with GPU zoom applied to container
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = viewModel.exoPlayer
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { playerView ->
                            if (playerView.player != viewModel.exoPlayer) {
                                playerView.player = viewModel.exoPlayer
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Hardware-accelerated GPU render transform
                                scaleX = currentZoomScale
                                scaleY = currentZoomScale
                            }
                            .testTag("exo_player_view")
                    )

                    // Cinematic framing guide overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    )

                    // Active boundary popup overlay
                    if (activeBoundary != null) {
                        activeBoundary?.let { b ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CardSurfaceElevated.copy(alpha = 0.92f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomOutMap,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${b.reason} (Conf: ${(b.confidence * 100).toInt()}%)",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Timeline Scrubber & Controls Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = BorderHighlight
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Time position & markers summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentSec = sliderValue / 1000
                    val totalSec = safeDuration / 1000
                    Text(
                        text = String.format(Locale.US, "%d:%02d / %d:%02d", currentSec.toInt() / 60, currentSec.toInt() % 60, totalSec / 60, totalSec % 60),
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NeonBadge(text = "${boundaries.size} Speech Cuts", color = GoldAccent)
                        NeonBadge(text = "${keyframes.size} KFs", color = NeonPurple)
                    }
                }

                // Interactive Scrubber Slider
                Slider(
                    value = sliderValue,
                    onValueChange = { newVal ->
                        isUserScrubbing = true
                        scrubPositionMs = newVal
                        viewModel.seekTo(newVal.toLong())
                    },
                    onValueChangeFinished = {
                        isUserScrubbing = false
                    },
                    valueRange = 0f..safeDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = BorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timeline_slider")
                )

                // Playback Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.seekTo((sliderValue - 2000L).toLong().coerceAtLeast(0L)) }
                    ) {
                        Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Back 2s", tint = TextPrimary)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Play/Pause Big Button
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(NeonPurple, NeonCyan))
                            )
                            .clickable { viewModel.togglePlayPause() }
                            .testTag("play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = DarkBackground,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = { viewModel.seekTo((sliderValue + 2000L).toLong().coerceAtMost(safeDuration)) }
                    ) {
                        Icon(imageVector = Icons.Default.FastForward, contentDescription = "Forward 2s", tint = TextPrimary)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = { viewModel.seekTo(0L) }
                    ) {
                        Icon(imageVector = Icons.Default.Replay, contentDescription = "Restart", tint = TextSecondary)
                    }
                }
            }
        }
    }
}
