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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
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
import androidx.compose.runtime.DisposableEffect
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


import net.jami.ui.components.video.GridLayoutMode
import net.jami.ui.components.video.ParticipantGrid
import net.jami.ui.components.video.DraggablePreview
import net.jami.ui.components.video.VideoParticipant

import net.jami.ui.composables.VideoRenderer
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

    DisposableEffect(Unit) {
        onDispose { viewModel.onCleared() }
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
        onSwitchCamera = { viewModel.switchCamera() },
        onToggleLocalPreview = { viewModel.toggleLocalPreviewVisibility() },
        onToggleScreenShare = { viewModel.toggleScreenShare() },
        onMuteAll = { viewModel.muteAllParticipants() },
        onToggleConferenceLock = { locked -> viewModel.toggleConferenceLock(locked) },
        onToggleStats = { viewModel.toggleVideoStats() },
        onSendDtmf = { key -> viewModel.sendDtmf(key) },
        onEnded = onEnd,
        onRetryVideo = { viewModel.retryRemoteVideo() },
        onFallbackAudio = { viewModel.fallbackToAudioOnly() },
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

    DisposableEffect(Unit) {
        onDispose { viewModel.onCleared() }
    }

    CallScreenContent(
        state = state,
        onAccept = { viewModel.acceptCurrent(withVideo = state.isVideo) },
        onDecline = { viewModel.refuseCurrent() },
        onEnd = {
            viewModel.endCall()
            onEnd()
        },
        onToggleMute = { viewModel.toggleMute() },
        onToggleSpeaker = { viewModel.toggleSpeaker() },
        onToggleVideo = { viewModel.toggleVideo() },
        onToggleHold = { viewModel.toggleHold() },
        onSwitchCamera = { viewModel.switchCamera() },
        onToggleLocalPreview = { viewModel.toggleLocalPreviewVisibility() },
        onToggleScreenShare = { viewModel.toggleScreenShare() },
        onMuteAll = { viewModel.muteAllParticipants() },
        onToggleConferenceLock = { locked -> viewModel.toggleConferenceLock(locked) },
        onToggleStats = { viewModel.toggleVideoStats() },
        onSendDtmf = { key -> viewModel.sendDtmf(key) },
        onEnded = onEnd,
        onRetryVideo = { viewModel.retryRemoteVideo() },
        onFallbackAudio = { viewModel.fallbackToAudioOnly() },
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
    onSwitchCamera: () -> Unit,
    onToggleLocalPreview: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onMuteAll: () -> Unit = {},
    onToggleConferenceLock: (Boolean) -> Unit = {},
    onToggleStats: () -> Unit = {},
    onSendDtmf: (Char) -> Unit,
    onEnded: () -> Unit,
    onRetryVideo: () -> Unit,
    onFallbackAudio: () -> Unit,
) {
    // Navigate back on terminal state
    LaunchedEffect(state.callMode) {
        if (state.callMode is CallMode.Ended) {
            onEnded()
        }
    }

    // Wire call state into PiP manager
    DisposableEffect(Unit) {
        try {
            val pipManager = org.koin.core.context.GlobalContext.get().get<net.jami.services.PictureInPictureManager>()
            pipManager.attachCallState(state)
            onDispose {
                pipManager.detachCallState()
            }
        } catch (e: Exception) {
            // PiP manager not available on this platform
            onDispose { }
        }
    }

    var showDtmfSheet by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    // Auto-hide controls after 3 seconds during ongoing video call
    LaunchedEffect(showControls, state.hasRemoteVideo) {
        if (showControls && state.hasRemoteVideo && state.callMode is CallMode.OnGoing) {
            kotlinx.coroutines.delay(5000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (state.hasRemoteVideo && !state.videoLoss.isVideoLost) {
                    showControls = !showControls
                }
            },
    ) {
        // Video rendering
        // Remote video or conference grid
        if (state.hasRemoteVideo && !state.videoLoss.isFallbackToAudioOnly) {
            if (state.isConference && state.participants.isNotEmpty()) {
                ConferenceVideoLayout(
                    participants = state.participants,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (state.remoteVideoSinkId.isNotEmpty()) {
                VideoRenderer(
                    modifier = Modifier.fillMaxSize(),
                    callId = state.callId,
                    isLocalVideo = false
                )
            }
        }

        // Local video preview (draggable)
        if (state.hasLocalVideo && !state.isVideoMuted && state.callMode is CallMode.OnGoing && !state.videoLoss.isFallbackToAudioOnly) {
            DraggablePreview(
                modifier = Modifier.fillMaxSize(),
                isVisible = state.isLocalPreviewVisible,
                onVisibilityToggle = onToggleLocalPreview
            ) {
                VideoRenderer(
                    modifier = Modifier.fillMaxSize(),
                    callId = state.callId,
                    isLocalVideo = true
                )
            }
        }

        // Audio-only call background if no video is active or in fallback mode
        if ((!state.hasRemoteVideo || state.videoLoss.isFallbackToAudioOnly) &&
            !(state.hasLocalVideo && !state.isVideoMuted && state.callMode is CallMode.OnGoing)) {
            AudioCallBackground(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Hold overlay (semi-transparent dimmer)
        if (state.callMode is CallMode.OnHold) {
            HoldOverlay()
        }

        // Video loss overlay (network resilience)
        VideoLossOverlay(
            videoLoss = state.videoLoss,
            onRetry = onRetryVideo,
            onAudioOnly = onFallbackAudio,
            modifier = Modifier.fillMaxSize()
        )

        // Video stats overlay
        VideoStatsOverlay(
            videoQuality = state.videoQuality,
            modifier = Modifier.fillMaxSize()
        )

        // Controls overlay with animation
        AnimatedVisibility(
            visible = showControls || state.callMode !is CallMode.OnGoing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top info bar (only for audio calls or when controls visible)
                if (!state.hasRemoteVideo || showControls) {
                    CallInfoOverlay(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = JamiTheme.spacing.xl)
                    )
                }

                // Bottom controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = JamiTheme.spacing.xxxl),
                ) {
                    when (state.callMode) {
                        is CallMode.Incoming -> IncomingControls(
                            onAccept = onAccept,
                            onDecline = onDecline
                        )
                        is CallMode.Outgoing -> OutgoingControls(onHangup = onEnd)
                        is CallMode.OnGoing, is CallMode.OnHold -> OnGoingControls(
                            state = state,
                            onEnd = onEnd,
                            onToggleMute = onToggleMute,
                            onToggleSpeaker = onToggleSpeaker,
                            onToggleVideo = onToggleVideo,
                            onToggleHold = onToggleHold,
                            onSwitchCamera = onSwitchCamera,
                            onToggleScreenShare = onToggleScreenShare,
                            onMuteAll = onMuteAll,
                            onToggleConferenceLock = onToggleConferenceLock,
                            onToggleStats = onToggleStats,
                            onOpenDtmf = { showDtmfSheet = true },
                        )
                        is CallMode.Ended -> Unit
                    }
                }
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
            DtmfDialpad(onKey = onSendDtmf)
            Spacer(Modifier.height(JamiTheme.spacing.xl))
        }
    }
}

// ==================== Video layouts ====================

@Composable
private fun ConferenceVideoLayout(
    participants: List<ParticipantUi>,
    modifier: Modifier = Modifier
) {
    val videoParticipants = participants.map { p ->
        VideoParticipant(
            id = p.callId,
            sinkId = p.sinkId,
            displayName = p.displayName,
            isMuted = p.isAudioMuted,
            isVideoEnabled = !p.isVideoMuted,
            isActiveSpeaker = p.isActive,
            isLocal = p.isLocal
        )
    }

    ParticipantGrid(
        participants = videoParticipants,
        modifier = modifier,
        layoutMode = GridLayoutMode.AUTO,
        showNames = true,
        showStatusIcons = true
    )
}

@Composable
private fun AudioCallBackground(
    state: CallState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(JamiColors.DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .semantics { contentDescription = "Caller avatar" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.l))

            // Peer name
            Text(
                text = state.peerName.ifEmpty { state.peerUri },
                style = JamiTheme.typography.headlineMedium,
                color = Color.White,
            )

            Spacer(Modifier.height(JamiTheme.spacing.s))

            // Status
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

            // Duration
            if (state.duration > 0 && state.callMode is CallMode.OnGoing) {
                Spacer(Modifier.height(JamiTheme.spacing.xs))
                Text(
                    text = formatDuration(state.duration),
                    style = JamiTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CallInfoOverlay(
    state: CallState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(JamiTheme.spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = state.peerName.ifEmpty { state.peerUri },
            style = JamiTheme.typography.titleMedium,
            color = Color.White,
        )

        if (state.duration > 0 && state.callMode is CallMode.OnGoing) {
            Spacer(Modifier.height(JamiTheme.spacing.xs))
            Text(
                text = formatDuration(state.duration),
                style = JamiTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
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
    onSwitchCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onMuteAll: () -> Unit = {},
    onToggleConferenceLock: (Boolean) -> Unit = {},
    onToggleStats: () -> Unit = {},
    onOpenDtmf: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // First row: mute, speaker, video, screen share / camera switch
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

            // Screen share button
            CallControlButton(
                icon = if (state.isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                contentDescription = if (state.isScreenSharing) "Stop screen share" else "Start screen share",
                isActive = state.isScreenSharing,
                onClick = onToggleScreenShare,
            )

            // DTMF dialpad button
            CallControlButton(
                icon = Icons.Default.Dialpad,
                contentDescription = stringResource(Res.string.content_desc_dialpad),
                isActive = false,
                onClick = onOpenDtmf,
            )

            // Video stats button
            CallControlButton(
                icon = Icons.Default.Info,
                contentDescription = "Video stats",
                isActive = state.videoQuality.isShowingStats,
                onClick = onToggleStats,
            )
        }

        // Second row: camera switch (if video active), hold
        if (state.hasLocalVideo && !state.isVideoMuted && !state.isScreenSharing) {
            Spacer(Modifier.height(JamiTheme.spacing.m))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CallControlButton(
                    icon = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    isActive = false,
                    onClick = onSwitchCamera,
                )

                CallControlButton(
                    icon = if (state.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = stringResource(
                        if (state.isOnHold) Res.string.content_desc_resume else Res.string.content_desc_hold
                    ),
                    isActive = state.isOnHold,
                    onClick = onToggleHold,
                )
            }
        } else {
            // Show hold button in a separate row when no video
            Spacer(Modifier.height(JamiTheme.spacing.m))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CallControlButton(
                    icon = if (state.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = stringResource(
                        if (state.isOnHold) Res.string.content_desc_resume else Res.string.content_desc_hold
                    ),
                    isActive = state.isOnHold,
                    onClick = onToggleHold,
                )
            }
        }

        // Moderator controls (visible only in conferences when user is moderator)
        if (state.isConference && state.isModerator && state.participants.size > 1) {
            Spacer(Modifier.height(JamiTheme.spacing.m))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Mute all button
                CallControlButton(
                    icon = Icons.Default.MicOff,
                    contentDescription = "Mute all participants",
                    isActive = false,
                    onClick = onMuteAll,
                )

                // Lock conference button
                CallControlButton(
                    icon = if (state.isConferenceLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (state.isConferenceLocked) "Unlock conference" else "Lock conference",
                    isActive = state.isConferenceLocked,
                    onClick = {
                        onToggleConferenceLock(!state.isConferenceLocked)
                    },
                )
            }
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
