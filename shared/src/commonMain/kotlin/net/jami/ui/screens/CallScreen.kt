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
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.jami.ui.contracts.CallContract
import net.jami.ui.theme.JamiColors
import net.jami.ui.theme.JamiTheme

/**
 * Full-screen call screen with peer info and call controls.
 * Keeps raw M3 (no JamiScaffold/JamiTopBar) -- fullscreen dark overlay.
 *
 * @param peerState The peer info state (Tier 1 split).
 * @param controlsState The controls state (Tier 1 split).
 * @param timerState The timer state (Tier 1 split).
 * @param onAction Dispatches call actions.
 * @param onEnd Called when the call ends (to navigate back).
 */
@Composable
fun CallScreen(
    peerState: CallContract.PeerState,
    controlsState: CallContract.ControlsState,
    timerState: CallContract.TimerState,
    onAction: (CallContract.Action) -> Unit,
    onEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JamiColors.DarkBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = peerState.peerName.ifEmpty { peerState.peerUri },
                style = JamiTheme.typography.headlineMedium,
                color = Color.White,
            )

            Spacer(Modifier.height(JamiTheme.spacing.s))

            Text(
                text = peerState.callStatus,
                style = JamiTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            if (timerState.duration > 0) {
                Text(
                    text = formatDuration(timerState.duration),
                    style = JamiTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
        }

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
            CallControlButton(
                icon = if (controlsState.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (controlsState.isAudioMuted) "Unmute" else "Mute",
                isActive = controlsState.isAudioMuted,
                onClick = { onAction(CallContract.Action.ToggleMute) },
            )

            CallControlButton(
                icon = if (controlsState.isSpeakerOn) Icons.Default.VolumeUp
                else Icons.Default.VolumeOff,
                contentDescription = if (controlsState.isSpeakerOn) "Speaker off" else "Speaker on",
                isActive = controlsState.isSpeakerOn,
                onClick = { onAction(CallContract.Action.ToggleSpeaker) },
            )

            CallControlButton(
                icon = if (controlsState.isVideoMuted) Icons.Default.VideocamOff
                else Icons.Default.Videocam,
                contentDescription = if (controlsState.isVideoMuted) "Enable video" else "Disable video",
                isActive = !controlsState.isVideoMuted,
                onClick = { onAction(CallContract.Action.ToggleVideo) },
            )

            IconButton(
                onClick = {
                    onAction(CallContract.Action.EndCall)
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
                    contentDescription = "End call",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

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
