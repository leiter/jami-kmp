package net.jami.viewmodel

import net.jami.ui.viewmodel.isRemoteVideoSink
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The iPhone showed its own camera full screen as the "received" video: the daemon's sink for
 * the local camera input was taken for the remote one (2026-11-18).
 */
class RemoteVideoSinkTest {
    private val call = listOf("c1")

    @Test
    fun theCallsOwnSinkIsRemote() {
        assertTrue(isRemoteVideoSink("c1_video_0", "", call))
        assertTrue(isRemoteVideoSink("c1", "", call))
    }

    @Test
    fun aLocalInputIsNeverRemote() {
        assertFalse(isRemoteVideoSink("camera://com.apple.avfoundation.avcapturedevice.built-in_video:1", "", call))
        assertFalse(isRemoteVideoSink("camera://1", "c1_video_0", call))
        assertFalse(isRemoteVideoSink("file:///tmp/a.mp4", "", call))
        assertFalse(isRemoteVideoSink("", "", call))
    }

    @Test
    fun aForeignSinkDoesNotReplaceTheCallsSink() {
        assertFalse(isRemoteVideoSink("other_video_0", "c1_video_0", call))
    }

    @Test
    fun anUnknownSinkIsStillShownWhenNothingOfTheCallIs() {
        // Conference mixers and older daemons may name sinks differently; keep showing them.
        assertTrue(isRemoteVideoSink("mixer42", "", call))
    }
}
