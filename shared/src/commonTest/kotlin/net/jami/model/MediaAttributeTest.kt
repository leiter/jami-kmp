package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaAttributeTest {

    @Test
    fun testAudioMediaAttribute() {
        val audio = MediaAttribute.audio(enabled = true, muted = false)

        assertEquals(MediaAttribute.MediaType.AUDIO, audio.mediaType)
        assertEquals("audio_0", audio.label)
        assertTrue(audio.enabled)
        assertFalse(audio.muted)
    }

    @Test
    fun testVideoMediaAttribute() {
        val video = MediaAttribute.video(enabled = true, muted = false, source = "camera://0")

        assertEquals(MediaAttribute.MediaType.VIDEO, video.mediaType)
        assertEquals("video_0", video.label)
        assertEquals("camera://0", video.source)
        assertTrue(video.enabled)
        assertFalse(video.muted)
    }

    @Test
    fun testMediaAttributeToMap() {
        val audio = MediaAttribute(
            mediaType = MediaAttribute.MediaType.AUDIO,
            label = "audio_0",
            enabled = true,
            muted = false,
            onHold = false,
            source = ""
        )

        val map = audio.toMap()
        assertEquals("AUDIO", map["MEDIA_TYPE"])
        assertEquals("audio_0", map["LABEL"])
        assertEquals("true", map["ENABLED"])
        assertEquals("false", map["MUTED"])
        assertEquals("false", map["ON_HOLD"])
    }

    @Test
    fun testMediaAttributeFromMap() {
        val map = mapOf(
            "MEDIA_TYPE" to "VIDEO",
            "LABEL" to "video_0",
            "ENABLED" to "true",
            "MUTED" to "true",
            "ON_HOLD" to "false",
            "SOURCE" to "screen://0"
        )

        val media = MediaAttribute.fromMap(map)
        assertEquals(MediaAttribute.MediaType.VIDEO, media.mediaType)
        assertEquals("video_0", media.label)
        assertTrue(media.enabled)
        assertTrue(media.muted)
        assertFalse(media.onHold)
        assertEquals("screen://0", media.source)
    }
}
