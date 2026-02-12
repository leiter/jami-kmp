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

/**
 * Represents an audio or video codec.
 *
 * Ported from: jami-client-android libjamiclient
 */
data class Codec(
    val payload: Long,
    val name: String,
    val type: Type,
    val sampleRate: String? = null,
    val bitRate: String? = null,
    val channels: String? = null,
    var isEnabled: Boolean = false
) {
    enum class Type {
        AUDIO,
        VIDEO;

        companion object {
            fun fromString(value: String): Type = when (value.uppercase()) {
                "AUDIO" -> AUDIO
                "VIDEO" -> VIDEO
                else -> AUDIO
            }
        }
    }

    /**
     * Create a codec from daemon codec details map.
     */
    constructor(payload: Long, details: Map<String, String>, enabled: Boolean) : this(
        payload = payload,
        name = details["CodecInfo.name"] ?: "",
        type = Type.fromString(details["CodecInfo.type"] ?: "AUDIO"),
        sampleRate = details["CodecInfo.sampleRate"],
        bitRate = details["CodecInfo.bitrate"],
        channels = details["CodecInfo.channelNumber"],
        isEnabled = enabled
    )

    val isAudio: Boolean
        get() = type == Type.AUDIO

    val isVideo: Boolean
        get() = type == Type.VIDEO

    val isSpeex: Boolean
        get() = name.equals("Speex", ignoreCase = true)

    val isOpus: Boolean
        get() = name.equals("opus", ignoreCase = true)

    val isH264: Boolean
        get() = name.equals("H264", ignoreCase = true)

    val isH265: Boolean
        get() = name.equals("H265", ignoreCase = true)

    val isVP8: Boolean
        get() = name.equals("VP8", ignoreCase = true)

    fun toggleState() {
        isEnabled = !isEnabled
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Codec) return false
        return payload == other.payload
    }

    override fun hashCode(): Int = payload.hashCode()

    override fun toString(): String = buildString {
        appendLine("Codec: $name")
        appendLine("Payload: $payload")
        appendLine("Type: $type")
        sampleRate?.let { appendLine("Sample Rate: $it") }
        bitRate?.let { appendLine("Bit Rate: $it") }
        channels?.let { appendLine("Channels: $it") }
        appendLine("Enabled: $isEnabled")
    }

    companion object {
        /**
         * Common codec details keys.
         */
        object Keys {
            const val NAME = "CodecInfo.name"
            const val TYPE = "CodecInfo.type"
            const val SAMPLE_RATE = "CodecInfo.sampleRate"
            const val BIT_RATE = "CodecInfo.bitrate"
            const val CHANNELS = "CodecInfo.channelNumber"
        }
    }
}
