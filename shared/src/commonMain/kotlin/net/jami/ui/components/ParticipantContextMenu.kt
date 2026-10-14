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
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.CenterFocusStrong
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
 *
 * The audio/video callbacks are passed the participant's call ID for backwards
 * compatibility; the moderator, hand and participant-level callbacks receive the whole
 * [ParticipantUi] because the daemon addresses those by (URI, device) pair.
 *
 * [showVideoControls] is off by default: libjami has no client-facing per-participant
 * video mute, so those entries would be dead buttons (see DaemonBridgeApi.muteStream).
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
    showVideoControls: Boolean = false,
    onSetModerator: (ParticipantUi, Boolean) -> Unit = { _, _ -> },
    onLowerHand: (ParticipantUi) -> Unit = {},
    onSetActiveStream: (ParticipantUi, Boolean) -> Unit = { _, _ -> },
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
                    stringResource(
                        if (participant.isAudioMuted) Res.string.action_call_unmute
                        else Res.string.action_call_mute
                    )
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

        // Video toggle — only when the platform can actually honour it
        if (showVideoControls) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (participant.isVideoMuted) Res.string.call_participant_enable_video
                            else Res.string.call_participant_disable_video
                        )
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
        }

        // Spotlight: focus this participant's stream in the conference mixer. Uses the modern
        // per-stream API rather than the deprecated call-ID based setActiveParticipant.
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (participant.isActive) Res.string.call_participant_unfocus
                        else Res.string.call_participant_focus
                    )
                )
            },
            leadingIcon = {
                Icon(imageVector = Icons.Default.CenterFocusStrong, contentDescription = null)
            },
            onClick = {
                onSetActiveStream(participant, !participant.isActive)
                onDismiss()
            }
        )

        // Moderator promotion / demotion
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (participant.isModerator) Res.string.call_participant_revoke_moderator
                        else Res.string.call_participant_make_moderator
                    )
                )
            },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Shield, contentDescription = null)
            },
            onClick = {
                onSetModerator(participant, !participant.isModerator)
                onDismiss()
            }
        )

        // Lower a raised hand
        if (participant.isHandRaised) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.call_lower_hand)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.BackHand, contentDescription = null)
                },
                onClick = {
                    onLowerHand(participant)
                    onDismiss()
                }
            )
        }

        // Remove participant
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.call_participant_remove)) },
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
