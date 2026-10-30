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

import androidx.lifecycle.ViewModel

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.jami.services.AccountService
import net.jami.services.CallService
import net.jami.services.ContactService
import net.jami.services.DeviceRuntimeService
import net.jami.services.expect.HardwareService
import net.jami.services.expect.VideoEvent
import net.jami.utils.Log
import net.jami.model.VideoLossState

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
 * A call that can be used as the target of an attended transfer.
 */
data class CallPickerItem(val callId: String, val peerName: String)

/**
 * Negotiated media details for the current call, as the daemon reports them.
 *
 * Every field can be absent: the daemon only fills in what has been negotiated, so an audio-only
 * call has no video codec and a call still connecting may have neither.
 */
data class CallStats(
    val audioCodec: String? = null,
    val audioSampleRate: String? = null,
    val videoCodec: String? = null,
    val videoBitrate: String? = null,
    val videoMinBitrate: String? = null,
    val videoMaxBitrate: String? = null,
    val isAudioOnly: Boolean = false,
    val videoSource: String? = null,
) {
    companion object {
        /** Keys from libjami `call_const.h`, namespace libjami::Call::Details. */
        fun fromDetails(details: Map<String, String>): CallStats = CallStats(
            audioCodec = details["AUDIO_CODEC"]?.ifBlank { null },
            audioSampleRate = details["AUDIO_SAMPLE_RATE"]?.ifBlank { null },
            videoCodec = details["VIDEO_CODEC"]?.ifBlank { null },
            videoBitrate = details["VIDEO_BITRATE"]?.ifBlank { null },
            videoMinBitrate = details["VIDEO_MIN_BITRATE"]?.ifBlank { null },
            videoMaxBitrate = details["VIDEO_MAX_BITRATE"]?.ifBlank { null },
            isAudioOnly = details["AUDIO_ONLY"]?.toBoolean() ?: false,
            videoSource = details["VIDEO_SOURCE"]?.ifBlank { null },
        )
    }
}

/**
 * One message in the in-call chat, resolved for display.
 */
data class InCallMessageUi(
    val author: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
)

/**
 * Conference mixer layouts understood by the daemon (libjami::Layout).
 */
object ConferenceLayout {
    const val GRID = 0
    const val ONE_BIG = 1
    const val ONE_BIG_WITH_SMALL = 2
}

/**
 * Minimal participant info surfaced to the UI.
 */
