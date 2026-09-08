package net.jami.services

import net.jami.services.expect.AudioOutput
import net.jami.services.expect.AudioOutputType
import net.jami.services.expect.AudioState
import net.jami.services.expect.BluetoothEvent
import net.jami.services.expect.HardwareService
import net.jami.services.expect.VideoEvent
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
import kotlin.test.BeforeTest
import net.jami.ensurePlatformTestKoin

/**
 * Behaviour specific to the desktop HardwareService, which is a stub.
 *
 * These moved out of commonTest because they assert stub values — no camera, a 0x0 sink
 * size — that are legitimately different on iOS, where HardwareService is a real
 * implementation backed by AVFoundation. Running them there was asserting that the iOS
 * implementation behaves like a stub, which it should not.
 */
class HardwareServiceStubTest {

    @Test
    fun testStubInitialState() = runTest {
        val stub = HardwareService()

        // Check initial audio state — default is INTERNAL output
        val audioState = stub.audioState.first()
        assertEquals(AudioOutputType.INTERNAL, audioState.output.type)

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
    fun testStubCameraOperations() {
        val stub = HardwareService()

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
    fun testStubLogging() {
        val stub = HardwareService()

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
    fun testStubSinkOperations() = runTest {
        val stub = HardwareService()

        val sinkSize = stub.getSinkSize("sink1")
        assertEquals(0 to 0, sinkSize)

        val sinkFlow = stub.connectSink("sink1", 123L)
        val firstSize = sinkFlow.first()
        assertEquals(0 to 0, firstSize)
    }
}
