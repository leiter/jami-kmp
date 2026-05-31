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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.model.VideoLossState
import net.jami.ui.theme.JamiColors
import net.jami.ui.theme.JamiTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Overlay shown when remote video is lost due to network issues.
 *
 * Shows:
 * - Loss status message
 * - Retry counter and progress
 * - Option to retry or fallback to audio-only
 */
@Composable
fun VideoLossOverlay(
    videoLoss: VideoLossState,
    onRetry: () -> Unit = {},
    onAudioOnly: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = videoLoss.isVideoLost || videoLoss.isFallbackToAudioOnly,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(JamiTheme.spacing.xxl)
                    .background(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(JamiTheme.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = JamiColors.Red500
                )

                Spacer(Modifier.height(JamiTheme.spacing.m))

                // Main message
                Text(
                    text = videoLoss.displayMessage,
                    style = JamiTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(JamiTheme.spacing.m))

                // Loss duration (if applicable)
                if (videoLoss.lossDurationSeconds > 0) {
                    Text(
                        text = "No video for ${videoLoss.lossDurationSeconds}s",
                        style = JamiTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.m))
                }

                // Progress indicator
                if (videoLoss.isRetrying && !videoLoss.isFallbackToAudioOnly) {
                    CircularProgressIndicator(
                        progress = { videoLoss.retryPercentage / 100f },
                        modifier = Modifier.size(48.dp),
                        color = JamiColors.Cyan500,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.m))

                    // Retry attempt counter
                    Text(
                        text = "Attempt ${videoLoss.retryAttempt + 1}/${videoLoss.maxRetries}",
                        style = JamiTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.l))
                }

                // Fallback to audio only
                if (videoLoss.isFallbackToAudioOnly) {
                    Text(
                        text = "Video is unavailable on this connection",
                        style = JamiTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.l))

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JamiColors.Cyan500,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(width = 160.dp, height = 44.dp)
                    ) {
                        Text("Try Again")
                    }
                } else if (videoLoss.isRetrying && !videoLoss.canRetry) {
                    // Max retries reached - offer audio only fallback
                    Button(
                        onClick = onAudioOnly,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JamiColors.Orange500,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(width = 160.dp, height = 44.dp)
                    ) {
                        Text("Audio Only")
                    }
                }
            }
        }
    }
}
