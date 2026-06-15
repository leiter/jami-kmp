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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
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
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
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

    suspend fun openCamera(cameraId: String? = null): Boolean = withContext(Dispatchers.IO) {
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

            // Add video output
            val output = AVCaptureVideoDataOutput()
            output.alwaysDiscardsLateVideoFrames = true
            output.setSampleBufferDelegate(sampleBufferDelegate, videoQueue)

            if (!session.canAddOutput(output)) {
                Log.e(tag, "Cannot add video output")
                _cameraState.value = CameraState.ERROR
                return@withContext false
            }
            session.addOutput(output)
            videoOutput = output

            // Configure video connection orientation
            output.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
                if (connection.isVideoOrientationSupported()) {
                    connection.videoOrientation = currentVideoOrientation()
                }
                if (connection.isVideoMirroringSupported() &&
                    targetCamera.position == AVCaptureDevicePositionFront) {
                    connection.automaticallyAdjustsVideoMirroring = false
                    connection.setVideoMirrored(true)
                }
            }

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

        scope.launch(Dispatchers.IO) {
            session.startRunning()
            _cameraState.value = CameraState.CAPTURING
            Log.d(tag, "Capture started")
        }
    }

    fun stopCapture() {
        val session = captureSession ?: return
        if (!session.isRunning()) return

        scope.launch(Dispatchers.IO) {
            session.stopRunning()
            _cameraState.value = CameraState.READY
            Log.d(tag, "Capture stopped")
        }
    }

    fun closeCamera() {
        stopCapture()

        currentParams?.let { params ->
            daemonBridge.removeVideoDevice(params.cameraId)
        }

        captureSession?.let { session ->
            session.beginConfiguration()
            currentInput?.let { session.removeInput(it) }
            videoOutput?.let { session.removeOutput(it) }
            session.commitConfiguration()
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

    suspend fun switchCamera(): String? = withContext(Dispatchers.IO) {
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

                // Update video connection
                videoOutput?.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
                    if (connection.isVideoOrientationSupported()) {
                        connection.videoOrientation = currentVideoOrientation()
                    }
                    if (connection.isVideoMirroringSupported()) {
                        connection.automaticallyAdjustsVideoMirroring = false
                        connection.setVideoMirrored(newCamera.position == AVCaptureDevicePositionFront)
                    }
                }

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

    private fun observeOrientationChanges() {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceOrientationDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            updateVideoOrientation()
        }
        UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
    }

    private fun updateVideoOrientation() {
        videoOutput?.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
            if (connection.isVideoOrientationSupported()) {
                connection.videoOrientation = currentVideoOrientation()
            }
        }
        previewLayer?.connection?.let { connection ->
            if (connection.isVideoOrientationSupported()) {
                connection.videoOrientation = currentVideoOrientation()
            }
        }
    }

    private fun currentVideoOrientation(): platform.AVFoundation.AVCaptureVideoOrientation {
        return when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientation.UIDeviceOrientationPortrait -> AVCaptureVideoOrientationPortrait
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeLeft
            else -> AVCaptureVideoOrientationPortrait
        }
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

            // Extract frame data and pass to daemon
            try {
                val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return

                // Lock base address to access raw pixel data
                platform.CoreVideo.CVPixelBufferLockBaseAddress(pixelBuffer, 0u.toULong())

                val baseAddress = platform.CoreVideo.CVPixelBufferGetBaseAddress(pixelBuffer)
                if (baseAddress != null) {
                    val bytesPerRow = platform.CoreVideo.CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
                    val frameSize = bytesPerRow * height * 3 / 2  // NV12: 1.5 bytes per pixel

                    // Extract frame data from native memory using typed pointer
                    val typedPtr = baseAddress.reinterpret<ByteVar>()
                    val frameData = ByteArray(frameSize) { typedPtr[it] }

                    // Forward NV12 frame to daemon
                    daemonBridge.captureVideoFrame(
                        uri = "camera://${params.cameraId}",
                        data = frameData,
                        rotation = deviceOrientation
                    )
                }

                // Unlock base address
                platform.CoreVideo.CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u.toULong())
            } catch (e: Exception) {
                Log.w(tag, "Failed to forward frame to daemon: ${e.message}")
            }

            // Emit frame event for UI
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
        UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
    }
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
