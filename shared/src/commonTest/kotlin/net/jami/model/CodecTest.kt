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
import kotlin.test.assertTrue

class CodecTest {

    @Test
    fun testAudioCodecCreation() {
        val codec = Codec(
            payload = 96,
            name = "opus",
            type = Codec.Type.AUDIO,
            sampleRate = "48000",
            bitRate = "64000",
            channels = "2",
            isEnabled = true
        )

        assertEquals(96, codec.payload)
        assertEquals("opus", codec.name)
        assertEquals(Codec.Type.AUDIO, codec.type)
        assertEquals("48000", codec.sampleRate)
        assertEquals("64000", codec.bitRate)
        assertEquals("2", codec.channels)
        assertTrue(codec.isEnabled)
        assertTrue(codec.isAudio)
        assertFalse(codec.isVideo)
    }

    @Test
    fun testVideoCodecCreation() {
        val codec = Codec(
            payload = 97,
            name = "H264",
            type = Codec.Type.VIDEO,
            bitRate = "2000000",
            isEnabled = false
        )

        assertEquals(97, codec.payload)
        assertEquals("H264", codec.name)
        assertEquals(Codec.Type.VIDEO, codec.type)
        assertFalse(codec.isEnabled)
        assertFalse(codec.isAudio)
        assertTrue(codec.isVideo)
        assertTrue(codec.isH264)
    }

    @Test
    fun testCodecFromDetailsMap() {
        val details = mapOf(
            "CodecInfo.name" to "opus",
            "CodecInfo.type" to "AUDIO",
            "CodecInfo.sampleRate" to "48000",
            "CodecInfo.bitrate" to "64000",
            "CodecInfo.channelNumber" to "2"
        )

        val codec = Codec(payload = 96, details = details, enabled = true)

        assertEquals(96, codec.payload)
        assertEquals("opus", codec.name)
        assertEquals(Codec.Type.AUDIO, codec.type)
        assertEquals("48000", codec.sampleRate)
        assertEquals("64000", codec.bitRate)
        assertEquals("2", codec.channels)
        assertTrue(codec.isEnabled)
    }

    @Test
    fun testCodecTypeFromString() {
        assertEquals(Codec.Type.AUDIO, Codec.Type.fromString("AUDIO"))
        assertEquals(Codec.Type.AUDIO, Codec.Type.fromString("audio"))
        assertEquals(Codec.Type.VIDEO, Codec.Type.fromString("VIDEO"))
        assertEquals(Codec.Type.VIDEO, Codec.Type.fromString("video"))
        assertEquals(Codec.Type.AUDIO, Codec.Type.fromString("unknown"))
    }

    @Test
    fun testToggleState() {
        val codec = Codec(
            payload = 96,
            name = "opus",
            type = Codec.Type.AUDIO,
            isEnabled = false
        )

        assertFalse(codec.isEnabled)
        codec.toggleState()
        assertTrue(codec.isEnabled)
        codec.toggleState()
        assertFalse(codec.isEnabled)
    }

    @Test
    fun testCodecNameChecks() {
        val speex = Codec(payload = 1, name = "Speex", type = Codec.Type.AUDIO)
        assertTrue(speex.isSpeex)
        assertFalse(speex.isOpus)

        val opus = Codec(payload = 2, name = "opus", type = Codec.Type.AUDIO)
        assertTrue(opus.isOpus)
        assertFalse(opus.isSpeex)

        val h264 = Codec(payload = 3, name = "H264", type = Codec.Type.VIDEO)
        assertTrue(h264.isH264)
        assertFalse(h264.isH265)

        val h265 = Codec(payload = 4, name = "H265", type = Codec.Type.VIDEO)
        assertTrue(h265.isH265)
        assertFalse(h264.isVP8)

        val vp8 = Codec(payload = 5, name = "VP8", type = Codec.Type.VIDEO)
        assertTrue(vp8.isVP8)
    }

    @Test
    fun testCodecEquality() {
        val codec1 = Codec(payload = 96, name = "opus", type = Codec.Type.AUDIO)
        val codec2 = Codec(payload = 96, name = "different", type = Codec.Type.VIDEO)
        val codec3 = Codec(payload = 97, name = "opus", type = Codec.Type.AUDIO)

        // Codecs are equal if they have the same payload
        assertEquals(codec1, codec2)
        assertEquals(codec1.hashCode(), codec2.hashCode())

        // Different payloads are not equal
        assertFalse(codec1 == codec3)
    }

    @Test
    fun testCodecToString() {
        val codec = Codec(
            payload = 96,
            name = "opus",
            type = Codec.Type.AUDIO,
            sampleRate = "48000",
            isEnabled = true
        )

        val str = codec.toString()
        assertTrue(str.contains("opus"))
        assertTrue(str.contains("96"))
        assertTrue(str.contains("AUDIO"))
        assertTrue(str.contains("48000"))
    }
}
