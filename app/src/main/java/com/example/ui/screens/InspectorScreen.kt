package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.model.MediaInspection
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonBadge
import com.example.ui.components.SectionHeader
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
fun InspectorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val inspection by viewModel.inspection.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Stream & Metadata Inspector",
            subtitle = "PTS / DTS timeline & container stream inspection",
            icon = Icons.Default.Dashboard
        )

        if (inspection == null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "No media loaded",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Import a video from the Project tab to view full technical metadata and stream analysis.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
            return
        }

        val info = inspection!!

        // Stream Validation Status
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = EmeraldGreen.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Stream Status", color = TextSecondary, fontSize = 11.sp)
                    Text(info.validationStatus, color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                NeonBadge(text = "H.264 / AAC Safe", color = EmeraldGreen)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Frame Rate & Timeline Safety Section
        SectionHeader(
            title = "Frame Rate & Timeline Safety",
            subtitle = "Inspected from presentation timestamps (PTS)",
            icon = Icons.Default.AvTimer
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                InspectorRow("Detected FPS", "${info.detectedFps} FPS")
                InspectorRow("r_frame_rate", info.rFrameRate)
                InspectorRow("avg_frame_rate", info.avgFrameRate)
                InspectorRow("time_base", info.timeBase)
                InspectorRow("Timeline Rate Mode", if (info.isVfr) "Variable Frame Rate (VFR)" else "Constant Frame Rate (CFR)")
                InspectorRow("High-FPS Video (>45)", if (info.isHighFps) "Yes (Safe Down-stepped Timeline)" else "Standard Speed")
                InspectorRow("Estimated Total Frames", "${info.estimatedFrameCount} frames")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Video Stream Section
        SectionHeader(
            title = "Video Stream (Track 0)",
            subtitle = "Geometry and encoding parameters",
            icon = Icons.Default.VideoFile
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                InspectorRow("Raw Dimensions", "${info.width} x ${info.height}")
                InspectorRow("Display Dimensions", "${info.displayWidth} x ${info.displayHeight} (${info.aspectRatio})")
                InspectorRow("Orientation", if (info.isPortrait) "Portrait (9:16)" else "Landscape (16:9)")
                InspectorRow("Hardware Rotation", "${info.rotation}°")
                InspectorRow("Video Codec", info.videoCodec)
                InspectorRow("MIME Type", info.videoMimeType)
                InspectorRow("Video Bitrate", if (info.videoBitrate > 0) "${info.videoBitrate / 1000} kbps" else "Adaptive VBR")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Audio Stream Section
        SectionHeader(
            title = "Audio Stream (Track 1)",
            subtitle = "Speech and sound track",
            icon = Icons.Default.Audiotrack
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                InspectorRow("Audio Present", if (info.hasAudio) "Yes" else "None")
                InspectorRow("Audio Codec", info.audioCodec)
                InspectorRow("Audio MIME", info.audioMimeType)
                InspectorRow("Channels", "${info.audioChannels} (${if (info.audioChannels == 2) "Stereo" else "Mono"})")
                InspectorRow("Sampling Rate", "${info.audioSampleRate} Hz")
                InspectorRow("Audio Bitrate", if (info.audioBitrate > 0) "${info.audioBitrate / 1000} kbps" else "128 kbps AAC")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Container & File Properties
        SectionHeader(
            title = "Container & File Properties",
            subtitle = "Physical file characteristics",
            icon = Icons.Default.Memory
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                InspectorRow("File Name", info.fileName)
                InspectorRow("Duration", "${info.durationFormatted} (${info.durationMs} ms)")
                InspectorRow("File Size", info.fileSizeFormatted)
                InspectorRow("Faststart Compatibility", "Supported (Moov atom front)")
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}
