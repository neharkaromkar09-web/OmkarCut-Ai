package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.engine.MediaStoreExporter
import com.example.model.ExportResult
import com.example.model.FramingMode
import com.example.model.RenderStage
import com.example.model.TargetAspectRatio
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonBadge
import com.example.ui.components.SectionHeader
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.EditorViewModel

@UnstableApi
@Composable
fun ExportScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val videoFile by viewModel.videoFile.collectAsState()
    val inspection by viewModel.inspection.collectAsState()
    val styleProfile by viewModel.styleProfile.collectAsState()
    val keyframes by viewModel.keyframes.collectAsState()
    val renderProgress by viewModel.renderProgress.collectAsState()

    val isRendering = renderProgress.stage != RenderStage.IDLE &&
            renderProgress.stage != RenderStage.COMPLETED &&
            renderProgress.stage != RenderStage.FAILED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Final MP4 Render & Export",
            subtitle = "Deterministic H.264/AAC with black-frame validation",
            icon = Icons.Default.VideoSettings
        )

        // If export is completed, show Render Complete screen!
        if (renderProgress.stage == RenderStage.COMPLETED && renderProgress.exportResult != null) {
            RenderCompleteCard(
                exportResult = renderProgress.exportResult!!,
                onShare = {
                    val shareIntent = MediaStoreExporter.createShareIntent(context, renderProgress.exportResult!!)
                    context.startActivity(Intent.createChooser(shareIntent, "Share Edited Video"))
                },
                onRenderAgain = {
                    viewModel.startRender()
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Render Configuration Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Export Parameters",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Aspect Ratio Selector
                Text("Target Composition Aspect Ratio", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TargetAspectRatio.entries.forEach { aspect ->
                        val isSelected = styleProfile.targetAspectRatio == aspect
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !isRendering) {
                                    viewModel.updateTargetAspectRatio(aspect)
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) NeonPurple.copy(alpha = 0.25f) else CardSurfaceElevated,
                            border = BorderStroke(1.dp, if (isSelected) NeonPurple else BorderSubtle)
                        ) {
                            Text(
                                text = aspect.displayName.take(8),
                                color = if (isSelected) NeonPurple else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Framing & Background Blur Mode
                Text("Framing & Background Behavior", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FramingMode.entries.forEach { mode ->
                        val isSelected = styleProfile.framingBehavior == mode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !isRendering) {
                                    viewModel.updateFramingMode(mode)
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else CardSurfaceElevated,
                            border = BorderStroke(1.dp, if (isSelected) NeonCyan else BorderSubtle)
                        ) {
                            Text(
                                text = mode.displayName.take(10),
                                color = if (isSelected) NeonCyan else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Summary of Keyframes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Active Keyframes", color = TextSecondary, fontSize = 11.sp)
                        Text("${keyframes.size} zoom transitions", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Encoding Spec", color = TextSecondary, fontSize = 11.sp)
                        Text("H.264 • YUV420P • AAC", color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Render Progress Card
        if (isRendering) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = NeonCyan.copy(alpha = 0.8f)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = renderProgress.stage.displayName,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${(renderProgress.progressFraction * 100).toInt()}%",
                            color = GoldAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { renderProgress.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = NeonCyan,
                        trackColor = CardSurfaceElevated
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = renderProgress.detailMessage,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Render Error Banner
        if (renderProgress.stage == RenderStage.FAILED && renderProgress.error != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = CardSurfaceElevated,
                border = BorderStroke(1.dp, Color(0xFFFF5252))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = renderProgress.error ?: "Render error", color = TextPrimary, fontSize = 12.sp)
                }
            }
        }

        // Main Render Action Button
        Button(
            onClick = { viewModel.startRender() },
            enabled = videoFile != null && !isRendering,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("render_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonPurple,
                disabledContainerColor = CardSurfaceElevated
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isRendering) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DarkBackground, strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Rendering Video...", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else {
                Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = DarkBackground)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Render Final Edited MP4", color = DarkBackground, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

/**
 * Render Complete Screen:
 * Displays player previewing the actual persistent content:// URI with Media3 ExoPlayer,
 * validated metadata, download confirmation, and share button.
 */
@UnstableApi
@Composable
fun RenderCompleteCard(
    exportResult: ExportResult,
    onShare: () -> Unit,
    onRenderAgain: () -> Unit
) {
    val context = LocalContext.current

    // Local ExoPlayer dedicated to previewing the exported content URI
    val exportedPlayer = remember(exportResult.contentUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(exportResult.contentUri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(exportResult.contentUri) {
        onDispose {
            exportedPlayer.release()
        }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = EmeraldGreen.copy(alpha = 0.8f)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Render Complete & Validated",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }
                NeonBadge(text = "H.264 MP4", color = EmeraldGreen)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dedicated Preview of Exported Video
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkBackground)
                    .border(1.dp, BorderHighlight, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exportedPlayer
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Export Details & Validation Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Duration", color = TextSecondary, fontSize = 11.sp)
                    Text(exportResult.durationFormatted, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("File Size", color = TextSecondary, fontSize = 11.sp)
                    Text(exportResult.fileSizeFormatted, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Resolution", color = TextSecondary, fontSize = 11.sp)
                    Text(exportResult.resolution, color = NeonCyan, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("No Black Video", color = TextSecondary, fontSize = 11.sp)
                    Text("Verified", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            }

            if (exportResult.isSafeFallbackUsed) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GoldAccent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Advanced render completed using safe fallback stream pass.",
                        color = GoldAccent,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // MediaStore path badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Saved to Movies/CutsZoom AI (MediaStore)",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Share & Download Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_video_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share MP4", color = DarkBackground, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRenderAgain,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("render_again_button"),
                    border = BorderStroke(1.dp, BorderHighlight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-Render", color = TextPrimary)
                }
            }
        }
    }
}
