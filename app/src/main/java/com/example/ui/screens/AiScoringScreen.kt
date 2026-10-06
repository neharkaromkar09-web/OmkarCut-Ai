package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.model.SpeechBoundary
import com.example.model.WordTimestamp
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonBadge
import com.example.ui.components.SectionHeader
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.AppTab
import com.example.viewmodel.EditorViewModel
import java.util.Locale

@UnstableApi
@Composable
fun AiScoringScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val transcript by viewModel.transcript.collectAsState()
    val aiResult by viewModel.aiResult.collectAsState()
    val boundaries by viewModel.boundaries.collectAsState()
    val keyframes by viewModel.keyframes.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "AI Scoring & Semantic Analysis",
                subtitle = "Speech boundaries, cadence rhythm, and keyframe reasons",
                icon = Icons.Default.Psychology
            )
        }

        // Executive AI Summary Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = NeonPurple.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(NeonPurple.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Gemini Semantic Rationale",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                        }
                        NeonBadge(text = aiResult.speechRhythmPace, color = NeonCyan)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = aiResult.summary.ifBlank { "Analyzing speech rhythm and semantic cadence to detect natural punch-out zoom anchors." },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Speech Boundaries", color = TextSecondary, fontSize = 11.sp)
                            Text("${boundaries.size} Natural Cuts", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Zoom Keyframes", color = TextSecondary, fontSize = 11.sp)
                            Text("${keyframes.size} Points", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Words Analyzed", color = TextSecondary, fontSize = 11.sp)
                            Text("${transcript.words.size} Words", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Section: Detected Boundaries
        item {
            SectionHeader(
                title = "Natural Speech Boundaries",
                subtitle = "Tap any boundary to inspect in the preview player",
                icon = Icons.Default.Speed
            )
        }

        if (boundaries.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No boundaries detected yet. Import a video or load the test sample from the Project tab.",
                        color = TextSecondary,
                        modifier = Modifier.padding(20.dp),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(boundaries) { boundary ->
                BoundaryItemCard(
                    boundary = boundary,
                    onJumpTo = {
                        viewModel.seekTo((boundary.time * 1000).toLong())
                        viewModel.selectTab(AppTab.EDIT_PLAY)
                    }
                )
            }
        }

        // Section: Word Timestamps
        item {
            Spacer(modifier = Modifier.height(6.dp))
            SectionHeader(
                title = "Speech-to-Text Word Timestamps",
                subtitle = "Word timing and speech pause duration",
                icon = Icons.Default.GraphicEq
            )
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (transcript.words.isEmpty()) {
                        Text(
                            text = "No transcript available.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        // Display words with timestamps
                        val wordsChunked = transcript.words.take(40)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            wordsChunked.forEach { w ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = w.word,
                                            fontWeight = if (w.isBoundaryCandidate) FontWeight.Bold else FontWeight.Normal,
                                            color = if (w.isBoundaryCandidate) NeonCyan else TextPrimary,
                                            fontSize = 13.sp
                                        )
                                        if (w.pauseAfterMs >= 200L) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = GoldAccent.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(0.5.dp, GoldAccent.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "${w.pauseAfterMs}ms pause",
                                                    color = GoldAccent,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%.2fs - %.2fs", w.start, w.end),
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun BoundaryItemCard(
    boundary: SpeechBoundary,
    onJumpTo: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onJumpTo() },
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderHighlight)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Jump",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f s (Frame %d)", boundary.time, boundary.frameIndex),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
                NeonBadge(
                    text = "${(boundary.confidence * 100).toInt()}% Conf",
                    color = if (boundary.confidence >= 0.90f) EmeraldGreen else GoldAccent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = boundary.reason,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { boundary.confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = NeonPurple,
                trackColor = CardSurfaceElevated
            )
        }
    }
}
