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
 * Represents an active or historical call in Jami.
 *
 * Handles call state machine, media streams, and call metadata.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes: RxJava → Kotlin Flow
 */
class Call(
    val account: String,
    val daemonId: String?,
    val peerUri: Uri,
    val isIncoming: Boolean,
    val conversationUri: Uri? = null
) {
    // Call state
    private val _callStatus = MutableStateFlow(CallStatus.NONE)
    val callStatusFlow: StateFlow<CallStatus> = _callStatus.asStateFlow()
    var callStatus: CallStatus
        get() = _callStatus.value
        private set(value) { _callStatus.value = value }

    // Media state
    private var isPeerHolding = false
    var isAudioMuted = false
    var isVideoMuted = false
    private var isRecording = false

    // Timestamps
    var timestamp: Long = 0
    var timestampEnd: Long = 0

    // Associated contact
    var contact: Contact? = null

    // Missed call tracking
    var isMissed = true
        private set

    // Codec information
    var audioCodec: String? = null
        private set
    var videoCodec: String? = null
        private set

    // Conference
    var confId: String? = null

    // Media list with Flow
    private val _mediaList = MutableStateFlow<List<Media>>(emptyList())
    val mediaListFlow: StateFlow<List<Media>> = _mediaList.asStateFlow()
    val mediaList: List<Media>
        get() = _mediaList.value

    fun setMediaList(media: List<Media>) {
        _mediaList.value = media.toList()
    }

    // Secondary constructors
    constructor(
        daemonId: String?,
        account: String,
        contact: Contact,
        direction: Direction,
        conversationUri: Uri? = null,
        timestampMs: Long = currentTimeMillis()
    ) : this(account, daemonId, contact.uri, direction == Direction.INCOMING, conversationUri) {
        timestamp = timestampMs
        this.contact = contact
    }

    constructor(
        daemonId: String?,
        account: String,
        contactNumber: String,
        direction: Direction,
        timestampMs: Long = currentTimeMillis()
    ) : this(account, daemonId, Uri.fromString(contactNumber), direction == Direction.INCOMING) {
        timestamp = timestampMs
    }

    constructor(daemonId: String?, callDetails: Map<String, String>) : this(
        daemonId = daemonId,
        account = callDetails[KEY_ACCOUNT_ID] ?: "",
        contactNumber = callDetails[KEY_PEER_NUMBER] ?: "",
        direction = Direction.fromInt(callDetails[KEY_CALL_TYPE]?.toIntOrNull() ?: 1),
        timestampMs = currentTimeMillis()
    ) {
        setCallState(CallStatus.fromString(callDetails[KEY_CALL_STATE] ?: ""))
        setDetails(callDetails)
    }

    fun setDetails(details: Map<String, String>) {
        isPeerHolding = details[KEY_PEER_HOLDING]?.toBoolean() ?: false
        isAudioMuted = details[KEY_AUDIO_MUTED]?.toBoolean() ?: false
        isVideoMuted = details[KEY_VIDEO_MUTED]?.toBoolean() ?: false
        audioCodec = details[KEY_AUDIO_CODEC]
        videoCodec = details[KEY_VIDEO_CODEC]
        confId = details[KEY_CONF_ID]?.ifEmpty { null }
    }

    val isConferenceParticipant: Boolean
        get() = confId != null

    val isGroupCall: Boolean
        get() = isConferenceParticipant

    fun setCallState(status: CallStatus) {
        callStatus = status
        if (status == CallStatus.CURRENT) {
            isMissed = false
        }
    }

    val isRinging: Boolean
        get() = callStatus.isRinging

    val isOnGoing: Boolean
        get() = callStatus.isOnGoing

    val isOver: Boolean
        get() = callStatus.isOver

    val duration: Long
        get() = if (timestampEnd > 0 && timestamp > 0) timestampEnd - timestamp else 0

    fun hasMedia(type: Media.MediaType): Boolean =
        mediaList.any { it.isEnabled && it.mediaType == type }

    fun hasActiveMedia(type: Media.MediaType): Boolean =
        mediaList.any { it.isEnabled && !it.isMuted && it.mediaType == type }

    fun hasActiveScreenSharing(): Boolean =
        mediaList.any { it.source == "camera://desktop" && it.isEnabled && !it.isMuted }

    fun hasVideo(): Boolean = hasMedia(Media.MediaType.MEDIA_TYPE_VIDEO)
    fun hasActiveVideo(): Boolean = hasActiveMedia(Media.MediaType.MEDIA_TYPE_VIDEO)
    fun hasAudio(): Boolean = hasMedia(Media.MediaType.MEDIA_TYPE_AUDIO)
    fun hasActiveAudio(): Boolean = hasActiveMedia(Media.MediaType.MEDIA_TYPE_AUDIO)

    /**
     * Call status state machine.
     */
    enum class CallStatus {
        NONE,
        SEARCHING,
        CONNECTING,
        RINGING,
        CURRENT,
        HUNGUP,
        BUSY,
        FAILURE,
        HOLD,
        INACTIVE,
        OVER;

        val isRinging: Boolean
            get() = this == CONNECTING || this == RINGING || this == NONE || this == SEARCHING

        val isOnGoing: Boolean
            get() = this == CURRENT || this == HOLD

        val isOver: Boolean
            get() = this == HUNGUP || this == BUSY || this == FAILURE || this == OVER

        companion object {
            fun fromString(state: String): CallStatus = when (state.uppercase()) {
                "SEARCHING" -> SEARCHING
                "CONNECTING" -> CONNECTING
                "INCOMING", "RINGING" -> RINGING
                "CURRENT" -> CURRENT
                "HUNGUP" -> HUNGUP
                "BUSY" -> BUSY
                "FAILURE" -> FAILURE
                "HOLD" -> HOLD
                "INACTIVE" -> INACTIVE
                "OVER" -> OVER
                "NONE" -> NONE
                else -> NONE
            }

            fun fromConferenceString(state: String): CallStatus = when (state) {
                "ACTIVE_ATTACHED" -> CURRENT
                "ACTIVE_DETACHED", "HOLD" -> HOLD
                else -> NONE
            }
        }
    }

    /**
     * Call direction (incoming or outgoing).
     */
    enum class Direction(val value: Int) {
        INCOMING(0),
        OUTGOING(1);

        companion object {
            fun fromInt(value: Int): Direction =
                if (value == INCOMING.value) INCOMING else OUTGOING
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Call) return false
        return daemonId == other.daemonId && account == other.account
    }

    override fun hashCode(): Int {
        var result = account.hashCode()
        result = 31 * result + (daemonId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "Call(id=$daemonId, peer=$peerUri, status=$callStatus, incoming=$isIncoming)"

    companion object {
        const val KEY_ACCOUNT_ID = "ACCOUNTID"
        const val KEY_HAS_VIDEO = "HAS_VIDEO"
        const val KEY_CALL_TYPE = "CALL_TYPE"
        const val KEY_CALL_STATE = "CALL_STATE"
        const val KEY_PEER_NUMBER = "PEER_NUMBER"
        const val KEY_PEER_HOLDING = "PEER_HOLDING"
        const val KEY_AUDIO_MUTED = "AUDIO_MUTED"
        const val KEY_VIDEO_MUTED = "VIDEO_MUTED"
        const val KEY_AUDIO_CODEC = "AUDIO_CODEC"
        const val KEY_VIDEO_CODEC = "VIDEO_CODEC"
        const val KEY_REGISTERED_NAME = "REGISTERED_NAME"
        const val KEY_DURATION = "duration"
        const val KEY_CONF_ID = "CONF_ID"
    }
}

/**
 * Platform-agnostic current time function.
 * Uses expect/actual for platform-specific implementation.
 */
internal expect fun currentTimeMillis(): Long
