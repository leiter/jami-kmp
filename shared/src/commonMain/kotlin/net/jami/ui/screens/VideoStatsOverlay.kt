/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.jami.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.jami.model.VideoQualityState
import net.jami.ui.theme.JamiTheme

/**
 * Overlay showing real-time video quality and network statistics.
 *
 * Displays:
 * - Current bitrate
 * - Resolution and framerate
 * - Packet loss percentage
 * - Network quality indicator
 * - Video quality setting
 */
@Composable
fun VideoStatsOverlay(
    videoQuality: VideoQualityState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = videoQuality.isShowingStats,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(JamiTheme.spacing.m),
            contentAlignment = Alignment.TopStart
        ) {
            // Semi-transparent background card
            Column(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(JamiTheme.spacing.m)
            ) {
                // Title
                Text(
                    text = "Video Stats",
                    style = JamiTheme.typography.labelLarge,
                    color = Color.White
                )

                Spacer(Modifier.height(JamiTheme.spacing.s))

                // Bitrate
                StatRow(label = "Bitrate:", value = videoQuality.displayBitrate)

                // Resolution
                StatRow(label = "Resolution:", value = videoQuality.displayResolution)

                // Frame rate
                StatRow(label = "FPS:", value = videoQuality.displayFrameRate)

                // Packet loss
                StatRow(
                    label = "Packet Loss:",
                    value = videoQuality.displayPacketLoss,
                    valueColor = when {
                        videoQuality.packetLoss > 0.10f -> Color(0xFFFF6B6B)  // Red
                        videoQuality.packetLoss > 0.05f -> Color(0xFFFFD93D)  // Yellow
                        else -> Color(0xFF6BCB77)  // Green
                    }
                )

                // Network quality
                StatRow(
                    label = "Network:",
                    value = videoQuality.qualityIndicator,
                    valueColor = when {
                        videoQuality.packetLoss > 0.10f -> Color(0xFFFF6B6B)
                        videoQuality.packetLoss > 0.05f -> Color(0xFFFFD93D)
                        else -> Color(0xFF6BCB77)
                    }
                )

                // Video quality setting
                StatRow(label = "Quality:", value = videoQuality.quality.label)

                // Auto-quality indicator
                if (videoQuality.isAutoQuality) {
                    Spacer(Modifier.height(JamiTheme.spacing.xs))
                    Text(
                        text = "Auto-quality: ON",
                        style = JamiTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = JamiTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.width(100.dp)
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        Text(
            text = value,
            style = JamiTheme.typography.labelSmall,
            color = valueColor
        )
    }
}
