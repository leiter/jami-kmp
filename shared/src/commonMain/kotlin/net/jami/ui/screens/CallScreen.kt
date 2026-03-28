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

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.theme.JamiColors
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.CallViewModel

/**
 * Full-screen call screen with peer info and call controls.
 *
 * Layout:
 * - Dark background
 * - Centered: peer name, call status, duration timer
 * - Bottom: control row (mute, speaker, video, end call)
 *
 * @param contactId The contact being called.
 * @param isVideo Whether this is a video call.
 * @param onEnd Called when the call ends (to navigate back).
 */
@Composable
fun CallScreen(
    contactId: String,
    isVideo: Boolean,
    onEnd: () -> Unit,
) {
    val viewModel = getViewModel<CallViewModel>()
    val state by viewModel.state.collectAsState()

    // Initiate the call on first composition
    LaunchedEffect(contactId, isVideo) {
        viewModel.initCall(contactId, isVideo)
    }

    // Navigate back when call is ended
    LaunchedEffect(state.callStatus) {
        if (state.callStatus == "OVER" || state.callStatus == "FAILURE") {
            onEnd()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JamiColors.DarkBackground),
    ) {
        // Center content: peer name, status, duration
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Peer name
            Text(
                text = state.peerName.ifEmpty { contactId },
                style = JamiTheme.typography.headlineMedium,
                color = Color.White,
            )

            Spacer(Modifier.height(JamiTheme.spacing.s))

            // Conference indicator
            if (state.isConference) {
                Text(
                    text = "Conference (${state.participantCount} participants)",
                    style = JamiTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(JamiTheme.spacing.xs))
            }

            // Call status
            Text(
                text = if (state.isOnHold) stringResource(Res.string.call_on_hold) else state.callStatus,
                style = JamiTheme.typography.bodyLarge,
                color = if (state.isOnHold) Color.Yellow.copy(alpha = 0.9f)
                    else Color.White.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // Duration timer
            if (state.duration > 0) {
                Text(
                    text = formatDuration(state.duration),
                    style = JamiTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
        }

        // Bottom controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(
                    horizontal = JamiTheme.spacing.xl,
                    vertical = JamiTheme.spacing.xxxl,
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mute button
            CallControlButton(
                icon = if (state.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (state.isAudioMuted) stringResource(Res.string.content_desc_unmute) else stringResource(Res.string.content_desc_mute),
                isActive = state.isAudioMuted,
                onClick = { viewModel.toggleMute() },
            )

            // Speaker button
            CallControlButton(
                icon = if (state.isSpeakerOn) Icons.Default.VolumeUp
                else Icons.Default.VolumeOff,
                contentDescription = if (state.isSpeakerOn) stringResource(Res.string.content_desc_speaker_off) else stringResource(Res.string.content_desc_speaker_on),
                isActive = state.isSpeakerOn,
                onClick = { viewModel.toggleSpeaker() },
            )

            // Video button
            CallControlButton(
                icon = if (state.isVideoMuted) Icons.Default.VideocamOff
                else Icons.Default.Videocam,
                contentDescription = if (state.isVideoMuted) stringResource(Res.string.content_desc_enable_video) else stringResource(Res.string.content_desc_disable_video),
                isActive = !state.isVideoMuted,
                onClick = { viewModel.toggleVideo() },
            )

            // Hold button
            CallControlButton(
                icon = if (state.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (state.isOnHold) stringResource(Res.string.content_desc_resume) else stringResource(Res.string.content_desc_hold),
                isActive = state.isOnHold,
                onClick = { viewModel.toggleHold() },
            )

            // End call button (red)
            IconButton(
                onClick = {
                    viewModel.endCall()
                    onEnd()
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(JamiColors.Red500),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = stringResource(Res.string.content_desc_end_call),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * Circular call control button with active/inactive state.
 */
@Composable
private fun CallControlButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isActive) Color.White.copy(alpha = 0.3f)
    else Color.White.copy(alpha = 0.1f)

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = Color.White,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * Format call duration in seconds to MM:SS or HH:MM:SS format.
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:" +
            "${minutes.toString().padStart(2, '0')}:" +
            secs.toString().padStart(2, '0')
    } else {
        "${minutes.toString().padStart(2, '0')}:" +
            secs.toString().padStart(2, '0')
    }
}
