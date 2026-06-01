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
import net.jami.model.Call
import net.jami.model.Call.CallStatus
import net.jami.model.Call.Direction
import net.jami.model.Conference
import net.jami.model.Media
import net.jami.utils.Log
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

    private val _currentConferences = MutableStateFlow<List<Conference>>(emptyList())
    val currentConferences: StateFlow<List<Conference>> = _currentConferences.asStateFlow()

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
            daemonBridge.unhold(accountId, callId)
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
                daemonBridge.unholdConference(conf.accountId, conf.id)
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

    // ==================== Video Quality Control ====================

    /**
     * Set video quality for a call.
     *
     * Updates camera resolution, frame rate, and bitrate constraints.
     */
    fun setVideoQuality(accountId: String, callId: String, quality: net.jami.model.VideoQuality) {
        scope.launch {
            daemonBridge.setVideoQuality(accountId, callId, quality.width, quality.height, quality.frameRate, quality.bitrateOptimal)
        }
    }

    /**
     * Set bitrate limit for video encoding.
     *
     * @param bitrate Maximum bitrate in kbps (0 = no limit)
     */
    fun setVideoBitrate(accountId: String, callId: String, bitrate: Int) {
        scope.launch {
            daemonBridge.setVideoBitrate(accountId, callId, bitrate)
        }
    }

    /**
     * Request current video stats (bitrate, resolution, framerate).
     *
     * Stats are returned via daemon callback onVideoStats().
     */
    fun requestVideoStats(accountId: String, callId: String) {
        scope.launch {
            daemonBridge.requestVideoStats(accountId, callId)
        }
    }

    /**
     * Mute all audio in a conference (moderator only).
     */
    fun muteAllParticipants(accountId: String, confId: String) {
        scope.launch {
            daemonBridge.muteAllParticipants(accountId, confId)
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
                val callType = if (callState == CallStatus.RINGING) Call.Direction.INCOMING.value else Call.Direction.OUTGOING.value
                call = Call(callId, mapOf(
                    Call.KEY_ACCOUNT_ID to accountId,
                    Call.KEY_CALL_STATE to stateStr,
                    Call.KEY_CALL_TYPE to callType.toString()
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
                    updateCurrentCalls()
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
            val medias = mediaList.map { Media(it) }
            val (peerUri, _) = Uri.fromStringWithName(from)
            val call = addCall(accountId, callId, peerUri, Direction.INCOMING, medias)
            _callUpdates.emit(call)
            updateCurrentCalls()

            // Auto-answer if enabled in settings
            val callSettings = settingsRepository.callSettings.value
            if (callSettings.autoAnswer) {
                // Delay auto-answer by configured seconds
                if (callSettings.autoAnswerDelay > 0) {
                    kotlinx.coroutines.delay(callSettings.autoAnswerDelay * 1000L)
                }
                // Accept with video based on video enabled setting
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
     * Called when media negotiation completes.
     */
    internal fun onMediaNegotiationStatus(callId: String, event: String, mediaList: List<Map<String, String>>) {
        scope.launch {
            val medias = mediaList.map { Media(it) }
            calls[callId]?.let { call ->
                call.setMediaList(medias)
                _callUpdates.emit(call)
            }
        }
    }

    /**
     * Called when a conference is created.
     */
    internal fun onConferenceCreated(accountId: String, conversationId: String, confId: String) {
        scope.launch {
            val conf = conferences.getOrPut(confId) {
                Conference(accountId, confId).apply {
                    if (conversationId.isNotEmpty()) {
                        this.conversationId = conversationId
                    }
                }
            }
            _conferenceUpdates.emit(conf)
            updateCurrentConferences()
        }
    }

    /**
     * Called when a conference state changes.
     */
    internal fun onConferenceChanged(accountId: String, confId: String, state: String) {
        scope.launch {
            val conf = conferences.getOrPut(confId) { Conference(accountId, confId) }
            conf.setState(state)
            _conferenceUpdates.emit(conf)
            updateCurrentConferences()
        }
    }

    /**
     * Called when a conference is removed.
     */
    internal fun onConferenceRemoved(accountId: String, confId: String) {
        scope.launch {
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

    /**
     * Called when conference info is updated (participants, layout, etc.).
     */
    internal fun onConferenceInfoUpdated(confId: String, info: List<Map<String, String>>) {
        scope.launch {
            val conference = conferences[confId] ?: return@launch
            val account = accountService.getAccount(conference.accountId) ?: return@launch
            var isModerator = false

            val newInfo = info.mapNotNull { i ->
                val uriStr = i["uri"] ?: ""
                val contactUri = if (uriStr.isEmpty()) {
                    Uri.fromId(account.username)
                } else {
                    Uri.fromString(uriStr)
                }

                val call = conference.findCallByContact(contactUri)
                val contact = account.getContactFromCache(contactUri)
                
                val confInfo = Conference.ParticipantInfo(call, contact, i)
                if (confInfo.isEmpty) return@mapNotNull null

                if (contact.uri.rawRingId == account.username && confInfo.isModerator) {
                    isModerator = true
                }
                confInfo
            }

            conference.isModerator = isModerator
            conference.setInfo(newInfo)
            _conferenceUpdates.emit(conference)
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
            ).also { calls[callId] = it }
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
     * Set typing/composing status.
     */
    fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        scope.launch {
            daemonBridge.setIsComposing(accountId, uri, isComposing)
        }
    }

    /**
     * Cancel a pending message.
     */
    suspend fun cancelMessage(accountId: String, messageId: Long): Boolean {
        return daemonBridge.cancelMessage(accountId, messageId)
    }

    companion object {
        private const val TAG = "CallService"
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