data class ParticipantUi(
    val callId: String,
    val displayName: String,
    /** Participant account URI (bare ring id) — needed to address moderation calls. */
    val peerUri: String = "",
    /** Participant device ID from the conference info — the daemon resolves mutes by it. */
    val deviceId: String = "",
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
    val isRecording: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val hasMicPermission: Boolean = true,
    val hasCamPermission: Boolean = true,
    val isIncoming: Boolean = false,
    val isOnHold: Boolean = false,
    val isConference: Boolean = false,
    val isModerator: Boolean = false,
    val isConferenceLocked: Boolean = false,
    val isVideo: Boolean = false,
    val participantCount: Int = 1,
    val participants: List<ParticipantUi> = emptyList(),
    val conferenceLayout: Int = 0,
    val isHandRaised: Boolean = false,
    /** The peer put us on hold — media stops while the call stays CURRENT. */
    val isPeerHolding: Boolean = false,
    /** At least one remote participant is recording the call. */
    val isRemoteRecording: Boolean = false,
    /** Chat exchanged inside this call, oldest first. Lives only for the call's duration. */
    val inCallMessages: List<InCallMessageUi> = emptyList(),
    /** In-call messages received since the chat sheet was last open. */
    val unreadInCallMessages: Int = 0,
    /**
     * Negotiated media details from the daemon (codecs, sample rate, bitrate), refreshed while
     * the stats panel is open. Empty when the panel has never been opened.
     */
    val callStats: CallStats? = null,
    /** Display names of the remote participants currently recording, comma separated. */
    val remoteRecordingBy: String = "",
    val hangupReason: HangupReason = HangupReason.NONE,
    // Video state
    val hasVideo: Boolean = false,
    val hasLocalVideo: Boolean = false,
    val hasRemoteVideo: Boolean = false,
    val remoteVideoSinkId: String = "",
    val isFrontCamera: Boolean = true,
    val isLocalPreviewVisible: Boolean = true,
    val remoteVideoWidth: Int = 0,
    val remoteVideoHeight: Int = 0,
    val isScreenSharing: Boolean = false,
    // Network resilience
    val videoLoss: VideoLossState = VideoLossState(),
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
) : ViewModel() {
    private val scope = scope

    private val _cameraPermissionRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cameraPermissionRequest: SharedFlow<Unit> = _cameraPermissionRequest.asSharedFlow()

    private val _micPermissionRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val micPermissionRequest: SharedFlow<Unit> = _micPermissionRequest.asSharedFlow()

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    /**
     * Microphone RMS level, 0..1, for the level ring on the mute button. Deliberately *not* part
     * of [CallState]: the daemon sends about ten values a second and putting it there would
     * recompose the whole call screen at that rate. Only the mute button collects it.
     */
    val micLevel: StateFlow<Float> = callService.localAudioLevel

    /** Metering runs only while the call screen is on screen; see [onCleared]. */
    fun setMicMeterEnabled(enabled: Boolean) = callService.setMicrophoneMeterEnabled(enabled)

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
        _state.value = _state.value.copy(
            hasMicPermission = deviceRuntimeService.hasMicrophonePermission(),
            hasCamPermission = deviceRuntimeService.hasCameraPermission(),
        )
        if (!deviceRuntimeService.hasMicrophonePermission()) {
            _micPermissionRequest.tryEmit(Unit)
        }
        scope.launch {
            val resolvedAccountId = accountId.ifEmpty {
                accountService.currentAccount.value?.accountId ?: return@launch
            }
            currentAccountId = resolvedAccountId
            // A group call is hosted on the conversation, not on a peer: the target is
            // `swarm:<conversationId>`, as in ConversationPresenter.goToGroupCall(), which passes
            // conversation.uri as both the number and the conversation. There is no single peer to
            // call, and handing the daemon a bare conversation id makes it look for devices
            // belonging to that id and fail with "No devices found" / code 480.
            val account = accountService.getAccount(resolvedAccountId)
            val conversation = conversationUri
                ?.let { account?.getSwarm(Uri.fromString(it).rawRingId) }
            val isGroupCall = conversation?.isSwarmGroup == true
            val peerUri = if (isGroupCall) conversation!!.uri else Uri.fromString(contactUri)

            // Mirror CallPresenter.initOutGoing(): only allow video if the device has a camera.
            val effectiveVideo = hasVideo && hardwareService.hasCamera()
            Log.d(TAG, "initOutgoing: resolvedAccount=$resolvedAccountId peerUri=${peerUri.uri} scheme=${peerUri.scheme} isGroupCall=$isGroupCall effectiveVideo=$effectiveVideo")

            _state.value = _state.value.copy(
                callMode = CallMode.Outgoing,
                callStatus = CallStatus.SEARCHING.name,
                peerUri = peerUri.uri,
                // A group has no peer to resolve a name for, so name the call by the conversation.
                peerName = if (isGroupCall) conversation!!.title else contactUri,
                isIncoming = false,
                hasVideo = effectiveVideo,
                hasLocalVideo = effectiveVideo,
                isFrontCamera = hardwareService.isPreviewFromFrontCamera
            )

            try {
                // The swarm uri, not the raw id the route carries: CallService.placeCall and the
                // Call it builds both test `isSwarm` on it.
                val convUri = conversation?.uri ?: conversationUri?.let { Uri.fromString(it) }
                Log.d(TAG, "initOutgoing: calling placeCall accountId=$resolvedAccountId uri=${peerUri.uri} convUri=${convUri?.uri}")
                val call = callService.placeCall(
                    accountId = resolvedAccountId,
                    contactUri = peerUri,
                    hasVideo = effectiveVideo,
                    conversationUri = convUri
                )
                Log.d(TAG, "initOutgoing: placeCall returned daemonId=${call.daemonId}")
                currentCallId = call.daemonId
                if (!isGroupCall) resolveContactName(resolvedAccountId, peerUri, call)
                val daemonId = call.daemonId
                if (daemonId != null) {
                    subscribeToConferenceUpdates(daemonId)
                } else {
                    // A hosted group call has no call id of its own; it is tracked through the
                    // conference the daemon runs on the conversation.
                    subscribeToHostedConference(call)
                }
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
     * Mirrors CallPresenter.initIncomingCall() and LetsJam's initializeAcceptedCall().
     *
     * If the call is already CURRENT when this is called (e.g. the user accepted it from
     * the notification action button and the app was then opened, or they are returning to
     * an active call via the ongoing-call notification) we jump straight to OnGoing mode
     * instead of Incoming — this avoids a flash of the ringing UI for a call that is
     * already connected.
     */
    fun initIncoming(callId: String, actionViewOnly: Boolean = false) {
        val call = callService.getCall(callId)
        currentCallId = callId
        currentAccountId = call?.account

        // Detect if video is offered in the incoming call by checking media list
        val hasVideoOffered = call?.mediaList?.any { media ->
            media.mediaType == net.jami.model.Media.MediaType.MEDIA_TYPE_VIDEO
        } ?: false

        // Map current daemon call state to a UI mode. Mirrors LetsJam's
        // initializeAcceptedCall() which queries the daemon for current state so the UI
        // never shows the ringing screen for a call that is already answered.
        val initialMode: CallMode = when (call?.callStatus) {
            Call.CallStatus.CURRENT -> CallMode.OnGoing
            Call.CallStatus.HOLD -> CallMode.OnHold
            Call.CallStatus.OVER,
            Call.CallStatus.HUNGUP,
            Call.CallStatus.FAILURE,
            Call.CallStatus.BUSY -> CallMode.Ended(
                call.hangupReason.takeIf { it != Call.HangupReason.NONE } ?: Call.HangupReason.REMOTE
            )
            else -> CallMode.Incoming
        }

        if (call != null) {
            currentAccountId = call.account
            _state.value = _state.value.copy(
                callMode = initialMode,
                callStatus = call.callStatus.name,
                peerUri = call.peerUri.uri,
                peerName = call.peerUri.uri,
                isIncoming = call.isIncoming,
                isVideo = hasVideoOffered,
                hasVideo = hasVideoOffered,
                hasLocalVideo = hasVideoOffered,
                isFrontCamera = hardwareService.isPreviewFromFrontCamera
            )
            resolveContactName(call.account, call.peerUri, call)
        } else {
            _state.value = _state.value.copy(
                callMode = initialMode,
                isIncoming = true,
                peerUri = callId,
                isVideo = hasVideoOffered
            )
        }

        subscribeToConferenceUpdates(callId)
    }

    // ==================== Incoming preview ====================

    /**
     * Start the local camera preview while an incoming video call is ringing.
     * Mirrors Android's CallFragment.initIncomingCallDisplay(hasVideo=true):
     * the user sees their own camera feed in the background before deciding to accept.
     * Call this only after camera permission is confirmed and [state.isVideo] is true.
     */
    fun startIncomingPreview() {
        scope.launch {
            hardwareService.initVideo()
            hardwareService.startCameraPreview(true)
        }
        _state.value = _state.value.copy(
            hasLocalVideo = true,
            isFrontCamera = hardwareService.isPreviewFromFrontCamera
        )
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

    fun toggleRecording() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        callService.toggleRecording(accountId, callId)
        // Optimistic flip for responsiveness; corrected by the daemon's
        // onRecordingStateChanged signal mapped in the call-updates collector.
        _state.value = _state.value.copy(isRecording = !_state.value.isRecording)
    }

    fun hasCameraPermission(): Boolean = deviceRuntimeService.hasCameraPermission()
    fun hasMicrophonePermission(): Boolean = deviceRuntimeService.hasMicrophonePermission()

    fun onMicPermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(hasMicPermission = granted)
        if (granted) {
            callService.restartAudioLayer()
        }
    }

    fun toggleVideo() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        val isCurrentlyActive = !_state.value.isVideoMuted && _state.value.hasLocalVideo
        if (isCurrentlyActive) {
            // Turn off: mute the existing video stream
            callService.muteLocalMedia(accountId, callId, CallService.MEDIA_TYPE_VIDEO, true)
            _state.value = _state.value.copy(isVideoMuted = true, hasLocalVideo = false)
        } else {
            // Turn on: needs camera permission + hardware init + media renegotiation
            if (!deviceRuntimeService.hasCameraPermission()) {
                _cameraPermissionRequest.tryEmit(Unit)
                return
            }
            enableVideoStream(accountId, callId)
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(hasCamPermission = granted)
        if (!granted) return
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        enableVideoStream(accountId, callId)
    }

    fun requestMicPermission() { _micPermissionRequest.tryEmit(Unit) }
    fun requestCameraPermission() { _cameraPermissionRequest.tryEmit(Unit) }

    // Mirrors jami-android-client CallPresenter.switchOnOffCamera():
    // initialise hardware, get camera id, then do a media renegotiation via requestMediaChange
    // so the daemon adds/replaces the video stream in the active call.
    private fun enableVideoStream(accountId: String, callId: String) {
        scope.launch {
            hardwareService.initVideo()
            val camId = hardwareService.changeCamera(true) ?: "0"
            callService.replaceVideoMedia(accountId, callId, "camera://$camId")
            _state.value = _state.value.copy(
                isVideoMuted = false,
                hasLocalVideo = true,
                isFrontCamera = hardwareService.isPreviewFromFrontCamera
            )
        }
    }

    fun toggleSpeaker() {
        // Without a conference there is nothing to switch, and updating the state anyway
        // desynced it from the hardware permanently: the next toggle re-read
        // isSpeakerphoneOn() (still false) and set "on" again, so it never flipped back.
        val conf = currentConference ?: return
        val newState = !hardwareService.isSpeakerphoneOn()
        hardwareService.toggleSpeakerphone(conf, newState)
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

    fun transfer(to: String) {
        val accountId = currentAccountId ?: return
        val callId = currentCallId ?: return
        callService.transfer(accountId, callId, to)
    }

    fun attendedTransfer(targetCallId: String) {
        val accountId = currentAccountId ?: return
        val callId = currentCallId ?: return
        callService.attendedTransfer(accountId, callId, targetCallId)
    }

    fun getOtherActiveCalls(): List<CallPickerItem> {
        val myCallId = currentCallId ?: return emptyList()
        return callService.getActiveCalls()
            .filter { it.daemonId != myCallId }
            .mapNotNull { call ->
                val id = call.daemonId ?: return@mapNotNull null
                val name = call.contact?.displayName?.takeIf { it.isNotBlank() }
                    ?: call.contact?.username?.takeIf { it.isNotBlank() }
                    ?: call.peerUri.rawRingId.take(12).ifEmpty { call.peerUri.uri }
                CallPickerItem(callId = id, peerName = name)
            }
    }

    fun setConferenceLayout(layout: Int) {
        val accountId = currentAccountId ?: return
        val confId = currentConference?.id ?: currentCallId ?: return
        callService.setConferenceLayout(accountId, confId, layout)
        _state.value = _state.value.copy(conferenceLayout = layout)
    }

    /**
     * Send a message into the ongoing call.
     *
     * This used to call CallService.sendTextMessage, which routes to the account-level send and
     * addresses a contact URI — handing it a call ID sent the message nowhere. The call-level
     * path also reaches every participant of a conference.
     */
    fun sendCallMessage(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val accountId = currentAccountId ?: return
        val callId = currentConference?.id ?: currentCallId ?: return
        val selfUri = accountService.getAccount(accountId)
            ?.let { Uri.fromString(it.username).rawRingId }
            ?: ""
        callService.sendInCallMessage(accountId, callId, selfUri, body)
    }

    /**
     * Refresh the media stats while the panel is open.
     *
     * Codecs and bitrates change during a call (renegotiation, adaptive bitrate), so this polls
     * rather than reading once. [stopCallStats] cancels it when the panel closes.
     */
    fun startCallStats() {
        if (callStatsJob?.isActive == true) return
        callStatsJob = scope.launch {
            while (true) {
                val accountId = currentAccountId
                val callId = currentCallId ?: currentConference?.firstCall?.daemonId
                if (accountId != null && callId != null) {
                    val details = callService.getCallDetails(accountId, callId)
                    _state.value = _state.value.copy(callStats = CallStats.fromDetails(details))
                }
                kotlinx.coroutines.delay(CALL_STATS_REFRESH_MS)
            }
        }
    }

    /** Stop refreshing media stats; the last values stay in state. */
    fun stopCallStats() {
        callStatsJob?.cancel()
        callStatsJob = null
    }

    /** The user opened the in-call chat, so nothing is unread any more. */
    fun markInCallMessagesRead() {
        if (_state.value.unreadInCallMessages != 0) {
            _state.value = _state.value.copy(unreadInCallMessages = 0)
        }
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
     *
     * If the platform permission prompt is denied (or otherwise never resolves —
     * e.g. the user backgrounds the picker), [hardwareService.screenShareReady] never
     * emits. Without a bound this would wait forever with isScreenSharing stuck false
     * and no way to retry. [SCREEN_SHARE_PERMISSION_TIMEOUT_MS] bounds the wait so the
     * pending state always clears.
     */
    fun startScreenShare() {
        val accountId = currentAccountId ?: return
        val callId = currentCallId ?: return
        pendingScreenShareAccountId = accountId
        pendingScreenShareCallId = callId

        screenShareReadyJob?.cancel()
        screenShareReadyJob = scope.launch {
            val ready = withTimeoutOrNull(SCREEN_SHARE_PERMISSION_TIMEOUT_MS) {
                hardwareService.screenShareReady.first()
            }
            if (ready == null) {
                Log.w(TAG, "startScreenShare: permission not granted within timeout, giving up")
                pendingScreenShareAccountId = null
                pendingScreenShareCallId = null
                return@launch
            }
            val a = pendingScreenShareAccountId ?: return@launch
            val c = pendingScreenShareCallId ?: return@launch
            pendingScreenShareAccountId = null
            pendingScreenShareCallId = null
            callService.replaceVideoMedia(a, c, "camera://desktop")
            // Force startCapture in case the daemon does not call it back after renegotiation
            hardwareService.startCapture("camera://desktop")
            _state.value = _state.value.copy(isScreenSharing = true)
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

    /**
     * Mute or unmute a participant's audio (moderator only).
     *
     * Addresses the participant by (URI, device) the way the daemon expects, unlike the
     * call-ID based [setParticipantAudioMuted].
     */
    fun setParticipantMuted(participant: ParticipantUi, muted: Boolean) {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        callService.muteParticipantStream(
            accountId, conf.id, participant.peerUri, participant.deviceId, muted
        )
    }

    /**
     * Eject a participant from the conference (moderator only).
     * Hangs up their own call leg directly when we host it.
     */
    fun ejectParticipant(participant: ParticipantUi) {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        callService.hangupParticipant(accountId, conf.id, participant.peerUri, participant.deviceId)
    }

    /**
     * Promote or demote a participant to/from moderator (moderator only).
     */
    fun setParticipantModerator(participant: ParticipantUi, moderator: Boolean) {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        callService.setModerator(accountId, conf.id, participant.peerUri, moderator)
    }

    /**
     * Focus a participant's video stream in the conference mixer (modern per-stream API).
     */
    fun setActiveStream(participant: ParticipantUi, active: Boolean) {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        callService.setActiveStream(
            accountId, conf.id, participant.peerUri, participant.deviceId,
            participant.sinkId, active
        )
    }

    /**
     * Raise or lower a participant's hand. Lowering someone else's hand requires moderator
     * rights; with no participant given this acts on the local user.
     */
    fun setHandRaised(raised: Boolean, participant: ParticipantUi? = null) {
        val accountId = currentAccountId ?: return
        val conf = currentConference ?: return
        val account = accountService.getAccount(accountId)
        val peerUri = participant?.peerUri
            ?: Uri.fromString(account?.username ?: "").rawRingId.ifEmpty { return }
        val deviceId = participant?.deviceId ?: account?.deviceId ?: ""
        callService.raiseHand(accountId, conf.id, peerUri, deviceId, raised)
        if (participant == null) {
            _state.value = _state.value.copy(isHandRaised = raised)
        }
    }

    /** Toggle the local user's raised hand. */
    fun toggleRaiseHand() = setHandRaised(!_state.value.isHandRaised)

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
        // Time-based detection requires frame-level callbacks to keep lastRemoteVideoEventTime
        // fresh. We don't have those — decodingStarted fires once at decoder start and never
        // again while frames flow. Polling would fire a false "video lost" every 2 s into any
        // call. Detection is driven by decodingStopped (via handleVideoEvent) instead.
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
        subscribeToInCallMessages()
    }

    /**
     * Track the conference the daemon hosts for a group call this device started.
     *
     * [CallService.placeCall] returns no call id for a hosted group call, so there is no call to
     * subscribe to and no raw call updates to follow — everything about the call arrives as
     * conference updates. [currentCallId] becomes the conference id, which is what the daemon
     * takes for the in-call controls, and [handleConferenceUpdate] sets `currentConference`, so
     * [endCall] hangs the conference up instead of silently doing nothing.
     */
    private fun subscribeToHostedConference(call: Call) {
        confUpdatesJob?.cancel()
        confUpdatesJob = scope.launch {
            callService.hostedConfUpdates(call).collect { conf ->
                currentCallId = conf.id
                handleConferenceUpdate(conf)
            }
        }
        subscribeToVideoEvents()
        subscribeToInCallMessages()
    }

    private var inCallMessagesJob: Job? = null
    private var callStatsJob: Job? = null

    /**
     * Mirror the call's chat into the UI state, counting anything incoming that arrives while the
     * sheet is closed as unread.
     */
    private fun subscribeToInCallMessages() {
        inCallMessagesJob?.cancel()
        inCallMessagesJob = scope.launch {
            callService.inCallMessages.collect { messages ->
                val callId = currentConference?.id ?: currentCallId
                val mine = messages.filter { callId == null || it.callId == callId }
                val previousIncoming = _state.value.inCallMessages.count { !it.isOutgoing }
                val incoming = mine.count { !it.isOutgoing }
                val newIncoming = (incoming - previousIncoming).coerceAtLeast(0)
                _state.value = _state.value.copy(
                    inCallMessages = mine.map { message ->
                        InCallMessageUi(
                            author = resolveInCallAuthor(message.author, message.isOutgoing),
                            body = message.body,
                            timestamp = message.timestamp,
                            isOutgoing = message.isOutgoing,
                        )
                    },
                    unreadInCallMessages = _state.value.unreadInCallMessages + newIncoming,
                )
            }
        }
    }

    /** Prefer a participant's display name over the raw URI for the chat's author line. */
    private fun resolveInCallAuthor(authorUri: String, isOutgoing: Boolean): String {
        if (isOutgoing) return ""
        val uri = Uri.fromString(authorUri)
        val participant = _state.value.participants
            .firstOrNull { it.peerUri == uri.rawRingId }
        return participant?.displayName
            ?: currentConference?.participants
                ?.firstOrNull { it.peerUri.rawRingId == uri.rawRingId }
                ?.contact?.displayName
            ?: _state.value.peerName.ifEmpty { uri.rawRingId.take(12) }
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
        Log.d(TAG, "handleVideoEvent: sinkId=${event.sinkId} started=${event.started} ${event.width}x${event.height} callId=$callId")

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
            isRecording = call.isRecording,
            isIncoming = call.isIncoming,
            isOnHold = isHold,
            isPeerHolding = call.isPeerHolding,
            hangupReason = call.hangupReason,
            hasVideo = hasVideo,
            hasLocalVideo = hasActiveVideo && !call.isVideoMuted,
            hasMicPermission = deviceRuntimeService.hasMicrophonePermission(),
            hasCamPermission = deviceRuntimeService.hasCameraPermission()
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
                peerUri = call.peerUri.rawRingId,
                deviceId = participantInfo?.device ?: "",
                sinkId = participantInfo?.sinkId ?: "",
                isAudioMuted = participantInfo?.let { it.audioModeratorMuted || it.audioLocalMuted }
                    ?: call.isAudioMuted,
                isVideoMuted = participantInfo?.videoMuted ?: call.isVideoMuted,
                isModerator = participantInfo?.isModerator ?: false,  // Moderator flag from daemon
                isHandRaised = participantInfo?.isHandRaised ?: false,
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
            conferenceLayout = _state.value.conferenceLayout,
            isPeerHolding = conf.participants.any { it.isPeerHolding },
            isRemoteRecording = conf.isRemoteRecording,
            remoteRecordingBy = conf.participantRecordingFlow.value
                .joinToString { it.displayName ?: it.uri.rawRingId.take(12) },
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

    public override fun onCleared() {
        callService.setMicrophoneMeterEnabled(false)
        durationJob?.cancel()
        confUpdatesJob?.cancel()
        callUpdatesJob?.cancel()
        videoEventsJob?.cancel()
        videoLossJob?.cancel()
        inCallMessagesJob?.cancel()
        callStatsJob?.cancel()
        hardwareService.cameraCleanup()
        scope.cancel()
    }

    companion object {
        private const val TAG = "CallViewModel"

        /** How often the stats panel re-reads the daemon's call details. */
        private const val CALL_STATS_REFRESH_MS = 2000L

        /** Bounds how long startScreenShare() waits for the platform permission prompt. */
        private const val SCREEN_SHARE_PERMISSION_TIMEOUT_MS = 30_000L
    }
}
