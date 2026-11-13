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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.jami.model.CameraFacing
import net.jami.model.DeviceParams
import net.jami.model.VideoDevices
import net.jami.model.VideoParams
import net.jami.utils.Log
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTripleCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInUltraWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureSessionPresetMedium
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.position
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVFrameRateRange
import platform.CoreMedia.CMFormatDescriptionRef
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.QuartzCore.CALayer
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * iOS Camera Service using AVFoundation.
 *
 * Handles:
 * - Camera enumeration and selection
 * - AVCaptureSession management
 * - Video frame capture with AVCaptureVideoDataOutput
 * - Preview layer management
 * - Device orientation handling
 */
@OptIn(ExperimentalForeignApi::class)
class IOSCameraService(
    private val scope: CoroutineScope,
    private val daemonBridge: DaemonBridgeApi
) {
    private val tag = "IOSCameraService"

    // Camera state
    private var captureSession: AVCaptureSession? = null
    private var currentInput: AVCaptureDeviceInput? = null
    private var videoOutput: AVCaptureVideoDataOutput? = null
    private var previewLayer: AVCaptureVideoPreviewLayer? = null

    // Camera devices
    private var frontCamera: AVCaptureDevice? = null
    private var backCamera: AVCaptureDevice? = null
    private var currentCamera: AVCaptureDevice? = null

    // State flows
    private val _cameraState = MutableStateFlow(CameraState.IDLE)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _currentCameraId = MutableStateFlow<String?>(null)
    val currentCameraId: StateFlow<String?> = _currentCameraId.asStateFlow()

    private val _frameEvents = MutableSharedFlow<FrameEvent>()
    val frameEvents: SharedFlow<FrameEvent> = _frameEvents.asSharedFlow()

    // Processing queue for video frames
    private val videoQueue = dispatch_queue_create("net.jami.video.capture", null)

    // Current video parameters
    private var currentParams: IOSVideoParams? = null

    // Orientation
    private var deviceOrientation: Int = 0

    /**
     * The daemon input frames are published under ("camera://<device>"), named by the
     * daemon's last `StartCapture`. Null before any call asked for video, in which case the
     * current camera's own input is used. Read on the capture queue.
     */
    @kotlin.concurrent.Volatile
    private var daemonCaptureInput: String? = null

    /**
     * The capture orientation for the device's last real orientation, kept for reading off the
     * main thread. `UIDevice.orientation` is main-thread-only UIKit state, but the camera is
     * configured from `Dispatchers.IO` ([openCamera], [switchCamera]); reading it there is the
     * same violation that crashed launches in 0012bca. Written only on the main thread, by
     * [refreshVideoOrientation]. Declared before `init`, which may already write it.
     */
    /**
     * Every AVCaptureSession call runs here, one at a time. AVFoundation throws (an uncatchable
     * NSGenericException) on stopRunning between beginConfiguration and commitConfiguration;
     * [closeCamera] used to start an asynchronous stop and reconfigure the session on the
     * calling thread at the same moment, and TestFlight build 39 crashed exactly there.
     */
    private val sessionDispatcher = Dispatchers.IO.limitedParallelism(1)

    @kotlin.concurrent.Volatile
    private var videoOrientation: platform.AVFoundation.AVCaptureVideoOrientation = AVCaptureVideoOrientationPortrait

    init {
        enumerateCameras()
        observeOrientationChanges()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Camera Enumeration
    // ══════════════════════════════════════════════════════════════════════════

    private fun enumerateCameras() {
        val deviceTypes = listOf(
            AVCaptureDeviceTypeBuiltInWideAngleCamera,
            AVCaptureDeviceTypeBuiltInDualCamera,
            AVCaptureDeviceTypeBuiltInDualWideCamera,
            AVCaptureDeviceTypeBuiltInTripleCamera,
            AVCaptureDeviceTypeBuiltInUltraWideCamera
        )

        val discoverySession = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            deviceTypes = deviceTypes,
            mediaType = AVMediaTypeVideo,
            position = platform.AVFoundation.AVCaptureDevicePositionUnspecified
        )

        discoverySession.devices.forEach { device ->
            val captureDevice = device as? AVCaptureDevice ?: return@forEach
            when (captureDevice.position) {
                AVCaptureDevicePositionFront -> {
                    if (frontCamera == null) frontCamera = captureDevice
                }
                AVCaptureDevicePositionBack -> {
                    if (backCamera == null) backCamera = captureDevice
                }
                else -> { }
            }
        }

        Log.d(tag, "Found cameras - front: ${frontCamera?.uniqueID}, back: ${backCamera?.uniqueID}")
    }

    fun getVideoDevices(): VideoDevices {
        val result = VideoDevices()
        frontCamera?.uniqueID?.let { id ->
            result.addCamera(id)
            result.cameraFront = id
        }
        backCamera?.uniqueID?.let { id ->
            result.addCamera(id)
            result.cameraBack = id
        }
        result.currentId = currentCamera?.uniqueID ?: frontCamera?.uniqueID
        return result
    }

    /**
     * Reports the capture capabilities of [camId] as reported by AVFoundation.
     *
     * Mirrors the Android CameraService contract: [sizes] is a flat width,height,width,height…
     * list and [rates] holds the supported maximum frame rates. [formats] carries a single 0,
     * since the daemon only ever consumes NV12 from the iOS capture path.
     *
     * Returns false when the camera is unknown, leaving the caller to fall back.
     */
    fun getCameraCapabilities(
        camId: String,
        sizes: MutableList<Int>,
        rates: MutableList<Int>,
    ): Boolean {
        val device = listOfNotNull(frontCamera, backCamera).firstOrNull { it.uniqueID == camId }
            ?: return false

        val seenSizes = mutableSetOf<Pair<Int, Int>>()
        val seenRates = mutableSetOf<Int>()
        device.formats.forEach { entry ->
            val format = entry as? AVCaptureDeviceFormat ?: return@forEach
            val description = format.formatDescription as CMFormatDescriptionRef?
            CMVideoFormatDescriptionGetDimensions(description).useContents {
                if (width > 0 && height > 0) seenSizes.add(width to height)
            }
            format.videoSupportedFrameRateRanges.forEach { rangeEntry ->
                val range = rangeEntry as? AVFrameRateRange ?: return@forEach
                seenRates.add(range.maxFrameRate.toInt())
            }
        }
        if (seenSizes.isEmpty()) return false

        // Largest first, matching the daemon's preference for the highest usable resolution.
        seenSizes.sortedByDescending { it.first.toLong() * it.second }.forEach { (w, h) ->
            sizes.add(w)
            sizes.add(h)
        }
        seenRates.sortedDescending().forEach { rates.add(it) }
        return true
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Permission Handling
    // ══════════════════════════════════════════════════════════════════════════

    suspend fun requestCameraPermission(): Boolean = suspendCoroutine { continuation ->
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> {
                continuation.resume(true)
            }
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    continuation.resume(granted)
                }
            }
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> {
                continuation.resume(false)
            }
            else -> {
                continuation.resume(false)
            }
        }
    }

    fun hasCameraPermission(): Boolean {
        return AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Session Management
    // ══════════════════════════════════════════════════════════════════════════

    suspend fun openCamera(cameraId: String? = null): Boolean = withContext(sessionDispatcher) {
        if (_cameraState.value == CameraState.CAPTURING) {
            Log.w(tag, "Camera already capturing")
            return@withContext true
        }

        _cameraState.value = CameraState.OPENING

        try {
            val targetCamera = when {
                cameraId != null -> {
                    if (frontCamera?.uniqueID == cameraId) frontCamera
                    else if (backCamera?.uniqueID == cameraId) backCamera
                    else frontCamera
                }
                else -> frontCamera ?: backCamera
            }

            if (targetCamera == null) {
                Log.e(tag, "No camera available")
                _cameraState.value = CameraState.ERROR
                return@withContext false
            }

            currentCamera = targetCamera
            _currentCameraId.value = targetCamera.uniqueID

            // Create capture session
            val session = AVCaptureSession()
            session.beginConfiguration()

            // Set session preset
            session.sessionPreset = AVCaptureSessionPreset1280x720

            // Add input
            val input = AVCaptureDeviceInput.deviceInputWithDevice(targetCamera, null)
            if (input == null || !session.canAddInput(input)) {
                Log.e(tag, "Cannot add camera input")
                _cameraState.value = CameraState.ERROR
                return@withContext false
            }
            session.addInput(input)
            currentInput = input

            // Add video output. NV12 is what the daemon's iOS input expects
            // (video_device_impl.cpp: pixel_format "nv12"); the key is
            // kCVPixelBufferPixelFormatTypeKey's value.
            val output = AVCaptureVideoDataOutput()
            output.alwaysDiscardsLateVideoFrames = true
            output.videoSettings = mapOf<Any?, Any?>(
                "PixelFormatType" to platform.Foundation.NSNumber(unsignedInt = kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange),
            )
            output.setSampleBufferDelegate(sampleBufferDelegate, videoQueue)

            if (!session.canAddOutput(output)) {
                Log.e(tag, "Cannot add video output")
                _cameraState.value = CameraState.ERROR
                return@withContext false
            }
            session.addOutput(output)
            videoOutput = output

            // Outgoing frames: fixed orientation, never mirrored. The rotation travels with
            // each frame instead (see outgoingFrameAngle), as in jami-client-ios
            // CameraCapturer.configureOutputConnection. Rotating the buffers themselves would
            // change the frame size whenever the phone turns, and mirroring them sent the
            // peer a mirror image. The local preview layer mirrors on its own.
            configureOutputConnection(output)

            session.commitConfiguration()
            captureSession = session

            // Create params
            currentParams = IOSVideoParams(
                cameraId = targetCamera.uniqueID,
                width = 1280,
                height = 720,
                frameRate = 30
            )

            // Register with daemon
            daemonBridge.addVideoDevice(targetCamera.uniqueID)
            daemonBridge.setDefaultDevice(targetCamera.uniqueID)

            _cameraState.value = CameraState.READY
            Log.d(tag, "Camera opened: ${targetCamera.uniqueID}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(tag, "Failed to open camera: ${e.message}")
            _cameraState.value = CameraState.ERROR
            return@withContext false
        }
    }

    fun startCapture() {
        val session = captureSession ?: return
        if (session.isRunning()) {
            Log.w(tag, "Capture already running")
            return
        }

        scope.launch(sessionDispatcher) {
            session.startRunning()
            _cameraState.value = CameraState.CAPTURING
            Log.d(tag, "Capture started")
        }
    }

    fun stopCapture() {
        val session = captureSession ?: return
        scope.launch(sessionDispatcher) {
            if (!session.isRunning()) return@launch
            session.stopRunning()
            _cameraState.value = CameraState.READY
            Log.d(tag, "Capture stopped")
        }
    }

    fun closeCamera() {

        // The camera stays registered with the daemon. HardwareService.initVideo() registers
        // every camera once at startup; removing this one here left the next video call with
        // no camera for the daemon to capture from (as jami-client-ios, which never removes
        // its devices). It never mattered before only because the registration itself never
        // took — see DaemonBridge.ios.kt addVideoDevice.

        // Stop and tear down in one block on the session queue, after anything already queued:
        // never a stop in the middle of the reconfiguration.
        val session = captureSession
        val input = currentInput
        val output = videoOutput
        if (session != null) {
            scope.launch(sessionDispatcher) {
                if (session.isRunning()) session.stopRunning()
                session.beginConfiguration()
                input?.let { session.removeInput(it) }
                output?.let { session.removeOutput(it) }
                session.commitConfiguration()
            }
        }

        captureSession = null
        currentInput = null
        videoOutput = null
        currentCamera = null
        currentParams = null
        previewLayer = null

        _cameraState.value = CameraState.IDLE
        _currentCameraId.value = null
        Log.d(tag, "Camera closed")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Camera Switching
    // ══════════════════════════════════════════════════════════════════════════

    suspend fun switchCamera(): String? = withContext(sessionDispatcher) {
        val session = captureSession ?: return@withContext null
        val currentPosition = currentCamera?.position ?: return@withContext null

        val newCamera = when (currentPosition) {
            AVCaptureDevicePositionFront -> backCamera
            AVCaptureDevicePositionBack -> frontCamera
            else -> frontCamera
        }

        if (newCamera == null) {
            Log.w(tag, "No alternative camera available")
            return@withContext currentCamera?.uniqueID
        }

        try {
            session.beginConfiguration()

            // Remove current input
            currentInput?.let { session.removeInput(it) }

            // Add new input
            val newInput = AVCaptureDeviceInput.deviceInputWithDevice(newCamera, null)
            if (newInput != null && session.canAddInput(newInput)) {
                session.addInput(newInput)
                currentInput = newInput
                currentCamera = newCamera
                _currentCameraId.value = newCamera.uniqueID

                // A new input gets a new connection: pin it again.
                videoOutput?.let { configureOutputConnection(it) }

                // Update daemon
                daemonBridge.setDefaultDevice(newCamera.uniqueID)
            }

            session.commitConfiguration()
            Log.d(tag, "Switched to camera: ${newCamera.uniqueID}")
            return@withContext newCamera.uniqueID
        } catch (e: Exception) {
            Log.e(tag, "Failed to switch camera: ${e.message}")
            session.commitConfiguration()
            return@withContext currentCamera?.uniqueID
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Preview Layer
    // ══════════════════════════════════════════════════════════════════════════

    fun createPreviewLayer(): AVCaptureVideoPreviewLayer? {
        val session = captureSession ?: return null

        val layer = AVCaptureVideoPreviewLayer(session = session)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        previewLayer = layer

        return layer
    }

    fun getPreviewLayer(): AVCaptureVideoPreviewLayer? = previewLayer

    // ══════════════════════════════════════════════════════════════════════════
    // Orientation Handling
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * On the main thread, always. This runs from `init`, and Koin creates this service on
     * whichever thread first asks for it — at startup usually `HardwareService.initVideo()` on
     * `Dispatchers.Default`. `beginGeneratingDeviceOrientationNotifications()` off the main
     * thread trips UIKit's "threading violation: expected the main thread" assertion and the
     * process dies with SIGTRAP, with no Kotlin exception and no crash report. It depended on
     * which thread won, so launches failed intermittently.
     */
    private fun observeOrientationChanges() {
        val setup = {
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIDeviceOrientationDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue
            ) { _ ->
                refreshVideoOrientation()
                updateVideoOrientation()
            }
            UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
            refreshVideoOrientation()
        }
        if (platform.Foundation.NSThread.isMainThread) setup()
        else platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) { setup() }
    }

    /** See the call site in [openCamera]. */
    private fun configureOutputConnection(output: AVCaptureVideoDataOutput) {
        output.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
            if (connection.isVideoOrientationSupported()) {
                connection.videoOrientation = OUTGOING_CONNECTION_ORIENTATION
            }
            if (connection.isVideoMirroringSupported()) {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.setVideoMirrored(false)
            }
        }
    }

    /**
     * Only the preview follows the device: the outgoing connection stays pinned (see
     * [configureOutputConnection]) and its frames carry [outgoingFrameAngle] instead.
     */
    private fun updateVideoOrientation() {
        previewLayer?.connection?.let { connection ->
            if (connection.isVideoOrientationSupported()) {
                connection.videoOrientation = currentVideoOrientation()
            }
        }
    }

    /** Safe from any thread: the cached value, never UIKit. */
    private fun currentVideoOrientation(): platform.AVFoundation.AVCaptureVideoOrientation = videoOrientation

    /** Main thread only. Keeps the last real orientation when the phone lies flat. */
    private fun refreshVideoOrientation() {
        captureOrientationFor(UIDevice.currentDevice.orientation)?.let { videoOrientation = it }
    }

    /** Names the daemon input outgoing frames go to; null falls back to the current camera. */
    fun setDaemonCaptureInput(device: String?) {
        daemonCaptureInput = device?.let { if (it.startsWith(CAMERA_SCHEME)) it else CAMERA_SCHEME + it }
    }

    fun setDeviceOrientation(rotation: Int) {
        deviceOrientation = rotation
        daemonBridge.setDeviceOrientation(currentCamera?.uniqueID ?: "", rotation)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Sample Buffer Delegate
    // ══════════════════════════════════════════════════════════════════════════

    private val sampleBufferDelegate = object : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection
        ) {
            val sampleBuffer = didOutputSampleBuffer ?: return
            val params = currentParams ?: return

            // Get pixel buffer dimensions
            val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return
            val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val height = CVPixelBufferGetHeight(pixelBuffer).toInt()

            // Update params if dimensions changed
            if (width != params.width || height != params.height) {
                currentParams = params.copy(width = width, height = height)
            }

            // Hand the frame to the daemon's encoder, zero-copy: the bridge points a daemon
            // frame at this buffer's planes and publishes it (doc/ios-video-pipeline.md §2).
            // Dropped by the bridge while the daemon is not capturing from this input.
            (daemonBridge as? IOSVideoFrameBridge)?.writeOutgoingVideoFrame(
                pixelBuffer,
                angle = outgoingFrameAngle(videoOrientation, isFront = isFrontCamera),
                videoInputId = daemonCaptureInput ?: (CAMERA_SCHEME + params.cameraId),
            )

            // Emit frame event for UI (drives the local-preview size / "camera live" state).
            scope.launch {
                _frameEvents.emit(FrameEvent(params.cameraId, width, height))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Properties
    // ══════════════════════════════════════════════════════════════════════════

    val isFrontCamera: Boolean
        get() = currentCamera?.position == AVCaptureDevicePositionFront

    val hasMultipleCameras: Boolean
        get() = frontCamera != null && backCamera != null

    val currentVideoParams: IOSVideoParams?
        get() = currentParams

    fun cleanup() {
        closeCamera()
        NSNotificationCenter.defaultCenter.removeObserver(this)
        // Main thread, like its begin counterpart in observeOrientationChanges().
        if (platform.Foundation.NSThread.isMainThread) UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
        else platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
            UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
        }
    }
}

private const val CAMERA_SCHEME = "camera://"

/** Orientation every outgoing frame is captured in; jami-client-ios uses the same. */
private val OUTGOING_CONNECTION_ORIENTATION = platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft

/**
 * Rotation, in degrees, the receiver applies to an outgoing frame captured in
 * [OUTGOING_CONNECTION_ORIENTATION] to show it upright, for a phone held in [orientation] —
 * the capture orientation [captureOrientationFor] derives from the device orientation.
 *
 * The values are jami-client-ios CameraOrientationState.outgoingAngle, but that table is keyed
 * on the native client's own device→capture mapping, which does *not* swap the landscape cases
 * (device landscape-left → capture landscape-left). [captureOrientationFor] follows Apple and
 * swaps them, so the two landscape rows are swapped here to land on the same angles: the
 * frame is upright as captured when the phone is held device-landscape-right (capture
 * landscape-left) and upside down the other way round. The front camera's portrait angles
 * differ because its sensor faces the other way.
 */
internal fun outgoingFrameAngle(
    orientation: platform.AVFoundation.AVCaptureVideoOrientation,
    isFront: Boolean,
): Int = when (orientation) {
    AVCaptureVideoOrientationPortrait -> if (isFront) 270 else 90
    AVCaptureVideoOrientationPortraitUpsideDown -> if (isFront) 90 else 270
    AVCaptureVideoOrientationLandscapeRight -> 180 // device landscape-left
    else -> 0 // capture landscape-left = device landscape-right: upright as captured
}

/**
 * iOS-specific video parameters.
 */
data class IOSVideoParams(
    val cameraId: String,
    val width: Int,
    val height: Int,
    val frameRate: Int
)

/**
 * Camera state enum.
 */
enum class CameraState {
    IDLE,
    OPENING,
    READY,
    CAPTURING,
    ERROR
}

/**
 * Frame event for video capture.
 */
data class FrameEvent(
    val cameraId: String,
    val width: Int,
    val height: Int
)

/**
 * The capture orientation for a device orientation, or null for one that says nothing about how
 * the phone is held — face up, face down, unknown. The caller keeps its previous value then;
 * this used to fall back to portrait, so a landscape phone laid on a table turned the outgoing
 * video sideways. Landscape is mirrored between the two enums by Apple's definition.
 */
internal fun captureOrientationFor(orientation: UIDeviceOrientation): platform.AVFoundation.AVCaptureVideoOrientation? =
    when (orientation) {
        UIDeviceOrientation.UIDeviceOrientationPortrait -> AVCaptureVideoOrientationPortrait
        UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
        UIDeviceOrientation.UIDeviceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeRight
        UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeLeft
        else -> null
    }
