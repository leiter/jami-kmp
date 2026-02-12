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
import net.jami.model.Uri

/**
 * Service for managing calls and conferences.
 *
 * Provides call operations (place, accept, refuse, hangup, hold) and
 * emits call state updates via Kotlin Flow.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes: RxJava → Kotlin Flow, JamiService → DaemonBridge
 */
class CallService(
    private val daemonBridge: DaemonBridge,
    private val accountService: AccountService,
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
            val call = calls[callId] ?: return@launch
            val mediaList = call.mediaList.map { media ->
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
            }
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
            // TODO: holdConference via DaemonBridge
        }
    }

    fun unholdCallOrConference(conf: Conference) {
        if (conf.isSimpleCall) {
            unhold(conf.accountId, conf.id)
        } else {
            // TODO: unholdConference via DaemonBridge
        }
    }

    // ==================== Call State ====================

    /**
     * Get a call by its ID.
     */
    fun getCall(callId: String): Call? = calls[callId]

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
        scope.launch {
            val callState = CallStatus.fromString(stateStr)

            // Get or create call
            var call = calls[callId]
            if (call != null) {
                call.setCallState(callState)
            } else if (callState != CallStatus.OVER && callState != CallStatus.FAILURE) {
                // New call - create it
                call = Call(callId, mapOf(
                    Call.KEY_ACCOUNT_ID to accountId,
                    Call.KEY_CALL_STATE to stateStr
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

    // ==================== Internal Helpers ====================

    private fun addCall(
        accountId: String,
        callId: String,
        peerUri: Uri,
        direction: Direction,
        mediaList: List<Media>,
        conversationUri: Uri? = null
    ): Call {
        val call = calls.getOrPut(callId) {
            Call(
                daemonId = callId,
                account = accountId,
                contactNumber = peerUri.uri,
                direction = direction
            )
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
