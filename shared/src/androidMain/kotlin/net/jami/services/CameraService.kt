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

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraManager.AvailabilityCallback
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.ImageReader.OnImageAvailableListener
import android.media.MediaCodec.CodecException
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.projection.MediaProjection
import android.os.*
import android.util.DisplayMetrics
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.daemon.JamiService
import net.jami.model.CameraFacing
import net.jami.model.DeviceParams
import net.jami.model.VideoDevices
import net.jami.model.VideoParams
import net.jami.utils.Log
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.Executor
import kotlin.math.abs

/**
 * Android Camera2 API service for video capture and encoding.
 *
 * Manages camera devices, hardware encoding via MediaCodec, and screen sharing
 * via MediaProjection. Converted from RxJava to Kotlin Coroutines/Flow.
 *
 * Ported from: jami-client-android CameraService.kt
 */
class CameraService(private val context: Context) {

    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
    private val videoParams = HashMap<String, AndroidVideoParams>()
    private val nativeParams = mutableMapOf<String, DeviceParams>()

    private val handlerThread = HandlerThread("CameraServiceThread")
    private val videoLooper: Looper
        get() = handlerThread.apply { if (state == Thread.State.NEW) start() }.looper
    private val videoHandler: Handler by lazy { Handler(videoLooper) }
    private val videoExecutor: Executor = Executor { command -> videoHandler.post(command) }

    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(null to null)
    val maxResolutions: Flow<Pair<Int?, Int?>> = _maxResolutions.asStateFlow()

    private var devices: VideoDevices? = null
    private var projectionDisposable: Runnable? = null

    val handler: Handler
        get() = videoHandler

