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

import kotlinx.serialization.Serializable

/**
 * Represents a media stream in a call (audio or video).
 *
 * Ported from: jami-client-android libjamiclient
 */
@Serializable
data class Media(
    val source: String?,
    val mediaType: MediaType?,
    val label: String?,
    val isEnabled: Boolean,
    val isOnHold: Boolean,
    val isMuted: Boolean
) {
    constructor(mediaMap: Map<String, String>) : this(
        source = mediaMap[SOURCE_KEY],
        mediaType = MediaType.parseMediaType(mediaMap[MEDIA_TYPE_KEY] ?: ""),
        label = mediaMap[LABEL_KEY],
        isEnabled = mediaMap[ENABLED_KEY]?.toBoolean() ?: false,
        isOnHold = mediaMap[ON_HOLD_KEY]?.toBoolean() ?: false,
        isMuted = mediaMap[MUTED_KEY]?.toBoolean() ?: false
    )

    constructor(type: MediaType, label: String) : this(
        source = "",
        mediaType = type,
        label = label,
        isEnabled = true,
        isOnHold = false,
        isMuted = false
    )

    val isAudio: Boolean
        get() = mediaType == MediaType.MEDIA_TYPE_AUDIO

    val isVideo: Boolean
        get() = mediaType == MediaType.MEDIA_TYPE_VIDEO

    val isScreenShare: Boolean
        get() = source == "camera://desktop" && isVideo

    fun toMap(): Map<String, String> = buildMap {
        source?.let { put(SOURCE_KEY, it) }
        put(MEDIA_TYPE_KEY, MediaType.getMediaTypeString(mediaType))
        label?.let { put(LABEL_KEY, it) }
        put(ENABLED_KEY, isEnabled.toString())
        put(ON_HOLD_KEY, isOnHold.toString())
        put(MUTED_KEY, isMuted.toString())
    }

    fun copyWithMuted(muted: Boolean): Media = copy(isMuted = muted)
    fun copyWithEnabled(enabled: Boolean): Media = copy(isEnabled = enabled)
    fun copyWithOnHold(onHold: Boolean): Media = copy(isOnHold = onHold)

    @Serializable
    enum class MediaType {
        MEDIA_TYPE_AUDIO,
        MEDIA_TYPE_VIDEO;

        companion object {
            fun parseMediaType(mediaType: String): MediaType? {
                return entries.find { it.name.contains(mediaType, ignoreCase = true) }
            }

            fun getMediaTypeString(mediaType: MediaType?): String {
                return mediaType?.name ?: "NULL"
            }
        }
    }

    companion object {
        const val SOURCE_KEY = "SOURCE"
        const val MEDIA_TYPE_KEY = "MEDIA_TYPE"
        const val LABEL_KEY = "LABEL"
        const val ENABLED_KEY = "ENABLED"
        const val ON_HOLD_KEY = "ON_HOLD"
        const val MUTED_KEY = "MUTED"

        val DEFAULT_AUDIO = Media(MediaType.MEDIA_TYPE_AUDIO, "audio_0")
        val DEFAULT_VIDEO = Media(MediaType.MEDIA_TYPE_VIDEO, "video_0")

        fun audioOnly(): List<Media> = listOf(DEFAULT_AUDIO)
        fun audioVideo(): List<Media> = listOf(DEFAULT_AUDIO, DEFAULT_VIDEO)
    }
}
