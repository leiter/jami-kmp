package net.jami.model

import kotlinx.serialization.Serializable

/**
 * Represents a media stream attribute for calls.
 * Used when placing or accepting calls to specify which media types to enable.
 */
@Serializable
data class MediaAttribute(
    val mediaType: MediaType,
    val label: String = "",
    val enabled: Boolean = true,
    val muted: Boolean = false,
    val onHold: Boolean = false,
    val source: String = ""
) {
    enum class MediaType {
        AUDIO,
        VIDEO
    }

    fun toMap(): Map<String, String> = mapOf(
        "MEDIA_TYPE" to mediaType.name,
        "LABEL" to label,
        "ENABLED" to enabled.toString(),
        "MUTED" to muted.toString(),
        "ON_HOLD" to onHold.toString(),
        "SOURCE" to source
    )

    companion object {
        fun fromMap(map: Map<String, String>): MediaAttribute = MediaAttribute(
            mediaType = MediaType.valueOf(map["MEDIA_TYPE"] ?: "AUDIO"),
            label = map["LABEL"] ?: "",
            enabled = map["ENABLED"]?.toBoolean() ?: true,
            muted = map["MUTED"]?.toBoolean() ?: false,
            onHold = map["ON_HOLD"]?.toBoolean() ?: false,
            source = map["SOURCE"] ?: ""
        )

        fun audio(enabled: Boolean = true, muted: Boolean = false) = MediaAttribute(
            mediaType = MediaType.AUDIO,
            label = "audio_0",
            enabled = enabled,
            muted = muted
        )

        fun video(enabled: Boolean = true, muted: Boolean = false, source: String = "camera://0") = MediaAttribute(
            mediaType = MediaType.VIDEO,
            label = "video_0",
            enabled = enabled,
            muted = muted,
            source = source
        )
    }
}
