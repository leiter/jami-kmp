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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.di.getViewModel
import net.jami.ui.theme.JamiColors
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.CallMode
import net.jami.ui.viewmodel.CallState
import net.jami.ui.viewmodel.CallViewModel
import net.jami.ui.viewmodel.ParticipantUi
import org.jetbrains.compose.resources.stringResource

// ==================== Public entry points ====================

/**
 * Outgoing call screen: places a new call to [contactId].
 */
@Composable
fun CallScreen(
    contactId: String,
    isVideo: Boolean,
    onEnd: () -> Unit,
) {
    val viewModel = getViewModel<CallViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(contactId, isVideo) {
        viewModel.initOutgoing(contactUri = contactId, hasVideo = isVideo)
    }

    CallScreenContent(
        state = state,
        onAccept = { viewModel.acceptCurrent(withVideo = isVideo) },
        onDecline = { viewModel.refuseCurrent() },
        onEnd = {
            viewModel.endCall()
            onEnd()
        },
        onToggleMute = { viewModel.toggleMute() },
        onToggleSpeaker = { viewModel.toggleSpeaker() },
        onToggleVideo = { viewModel.toggleVideo() },
        onToggleHold = { viewModel.toggleHold() },
        onSendDtmf = { key -> viewModel.sendDtmf(key) },
        onEnded = onEnd,
    )
}

/**
 * Attach to an existing in-progress call by daemon [callId] (from notification tap).
 */
@Composable
fun IncomingCallScreen(
    callId: String,
    onEnd: () -> Unit,
) {
    val viewModel = getViewModel<CallViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(callId) {
        viewModel.initIncoming(callId, actionViewOnly = false)
    }

    CallScreenContent(
        state = state,
        onAccept = { viewModel.acceptCurrent(withVideo = false) },
        onDecline = { viewModel.refuseCurrent() },
        onEnd = {
            viewModel.endCall()
            onEnd()
        },
        onToggleMute = { viewModel.toggleMute() },
        onToggleSpeaker = { viewModel.toggleSpeaker() },
        onToggleVideo = { viewModel.toggleVideo() },
        onToggleHold = { viewModel.toggleHold() },
        onSendDtmf = { key -> viewModel.sendDtmf(key) },
        onEnded = onEnd,
    )
}

// ==================== Core state-driven composable ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallScreenContent(
    state: CallState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleHold: () -> Unit,
    onSendDtmf: (Char) -> Unit,
    onEnded: () -> Unit,
) {
    // Navigate back on terminal state
    LaunchedEffect(state.callMode) {
        if (state.callMode is CallMode.Ended) {
            onEnded()
        }
    }

    var showDtmfSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JamiColors.DarkBackground),
    ) {
        // Hold overlay (semi-transparent dimmer above everything else)
        if (state.callMode is CallMode.OnHold) {
            HoldOverlay()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = JamiTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .semantics { contentDescription = "Caller avatar" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // Peer name
            Text(
                text = state.peerName.ifEmpty { state.peerUri },
                style = JamiTheme.typography.headlineMedium,
                color = Color.White,
            )

            Spacer(Modifier.height(JamiTheme.spacing.s))

            // Status line
            val statusText = when (state.callMode) {
                is CallMode.Incoming -> stringResource(Res.string.call_incoming_audio)
                is CallMode.Outgoing -> stringResource(Res.string.call_outgoing)
                is CallMode.OnHold -> stringResource(Res.string.call_on_hold)
                is CallMode.OnGoing -> if (state.isConference)
                    "${state.participantCount} participants"
                else stringResource(Res.string.call_human_state_current)
                is CallMode.Ended -> stringResource(Res.string.call_human_state_over)
            }
            Text(
                text = statusText,
                style = JamiTheme.typography.bodyLarge,
                color = if (state.callMode is CallMode.OnHold) Color.Yellow.copy(alpha = 0.9f)
                    else Color.White.copy(alpha = 0.75f),
            )

            // Duration (only when ongoing)
            if (state.duration > 0 && state.callMode is CallMode.OnGoing) {
                Spacer(Modifier.height(JamiTheme.spacing.xs))
                Text(
                    text = formatDuration(state.duration),
                    style = JamiTheme.typography.titleLarge,
                    color = Color.White,
                )
            }

            // Participant list (conference)
            if (state.isConference && state.participants.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.l))
                ParticipantList(state.participants)
            }
        }

        // Bottom controls — different layouts per mode
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = JamiTheme.spacing.xxxl),
        ) {
            when (state.callMode) {
                is CallMode.Incoming -> IncomingControls(onAccept = onAccept, onDecline = onDecline)
                is CallMode.Outgoing -> OutgoingControls(onHangup = onEnd)
                is CallMode.OnGoing, is CallMode.OnHold -> OnGoingControls(
                    state = state,
                    onEnd = onEnd,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onToggleVideo = onToggleVideo,
                    onToggleHold = onToggleHold,
                    onOpenDtmf = { showDtmfSheet = true },
                )
                is CallMode.Ended -> Unit
            }
        }
    }

    // DTMF dialpad bottom sheet
    if (showDtmfSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showDtmfSheet = false },
            sheetState = sheetState,
        ) {
            DtmfDialpad(
                onKey = { key ->
                    onSendDtmf(key)
                },
            )
            Spacer(Modifier.height(JamiTheme.spacing.xl))
        }
    }
}

