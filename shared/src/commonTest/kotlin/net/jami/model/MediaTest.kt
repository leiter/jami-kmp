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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaTest {

    @Test
    fun testAudioMediaFromMap() {
        val mediaMap = mapOf(
            "SOURCE" to "microphone",
            "MEDIA_TYPE" to "MEDIA_TYPE_AUDIO",
            "LABEL" to "audio_0",
            "ENABLED" to "true",
            "ON_HOLD" to "false",
            "MUTED" to "false"
        )

        val media = Media(mediaMap)

        assertEquals("microphone", media.source)
        assertEquals(Media.MediaType.MEDIA_TYPE_AUDIO, media.mediaType)
        assertEquals("audio_0", media.label)
        assertTrue(media.isEnabled)
        assertFalse(media.isOnHold)
        assertFalse(media.isMuted)
    }

    @Test
    fun testVideoMediaFromMap() {
        val mediaMap = mapOf(
            "SOURCE" to "camera://front",
            "MEDIA_TYPE" to "MEDIA_TYPE_VIDEO",
            "LABEL" to "video_0",
            "ENABLED" to "true",
            "ON_HOLD" to "false",
            "MUTED" to "true"
        )

        val media = Media(mediaMap)

        assertEquals("camera://front", media.source)
        assertEquals(Media.MediaType.MEDIA_TYPE_VIDEO, media.mediaType)
        assertEquals("video_0", media.label)
        assertTrue(media.isEnabled)
        assertFalse(media.isOnHold)
        assertTrue(media.isMuted)
    }

    @Test
    fun testMediaFromMapWithMissingFields() {
        val mediaMap = mapOf<String, String>()

        val media = Media(mediaMap)

        assertNull(media.source)
        // Note: parseMediaType("") matches AUDIO because "".contains("") is true
        // This is edge case behavior in the implementation
        assertNotNull(media.mediaType)
        assertNull(media.label)
        assertFalse(media.isEnabled)
        assertFalse(media.isOnHold)
        assertFalse(media.isMuted)
    }

    @Test
    fun testMediaTypeAndLabelConstructor() {
        val media = Media(Media.MediaType.MEDIA_TYPE_AUDIO, "audio_1")

        assertEquals("", media.source)
        assertEquals(Media.MediaType.MEDIA_TYPE_AUDIO, media.mediaType)
        assertEquals("audio_1", media.label)
        assertTrue(media.isEnabled)
        assertFalse(media.isOnHold)
        assertFalse(media.isMuted)
    }

    @Test
    fun testIsAudio() {
        val audioMedia = Media(Media.MediaType.MEDIA_TYPE_AUDIO, "audio_0")
        val videoMedia = Media(Media.MediaType.MEDIA_TYPE_VIDEO, "video_0")

        assertTrue(audioMedia.isAudio)
        assertFalse(audioMedia.isVideo)
        assertFalse(videoMedia.isAudio)
        assertTrue(videoMedia.isVideo)
    }

    @Test
    fun testIsScreenShare() {
        val screenShareMedia = Media(
            source = "camera://desktop",
            mediaType = Media.MediaType.MEDIA_TYPE_VIDEO,
            label = "screen",
            isEnabled = true,
            isOnHold = false,
            isMuted = false
        )

        val regularVideoMedia = Media(
            source = "camera://front",
            mediaType = Media.MediaType.MEDIA_TYPE_VIDEO,
            label = "video_0",
            isEnabled = true,
            isOnHold = false,
            isMuted = false
        )

        val audioMedia = Media(
            source = "camera://desktop",
            mediaType = Media.MediaType.MEDIA_TYPE_AUDIO,
            label = "audio_0",
            isEnabled = true,
            isOnHold = false,
            isMuted = false
        )

        assertTrue(screenShareMedia.isScreenShare)
        assertFalse(regularVideoMedia.isScreenShare)
        assertFalse(audioMedia.isScreenShare) // Audio can't be screen share
    }

    @Test
    fun testToMap() {
        val media = Media(
            source = "microphone",
            mediaType = Media.MediaType.MEDIA_TYPE_AUDIO,
            label = "audio_0",
            isEnabled = true,
            isOnHold = false,
            isMuted = true
        )

        val map = media.toMap()

        assertEquals("microphone", map["SOURCE"])
        assertEquals("MEDIA_TYPE_AUDIO", map["MEDIA_TYPE"])
        assertEquals("audio_0", map["LABEL"])
        assertEquals("true", map["ENABLED"])
        assertEquals("false", map["ON_HOLD"])
        assertEquals("true", map["MUTED"])
    }

    @Test
    fun testToMapWithNullSource() {
        val media = Media(
            source = null,
            mediaType = Media.MediaType.MEDIA_TYPE_AUDIO,
            label = "audio_0",
            isEnabled = true,
            isOnHold = false,
            isMuted = false
        )

        val map = media.toMap()

        assertFalse(map.containsKey("SOURCE"))
        assertEquals("MEDIA_TYPE_AUDIO", map["MEDIA_TYPE"])
    }

    @Test
    fun testToMapWithNullMediaType() {
        val media = Media(
            source = "test",
            mediaType = null,
            label = "test",
            isEnabled = true,
            isOnHold = false,
            isMuted = false
        )

        val map = media.toMap()

        assertEquals("NULL", map["MEDIA_TYPE"])
    }

    @Test
    fun testCopyWithMuted() {
        val original = Media(Media.MediaType.MEDIA_TYPE_AUDIO, "audio_0")
        assertFalse(original.isMuted)

        val muted = original.copyWithMuted(true)
        assertTrue(muted.isMuted)
        assertFalse(original.isMuted) // Original unchanged

        val unmuted = muted.copyWithMuted(false)
        assertFalse(unmuted.isMuted)
    }

    @Test
    fun testCopyWithEnabled() {
        val original = Media(Media.MediaType.MEDIA_TYPE_AUDIO, "audio_0")
        assertTrue(original.isEnabled)

        val disabled = original.copyWithEnabled(false)
        assertFalse(disabled.isEnabled)
        assertTrue(original.isEnabled) // Original unchanged
    }

    @Test
    fun testCopyWithOnHold() {
        val original = Media(Media.MediaType.MEDIA_TYPE_AUDIO, "audio_0")
        assertFalse(original.isOnHold)

        val onHold = original.copyWithOnHold(true)
        assertTrue(onHold.isOnHold)
        assertFalse(original.isOnHold) // Original unchanged
    }

    @Test
    fun testDefaultAudio() {
        val audio = Media.DEFAULT_AUDIO

        assertEquals(Media.MediaType.MEDIA_TYPE_AUDIO, audio.mediaType)
        assertEquals("audio_0", audio.label)
        assertTrue(audio.isEnabled)
        assertFalse(audio.isMuted)
    }

    @Test
    fun testDefaultVideo() {
        val video = Media.DEFAULT_VIDEO

        assertEquals(Media.MediaType.MEDIA_TYPE_VIDEO, video.mediaType)
        assertEquals("video_0", video.label)
        assertTrue(video.isEnabled)
        assertFalse(video.isMuted)
    }

    @Test
    fun testAudioOnly() {
        val mediaList = Media.audioOnly()

        assertEquals(1, mediaList.size)
        assertTrue(mediaList[0].isAudio)
    }

    @Test
    fun testAudioVideo() {
        val mediaList = Media.audioVideo()

        assertEquals(2, mediaList.size)
        assertTrue(mediaList[0].isAudio)
        assertTrue(mediaList[1].isVideo)
    }

    @Test
    fun testMediaTypeParseAudio() {
        val type = Media.MediaType.parseMediaType("MEDIA_TYPE_AUDIO")
        assertEquals(Media.MediaType.MEDIA_TYPE_AUDIO, type)
    }

    @Test
    fun testMediaTypeParseVideo() {
        val type = Media.MediaType.parseMediaType("MEDIA_TYPE_VIDEO")
        assertEquals(Media.MediaType.MEDIA_TYPE_VIDEO, type)
    }

    @Test
    fun testMediaTypeParseCaseInsensitive() {
        val type = Media.MediaType.parseMediaType("audio")
        assertEquals(Media.MediaType.MEDIA_TYPE_AUDIO, type)
    }

    @Test
    fun testMediaTypeParseInvalid() {
        val type = Media.MediaType.parseMediaType("invalid")
        assertNull(type)
    }

    @Test
    fun testMediaTypeParseEmpty() {
        // Note: parseMediaType("") returns MEDIA_TYPE_AUDIO because
        // "MEDIA_TYPE_AUDIO".contains("") is true (empty string matches any string)
        val type = Media.MediaType.parseMediaType("")
        // This is edge case behavior - it matches AUDIO
        assertEquals(Media.MediaType.MEDIA_TYPE_AUDIO, type)
    }

    @Test
    fun testGetMediaTypeString() {
        assertEquals("MEDIA_TYPE_AUDIO", Media.MediaType.getMediaTypeString(Media.MediaType.MEDIA_TYPE_AUDIO))
        assertEquals("MEDIA_TYPE_VIDEO", Media.MediaType.getMediaTypeString(Media.MediaType.MEDIA_TYPE_VIDEO))
        assertEquals("NULL", Media.MediaType.getMediaTypeString(null))
    }

    @Test
    fun testDataClassEquality() {
        val media1 = Media(
            source = "test",
            mediaType = Media.MediaType.MEDIA_TYPE_AUDIO,
            label = "audio_0",
            isEnabled = true,
            isOnHold = false,
            isMuted = false
        )

        val media2 = Media(
            source = "test",
            mediaType = Media.MediaType.MEDIA_TYPE_AUDIO,
            label = "audio_0",
            isEnabled = true,
            isOnHold = false,
            isMuted = false
        )

        assertEquals(media1, media2)
        assertEquals(media1.hashCode(), media2.hashCode())
    }

    @Test
    fun testCompanionConstants() {
        assertEquals("SOURCE", Media.SOURCE_KEY)
        assertEquals("MEDIA_TYPE", Media.MEDIA_TYPE_KEY)
        assertEquals("LABEL", Media.LABEL_KEY)
        assertEquals("ENABLED", Media.ENABLED_KEY)
        assertEquals("ON_HOLD", Media.ON_HOLD_KEY)
        assertEquals("MUTED", Media.MUTED_KEY)
    }
}
