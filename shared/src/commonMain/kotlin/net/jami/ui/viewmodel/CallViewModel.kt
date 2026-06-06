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
package net.jami.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.jami.model.Call
import net.jami.model.Call.CallStatus
import net.jami.model.Call.HangupReason
import net.jami.model.Conference
import net.jami.model.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.jami.services.AccountService
import net.jami.services.CallService
import net.jami.services.ContactService
import net.jami.services.DeviceRuntimeService
import net.jami.services.expect.HardwareService
import net.jami.services.expect.VideoEvent
import net.jami.utils.Log
import net.jami.model.VideoLossState
import net.jami.model.VideoQuality
import net.jami.model.VideoQualityState

/**
 * Mode of the current call — drives which CallScreen composable is shown.
 */
sealed class CallMode {
    object Incoming : CallMode()
    object Outgoing : CallMode()
    object OnGoing : CallMode()
    object OnHold : CallMode()
    data class Ended(val reason: HangupReason) : CallMode()
}

/**
 * Minimal participant info surfaced to the UI.
 */
data class ParticipantUi(
    val callId: String,
    val displayName: String,
    val sinkId: String = "",
    val isModerator: Boolean = false,
    val isAudioMuted: Boolean = false,
    val isVideoMuted: Boolean = false,
    val isHandRaised: Boolean = false,
    val isActive: Boolean = false,
    val isLocal: Boolean = false
)

/**
 * Full UI state for the call screen.
 */
data class CallState(
    val callMode: CallMode = CallMode.Outgoing,
    val callStatus: String = "",
    val peerName: String = "",
    val peerUri: String = "",
    val duration: Long = 0L,
    val isAudioMuted: Boolean = false,
    val isVideoMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isIncoming: Boolean = false,
    val isOnHold: Boolean = false,
    val isConference: Boolean = false,
    val isModerator: Boolean = false,
    val isConferenceLocked: Boolean = false,
    val isVideo: Boolean = false,
    val participantCount: Int = 1,
    val participants: List<ParticipantUi> = emptyList(),
    val conferenceLayout: Int = 0,
    val hangupReason: HangupReason = HangupReason.NONE,
    // Video state
    val hasVideo: Boolean = false,
    val hasLocalVideo: Boolean = false,
    val hasRemoteVideo: Boolean = false,
    val remoteVideoSinkId: String = "",
    val localVideoSinkId: String = "",
    val isFrontCamera: Boolean = true,
    val isLocalPreviewVisible: Boolean = true,
    val remoteVideoWidth: Int = 0,
    val remoteVideoHeight: Int = 0,
    val isScreenSharing: Boolean = false,
    // Network resilience
    val videoLoss: VideoLossState = VideoLossState(),
    // Video quality
    val videoQuality: VideoQualityState = VideoQualityState()
)

/**
 * ViewModel for the active call screen. Mirrors the logic of CallPresenter from
 * jami-android-client libjamiclient (787 LOC), adapted for KMP Kotlin Flows.
 *
 * Entry points:
 *  - [initOutgoing] — user places an outgoing call from a conversation screen.
 *  - [initIncoming] — attaches to an existing incoming call (from notification tap).
 */
