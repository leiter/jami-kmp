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
package net.jami.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.jami.model.Call
import net.jami.model.Call.CallStatus
import net.jami.model.Call.Direction
import net.jami.model.Conference
import net.jami.model.InCallMessage
import net.jami.model.Media
import net.jami.utils.Log
import net.jami.utils.currentTimeMillis
import net.jami.model.Uri

/**
 * Service for managing calls and conferences.
 *
 * Provides call operations (place, accept, refuse, hangup, hold) and
 * emits call state updates via Kotlin Flow.
 *
 * Enforces call settings (video enabled, auto-answer) from SettingsRepository.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes: RxJava → Kotlin Flow, JamiService → DaemonBridge
 */
class CallService(
    private val daemonBridge: DaemonBridgeApi,
    private val accountService: AccountService,
    private val settingsRepository: net.jami.repository.SettingsRepository,
    private val scope: CoroutineScope
) {
    private val calls = mutableMapOf<String, Call>()
    private val conferences = mutableMapOf<String, Conference>()
    // Serializes all daemon callback coroutines — prevents concurrent LinkedHashMap mutation
    private val mutex = Mutex()

    private val _callUpdates = MutableSharedFlow<Call>(replay = 1)
    val callUpdates: SharedFlow<Call> = _callUpdates.asSharedFlow()

    private val _conferenceUpdates = MutableSharedFlow<Conference>(replay = 1)
    val conferenceUpdates: SharedFlow<Conference> = _conferenceUpdates.asSharedFlow()

    private val _currentCalls = MutableStateFlow<List<Call>>(emptyList())
    val currentCalls: StateFlow<List<Call>> = _currentCalls.asStateFlow()

    // Used by the platform layer (e.g. MainActivity) to request navigation to a specific call
    // after a notification tap. JamiNavigation observes this and navigates, then clears it.
    private val _pendingCallNavId = MutableStateFlow<String?>(null)
    val pendingCallNavId: StateFlow<String?> = _pendingCallNavId.asStateFlow()

    fun setPendingCallNavId(callId: String) { _pendingCallNavId.value = callId }
    fun consumePendingCallNavId() { _pendingCallNavId.value = null }

    private val _inCallMessages = MutableStateFlow<List<InCallMessage>>(emptyList())

    /** Messages exchanged inside active calls, oldest first. Cleared per call when it ends. */
    val inCallMessages: StateFlow<List<InCallMessage>> = _inCallMessages.asStateFlow()

    private val _currentConferences = MutableStateFlow<List<Conference>>(emptyList())
    val currentConferences: StateFlow<List<Conference>> = _currentConferences.asStateFlow()

    // ==================== Instant conference ====================

    /**
     * Peers we have just asked the daemon to dial for a conference, lower-cased raw ids. The
     * daemon's `createConfFromParticipantList` returns no call ids, so the calls it places first
     * reach us as `callStateChanged` for ids we have never seen — and that path assumes a
     * RINGING call it does not know is *incoming*. Without this set our own outgoing calls would
     * be shown on the incoming side.
     */
    private val pendingConferenceDials = mutableSetOf<String>()

    /**
     * Set with [pendingConferenceDials] and consumed by the first of those calls to appear, so the
     * UI opens on a real call we placed. The conference itself is attached a moment later, and the
     * call screen picks that up through the same path as adding a participant to an ongoing call.
     */
    private var conferenceNavPending = false

    /**
     * Dial [peerUris] at once and join them into one conference.
     *
     * The daemon refuses fewer than two participants outright
     * (`Manager::createConfFromParticipantList` logs and returns), and only attaches the
     * conference if at least two calls were created — with one reachable peer out of three you
     * are left holding ordinary 1:1 calls, not a conference. Returns false when the request was
     * not even sent.
     */
    suspend fun createConferenceWith(accountId: String, peerUris: List<Uri>): Boolean {
        if (peerUris.size < 2) {
            Log.w(TAG, "createConferenceWith: need at least two participants, got ${peerUris.size}")
            return false
        }
        mutex.withLock {
            peerUris.forEach { pendingConferenceDials.add(it.rawRingId.lowercase()) }
            conferenceNavPending = true
        }
        // "<peer uri>,<account id>" — the daemon splits on the first comma (manager.cpp).
        val participants = peerUris.map { "${it.rawRingId},$accountId" }
        Log.d(TAG, "createConferenceWith: $participants")
        daemonBridge.createConfFromParticipantList(accountId, participants)
        return true
    }

    /**
     * Whether a call to [peerUri] that we did not place ourselves is in fact one of our own
     * conference dials. Consumes the entry: the daemon places exactly one call per participant.
     */
    private fun claimConferenceDial(peerUri: String): Boolean =
        pendingConferenceDials.remove(Uri.fromString(peerUri).rawRingId.lowercase())

    // ==================== Microphone level ====================

    private val _localAudioLevel = MutableStateFlow(0f)

    /**
     * RMS level of the microphone, 0..1, while [setMicrophoneMeterEnabled] is on; 0 otherwise.
     * The daemon emits roughly ten values a second, so collectors should not do heavy work per
     * value. Only the capture buffer is metered — a per-participant level would mean metering
     * each call's own ring buffer as well.
     */
    val localAudioLevel: StateFlow<Float> = _localAudioLevel.asStateFlow()

    private var meterEnabled = false

    /**
     * Turn microphone metering on while something is showing it (the call screen), off when it
     * goes away. The daemon computes RMS on every captured frame while this is on, so it is not
     * left running in the background.
     */
    fun setMicrophoneMeterEnabled(enabled: Boolean) {
        if (meterEnabled == enabled) return
        meterEnabled = enabled
        Log.d(TAG, "setMicrophoneMeterEnabled: $enabled")
        daemonBridge.setAudioMeterState(AudioMeter.CAPTURE_ID, enabled)
        if (!enabled) _localAudioLevel.value = 0f
    }

    /** Daemon callback: one RMS sample for ring buffer [id]. */
    internal fun onAudioMeter(id: String, level: Float) {
        if (id == AudioMeter.CAPTURE_ID) _localAudioLevel.value = level.coerceIn(0f, 1f)
    }

    // ==================== Call Operations ====================

    /**
     * Place a new outgoing call.
     */
    suspend fun placeCall(
        accountId: String,
        contactUri: Uri,
        hasVideo: Boolean,
        conversationUri: Uri? = null
    ): Call {
        Log.d(TAG, "placeCall: accountId=$accountId contactUri=${contactUri.uri} scheme=${contactUri.scheme} hasVideo=$hasVideo conversationUri=${conversationUri?.uri}")
        val mediaList = if (hasVideo) {
            listOf(Media.DEFAULT_AUDIO, Media.DEFAULT_VIDEO)
        } else {
            listOf(Media.DEFAULT_AUDIO)
        }

        val mediaAttributes = mediaList.map { media ->
            net.jami.model.MediaAttribute(
                mediaType = if (media.mediaType == Media.MediaType.MEDIA_TYPE_AUDIO)
                    net.jami.model.MediaAttribute.MediaType.AUDIO
                else
                    net.jami.model.MediaAttribute.MediaType.VIDEO,
                label = media.label ?: "",
                enabled = media.isEnabled,
                muted = media.isMuted,
                source = media.source ?: ""
            )
        }

        val callId = daemonBridge.placeCall(accountId, contactUri.uri, mediaAttributes)
        Log.d(TAG, "placeCall: daemon returned callId='$callId'")

        return if (callId.isEmpty()) {
            // For swarm calls, may return empty ID initially
            if (contactUri.isSwarm && conversationUri?.isSwarm == true) {
                Call(accountId, null, contactUri, false, conversationUri).apply {
                    setMediaList(mediaList)
                }
            } else {
                throw IllegalStateException("Failed to place call - empty call ID")
            }
        } else {
            addCall(accountId, callId, contactUri, Direction.OUTGOING, mediaList, conversationUri)
        }
    }

    /**
     * Accept an incoming call.
     */
    fun accept(accountId: String, callId: String, hasVideo: Boolean = false) {
        scope.launch {
            val call = calls[callId] ?: calls.values.firstOrNull { it.confId == callId }
            val mediaList = call?.mediaList?.map { media ->
                net.jami.model.MediaAttribute(
                    mediaType = if (media.mediaType == Media.MediaType.MEDIA_TYPE_AUDIO)
                        net.jami.model.MediaAttribute.MediaType.AUDIO
                    else
                        net.jami.model.MediaAttribute.MediaType.VIDEO,
                    label = media.label ?: "",
                    enabled = media.isEnabled,
                    muted = if (!hasVideo && media.mediaType == Media.MediaType.MEDIA_TYPE_VIDEO)
                        true
                    else
                        media.isMuted,
                    source = media.source ?: ""
                )
            } ?: emptyList()  // daemon will use its own defaults if media list is empty
            daemonBridge.accept(accountId, callId, mediaList)
        }
    }

    /**
     * Refuse an incoming call.
     */
    fun refuse(accountId: String, callId: String) {
        scope.launch {
            // Mirror jami-android-client: refuse() for ringing calls, hangUp() as fallback.
            // refuse() sends a SIP 486 Busy / Decline to the caller for calls not yet accepted.
            // hangUp() terminates calls that are already active or in negotiation.
            daemonBridge.refuse(accountId, callId)
            daemonBridge.hangUp(accountId, callId)
        }
    }

    /**
     * Hang up an active call.
     */
    fun hangUp(accountId: String, callId: String) {
        scope.launch {
            daemonBridge.hangUp(accountId, callId)
        }
    }

    /**
     * Put a call on hold.
     */
    fun hold(accountId: String, callId: String) {
        scope.launch {
            daemonBridge.hold(accountId, callId)
        }
    }

    /**
     * Resume a held call.
     */
    fun unhold(accountId: String, callId: String) {
        scope.launch {
            daemonBridge.resume(accountId, callId)
        }
    }

    /**
     * Mute or unmute local media (audio/video).
     */
    fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {
        scope.launch {
            daemonBridge.muteLocalMedia(accountId, callId, mediaType, mute)
        }
    }

    fun restartAudioLayer() {
        scope.launch { daemonBridge.restartAudioLayer() }
    }

    /**
     * Change the video source for an active call via media renegotiation.
     * Used for screen sharing: sets source to "camera://desktop" or back to a camera URI.
     */
    fun replaceVideoMedia(accountId: String, callId: String, uri: String) {
        val call = getCall(callId) ?: return
        var videoExists = false
        val newMediaList = call.mediaList.map { media ->
            when (media.mediaType) {
                Media.MediaType.MEDIA_TYPE_VIDEO -> {
                    videoExists = true
                    media.copy(source = uri, isMuted = false)
                }
                Media.MediaType.MEDIA_TYPE_AUDIO ->
                    // The media list entry is not updated by muteLocalMedia; only call.isAudioMuted
                    // is kept current by onAudioMuted callbacks. Use that authoritative flag so the
                    // renegotiation doesn't inadvertently unmute audio that was already muted.
                    media.copy(isMuted = call.isAudioMuted)
                else -> media
            }
        }.toMutableList()
        if (!videoExists) {
            newMediaList.add(Media.DEFAULT_VIDEO.copy(source = uri))
        }
        daemonBridge.requestMediaChange(accountId, callId, newMediaList.map { it.toMap() })
    }

    /**
     * Hold or unhold a call or conference.
     */
    fun holdCallOrConference(conf: Conference) {
        if (conf.isSimpleCall) {
            hold(conf.accountId, conf.id)
        } else {
            scope.launch {
                daemonBridge.holdConference(conf.accountId, conf.id)
            }
        }
    }

    fun unholdCallOrConference(conf: Conference) {
        if (conf.isSimpleCall) {
            unhold(conf.accountId, conf.id)
        } else {
            scope.launch {
                daemonBridge.resumeConference(conf.accountId, conf.id)
            }
        }
    }

    fun setActiveParticipant(accountId: String, confId: String, callId: String) {
        scope.launch {
            daemonBridge.setActiveParticipant(accountId, confId, callId)
        }
    }

    fun setConferenceLayout(accountId: String, confId: String, layout: Int) {
        scope.launch {
            daemonBridge.setConferenceLayout(accountId, confId, layout)
        }
    }

    fun playDtmf(key: String) {
        daemonBridge.playDtmf(key)
    }

    fun muteRingtone(mute: Boolean) {
        daemonBridge.muteRingtone(mute)
    }

    fun transfer(accountId: String, callId: String, to: String) {
        scope.launch { daemonBridge.transfer(accountId, callId, to) }
    }

    /**
     * Toggle recording of the active call. The daemon captures, mixes, and writes the file to
     * the account's recording path; the authoritative recording state arrives back via
     * [onRecordingStateChanged].
     */
    fun toggleRecording(accountId: String, callId: String) {
        scope.launch { daemonBridge.toggleRecording(accountId, callId) }
    }

    fun attendedTransfer(accountId: String, transferId: String, targetId: String) {
        scope.launch { daemonBridge.attendedTransfer(accountId, transferId, targetId) }
    }

    fun hangUpParticipant(accountId: String, callId: String) {
        scope.launch { daemonBridge.detachParticipant(accountId, callId) }
    }

    fun hangUpConference(accountId: String, confId: String) {
        scope.launch { daemonBridge.hangUpConference(accountId, confId) }
    }

    fun joinParticipant(accountId: String, selCallId: String, account2Id: String, dragCallId: String) {
        scope.launch { daemonBridge.joinParticipant(accountId, selCallId, account2Id, dragCallId) }
    }

    fun addParticipant(accountId: String, callId: String, account2Id: String, confId: String) {
        scope.launch { daemonBridge.addParticipant(accountId, callId, account2Id, confId) }
    }

    /**
     * Mute every participant of a conference (moderator only).
     *
     * libjami has no "mute all" primitive, so this mutes each participant stream in turn.
     * Falls back to the coarse bridge call only when no conference info is known yet.
     */
    fun muteAllParticipants(accountId: String, confId: String) {
        scope.launch {
            val participants = conferences[confId]?.participantInfo.orEmpty()
            if (participants.isEmpty()) {
                Log.w(TAG, "muteAllParticipants: no participant info for $confId")
                daemonBridge.muteAllParticipants(accountId, confId)
                return@launch
            }
            participants.forEach { info ->
                daemonBridge.muteStream(
                    accountId, confId,
                    info.contact.uri.rawRingId, info.device ?: "", "", true
                )
            }
        }
    }

    /**
     * Mute or unmute one participant's audio stream (moderator only).
     *
     * The daemon resolves the target call by (peerUri, deviceId), so pass the device ID
     * reported in the conference info; see Conference.ParticipantInfo.device.
     */
    fun muteParticipantStream(
        accountId: String,
        confId: String,
        peerUri: String,
        deviceId: String,
        muted: Boolean,
        streamId: String = ""
    ) {
        scope.launch {
            daemonBridge.muteStream(accountId, confId, peerUri, deviceId, streamId, muted)
        }
    }

    /**
     * Focus (or unfocus) a participant's video stream in the conference mixer.
     * Modern replacement for [setActiveParticipant], which only takes a call ID.
     */
    fun setActiveStream(
        accountId: String,
        confId: String,
        peerUri: String,
        deviceId: String,
        streamId: String,
        active: Boolean
    ) {
        scope.launch {
            daemonBridge.setActiveStream(accountId, confId, peerUri, deviceId, streamId, active)
        }
    }

    /**
     * Eject one participant from a conference (moderator only).
     */
    fun hangupParticipant(accountId: String, confId: String, peerUri: String, deviceId: String = "") {
        scope.launch {
            daemonBridge.hangupParticipant(accountId, confId, peerUri, deviceId)
        }
    }

    /**
     * Raise or lower a hand in a conference. Participants may only raise their own hand.
     */
    fun raiseHand(accountId: String, confId: String, peerUri: String, deviceId: String, raised: Boolean) {
        scope.launch {
            daemonBridge.raiseHand(accountId, confId, peerUri, deviceId, raised)
        }
    }

    /**
     * Grant or revoke moderator rights for a participant (moderator only).
     */
    fun setModerator(accountId: String, confId: String, peerUri: String, moderator: Boolean) {
        scope.launch {
            daemonBridge.setModerator(accountId, confId, peerUri, moderator)
        }
    }

    /**
     * Remove a participant from a conference.
     */
    fun removeParticipant(accountId: String, confId: String, participantId: String) {
        scope.launch {
            daemonBridge.detachParticipant(accountId, participantId)
        }
    }

    /**
     * Lock or unlock a conference (moderator only).
     */
    fun setConferenceLocked(accountId: String, confId: String, locked: Boolean) {
        scope.launch {
            daemonBridge.setConferenceLocked(accountId, confId, locked)
        }
    }

    /**
     * Mute or unmute a specific participant's audio (moderator only).
     */
    fun muteParticipantAudio(accountId: String, confId: String, participantId: String) {
        scope.launch {
            daemonBridge.muteParticipantAudio(accountId, confId, participantId)
        }
    }

    /**
     * Unmute a participant's audio (moderator only).
     */
    fun unmuteParticipantAudio(accountId: String, confId: String, participantId: String) {
        scope.launch {
            daemonBridge.unmuteParticipantAudio(accountId, confId, participantId)
        }
    }

    /**
     * Disable a participant's video (moderator only).
     */
    fun disableParticipantVideo(accountId: String, confId: String, participantId: String) {
        scope.launch {
            daemonBridge.disableParticipantVideo(accountId, confId, participantId)
        }
    }

    /**
     * Enable a participant's video (moderator only).
     */
    fun enableParticipantVideo(accountId: String, confId: String, participantId: String) {
        scope.launch {
            daemonBridge.enableParticipantVideo(accountId, confId, participantId)
        }
    }

    /**
     * Returns a flow of Conference updates filtered to a specific call/conference id.
     * Mirrors Android's getConfUpdates(call) reactive chain.
     */
    fun getConfUpdates(callOrConfId: String): kotlinx.coroutines.flow.Flow<Conference> =
        conferenceUpdates.filter { conf ->
            conf.id == callOrConfId ||
            conf.participants.any { it.daemonId == callOrConfId }
        }

    // ==================== Call State ====================

    /**
     * Get a call by its ID.
     */
    fun getCall(callId: String): Call? =
        calls[callId] ?: calls.values.firstOrNull { it.confId == callId }

    /**
     * Get a conference by its ID.
     */
    fun getConference(confId: String): Conference? = conferences[confId]

    /**
     * Get calls for a specific account.
     */
    fun getCallsForAccount(accountId: String): List<Call> =
        calls.values.filter { it.account == accountId }

    /**
     * Get active (ongoing) calls.
     */
    fun getActiveCalls(): List<Call> =
        calls.values.filter { it.callStatus.isOnGoing }

    /**
     * Get current conferences (with CURRENT state).
     */
    fun getCurrentConferences(): List<Conference> =
        conferences.values.filter { it.state == CallStatus.CURRENT }

    // ==================== Daemon Callbacks ====================

    /**
     * Called when call state changes.
     */
    internal fun onCallStateChanged(
        accountId: String,
        callId: String,
        stateStr: String,
        detailCode: Int
    ) {
        Log.d(TAG, "onCallStateChanged: callId=$callId state=$stateStr code=$detailCode")
        scope.launch {
            mutex.withLock {
                val callState = CallStatus.fromString(stateStr)

                // Get or create call
                var call = calls[callId]
                if (call != null) {
                    call.setCallState(callState)
                } else if (callState != CallStatus.OVER && callState != CallStatus.FAILURE) {
                    // New call - create it.
                    // RINGING without an existing call is always an incoming call: outgoing calls are
                    // added to `calls` by placeCall() before any callStateChanged fires, so they already
                    // exist here. Mark RINGING-created calls as INCOMING so the UI shows the right side.
                    // …except for the calls createConfFromParticipantList places on our behalf:
                    // those are ours, and the daemon hands us no ids to pre-register them with.
                    val peerNumber = daemonBridge.getCallDetails(accountId, callId)[Call.KEY_PEER_NUMBER]
                    val isOurConferenceDial = peerNumber != null && claimConferenceDial(peerNumber)
                    if (isOurConferenceDial && conferenceNavPending) {
                        conferenceNavPending = false
                        _pendingCallNavId.value = callId
                    }
                    val callType = if (callState == CallStatus.RINGING && !isOurConferenceDial)
                        Call.Direction.INCOMING.value else Call.Direction.OUTGOING.value
                    call = Call(callId, mapOf(
                        Call.KEY_ACCOUNT_ID to accountId,
                        Call.KEY_CALL_STATE to stateStr,
                        Call.KEY_CALL_TYPE to callType.toString(),
                        Call.KEY_PEER_NUMBER to (peerNumber ?: ""),
                    ))
                    call.setCallState(callState)
                    calls[callId] = call
                }

                if (call != null) {
                    _callUpdates.emit(call)
                    updateCurrentCalls()

                    if (call.callStatus == CallStatus.OVER) {
                        calls.remove(call.daemonId)
                        conferences.remove(call.daemonId)
                        call.daemonId?.let { clearInCallMessages(it) }
                        updateCurrentCalls()
                    }
                }
            }
        }
    }

    /**
     * Called when an incoming call is received.
     * Automatically accepts if auto-answer is enabled in call settings.
     */
    internal fun onIncomingCall(
        accountId: String,
        callId: String,
        from: String,
        mediaList: List<Map<String, String>>
    ) {
        scope.launch {
            mutex.withLock {
                val medias = mediaList.map { Media(it) }
                val (peerUri, _) = Uri.fromStringWithName(from)
                val call = addCall(accountId, callId, peerUri, Direction.INCOMING, medias)
                _callUpdates.emit(call)
                updateCurrentCalls()
            }
            // Auto-answer outside the lock to allow the accept() call to also acquire it
            val callSettings = settingsRepository.callSettings.value
            if (callSettings.autoAnswer) {
                if (callSettings.autoAnswerDelay > 0) {
                    kotlinx.coroutines.delay(callSettings.autoAnswerDelay * 1000L)
                }
                accept(accountId, callId, callSettings.videoEnabled)
            }
        }
    }

    /**
     * Called when audio mute state changes.
     */
    internal fun onAudioMuted(callId: String, muted: Boolean) {
        scope.launch {
            calls[callId]?.let { call ->
                call.isAudioMuted = muted
                if (call.callStatus == CallStatus.CURRENT) {
                    _callUpdates.emit(call)
                }
            }
            conferences[callId]?.let { conf ->
                conf.isAudioMuted = muted
                _conferenceUpdates.emit(conf)
            }
        }
    }

    /**
     * Called when video mute state changes.
     */
    internal fun onVideoMuted(callId: String, muted: Boolean) {
        scope.launch {
            calls[callId]?.let { call ->
                call.isVideoMuted = muted
                if (call.callStatus == CallStatus.CURRENT) {
                    _callUpdates.emit(call)
                }
            }
            conferences[callId]?.let { conf ->
                conf.isVideoMuted = muted
                _conferenceUpdates.emit(conf)
            }
        }
    }

    /**
     * Called when the daemon's recording state for a call changes (local or remote initiated).
     */
    internal fun onRecordingStateChanged(callId: String, recording: Boolean) {
        scope.launch {
            calls[callId]?.let { call ->
                call.isRecording = recording
                if (call.callStatus == CallStatus.CURRENT) {
                    _callUpdates.emit(call)
                }
            }
            conferences[callId]?.let { conf ->
                _conferenceUpdates.emit(conf)
            }
        }
    }

    /**
     * Called when the peer puts the call on hold, or resumes it.
     *
     * The daemon reports this separately from `callStateChanged`: a peer-side hold keeps the
     * call CURRENT, so without this signal the UI cannot tell that media stopped flowing.
     */
    internal fun onPeerHold(callId: String, holding: Boolean) {
        scope.launch {
            calls[callId]?.let { call ->
                call.isPeerHolding = holding
                _callUpdates.emit(call)
                // A held leg inside a conference also refreshes the conference view.
                conferences[call.confId ?: ""]?.let { _conferenceUpdates.emit(it) }
            }
            conferences[callId]?.let { conf ->
                _conferenceUpdates.emit(conf)
            }
        }
    }

    /**
     * ICE/connection state update for a call or peer connection (0 = connected, 1/2 = closed).
     * Logged only: the daemon drives the authoritative call state through `callStateChanged`.
     */
    internal fun onConnectionUpdate(id: String, state: Int) {
        Log.d(TAG, "connectionUpdate: $id state=$state")
    }

    /**
     * A remote participant started or stopped recording the call.
     * Ports jami-client-android CallService.remoteRecordingChanged (:151-168).
     */
    internal fun onRemoteRecordingChanged(callId: String, peerUri: String, recording: Boolean) {
        scope.launch {
            val conference = conferences[callId]
                ?: calls[callId]?.let { call -> conferences[call.confId ?: call.daemonId ?: ""] }
            val call = conference?.firstCall ?: calls[callId]
            val account = call?.account?.let { accountService.getAccount(it) }
            val contact = account?.getContactFromCache(Uri.fromString(peerUri))
            if (conference == null || contact == null) {
                Log.w(TAG, "remoteRecordingChanged: no conference/contact for $callId $peerUri")
                return@launch
            }
            conference.setParticipantRecording(contact, recording)
            _conferenceUpdates.emit(conference)
        }
    }

    /**
     * Called when media negotiation completes.
     */
    internal fun onMediaNegotiationStatus(callId: String, event: String, mediaList: List<Map<String, String>>) {
        scope.launch {
            mutex.withLock {
                val medias = mediaList.map { Media(it) }
                calls[callId]?.let { call ->
                    call.setMediaList(medias)
                    _callUpdates.emit(call)
                }
            }
        }
    }

    /**
     * Called when the peer (or the daemon) requests a media change.
     *
     * Ports jami-client-android CallService.mediaChangeRequested (:609-623):
     * - If the call has no active video stream, mark all video entries as muted
     *   (we have no video to offer, so we refuse new video).
     * - Mirror the current audio-mute state into every audio entry.
     * - Then answer the request so the daemon can complete the renegotiation.
     */
    internal fun mediaChangeRequested(accountId: String, callId: String, mediaList: List<Map<String, String>>) {
        scope.launch {
            mutex.withLock {
                val call = calls[callId] ?: return@withLock
                val hasActiveVideo = call.mediaList.any {
                    it.mediaType == Media.MediaType.MEDIA_TYPE_VIDEO && !it.isMuted
                }
                val updatedList = mediaList.map { entry ->
                    val type = Media.MediaType.parseMediaType(entry[Media.MEDIA_TYPE_KEY] ?: "")
                    val mutable = entry.toMutableMap()
                    when (type) {
                        Media.MediaType.MEDIA_TYPE_VIDEO ->
                            mutable[Media.MUTED_KEY] = (!hasActiveVideo).toString()
                        Media.MediaType.MEDIA_TYPE_AUDIO ->
                            mutable[Media.MUTED_KEY] = call.isAudioMuted.toString()
                        null -> { }
                    }
                    mutable.toMap()
                }
                daemonBridge.answerMediaChangeRequest(accountId, callId, updatedList)
            }
        }
    }

    /**
     * Called when a conference is created.
     *
     * Ports jami-client-android CallService.conferenceCreated (:731-755):
     * fetch participant list and details from the daemon, update conference state,
     * and move each participant call from the top-level calls map into the conference.
     */
    internal fun onConferenceCreated(accountId: String, conversationId: String, confId: String) {
        scope.launch {
            mutex.withLock {
                val conf = conferences.getOrPut(confId) {
                    Conference(accountId, confId).apply {
                        if (conversationId.isNotEmpty()) this.conversationId = conversationId
                    }
                }
                val details = daemonBridge.getConferenceDetails(accountId, confId)
                details["STATE"]?.let { conf.setState(it) }
                val participants = daemonBridge.getParticipantList(accountId, confId)
                for (callId in participants) {
                    val call = calls[callId] ?: continue
                    call.confId = confId
                    if (!conf.contains(callId)) conf.addParticipant(call)
                    if (callId != confId) conferences.remove(callId)
                }
                _conferenceUpdates.emit(conf)
                updateCurrentConferences()
            }
        }
    }

    /**
     * Called when a conference state changes.
     */
    internal fun onConferenceChanged(accountId: String, confId: String, state: String) {
        scope.launch {
            mutex.withLock {
                val conf = conferences.getOrPut(confId) { Conference(accountId, confId) }
                conf.setState(state)
                val participants = daemonBridge.getParticipantList(accountId, confId).toSet()
                for (callId in participants) {
                    val call = calls[callId] ?: continue
                    call.confId = confId
                    if (!conf.contains(callId)) conf.addParticipant(call)
                    if (callId != confId) conferences.remove(callId)
                }
                val toRemove = conf.participants.filter { it.daemonId !in participants }
                for (call in toRemove) { call.confId = null; conf.removeParticipant(call) }
                val remaining = conf.participants
                if (remaining.size == 1) {
                    val lastCall = remaining.first()
                    lastCall.confId = null
                    conferences.remove(confId)
                    _callUpdates.emit(lastCall)
                    updateCurrentCalls()
                    updateCurrentConferences()
                    return@withLock
                }
                _conferenceUpdates.emit(conf)
                updateCurrentConferences()
            }
        }
    }

    /**
     * Called when a conference is removed.
     */
    internal fun onConferenceRemoved(accountId: String, confId: String) {
        scope.launch {
            mutex.withLock {
                conferences.remove(confId)?.let { conf ->
                    conf.removeParticipants()
                    conf.hostCall?.let { hostCall ->
                        hostCall.setCallState(CallStatus.OVER)
                        _callUpdates.emit(hostCall)
                    }
                    _conferenceUpdates.emit(conf)
                }
                updateCurrentConferences()
            }
        }
    }

    /**
     * Called when conference info is updated (participants, layout, etc.).
     */
    internal fun onConferenceInfoUpdated(confId: String, info: List<Map<String, String>>) {
        scope.launch {
            mutex.withLock {
                val conference = conferences[confId] ?: return@withLock
                val account = accountService.getAccount(conference.accountId) ?: return@withLock
                var isModerator = false
                val newInfo = info.mapNotNull { i ->
                    val uriStr = i["uri"] ?: ""
                    val contactUri = if (uriStr.isEmpty()) Uri.fromId(account.username)
                                     else Uri.fromString(uriStr)
                    val call = conference.findCallByContact(contactUri)
                    val contact = account.getContactFromCache(contactUri)
                    val confInfo = Conference.ParticipantInfo(call, contact, i)
                    if (confInfo.isEmpty) return@mapNotNull null
                    if (contact.uri.rawRingId == account.username && confInfo.isModerator) isModerator = true
                    confInfo
                }
                conference.isModerator = isModerator
                conference.setInfo(newInfo)
                _conferenceUpdates.emit(conference)
            }
        }
    }

    // ==================== Internal Helpers ====================

    private fun addCall(
        accountId: String,
        callId: String,
        peerUri: Uri,
        direction: Direction,
        mediaList: List<Media>,
        conversationUri: Uri? = null
    ): Call {
        val existing = calls[callId]
        // If callStateChanged created the call first (race) it has an empty peerUri and may have
        // wrong direction. Replace it so onIncomingCall's richer info wins.
        val call = if (existing != null && !existing.peerUri.isEmpty) {
            existing
        } else {
            Call(
                daemonId = callId,
                account = accountId,
                contactNumber = peerUri.uri,
                direction = direction
            ).also { newCall ->
                // Carry over the call status already accumulated by onCallStateChanged callbacks
                // that fired before onIncomingCall (e.g. CONNECTING → RINGING on incoming).
                // Without this the new INCOMING call starts as NONE and updateAudioState never
                // sees RINGING+isIncoming=true, so the ringtone is never played.
                existing?.callStatus?.let { newCall.setCallState(it) }
                calls[callId] = newCall
            }
        }
        call.setMediaList(mediaList)
        return call
    }

    private fun updateCurrentCalls() {
        _currentCalls.value = calls.values.toList()
    }

    private fun updateCurrentConferences() {
        _currentConferences.value = conferences.values.toList()
    }

    // ==================== Messaging ====================

    /**
     * Send a text message in a call/conference.
     */
    fun sendTextMessage(accountId: String, callId: String, message: String) {
        scope.launch {
            daemonBridge.sendTextMessage(accountId, callId, message)
        }
    }

    /**
     * Send a message inside an ongoing call or conference.
     *
     * [sendTextMessage] cannot do this: it routes to the account-level send, which addresses a
     * contact URI rather than a call, so passing a call ID there sent the message nowhere useful.
     *
     * @param from Our own URI, recorded as the author by the daemon and echoed to peers.
     */
    fun sendInCallMessage(accountId: String, callId: String, from: String, message: String) {
        val conference = conferences[callId]
        val isMixed = conference?.isConference == true
        scope.launch {
            daemonBridge.sendCallTextMessage(
                accountId, callId, mapOf(MIME_TEXT_PLAIN to message), from, isMixed
            )
            // The daemon does not echo our own in-call messages back, so record it locally.
            appendInCallMessage(
                InCallMessage(
                    callId = callId,
                    author = from,
                    body = message,
                    timestamp = currentTimeMillis(),
                    isOutgoing = true,
                )
            )
        }
    }

    /**
     * Called when a text message arrives inside a call or conference.
     * Only `text/plain` bodies are surfaced; other MIME parts are ignored.
     */
    internal fun onInCallMessage(accountId: String, callId: String, from: String, messages: Map<String, String>) {
        val body = messages[MIME_TEXT_PLAIN] ?: messages.values.firstOrNull() ?: return
        Log.d(TAG, "in-call message on $callId from $from")
        appendInCallMessage(
            InCallMessage(
                callId = callId,
                author = from,
                body = body,
                timestamp = currentTimeMillis(),
                isOutgoing = false,
            )
        )
    }

    private fun appendInCallMessage(message: InCallMessage) {
        // A call's chat is bounded: it exists only for the duration of the call, and the UI shows
        // the tail of it.
        val existing = _inCallMessages.value
        val trimmed = if (existing.size >= MAX_IN_CALL_MESSAGES) {
            existing.takeLast(MAX_IN_CALL_MESSAGES - 1)
        } else existing
        _inCallMessages.value = trimmed + message
    }

    /** Drop the chat history for a call that has ended. */
    private fun clearInCallMessages(callId: String) {
        val remaining = _inCallMessages.value.filterNot { it.callId == callId }
        if (remaining.size != _inCallMessages.value.size) {
            _inCallMessages.value = remaining
        }
    }

    /**
     * Live media details for a call, straight from the daemon: negotiated codecs, sample rate,
     * bitrates and socket info. Keys come from libjami's `call_const.h` Details namespace.
     */
    fun getCallDetails(accountId: String, callId: String): Map<String, String> =
        daemonBridge.getCallDetails(accountId, callId)

    /**
     * Set typing/composing status.
     */
    fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        scope.launch {
            daemonBridge.setIsComposing(accountId, uri, isComposing)
        }
    }

    companion object {
        private const val TAG = "CallService"

        /** Upper bound on the in-call chat kept in memory for a call. */
        private const val MAX_IN_CALL_MESSAGES = 200
        const val MIME_TEXT_PLAIN = "text/plain"
        const val MIME_GEOLOCATION = "application/geo"
        const val MEDIA_TYPE_AUDIO = "MEDIA_TYPE_AUDIO"
        const val MEDIA_TYPE_VIDEO = "MEDIA_TYPE_VIDEO"
    }
}

/**
 * Events emitted by CallService.
 */
sealed class CallEvent {
    data class StateChanged(val call: Call, val previousState: CallStatus) : CallEvent()
    data class IncomingCall(val call: Call) : CallEvent()
    data class MediaChanged(val call: Call) : CallEvent()
    data class ConferenceCreated(val conference: Conference) : CallEvent()
    data class ConferenceChanged(val conference: Conference) : CallEvent()
    data class ConferenceRemoved(val conferenceId: String) : CallEvent()
}