    private val availabilityCallback = object : AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            Log.w(TAG, "onCameraAvailable $cameraId")
            try {
                filterCompatibleCamera(arrayOf(cameraId), manager!!).forEach { camera ->
                    val devs = devices ?: return
                    synchronized(addedDevices) {
                        if (!devs.cameras.contains(camera.first)) {
                            when (camera.second.get(CameraCharacteristics.LENS_FACING)) {
                                CameraCharacteristics.LENS_FACING_FRONT -> if (devs.cameraFront == null) {
                                    addCameraDevice(devs, camera.first)
                                    devs.cameraFront = camera.first
                                }
                                CameraCharacteristics.LENS_FACING_BACK -> if (devs.cameraBack == null) {
                                    addCameraDevice(devs, camera.first)
                                    devs.cameraBack = camera.first
                                }
                                else -> addCameraDevice(devs, camera.first)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error handling camera", e)
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            val devs = devices
            if (devs == null || devs.currentId == null || devs.currentId != cameraId) {
                synchronized(addedDevices) {
                    devs?.cameras?.remove(cameraId)
                    if (devs?.cameraFront == cameraId) devs.cameraFront = null
                    if (devs?.cameraBack == cameraId) devs.cameraBack = null
                    if (addedDevices.remove(cameraId)) {
                        Log.w(TAG, "onCameraUnavailable $cameraId")
                        JamiService.removeVideoDevice(cameraId)
                    }
                }
            }
        }
    }

    private fun addCameraDevice(devs: VideoDevices, cameraId: String) {
        devs.cameras.add(cameraId)
        if (addedDevices.add(cameraId)) {
            JamiService.addVideoDevice(cameraId)
        }
    }

    fun switchInput(setDefaultCamera: Boolean): String? {
        return devices?.switchInput(setDefaultCamera)
    }

    fun getParams(camId: String?): AndroidVideoParams? {
        Log.w(TAG, "getParams() $camId")
        if (camId != null) {
            return videoParams[camId]
        } else if (!devices?.cameras.isNullOrEmpty()) {
            Log.w(TAG, "getParams() fallback")
            devices!!.currentId = devices!!.cameras[0]
            return videoParams[devices!!.currentId]
        }
        return null
    }

    fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int, rotation: Int) {
        Log.w(TAG, "setParameters() $camId $format $width $height $rate $rotation")
        val deviceParams = nativeParams[camId]
        if (deviceParams == null) {
            Log.w(TAG, "setParameters() unable to find device")
            return
        }
        var params = videoParams[camId]
        if (params == null) {
            params = AndroidVideoParams(
                camId,
                Size(deviceParams.width, deviceParams.height),
                rate
            )
            videoParams[camId] = params
        } else {
            params.size = Size(deviceParams.width, deviceParams.height)
            params.rate = rate
        }
        params.rotation = if (camId == VideoDevices.SCREEN_SHARING) {
            0
        } else {
            getCameraDisplayRotation(deviceParams, rotation)
        }
        val r = params.rotation
        videoHandler.post { JamiService.setDeviceOrientation(camId, r) }
    }

    fun setOrientation(rotation: Int) {
        Log.w(TAG, "setOrientation() $rotation")
        for (id in cameraIds()) setDeviceOrientation(id, rotation)
    }

    private fun setDeviceOrientation(camId: String, screenRotation: Int) {
        Log.w(TAG, "setDeviceOrientation() $camId $screenRotation")
        val deviceParams = nativeParams[camId]
        var rotation = 0
        if (deviceParams != null) {
            rotation = getCameraDisplayRotation(deviceParams, screenRotation)
        }
        videoParams[camId]?.rotation = rotation
        JamiService.setDeviceOrientation(camId, rotation)
    }

    fun getCameraInfo(
        camId: String,
        formats: MutableList<Int>,
        sizes: MutableList<Int>,
        rates: MutableList<Int>,
        minVideoSize: Size
    ) {
        Log.d(TAG, "getCameraInfo: $camId min size: $minVideoSize")
        rates.clear()
        val p = getCameraInfo(camId, minVideoSize)
        sizes.add(p.width)
        sizes.add(p.height)
        rates.add(p.rate.toInt())
        nativeParams[camId] = p
    }

    private val maxResolution: Size?
        get() {
            var max: Size? = null
            for (deviceParams in nativeParams.values) {
                val current = Size(deviceParams.maxWidth, deviceParams.maxHeight)
                if (max == null || max.surface() < current.surface())
                    max = current
            }
            return max
        }

    val isPreviewFromFrontCamera: Boolean
        get() = nativeParams.size == 1 || devices?.let { it.currentId != null && it.currentId == it.cameraFront } == true

    val previewSettings: Map<String, Map<String, String>>
        get() {
            val camSettings = mutableMapOf<String, Map<String, String>>()
            for (id in cameraIds()) {
                nativeParams[id]?.let { params -> camSettings[id] = params.toMap() }
            }
            return camSettings
        }

    fun hasCamera(): Boolean = getCameraCount() > 0

    suspend fun init() {
        if (manager == null) {
            Log.e(TAG, "Camera manager unavailable")
            _maxResolutions.value = null to null
            return
        }

        try {
            val devs = loadDevices(manager)
            synchronized(addedDevices) {
                val old = devices
                devices = devs

                // Remove old devices
                old?.cameras?.forEach { oldId ->
                    if (!devs.cameras.contains(oldId)) {
                        if (addedDevices.remove(oldId)) {
                            JamiService.removeVideoDevice(oldId)
                        }
                    }
                }

                // Add new devices
                for (camera in devs.cameras) {
                    Log.w(TAG, "JamiService.addVideoDevice init $camera")
                    if (addedDevices.add(camera)) {
                        JamiService.addVideoDevice(camera)
                    }
                }

                // Add screen sharing device
                if (addedDevices.add(VideoDevices.SCREEN_SHARING)) {
                    JamiService.addVideoDevice(VideoDevices.SCREEN_SHARING)
                }

                // Set default device
                devs.currentId?.let { currentId ->
                    JamiService.setDefaultDevice(currentId)
                }
            }

            val max = maxResolution
            Log.w(TAG, "Found max resolution: $max")
            _maxResolutions.value = if (max == null) null to null else max.width to max.height
            manager.registerAvailabilityCallback(availabilityCallback, videoHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing video devices", e)
            _maxResolutions.value = null to null
        }
    }

    private fun loadDevices(manager: CameraManager): VideoDevices {
        val devs = VideoDevices()
        val cameras = filterCompatibleCamera(manager.cameraIdList, manager)
        val backCamera = filterCameraIdsFacing(cameras, CameraCharacteristics.LENS_FACING_BACK).firstOrNull()
        val frontCamera = filterCameraIdsFacing(cameras, CameraCharacteristics.LENS_FACING_FRONT).firstOrNull()
        val externalCameras = filterCameraIdsFacing(cameras, CameraCharacteristics.LENS_FACING_EXTERNAL)

        if (frontCamera != null) devs.cameras.add(frontCamera)
        if (backCamera != null) devs.cameras.add(backCamera)
        devs.cameras.addAll(externalCameras)
        if (devs.cameras.isNotEmpty()) devs.currentId = devs.cameras[0]
        devs.cameraFront = frontCamera
        devs.cameraBack = backCamera
        Log.w(TAG, "Loading video devices: found ${devs.cameras.size}")
        return devs
    }

    interface CameraListener {
        fun onOpened()
        fun onError()
    }

    fun closeCamera(camId: String) {
        videoParams[camId]?.let { params ->
            params.cameraSession?.let { session ->
                try { session.stopRepeating() } catch (_: Exception) {}
                try { session.abortCaptures() } catch (_: Exception) {}
                try { session.close() } catch (_: Exception) {}
                params.cameraSession = null
            }
            params.camera?.let { camera ->
                camera.close()
                params.camera = null
            }
            params.projection?.let { mediaProjection ->
                projectionDisposable?.let { videoHandler.removeCallbacks(it) }
                val runnable = Runnable {
                    if (!params.isCapturing) {
                        params.projection = null
                        mediaProjection.stop()
                    }
                }
                projectionDisposable = runnable
                videoHandler.postDelayed(runnable, 5000)
            }
            params.isCapturing = false
        }
    }

    fun requestKeyFrame(camId: String) {
        val cam = camId.substringAfter("camera://", missingDelimiterValue = camId)
        Log.w(TAG, "requestKeyFrame() $cam")
        try {
            videoParams[cam]?.mediaCodec?.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            })
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Unable to send keyframe request", e)
        }
    }

    fun setBitrate(camId: String, bitrate: Int) {
        Log.w(TAG, "setBitrate() $camId $bitrate")
        try {
            videoParams[camId]?.mediaCodec?.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate * 1024)
            })
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Unable to set bitrate", e)
        }
    }

    fun cameraIds(): List<String> = devices?.cameras ?: emptyList()

    fun getCameraCount(): Int = try {
        devices?.cameras?.size ?: manager?.cameraIdList?.size ?: 0
    } catch (e: CameraAccessException) {
        0
    }

    private fun getCameraInfo(camId: String, minVideoSize: Size): DeviceParams {
        if (manager == null) return DeviceParams()

        try {
            if (camId == VideoDevices.SCREEN_SHARING) {
                val metrics = context.resources.displayMetrics
                val size = resolutionFit(minVideoSize, Size(metrics.widthPixels, metrics.heightPixels))
                Log.d(TAG, "getCameraInfo >> Screen sharing resolution: $size")
                return DeviceParams(
                    width = size.width,
                    height = size.height,
                    rate = 24
                )
            }

            val cc = manager.getCameraCharacteristics(camId)
            val streamConfigs = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return DeviceParams()
            val rawSizes = streamConfigs.getOutputSizes(ImageFormat.YUV_420_888)
            var newSize = rawSizes[0]
            var maxSize = Size(0, 0)

            for (s in rawSizes) {
                if (s.width < s.height) continue
                if (s == minVideoSize ||
                    (if (newSize.height < minVideoSize.height) s.height > newSize.height
                    else s.height >= minVideoSize.height && s.height < newSize.height) ||
                    (if (s.height == newSize.height && newSize.width < minVideoSize.width)
                        s.width > newSize.width
                    else s.width >= minVideoSize.width && s.width < newSize.width)
                ) {
                    if (s.surface() > maxSize.surface()) {
                        maxSize = s
                    }
                    newSize = s
                }
            }

            val minDuration = streamConfigs.getOutputMinFrameDuration(ImageFormat.YUV_420_888, newSize)
            val fps = 1000e9 / minDuration
            val orientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val facing = when (cc.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
                CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.BACK
                CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraFacing.EXTERNAL
                else -> CameraFacing.UNKNOWN
            }

            return DeviceParams(
                width = newSize.width,
                height = newSize.height,
                maxWidth = maxSize.width,
                maxHeight = maxSize.height,
                rate = fps.toLong(),
                facing = facing,
                orientation = orientation
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting camera info", e)
            return DeviceParams()
        }
    }

    fun unregisterCameraDetectionCallback() {
        manager?.unregisterAvailabilityCallback(availabilityCallback)
    }

    // ==================== Hardware Encoding ====================

    private fun openEncoder(
        videoParams: AndroidVideoParams,
        mimeType: String,
        handler: Handler,
        resolution: Int,
        bitrate: Int
    ): Pair<MediaCodec?, Surface?> {
        Log.d(TAG, "openEncoder $mimeType resolution: ${videoParams.size} bitrate: $bitrate")

        val bitrateValue = if (bitrate == 0) {
            if (resolution >= 720) 192 * 8 * 1024 else 100 * 8 * 1024
        } else {
            bitrate * 8 * 1024
        }

        val frameRate = videoParams.rate
        val format = MediaFormat.createVideoFormat(mimeType, videoParams.size.width, videoParams.size.height).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateValue)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
        }

        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codecName = codecs.findEncoderForFormat(format) ?: return Pair(null, null)

        var encoderInput: Surface? = null
        var codec: MediaCodec? = null

        try {
            codec = MediaCodec.createByCodecName(codecName)
            codec.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrateValue)
            })
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderInput = codec.createInputSurface()

            val callback = object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    try {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) {
                            val isConfigFrame = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            val buffer = codec.getOutputBuffer(index)

                            if (isConfigFrame) {
                                buffer?.let { outputBuffer ->
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    videoParams.codecData = ByteBuffer.allocateDirect(info.size).apply {
                                        put(outputBuffer)
                                        rewind()
                                    }
                                    Log.i(TAG, "Cached codec data (SPS/PPS)")
                                }
                            } else {
                                val isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0

                                if (isKeyFrame || videoParams.forceKeyFrame) {
                                    videoParams.forceKeyFrame = false
                                    videoParams.codecData?.let { data ->
                                        JamiService.captureVideoPacket(
                                            videoParams.inputUri,
                                            data,
                                            data.capacity(),
                                            0,
                                            false,
                                            info.presentationTimeUs,
                                            videoParams.rotation
                                        )
                                    }
                                }

                                JamiService.captureVideoPacket(
                                    videoParams.inputUri,
                                    buffer,
                                    info.size,
                                    info.offset,
                                    isKeyFrame,
                                    info.presentationTimeUs,
                                    videoParams.rotation
                                )
                            }
                            codec.releaseOutputBuffer(index, false)
                        }
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "MediaCodec buffer error", e)
                    }
                }

                override fun onError(codec: MediaCodec, e: CodecException) {
                    Log.e(TAG, "MediaCodec onError", e)
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    Log.d(TAG, "MediaCodec onOutputFormatChanged $format")
                }
            }
            codec.setCallback(callback, handler)

        } catch (e: Exception) {
            Log.e(TAG, "Unable to open codec", e)
            codec?.release()
            encoderInput?.release()
            return Pair(null, null)
        }

        return Pair(codec, encoderInput)
    }

    // ==================== Screen Sharing ====================

    private fun getSmallerResolution(screenSize: Size): Size {
        val flip = screenSize.height > screenSize.width
        val surface = screenSize.surface()
        for (resolution in VIDEO_RESOLUTIONS) {
            if (resolution.surface() < surface) {
                return resolution.flip(flip).fit(screenSize).round()
            }
        }
        return VIDEO_SIZE_DEFAULT.flip(flip)
    }

    private fun createVirtualDisplay(
        params: AndroidVideoParams,
        projection: MediaProjection,
        surface: TextureView,
        metrics: DisplayMetrics
    ): Pair<MediaCodec?, VirtualDisplay>? {
        val screenDensity = metrics.densityDpi
        val handler = videoHandler

        if (params.rate == 0) params.rate = 24

        var r = openEncoder(params, MediaFormat.MIMETYPE_VIDEO_AVC, handler, params.size.width, 0)
        if (r.first == null) {
            Log.e(TAG, "Error opening encoder, trying lower resolution")
            while (params.size.width > 320) {
                val res = getSmallerResolution(params.size)
                Log.d(TAG, "Resolution reduced from ${params.size} to $res")
                params.size = res
                r = openEncoder(params, MediaFormat.MIMETYPE_VIDEO_AVC, handler, params.size.width, 0)
                if (r.first != null) break
            }
            if (r.first == null) {
                Log.e(TAG, "createVirtualDisplay failed, unable to open encoder")
                return null
            }
        }

        val encoderSurface = r.second
        val codec = r.first
        codec?.start()
        Log.d(TAG, "createVirtualDisplay success, resolution: ${params.size}")

        return try {
            Pair(codec, projection.createVirtualDisplay(
                "ScreenSharing",
                params.size.width,
                params.size.height,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                encoderSurface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() { Log.w(TAG, "VirtualDisplay.onPaused") }
                    override fun onResumed() { Log.w(TAG, "VirtualDisplay.onResumed") }
                    override fun onStopped() {
                        Log.w(TAG, "VirtualDisplay.onStopped")
                        encoderSurface?.release()
                        codec?.release()
                    }
                },
                handler
            )!!)
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating virtual display", e)
            codec?.stop()
            codec?.release()
            encoderSurface?.release()
            null
        }
    }

    fun startScreenSharing(
        params: AndroidVideoParams,
        mediaProjection: MediaProjection,
        surface: TextureView,
        metrics: DisplayMetrics
    ): Boolean {
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                params.mediaCodec?.let { codec ->
                    codec.signalEndOfInputStream()
                    codec.stop()
                    codec.release()
                    params.mediaCodec = null
                }
                params.display?.release()
                params.display = null
                params.codecStarted = false
            }
        }, videoHandler)

        val r = createVirtualDisplay(params, mediaProjection, surface, metrics)
        if (r != null) {
            projectionDisposable?.let { videoHandler.removeCallbacks(it) }
            params.projection?.stop()

            params.codecStarted = true
            params.mediaCodec = r.first
            params.projection = mediaProjection
            params.display = r.second
            return true
        }
        mediaProjection.stop()
        return false
    }

    // ==================== Camera Capture Session ====================

    private fun buildCaptureRequest(
        camera: CameraDevice,
        previewSurface: Surface,
        captureSurface: Surface?,
        fpsRange: Range<Int>
    ): CaptureRequest {
        return camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            captureSurface?.let { addTarget(it) }
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }.build()
    }

    private fun startMediaCodecIfNeeded(params: AndroidVideoParams) {
        if (!params.codecStarted) {
            try {
                params.forceKeyFrame = true
                params.mediaCodec?.start()
                params.codecStarted = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start codec", e)
            }
        }
    }

    fun startCodec(params: AndroidVideoParams) {
        startMediaCodecIfNeeded(params)

        val session = params.cameraSession
        val camera = params.camera
        val captureSurface = params.captureSurface
        val previewSurface = params.previewSurface

        if (session != null && camera != null && captureSurface != null && previewSurface != null) {
            handler.post {
                try {
                    session.stopRepeating()
                    val cc = manager!!.getCameraCharacteristics(params.id)
                    val availableFpsRanges = cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    val fpsRange = chooseOptimalFpsRange(availableFpsRanges)
                    val request = buildCaptureRequest(camera, previewSurface, captureSurface, fpsRange)
                    session.setRepeatingRequest(request, null, handler)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart repeating request", e)
                }
            }
        }
    }

    fun createCameraSession(
        camera: CameraDevice,
        previewSurface: Surface,
        captureSurface: Surface?,
        codec: MediaCodec?,
        listener: CameraListener,
        fpsRange: Range<Int>,
        videoParams: AndroidVideoParams,
        codecStart: Boolean
    ) {
        try {
            videoParams.apply {
                this.previewSurface = previewSurface
                this.captureSurface = captureSurface
                this.camera = camera
            }

            videoParams.cameraSession?.close()
            videoParams.cameraSession = null

            val request = buildCaptureRequest(
                camera,
                previewSurface,
                if (codecStart) captureSurface else null,
                fpsRange
            )

            val captureCallback = if (codecStart && codec != null) {
                object : CaptureCallback() {
                    override fun onCaptureStarted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        timestamp: Long,
                        frameNumber: Long
                    ) {
                        if (frameNumber != 0L) return
                        startMediaCodecIfNeeded(videoParams)
                    }
                }
            } else null

            val sessionCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    videoParams.cameraSession = session
                    listener.onOpened()
                    try {
                        session.setRepeatingRequest(request, captureCallback, handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set repeating request", e)
                        camera.close()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.w(TAG, "Session configuration failed")
                    listener.onError()
                }
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOfNotNull(previewSurface, captureSurface),
                sessionCallback,
                handler
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            listener.onError()
        }
    }

    @Suppress("DEPRECATION")
    fun openCamera(
        videoParams: AndroidVideoParams,
        surface: TextureView,
        listener: CameraListener,
        hwAccel: Boolean,
        resolution: Int,
        bitrate: Int,
        codecStart: Boolean,
        videoPreview: Boolean
    ) {
        val handler = videoHandler
        try {
            val flip = videoParams.rotation % 180 != 0
            val cc = manager!!.getCameraCharacteristics(videoParams.id)
            val fpsRange = chooseOptimalFpsRange(cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))
            val streamConfigs = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSize = chooseOptimalSize(
                streamConfigs?.getOutputSizes(SurfaceHolder::class.java),
                if (flip) surface.height else surface.width,
                if (flip) surface.width else surface.height,
                videoParams.size.width,
                videoParams.size.height,
                videoParams.size
            )

            Log.d(TAG, "Selected preview size: $previewSize, fps range: $fpsRange, rate: ${videoParams.rate}")

            val texture = surface.surfaceTexture ?: throw IllegalStateException("No SurfaceTexture")
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(texture)

            var tmpReader: ImageReader? = null
            var codec: Pair<MediaCodec?, Surface?> = Pair(null, null)
            var captureSurface: Surface? = null

            if (!videoPreview) {
                codec = if (hwAccel) {
                    openEncoder(videoParams, videoParams.getAndroidCodec(), handler, resolution, bitrate)
                } else {
                    Pair(null, null)
                }

                if (codec.second != null) {
                    videoParams.mediaCodec = codec.first
                } else {
                    tmpReader = ImageReader.newInstance(
                        videoParams.size.width,
                        videoParams.size.height,
                        ImageFormat.YUV_420_888,
                        8
                    )
                    tmpReader.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            JamiService.captureVideoFrame(
                                videoParams.inputUri,
                                image,
                                videoParams.rotation
                            )
                            image.close()
                        }
                    }, handler)
                }
                captureSurface = codec.second ?: tmpReader?.surface
            }

            val camera = videoParams.camera
            if (videoParams.isCapturing && camera != null) {
                createCameraSession(
                    camera, previewSurface, captureSurface, codec.first,
                    listener, fpsRange, videoParams, codecStart
                )
            } else {
                manager.openCamera(videoParams.id, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        try {
                            Log.w(TAG, "onOpened ${videoParams.id}")
                            videoParams.camera = camera
                            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                            createCameraSession(
                                camera, previewSurface, captureSurface,
                                codec.first, listener, fpsRange, videoParams, codecStart
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "onOpened error:", e)
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.w(TAG, "onDisconnected")
                        camera.close()
                        listener.onError()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.w(TAG, "onError: $error")
                        camera.close()
                        listener.onError()
                    }

                    override fun onClosed(camera: CameraDevice) {
                        Log.w(TAG, "onClosed")
                        try {
                            videoParams.mediaCodec?.let { mediaCodec ->
                                if (videoParams.codecStarted) {
                                    mediaCodec.signalEndOfInputStream()
                                }
                                mediaCodec.release()
                                videoParams.mediaCodec = null
                                videoParams.codecStarted = false
                            }
                            codec.second?.release()
                            tmpReader?.close()
                            previewSurface.release()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error stopping codec", e)
                        }
                    }
                }, handler)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while setting preview parameters", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while setting preview parameters", e)
        }
    }

    companion object {
        private const val TAG = "CameraService"
        private const val FPS_MAX = 30
        private const val FPS_TARGET = 15
        private val addedDevices = mutableSetOf<String>()

        // Video resolutions
        private val VIDEO_SIZE_LOW = Size(480, 320)
        private val VIDEO_SIZE_SD = Size(720, 480)
        private val VIDEO_SIZE_HD = Size(1280, 720)
        private val VIDEO_SIZE_FULL_HD = Size(1920, 1080)
        private val VIDEO_SIZE_QUAD_HD = Size(2560, 1440)
        private val VIDEO_SIZE_ULTRA_HD = Size(3840, 2160)
        val VIDEO_SIZE_DEFAULT = VIDEO_SIZE_SD
        val VIDEO_RESOLUTIONS = listOf(
            VIDEO_SIZE_ULTRA_HD, VIDEO_SIZE_QUAD_HD, VIDEO_SIZE_FULL_HD,
            VIDEO_SIZE_HD, VIDEO_SIZE_SD, VIDEO_SIZE_LOW
        )

        private fun getCameraDisplayRotation(device: DeviceParams, screenRotation: Int): Int =
            getCameraDisplayRotation(device.orientation, rotationToDegrees(screenRotation), device.facing)

        private fun getCameraDisplayRotation(sensorOrientation: Int, screenOrientation: Int, cameraFacing: CameraFacing): Int {
            val facingValue = when (cameraFacing) {
                CameraFacing.FRONT -> CameraCharacteristics.LENS_FACING_FRONT
                CameraFacing.BACK -> CameraCharacteristics.LENS_FACING_BACK
                else -> CameraCharacteristics.LENS_FACING_BACK
            }
            val rotation = if (facingValue == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation + screenOrientation + 360) % 360
            } else {
                (sensorOrientation - screenOrientation + 360) % 360
            }
            return (180 - rotation + 180) % 360
        }

        private fun filterCompatibleCamera(cameras: Array<String>, cameraManager: CameraManager) =
            cameras.mapNotNull { id ->
                try {
                    Pair(id, cameraManager.getCameraCharacteristics(id))
                } catch (e: Exception) {
                    null
                }
            }.filter { camera ->
                try {
                    val caps = camera.second.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                        ?: return@filter false
                    caps.any { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE } &&
                            caps.none { it == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME }
                } catch (e: Exception) {
                    false
                }
            }

        private fun filterCameraIdsFacing(cameras: List<Pair<String, CameraCharacteristics>>, facing: Int) =
            cameras.filter { camera -> camera.second.get(CameraCharacteristics.LENS_FACING) == facing }
                .map { it.first }

        private fun chooseOptimalSize(
            choices: Array<Size>?,
            textureViewWidth: Int,
            textureViewHeight: Int,
            maxWidth: Int,
            maxHeight: Int,
            target: Size
        ): Size {
            if (choices == null) return target
            val bigEnough = mutableListOf<Size>()
            val notBigEnough = mutableListOf<Size>()
            val w = target.width
            val h = target.height

            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight &&
                    option.height == option.width * h / w
                ) {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            return when {
                bigEnough.isNotEmpty() -> Collections.min(bigEnough, CompareSizesByArea())
                notBigEnough.isNotEmpty() -> Collections.max(notBigEnough, CompareSizesByArea())
                else -> {
                    Log.e(TAG, "Unable to find suitable preview size")
                    choices[0]
                }
            }
        }

        private fun chooseOptimalFpsRange(ranges: Array<Range<Int>>?): Range<Int> {
            var range: Range<Int>? = null
            if (!ranges.isNullOrEmpty()) {
                for (r in ranges) {
                    if (r.upper > FPS_MAX) continue
                    if (range != null) {
                        val d = abs(r.upper - FPS_TARGET) - abs(range.upper - FPS_TARGET)
                        if (d > 0) continue
                        if (d == 0 && r.lower > range.lower) continue
                    }
                    range = r
                }
                if (range == null) range = ranges[0]
            }
            return range ?: Range(FPS_TARGET, FPS_TARGET)
        }

        private fun rotationToDegrees(rotation: Int) = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        private fun resolutionFit(param: Size, screenSize: Size) =
            param.flip(screenSize.height > screenSize.width)
                .fit(screenSize)
                .round()

        private class CompareSizesByArea : Comparator<Size> {
            override fun compare(lhs: Size, rhs: Size): Int =
                java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }
}

