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
package net.jami.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.ui.viewmodel.ParticipantUi
import org.jetbrains.compose.resources.stringResource

/**
 * Context menu for moderator actions on a participant.
 *
 * Shows options like mute, kick, etc. (only for moderators).
 */
@Composable
fun ParticipantContextMenu(
    expanded: Boolean,
    participant: ParticipantUi,
    isModerator: Boolean,
    onDismiss: () -> Unit,
    onMuteAudio: (String) -> Unit = {},
    onUnmuteAudio: (String) -> Unit = {},
    onDisableVideo: (String) -> Unit = {},
    onEnableVideo: (String) -> Unit = {},
    onRemove: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded && isModerator,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        // Audio mute toggle
        DropdownMenuItem(
            text = {
                Text(
                    if (participant.isAudioMuted) "Unmute Audio" else "Mute Audio"
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (participant.isAudioMuted)
                        Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null
                )
            },
            onClick = {
                if (participant.isAudioMuted) {
                    onUnmuteAudio(participant.callId)
                } else {
                    onMuteAudio(participant.callId)
                }
                onDismiss()
            }
        )

        // Video toggle
        DropdownMenuItem(
            text = {
                Text(
                    if (participant.isVideoMuted) "Enable Video" else "Disable Video"
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (participant.isVideoMuted)
                        Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = null
                )
            },
            onClick = {
                if (participant.isVideoMuted) {
                    onEnableVideo(participant.callId)
                } else {
                    onDisableVideo(participant.callId)
                }
                onDismiss()
            }
        )

        // Remove participant
        DropdownMenuItem(
            text = { Text("Remove from Call") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PersonRemove,
                    contentDescription = null
                )
            },
            onClick = {
                onRemove(participant.callId)
                onDismiss()
            }
        )
    }
}