class CallViewModel(
    private val callService: CallService,
    private val accountService: AccountService,
    private val contactService: ContactService,
    private val hardwareService: HardwareService,
    private val deviceRuntimeService: DeviceRuntimeService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _cameraPermissionRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cameraPermissionRequest: SharedFlow<Unit> = _cameraPermissionRequest.asSharedFlow()

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var currentCallId: String? = null
    private var currentAccountId: String? = null
    private var currentConference: Conference? = null
    private var durationJob: Job? = null
    private var callStartTime: Long = 0L
    private var confUpdatesJob: Job? = null
    private var callUpdatesJob: Job? = null

    // Video loss tracking
    private var videoLossJob: Job? = null
    private var lastRemoteVideoEventTime: Long = 0L
    private var lastLocalVideoEventTime: Long = 0L
    private var remoteVideoRetries: Int = 0
    private val VIDEO_LOSS_TIMEOUT_MS = 2000L
    private val MAX_VIDEO_LOSS_RETRIES = 5
    private val RETRY_INTERVAL_MS = 2000L
    private val FALLBACK_TIMEOUT_MS = 15000L

    // ==================== Entry points ====================

    /**
     * Initiate a new outgoing call from a conversation.
     * Mirrors CallPresenter.initOutGoing().
     */
    fun initOutgoing(accountId: String = "", contactUri: String, hasVideo: Boolean, conversationUri: String? = null) {
        Log.d(TAG, "initOutgoing: contactUri=$contactUri hasVideo=$hasVideo conversationUri=$conversationUri")
        scope.launch {
            val resolvedAccountId = accountId.ifEmpty {
                accountService.currentAccount.value?.accountId ?: return@launch
            }
            currentAccountId = resolvedAccountId
            val peerUri = Uri.fromString(contactUri)
            Log.d(TAG, "initOutgoing: resolvedAccount=$resolvedAccountId peerUri=${peerUri.uri} scheme=${peerUri.scheme}")

            _state.value = _state.value.copy(
                callMode = CallMode.Outgoing,
                callStatus = CallStatus.SEARCHING.name,
                peerUri = contactUri,
                peerName = contactUri,
                isIncoming = false,
                hasVideo = hasVideo,
                hasLocalVideo = hasVideo,
                isFrontCamera = hardwareService.isPreviewFromFrontCamera
            )

            try {
                val convUri = conversationUri?.let { Uri.fromString(it) }
                Log.d(TAG, "initOutgoing: calling placeCall accountId=$resolvedAccountId uri=${peerUri.uri} convUri=${convUri?.uri}")
                val call = callService.placeCall(
                    accountId = resolvedAccountId,
                    contactUri = peerUri,
                    hasVideo = hasVideo,
                    conversationUri = convUri
                )
                Log.d(TAG, "initOutgoing: placeCall returned daemonId=${call.daemonId}")
                currentCallId = call.daemonId
                resolveContactName(resolvedAccountId, peerUri, call)
                subscribeToConferenceUpdates(call.daemonId ?: return@launch)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    callMode = CallMode.Ended(HangupReason.ERROR),
                    callStatus = CallStatus.FAILURE.name,
                    hangupReason = HangupReason.ERROR
                )
                hardwareService.closeAudioState()
            }
        }
    }

    /**
     * Attach to an existing incoming daemon call (arrived via notification).
     * Mirrors CallPresenter.initIncomingCall().
     *
     * @param callId The daemon call ID from the notification.
     * @param actionViewOnly When true, just display the call state (call already answered elsewhere).
     */
    fun initIncoming(callId: String, actionViewOnly: Boolean = false) {
        val call = callService.getCall(callId)
        currentCallId = callId
        currentAccountId = call?.account

        // Detect if video is offered in the incoming call by checking media list
        val hasVideoOffered = call?.mediaList?.any { media ->
            media.mediaType == net.jami.model.Media.MediaType.MEDIA_TYPE_VIDEO
        } ?: false

        if (call != null) {
            currentAccountId = call.account
            _state.value = _state.value.copy(
                callMode = CallMode.Incoming,
                callStatus = call.callStatus.name,
                peerUri = call.peerUri.uri,
                peerName = call.peerUri.uri,
                isIncoming = true,
                isVideo = hasVideoOffered,
                hasVideo = hasVideoOffered,
                hasLocalVideo = hasVideoOffered,
                isFrontCamera = hardwareService.isPreviewFromFrontCamera
            )
            resolveContactName(call.account, call.peerUri, call)
        } else {
            _state.value = _state.value.copy(
                callMode = CallMode.Incoming,
                isIncoming = true,
                peerUri = callId,
                isVideo = hasVideoOffered
            )
        }

        subscribeToConferenceUpdates(callId)
    }

    // ==================== Call actions ====================

    fun acceptCurrent(withVideo: Boolean = false) {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        callService.accept(accountId, callId, hasVideo = withVideo)
    }

    fun refuseCurrent() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        callService.refuse(accountId, callId)
    }

    fun endCall() {
        val conf = currentConference
        val accountId = currentAccountId ?: return
        if (conf != null && !conf.isSimpleCall) {
            callService.hangUpConference(accountId, conf.id)
        } else {
            val callId = currentCallId ?: return
            callService.hangUp(accountId, callId)
        }
    }

    fun toggleMute() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        val newMuteState = !_state.value.isAudioMuted
        callService.muteLocalMedia(accountId, callId, CallService.MEDIA_TYPE_AUDIO, newMuteState)
        _state.value = _state.value.copy(isAudioMuted = newMuteState)
    }

    fun hasCameraPermission(): Boolean = deviceRuntimeService.hasCameraPermission()

    fun toggleVideo() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        val newMuteState = !_state.value.isVideoMuted
        // Enabling video requires camera permission; request it if absent
        if (!newMuteState && !deviceRuntimeService.hasCameraPermission()) {
            _cameraPermissionRequest.tryEmit(Unit)
            return
        }
        callService.muteLocalMedia(accountId, callId, CallService.MEDIA_TYPE_VIDEO, newMuteState)
        _state.value = _state.value.copy(isVideoMuted = newMuteState)
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (!granted) return
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        // Unmute video now that permission is confirmed
        if (_state.value.isVideoMuted) {
            callService.muteLocalMedia(accountId, callId, CallService.MEDIA_TYPE_VIDEO, false)
            _state.value = _state.value.copy(isVideoMuted = false)
        }
    }

    fun toggleSpeaker() {
        val newState = !hardwareService.isSpeakerphoneOn()
        val conf = currentConference
        if (conf != null) {
            hardwareService.toggleSpeakerphone(conf, newState)
        }
        _state.value = _state.value.copy(isSpeakerOn = newState)
    }

    fun toggleHold() {
        val accountId = currentAccountId ?: return
        val conf = currentConference
        if (conf != null) {
            if (_state.value.isOnHold) callService.unholdCallOrConference(conf)
            else callService.holdCallOrConference(conf)
        } else {
            val callId = currentCallId ?: return
            if (_state.value.isOnHold) callService.unhold(accountId, callId)
            else callService.hold(accountId, callId)
        }
    }

    fun sendDtmf(key: Char) {
        callService.playDtmf(key.toString())
    }

    fun setConferenceLayout(layout: Int) {
        val accountId = currentAccountId ?: return
        val confId = currentConference?.id ?: currentCallId ?: return
        callService.setConferenceLayout(accountId, confId, layout)
        _state.value = _state.value.copy(conferenceLayout = layout)
    }

    fun sendCallMessage(text: String) {
        val accountId = currentAccountId ?: return
        val callId = currentCallId ?: return
        callService.sendTextMessage(accountId, callId, text)
    }

    // ==================== Video controls ====================

    /**
     * Switch between front and back camera.
     */
    fun switchCamera() {
        val newCamId = hardwareService.changeCamera(false)
        if (newCamId != null) {
            val isFront = hardwareService.isPreviewFromFrontCamera
            _state.value = _state.value.copy(isFrontCamera = isFront)
        }
    }

    /**
     * Toggle local video preview visibility (not the actual video stream).
     */
    fun toggleLocalPreviewVisibility() {
        _state.value = _state.value.copy(
            isLocalPreviewVisible = !_state.value.isLocalPreviewVisible
        )
    }

    private var pendingScreenShareAccountId: String? = null
    private var pendingScreenShareCallId: String? = null
    private var screenShareReadyJob: Job? = null

    /**
     * Start screen sharing. On Android this requests MediaProjection permission first;
     * the actual switchInput is deferred until the permission is granted and
     * hardwareService.screenShareReady fires.
     */
    fun startScreenShare() {
        val accountId = currentAccountId ?: return
        val callId = currentCallId ?: return
        pendingScreenShareAccountId = accountId
        pendingScreenShareCallId = callId

        screenShareReadyJob?.cancel()
        screenShareReadyJob = scope.launch {
            hardwareService.screenShareReady.collect {
                val a = pendingScreenShareAccountId ?: return@collect
                val c = pendingScreenShareCallId ?: return@collect
                pendingScreenShareAccountId = null
                pendingScreenShareCallId = null
                callService.replaceVideoMedia(a, c, "camera://desktop")
                // Force startCapture in case the daemon does not call it back after renegotiation
                hardwareService.startCapture("camera://desktop")
                _state.value = _state.value.copy(isScreenSharing = true)
                screenShareReadyJob?.cancel()
            }
        }
        hardwareService.requestScreenSharePermission()
    }

    /**
     * Stop screen sharing and return to camera.
     */
    fun stopScreenShare() {
        val accountId = currentAccountId ?: return
        val callId = currentCallId ?: return
        val cameraUri = if (_state.value.isFrontCamera) "camera://0" else "camera://1"
        callService.replaceVideoMedia(accountId, callId, cameraUri)
        _state.value = _state.value.copy(isScreenSharing = false)
    }

    /**
     * Toggle screen sharing on/off.
     */
    fun toggleScreenShare() {
        if (_state.value.isScreenSharing) {
            stopScreenShare()
        } else {
            startScreenShare()
        }
    }

    // ==================== Conference Moderation ====================

    /**
     * Mute all participants in the conference (moderator only).
     */
    fun muteAllParticipants() {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        callService.muteAllParticipants(accountId, conf.id)
    }

    /**
     * Remove a participant from the conference (moderator only).
     */
    fun removeParticipant(participantId: String) {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        callService.removeParticipant(accountId, conf.id, participantId)
    }

    /**
     * Lock/unlock the conference (moderator only).
     */
    fun toggleConferenceLock(locked: Boolean) {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        callService.setConferenceLocked(accountId, conf.id, locked)
    }

    /**
     * Mute or unmute a specific participant's audio (moderator only).
     */
    fun setParticipantAudioMuted(participantId: String, muted: Boolean) {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        if (muted) {
            callService.muteParticipantAudio(accountId, conf.id, participantId)
        } else {
            callService.unmuteParticipantAudio(accountId, conf.id, participantId)
        }
    }

    /**
     * Enable or disable a specific participant's video (moderator only).
     */
    fun setParticipantVideoEnabled(participantId: String, enabled: Boolean) {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        if (!enabled) {
            callService.disableParticipantVideo(accountId, conf.id, participantId)
        } else {
            callService.enableParticipantVideo(accountId, conf.id, participantId)
        }
    }

    // ==================== Video Quality Control ====================

    /**
     * Set video quality preset (Low/Medium/High/Ultra).
     * Adjusts resolution, frame rate, and bitrate constraints.
     */
    fun setVideoQuality(quality: VideoQuality) {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        callService.setVideoQuality(accountId, callId, quality)
        _state.value = _state.value.copy(
            videoQuality = _state.value.videoQuality.copy(quality = quality)
        )
    }

    /**
     * Set custom bitrate limit in kbps (0 = no limit).
     */
    fun setVideoBitrate(bitrate: Int) {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        callService.setVideoBitrate(accountId, callId, bitrate)
    }

    /**
     * Toggle video stats overlay visibility.
     */
    fun toggleVideoStats() {
        _state.value = _state.value.copy(
            videoQuality = _state.value.videoQuality.copy(
                isShowingStats = !_state.value.videoQuality.isShowingStats
            )
        )
    }

    /**
     * Update video quality based on network conditions (packet loss).
     * Called periodically to adapt video quality to network state.
     */
    fun updateVideoQualityBasedOnNetwork(packetLoss: Float) {
        if (!_state.value.videoQuality.isAutoQuality) return

        val currentQuality = _state.value.videoQuality.quality
        val recommendedBitrate = currentQuality.getRecommendedBitrate(packetLoss)

        // Auto-downgrade if packet loss is high
        if (packetLoss > 0.10f && currentQuality != VideoQuality.LOW) {
            setVideoQuality(VideoQuality.LOW)
        } else if (packetLoss > 0.05f && currentQuality == VideoQuality.ULTRA) {
            setVideoQuality(VideoQuality.HIGH)
        }

        setVideoBitrate(recommendedBitrate)
        _state.value = _state.value.copy(
            videoQuality = _state.value.videoQuality.copy(packetLoss = packetLoss)
        )
    }

    // ==================== Video Loss Recovery ====================

    fun retryRemoteVideo() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        remoteVideoRetries++

        if (remoteVideoRetries <= MAX_VIDEO_LOSS_RETRIES) {
            hardwareService.requestKeyFrame("remote")
            _state.value = _state.value.copy(
                videoLoss = _state.value.videoLoss.copy(
                    isRetrying = true,
                    retryAttempt = remoteVideoRetries
                )
            )
        }
    }

    fun fallbackToAudioOnly() {
        _state.value = _state.value.copy(
            videoLoss = _state.value.videoLoss.copy(
                isFallbackToAudioOnly = true,
                isVideoLost = false,
                isRetrying = false
            ),
            hasRemoteVideo = false,
            remoteVideoSinkId = ""
        )
    }

    fun resumeVideoAttempt() {
        remoteVideoRetries = 0
        lastRemoteVideoEventTime = net.jami.utils.currentTimeMillis()
        _state.value = _state.value.copy(
            videoLoss = VideoLossState()
        )
    }

    private fun startVideoLossDetection() {
        videoLossJob?.cancel()
        videoLossJob = scope.launch {
            while (isActive) {
                val now = net.jami.utils.currentTimeMillis()
                val remoteVideoLoss = _state.value.hasRemoteVideo &&
                        (now - lastRemoteVideoEventTime) > VIDEO_LOSS_TIMEOUT_MS

                if (remoteVideoLoss && !_state.value.videoLoss.isVideoLost) {
                    _state.value = _state.value.copy(
                        videoLoss = _state.value.videoLoss.copy(
                            isVideoLost = true,
                            lostSinkId = _state.value.remoteVideoSinkId,
                            lossDurationSeconds = (now - lastRemoteVideoEventTime) / 1000,
                            isRetrying = true,
                            retryAttempt = 0
                        )
                    )
                    retryRemoteVideo()
                } else if (_state.value.videoLoss.isVideoLost) {
                    val lossDuration = (now - lastRemoteVideoEventTime) / 1000
                    _state.value = _state.value.copy(
                        videoLoss = _state.value.videoLoss.copy(
                            lossDurationSeconds = lossDuration
                        )
                    )

                    if (lossDuration > FALLBACK_TIMEOUT_MS / 1000 &&
                        !_state.value.videoLoss.isFallbackToAudioOnly) {
                        fallbackToAudioOnly()
                    }
                }

                delay(500)
            }
        }
    }

    // ==================== Internal ====================

    private fun subscribeToConferenceUpdates(callOrConfId: String) {
        confUpdatesJob?.cancel()
        confUpdatesJob = scope.launch {
            callService.getConfUpdates(callOrConfId).collect { conf ->
                handleConferenceUpdate(conf)
            }
        }
        // Also observe raw call updates for simple calls that aren't in a conference yet
        callUpdatesJob?.cancel()
        callUpdatesJob = scope.launch {
            callService.callUpdates.collect { call ->
                if (call.daemonId == currentCallId) {
                    handleCallUpdate(call)
                }
            }
        }
        // Subscribe to video events
        subscribeToVideoEvents()
    }

    private var videoEventsJob: Job? = null

    private fun subscribeToVideoEvents() {
        videoEventsJob?.cancel()
        videoEventsJob = scope.launch {
            hardwareService.videoEvents.collect { event ->
                handleVideoEvent(event)
            }
        }
    }

    private fun handleVideoEvent(event: VideoEvent) {
        val callId = currentCallId ?: return

        if (event.started && event.width > 0 && event.height > 0) {
            // Remote video started — update last event time for loss detection
            lastRemoteVideoEventTime = net.jami.utils.currentTimeMillis()
            remoteVideoRetries = 0
            _state.value = _state.value.copy(
                hasRemoteVideo = true,
                remoteVideoSinkId = event.sinkId,
                remoteVideoWidth = event.width,
                remoteVideoHeight = event.height,
                videoLoss = VideoLossState()  // Clear loss state on recovery
            )
        } else if (!event.started && event.sinkId == _state.value.remoteVideoSinkId) {
            // Remote video stopped
            _state.value = _state.value.copy(
                hasRemoteVideo = false,
                remoteVideoSinkId = "",
                remoteVideoWidth = 0,
                remoteVideoHeight = 0
            )
        }
    }

    private fun handleCallUpdate(call: Call) {
        val status = call.callStatus
        val isHold = status == CallStatus.HOLD
        val isOnGoing = status.isOnGoing
        val isOver = status.isOver

        val mode = when {
            isOver || status == CallStatus.HUNGUP || status == CallStatus.FAILURE || status == CallStatus.BUSY ->
                CallMode.Ended(call.hangupReason.takeIf { it != HangupReason.NONE } ?: HangupReason.REMOTE)
            isHold -> CallMode.OnHold
            isOnGoing -> CallMode.OnGoing
            call.isIncoming -> CallMode.Incoming
            else -> CallMode.Outgoing
        }

        val hasVideo = call.hasVideo()
        val hasActiveVideo = call.hasActiveVideo()

        _state.value = _state.value.copy(
            callMode = mode,
            callStatus = status.name,
            peerUri = call.peerUri.uri,
            isAudioMuted = call.isAudioMuted,
            isVideoMuted = call.isVideoMuted,
            isIncoming = call.isIncoming,
            isOnHold = isHold,
            hangupReason = call.hangupReason,
            hasVideo = hasVideo,
            hasLocalVideo = hasActiveVideo && !call.isVideoMuted
        )

        if (status == CallStatus.CURRENT && durationJob == null) {
            callStartTime = if (call.timestamp > 0) call.timestamp
                else net.jami.utils.currentTimeMillis()
            lastRemoteVideoEventTime = callStartTime
            startDurationTimer()
            startVideoLossDetection()  // Start detecting video loss during call
            val currentCall = call
            val conf = currentConference
            currentAccountId?.let { accountId ->
                hardwareService.updateAudioState(conf, currentCall, call.isIncoming, false)
            }
        }

        if (isOver) {
            durationJob?.cancel()
            durationJob = null
            hardwareService.closeAudioState()
        }
    }

    private fun handleConferenceUpdate(conf: Conference) {
        currentConference = conf
        if (conf.isSimpleCall) {
            // Single-call conference: delegate to the underlying call state
            conf.participants.firstOrNull()?.let { handleCallUpdate(it) }
            return
        }

        val status = conf.state ?: CallStatus.NONE
        val isHold = status == CallStatus.HOLD
        val isOver = status.isOver

        val mode = when {
            isOver -> CallMode.Ended(HangupReason.REMOTE)
            isHold -> CallMode.OnHold
            conf.isOnGoing -> CallMode.OnGoing
            else -> CallMode.Outgoing
        }

        // Map participant info by call ID or peer URI for active speaker tracking + moderator status
        val participantInfoMap = conf.participantInfo.associateBy { it.tag }

        val participantUis = conf.participants.map { call ->
            val participantInfo = participantInfoMap[call.daemonId ?: ""]
                ?: participantInfoMap[call.peerUri.uri]
            ParticipantUi(
                callId = call.daemonId ?: "",
                displayName = call.contact?.displayName ?: call.peerUri.uri,
                sinkId = participantInfo?.sinkId ?: "",
                isAudioMuted = call.isAudioMuted,
                isVideoMuted = call.isVideoMuted,
                isModerator = participantInfo?.isModerator ?: false,  // Moderator flag from daemon
                isActive = participantInfo?.active ?: false  // Active speaker from daemon
            )
        }

        _state.value = _state.value.copy(
            callMode = mode,
            callStatus = status.name,
            isOnHold = isHold,
            isConference = true,
            isModerator = conf.isModerator,
            isConferenceLocked = conf.isLocked,
            participantCount = conf.participants.size,
            participants = participantUis,
            conferenceLayout = _state.value.conferenceLayout
        )

        if (isOver) {
            durationJob?.cancel()
            durationJob = null
            hardwareService.closeAudioState()
        }
    }

    private fun resolveContactName(accountId: String, peerUri: Uri, call: Call) {
        scope.launch {
            try {
                val contact = contactService.findContact(accountId, peerUri)
                val profile = contactService.loadContactData(contact, accountId)
                val displayName = profile.displayName?.takeIf { it.isNotBlank() }
                    ?: contact.displayName?.takeIf { it.isNotBlank() }
                    ?: peerUri.uri
                _state.value = _state.value.copy(peerName = displayName)
                call.contact = contact
            } catch (_: Exception) {
                // Keep URI as display name on failure
            }
        }
    }

    private fun startDurationTimer() {
        durationJob = scope.launch {
            while (isActive) {
                val now = net.jami.utils.currentTimeMillis()
                _state.value = _state.value.copy(duration = (now - callStartTime) / 1000)
                delay(1000)
            }
        }
    }

    fun onCleared() {
        durationJob?.cancel()
        confUpdatesJob?.cancel()
        callUpdatesJob?.cancel()
        videoEventsJob?.cancel()
        videoLossJob?.cancel()
        hardwareService.cameraCleanup()
        scope.cancel()
    }

    companion object {
        private const val TAG = "CallViewModel"
    }
}
