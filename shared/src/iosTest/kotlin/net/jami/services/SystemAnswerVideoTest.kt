package net.jami.services

import net.jami.model.Call
import net.jami.model.Media
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R1: an answer given through CallKit, the lock-screen banner or a notification used to send no
 * video, whatever the call offered.
 */
class SystemAnswerVideoTest {

    private fun call(vararg media: Media) =
        Call("acc1", "c1", Uri.fromString("jami:bbbb"), isIncoming = true).apply { setMediaList(media.toList()) }

    private val audio = Media(Media.MediaType.MEDIA_TYPE_AUDIO, "audio_0")
    private val video = Media(Media.MediaType.MEDIA_TYPE_VIDEO, "video_0")

    @Test
    fun aVideoCallIsAnsweredWithVideo() {
        assertTrue(systemAnswerHasVideo(call(audio, video), cameraAuthorized = true))
    }

    @Test
    fun anAudioCallIsAnsweredWithoutVideo() {
        assertFalse(systemAnswerHasVideo(call(audio), cameraAuthorized = true))
    }

    @Test
    fun withoutTheCameraPermissionThereIsNoVideo() {
        assertFalse(systemAnswerHasVideo(call(audio, video), cameraAuthorized = false))
    }

    @Test
    fun anUnknownCallIsAnsweredWithoutVideo() {
        assertFalse(systemAnswerHasVideo(null, cameraAuthorized = true))
    }
}