// ==================== Android-specific VideoParams ====================

/**
 * Extended VideoParams with Android-specific fields for camera and codec management.
 */
class AndroidVideoParams(
    val id: String,
    var size: Size,
    var rate: Int
) {
    val inputUri: String = "camera://$id"
    var rotation: Int = 0
    var codec: String? = null
    var isCapturing: Boolean = false

    // Camera2 resources
    var camera: CameraDevice? = null
    var cameraSession: CameraCaptureSession? = null
    var previewSurface: Surface? = null
    var captureSurface: Surface? = null

    // MediaCodec resources
    var mediaCodec: MediaCodec? = null
    var codecData: ByteBuffer? = null
    var codecStarted: Boolean = false
    var forceKeyFrame: Boolean = false

    // Screen sharing resources
    var projection: MediaProjection? = null
    var display: VirtualDisplay? = null

    fun getAndroidCodec(): String = when (codec) {
        "H264" -> MediaFormat.MIMETYPE_VIDEO_AVC
        "H265" -> MediaFormat.MIMETYPE_VIDEO_HEVC
        "VP8" -> MediaFormat.MIMETYPE_VIDEO_VP8
        "VP9" -> MediaFormat.MIMETYPE_VIDEO_VP9
        "MP4V-ES" -> MediaFormat.MIMETYPE_VIDEO_MPEG4
        null -> MediaFormat.MIMETYPE_VIDEO_AVC
        else -> codec!!
    }
}

// ==================== Size Extension Functions ====================

private fun Size.surface() = width * height
private fun Size.contains(w: Int, h: Int) = w < width && h < height
private fun Size.contains(s: Size) = contains(s.width, s.height)
private fun Size.flip(f: Boolean? = null) = if (f == null || f) Size(height, width) else this
private fun Size.round(n: Int = 16) = Size(width / n * n, height / n * n)

private fun Size.fitOrScale(w: Int, h: Int): Size {
    val wRatio = w.toFloat() / width
    val hRatio = h.toFloat() / height
    return if (wRatio < hRatio) {
        Size(w, (height * wRatio).toInt())
    } else {
        Size((width * hRatio).toInt(), h)
    }
}

private fun Size.fit(w: Int, h: Int) = if (contains(w, h)) Size(w, h) else fitOrScale(w, h)
private fun Size.fit(s: Size) = if (contains(s)) s else fitOrScale(s.width, s.height)
