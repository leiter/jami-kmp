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
package net.jami.services

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.jami.model.Call
import net.jami.model.Conference
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HardwareServiceTest {

    // ══════════════════════════════════════════════════════════════════════════
    // Data Class Tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun testVideoEventDefaults() {
        val event = VideoEvent("sink1")
        assertEquals("sink1", event.sinkId)
        assertFalse(event.start)
        assertFalse(event.started)
        assertEquals(0, event.width)
        assertEquals(0, event.height)
        assertEquals(0, event.rotation)
    }

    @Test
    fun testVideoEventWithValues() {
        val event = VideoEvent(
            sinkId = "sink1",
            start = true,
            started = true,
            width = 1920,
            height = 1080,
            rotation = 90
        )
        assertEquals("sink1", event.sinkId)
        assertTrue(event.start)
        assertTrue(event.started)
        assertEquals(1920, event.width)
        assertEquals(1080, event.height)
        assertEquals(90, event.rotation)
    }

    @Test
    fun testBluetoothEvent() {
        val connected = BluetoothEvent(connected = true)
        assertTrue(connected.connected)

        val disconnected = BluetoothEvent(connected = false)
        assertFalse(disconnected.connected)
    }

    @Test
    fun testAudioOutputTypes() {
        assertEquals(4, AudioOutputType.entries.size)
        assertTrue(AudioOutputType.entries.contains(AudioOutputType.INTERNAL))
        assertTrue(AudioOutputType.entries.contains(AudioOutputType.WIRED))
        assertTrue(AudioOutputType.entries.contains(AudioOutputType.SPEAKERS))
        assertTrue(AudioOutputType.entries.contains(AudioOutputType.BLUETOOTH))
    }

    @Test
    fun testAudioOutputDefaults() {
        val output = AudioOutput(AudioOutputType.SPEAKERS)
        assertEquals(AudioOutputType.SPEAKERS, output.type)
        assertNull(output.outputName)
        assertNull(output.outputId)
    }

    @Test
    fun testAudioOutputWithValues() {
        val output = AudioOutput(
            type = AudioOutputType.BLUETOOTH,
            outputName = "AirPods",
            outputId = "bt:00:11:22:33:44:55"
        )
        assertEquals(AudioOutputType.BLUETOOTH, output.type)
        assertEquals("AirPods", output.outputName)
        assertEquals("bt:00:11:22:33:44:55", output.outputId)
    }

    @Test
    fun testAudioStateDefaults() {
        val state = AudioState(HardwareService.OUTPUT_INTERNAL)
        assertEquals(HardwareService.OUTPUT_INTERNAL, state.output)
        assertTrue(state.availableOutputs.isEmpty())
    }

    @Test
    fun testAudioStateWithMultipleOutputs() {
        val outputs = listOf(
            HardwareService.OUTPUT_INTERNAL,
            HardwareService.OUTPUT_SPEAKERS,
            HardwareService.OUTPUT_BLUETOOTH
        )
        val state = AudioState(
            output = HardwareService.OUTPUT_SPEAKERS,
            availableOutputs = outputs
        )
        assertEquals(HardwareService.OUTPUT_SPEAKERS, state.output)
        assertEquals(3, state.availableOutputs.size)
    }

    @Test
    fun testCompanionObjectConstants() {
        assertEquals(AudioOutputType.SPEAKERS, HardwareService.OUTPUT_SPEAKERS.type)
        assertEquals(AudioOutputType.INTERNAL, HardwareService.OUTPUT_INTERNAL.type)
        assertEquals(AudioOutputType.WIRED, HardwareService.OUTPUT_WIRED.type)
        assertEquals(AudioOutputType.BLUETOOTH, HardwareService.OUTPUT_BLUETOOTH.type)
        assertEquals(HardwareService.OUTPUT_INTERNAL, HardwareService.STATE_INTERNAL.output)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // StubHardwareService Tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun testStubInitialState() = runTest {
        val stub = StubHardwareService()

        // Check initial audio state
        val audioState = stub.audioState.first()
        assertEquals(HardwareService.STATE_INTERNAL, audioState)

        // Check default values
        assertFalse(stub.isSpeakerphoneOn())
        assertFalse(stub.isVideoAvailable)
        assertFalse(stub.hasCamera())
        assertEquals(0, stub.cameraCount())
        assertTrue(stub.hasMicrophone())
        assertFalse(stub.shouldPlaySpeaker())
        assertTrue(stub.isPreviewFromFrontCamera)
        assertFalse(stub.isLogging)
    }

    @Test
    fun testStubSpeakerToggle() {
        val stub = StubHardwareService()

        assertFalse(stub.isSpeakerphoneOn())

        // Use a dummy conference - StubHardwareService ignores it
        stub.toggleSpeakerphone(createDummyConference(), true)
        assertTrue(stub.isSpeakerphoneOn())

        stub.toggleSpeakerphone(createDummyConference(), false)
        assertFalse(stub.isSpeakerphoneOn())
    }

    @Test
    fun testStubConnectivityChanged() = runTest {
        val stub = StubHardwareService()

        // Initial state should be connected (true)
        val initialState = stub.connectivityState.first()
        assertTrue(initialState)

        // Change connectivity
        stub.connectivityChanged(false)
        val newState = stub.connectivityState.first()
        assertFalse(newState)

        // Restore connectivity
        stub.connectivityChanged(true)
        val restoredState = stub.connectivityState.first()
        assertTrue(restoredState)
    }

    @Test
    fun testStubLogging() {
        val stub = StubHardwareService()

        assertFalse(stub.isLogging)

        stub.startLogs()
        assertTrue(stub.isLogging)

        stub.stopLogs()
        assertFalse(stub.isLogging)

        stub.saveLoggingState(true)
        assertTrue(stub.isLogging)

        stub.saveLoggingState(false)
        assertFalse(stub.isLogging)
    }

    @Test
    fun testStubVideoOperations() {
        val stub = StubHardwareService()

        // These should not throw
        assertFalse(stub.hasInput("test"))
        assertNull(stub.changeCamera())
        assertNull(stub.changeCamera(setDefaultCamera = true))
        assertEquals(0L, stub.startVideo("input1", Any(), 1920, 1080))
        stub.stopVideo("input1", 0L)
        stub.switchInput("account1", "call1", "camera:0")
    }

    @Test
    fun testStubCameraOperations() {
        val stub = StubHardwareService()

        // These should not throw
        stub.startCameraPreview(true)
        stub.cameraCleanup()
        stub.startCapture("camera:0")
        stub.stopCapture("camera:0")
        stub.requestKeyFrame("camera:0")
        stub.setBitrate("camera:0", 2000000)
        stub.setParameters("camera:0", 0, 1920, 1080, 30)

        val formats = mutableListOf<Int>()
        val sizes = mutableListOf<Int>()
        val rates = mutableListOf<Int>()
        stub.getCameraInfo("camera:0", formats, sizes, rates)
        // Stub doesn't populate these lists
        assertTrue(formats.isEmpty())
    }

    @Test
    fun testStubSurfaceOperations() {
        val stub = StubHardwareService()

        // These should not throw
        stub.addVideoSurface("sink1", Any())
        stub.updateVideoSurfaceId("sink1", "sink2")
        stub.removeVideoSurface("sink2")
        stub.addPreviewVideoSurface(Any(), null)
        stub.updatePreviewVideoSurface(createDummyConference())
        stub.removePreviewVideoSurface()
        stub.addFullScreenPreviewSurface(Any())
        stub.removeFullScreenPreviewSurface()
    }

    @Test
    fun testStubMediaHandler() {
        val stub = StubHardwareService()

        // These should not throw
        stub.startMediaHandler("handler1")
        stub.stopMediaHandler()
        stub.setPendingScreenShareProjection(Any())
        stub.setPendingScreenShareProjection(null)
    }

    @Test
    fun testStubAudioOperations() {
        val stub = StubHardwareService()

        stub.updateAudioState(null, createDummyCall(), true, false)
        stub.closeAudioState()
        stub.abandonAudioFocus()
        stub.setDeviceOrientation(90)
        stub.unregisterCameraDetectionCallback()
    }

    @Test
    fun testStubSinkOperations() = runTest {
        val stub = StubHardwareService()

        val sinkSize = stub.getSinkSize("sink1")
        assertEquals(0 to 0, sinkSize)

        val sinkFlow = stub.connectSink("sink1", 123L)
        val firstSize = sinkFlow.first()
        assertEquals(0 to 0, firstSize)
    }

    @Test
    fun testStubPreviewSettings() {
        val stub = StubHardwareService()

        stub.setPreviewSettings()
        stub.setPreviewSettings(mapOf("camera:0" to mapOf("width" to "1920")))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper Functions
    // ══════════════════════════════════════════════════════════════════════════

    private fun createDummyConference(): Conference {
        return Conference("account1", "conf1")
    }

    private fun createDummyCall(): Call {
        return Call(
            account = "account1",
            daemonId = "call1",
            peerUri = Uri.fromString("jami:abc123"),
            isIncoming = true
        )
    }
}
