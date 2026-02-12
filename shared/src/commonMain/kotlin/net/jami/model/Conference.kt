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
package net.jami.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a conference (multi-party call) in Jami.
 *
 * A conference can be:
 * - A simple 1:1 call (single participant)
 * - A multi-party conference (multiple participants)
 * - A swarm call (associated with a conversation)
 *
 * Ported from: jami-client-android libjamiclient
 */
class Conference(
    val accountId: String,
    val id: String
) {
    private val _participants = MutableStateFlow<List<Call>>(emptyList())
    val participantsFlow: StateFlow<List<Call>> = _participants.asStateFlow()

    private val _participantInfo = MutableStateFlow<List<ParticipantInfo>>(emptyList())
    val participantInfoFlow: StateFlow<List<ParticipantInfo>> = _participantInfo.asStateFlow()

    private val _state = MutableStateFlow<Call.CallStatus?>(null)
    val stateFlow: StateFlow<Call.CallStatus?> = _state.asStateFlow()

    var hostCall: Call? = null
    var conversationId: String? = null
    var isModerator: Boolean = false
    var isAudioMuted: Boolean = false
    var isVideoMuted: Boolean = false
    var maximizedParticipant: Contact? = null

    val participants: List<Call>
        get() = _participants.value

    val state: Call.CallStatus?
        get() = if (isSimpleCall) participants.firstOrNull()?.callStatus else _state.value

    val confState: Call.CallStatus?
        get() = if (participants.size == 1) participants[0].callStatus else _state.value

    val isSimpleCall: Boolean
        get() = participants.size == 1 && id == participants[0].daemonId

    val isConference: Boolean
        get() = participants.size > 1 || conversationId != null ||
                (participants.size == 1 && participants[0].daemonId != id)

    val isRinging: Boolean
        get() = participants.isNotEmpty() && participants[0].isRinging

    val isOnGoing: Boolean
        get() = state?.isOnGoing == true

    val call: Call?
        get() = if (!isConference) firstCall else null

    val firstCall: Call?
        get() = participants.firstOrNull()

    constructor(call: Call) : this(call.account, call.confId ?: call.daemonId!!) {
        addParticipant(call)
    }

    fun setState(state: String) {
        _state.value = Call.CallStatus.fromConferenceString(state)
    }

    fun addParticipant(call: Call) {
        _participants.value = _participants.value + call
    }

    fun removeParticipant(call: Call): Boolean {
        val current = _participants.value
        if (call in current) {
            _participants.value = current - call
            return true
        }
        return false
    }

    fun removeParticipants() {
        _participants.value = emptyList()
    }

    fun contains(callId: String): Boolean =
        participants.any { it.daemonId == callId }

    fun getCallById(callId: String): Call? =
        participants.firstOrNull { it.daemonId == callId }

    fun findCallByContact(uri: Uri): Call? =
        participants.find { it.peerUri == uri }

    fun setInfo(info: List<ParticipantInfo>) {
        _participantInfo.value = info
    }

    fun setParticipantRecording(contact: Contact, recording: Boolean) {
        // TODO: Track recording state per participant
    }

    /**
     * Information about a participant in a conference.
     */
    data class ParticipantInfo(
        val call: Call?,
        val contact: Contact,
        val x: Int = 0,
        val y: Int = 0,
        val w: Int = 0,
        val h: Int = 0,
        val videoMuted: Boolean = false,
        val audioModeratorMuted: Boolean = false,
        val audioLocalMuted: Boolean = false,
        val isModerator: Boolean = false,
        val isHandRaised: Boolean = false,
        val active: Boolean = false,
        val device: String? = null,
        val sinkId: String? = null,
        val pending: Boolean = false
    ) {
        constructor(call: Call?, contact: Contact, info: Map<String, String>, pending: Boolean = false) : this(
            call = call,
            contact = contact,
            x = info["x"]?.toIntOrNull() ?: 0,
            y = info["y"]?.toIntOrNull() ?: 0,
            w = info["w"]?.toIntOrNull() ?: 0,
            h = info["h"]?.toIntOrNull() ?: 0,
            videoMuted = info["videoMuted"]?.toBoolean() ?: false,
            audioModeratorMuted = info["audioModeratorMuted"]?.toBoolean() ?: false,
            audioLocalMuted = info["audioLocalMuted"]?.toBoolean() ?: false,
            isModerator = info["isModerator"]?.toBoolean() ?: false,
            isHandRaised = info["handRaised"]?.toBoolean() ?: false,
            active = info["active"]?.toBoolean() ?: false,
            device = info["device"],
            sinkId = info["sinkId"],
            pending = pending
        )

        val isEmpty: Boolean
            get() = x == 0 && y == 0 && w == 0 && h == 0

        val tag: String
            get() = sinkId ?: contact.uri.uri
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Conference) return false
        return id == other.id && accountId == other.accountId
    }

    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    override fun toString(): String =
        "Conference(id=$id, participants=${participants.size}, state=$state)"
}