// ==================== Control sub-composables ====================

@Composable
private fun IncomingControls(onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decline (red)
        val declineDesc = stringResource(Res.string.content_desc_decline_call)
        IconButton(
            onClick = onDecline,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(JamiColors.Red500)
                .semantics { contentDescription = declineDesc },
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Default.PhoneDisabled, contentDescription = null, modifier = Modifier.size(36.dp))
        }

        // Answer (green)
        val answerDesc = stringResource(Res.string.content_desc_answer_call)
        IconButton(
            onClick = onAccept,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(JamiColors.Green500)
                .semantics { contentDescription = answerDesc },
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun OutgoingControls(onHangup: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        val desc = stringResource(Res.string.content_desc_end_call)
        IconButton(
            onClick = onHangup,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(JamiColors.Red500)
                .semantics { contentDescription = desc },
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun OnGoingControls(
    state: CallState,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleHold: () -> Unit,
    onOpenDtmf: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // First row: mute, speaker, video, hold
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallControlButton(
                icon = if (state.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = stringResource(
                    if (state.isAudioMuted) Res.string.content_desc_unmute else Res.string.content_desc_mute
                ),
                isActive = state.isAudioMuted,
                onClick = onToggleMute,
            )

            CallControlButton(
                icon = if (state.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = stringResource(
                    if (state.isSpeakerOn) Res.string.content_desc_speaker_off else Res.string.content_desc_speaker_on
                ),
                isActive = state.isSpeakerOn,
                onClick = onToggleSpeaker,
            )

            CallControlButton(
                icon = if (state.isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                contentDescription = stringResource(
                    if (state.isVideoMuted) Res.string.content_desc_enable_video else Res.string.content_desc_disable_video
                ),
                isActive = !state.isVideoMuted,
                onClick = onToggleVideo,
            )

            CallControlButton(
                icon = if (state.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(
                    if (state.isOnHold) Res.string.content_desc_resume else Res.string.content_desc_hold
                ),
                isActive = state.isOnHold,
                onClick = onToggleHold,
            )

            // DTMF dialpad button
            CallControlButton(
                icon = Icons.Default.Dialpad,
                contentDescription = stringResource(Res.string.content_desc_dialpad),
                isActive = false,
                onClick = onOpenDtmf,
            )
        }

        Spacer(Modifier.height(JamiTheme.spacing.l))

        // End call button
        val endDesc = stringResource(Res.string.content_desc_end_call)
        IconButton(
            onClick = onEnd,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(JamiColors.Red500)
                .semantics { contentDescription = endDesc },
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun HoldOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.call_on_hold),
            style = JamiTheme.typography.headlineMedium,
            color = Color.Yellow.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun ParticipantList(participants: List<ParticipantUi>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.m),
    ) {
        participants.forEach { participant ->
            ParticipantRow(participant)
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        }
    }
}

@Composable
private fun ParticipantRow(participant: ParticipantUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = if (participant.isActive) Color.Yellow else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(JamiTheme.spacing.s))
        Text(
            text = participant.displayName,
            style = JamiTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (participant.isModerator) {
            Text(
                text = stringResource(Res.string.call_moderator),
                style = JamiTheme.typography.labelSmall,
                color = Color.Yellow.copy(alpha = 0.8f),
            )
            Spacer(Modifier.size(JamiTheme.spacing.xs))
        }
        if (participant.isAudioMuted) {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = stringResource(Res.string.call_muted),
                tint = JamiColors.Red500,
                modifier = Modifier.size(16.dp),
            )
        }
        if (participant.isHandRaised) {
            Text(
                text = stringResource(Res.string.call_handRaised),
                style = JamiTheme.typography.labelSmall,
                color = Color.Yellow,
            )
        }
    }
}

@Composable
private fun DtmfDialpad(onKey: (Char) -> Unit) {
    val keys = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.xxl, vertical = JamiTheme.spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.call_dtmf_dialpad),
            style = JamiTheme.typography.titleMedium,
            color = JamiTheme.colors.onSurface,
        )
        Spacer(Modifier.height(JamiTheme.spacing.m))
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    IconButton(
                        onClick = { onKey(key) },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(JamiTheme.colors.surfaceVariant),
                    ) {
                        Text(
                            text = key.toString(),
                            style = JamiTheme.typography.headlineSmall,
                            color = JamiTheme.colors.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(JamiTheme.spacing.s))
        }
    }
}

// ==================== Reusable components ====================

@Composable
private fun CallControlButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isActive) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(bg)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
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
        "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }
}
