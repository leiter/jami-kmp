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

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Size
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.WindowManager
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import net.jami.daemon.JamiService
import net.jami.model.Call
import net.jami.model.Conference
import net.jami.utils.Log
import java.lang.ref.WeakReference

/**
 * Android implementation of HardwareService.
 *
 * Provides audio management via AudioManager, video capture via CameraService,
 * and connectivity monitoring via ConnectivityManager.
 *
 * Ported from: jami-client-android HardwareServiceImpl.kt
 */
class AndroidHardwareService(
    private val context: Context
) : HardwareService {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Camera service for video capture
    private val cameraService = CameraService(context)

    // Video input tracking (shared memory)
    private val videoInputs = mutableMapOf<String, VideoInputInfo>()

    // Event flows
    private val _videoEvents = MutableSharedFlow<VideoEvent>()
    private val _cameraEvents = MutableSharedFlow<VideoEvent>()
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(HardwareService.STATE_INTERNAL)
    private val _connectivityState = MutableStateFlow(isNetworkConnected())

    private var speakerphoneOn = false
    private var logging = false
    private var pendingScreenShareProjection: MediaProjection? = null
    private var previewCamId: String? = null

    // Surface references
    private var cameraPreviewSurface = WeakReference<TextureView>(null)
    private var fullScreenPreviewSurface = WeakReference<TextureView>(null)
    private var cameraPreviewCall = WeakReference<Conference>(null)

    override val videoEvents: Flow<VideoEvent> = _videoEvents.asSharedFlow()
    override val cameraEvents: Flow<VideoEvent> = _cameraEvents.asSharedFlow()
    override val bluetoothEvents: Flow<BluetoothEvent> = _bluetoothEvents.asSharedFlow()
    override val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    override val connectivityState: Flow<Boolean> = _connectivityState.asStateFlow()

    init {
        logging = sharedPreferences.getBoolean(LOGGING_ENABLED_KEY, false)
        registerNetworkCallback()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Connectivity
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private fun isNetworkConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @SuppressLint("MissingPermission")
    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _connectivityState.value = true
            }

            override fun onLost(network: Network) {
                _connectivityState.value = isNetworkConnected()
            }
        })
    }

    override fun connectivityChanged(isConnected: Boolean) {
        _connectivityState.value = isConnected
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Audio Management
    // ══════════════════════════════════════════════════════════════════════════

    override fun getAudioState(conf: Conference): Flow<AudioState> = audioState

    override fun updateAudioState(
        conference: Conference?,
        call: Call,
        incomingCall: Boolean,
        isOngoingVideo: Boolean
    ) {
        val state = call.callStatus
        val callEnded = state == Call.CallStatus.HUNGUP ||
                state == Call.CallStatus.FAILURE ||
                state == Call.CallStatus.OVER

        if (callEnded) {
            closeAudioState()
            return
        }

        when (state) {
            Call.CallStatus.RINGING -> {
                if (incomingCall) {
                    audioManager.mode = AudioManager.MODE_RINGTONE
                }
            }
            Call.CallStatus.CURRENT -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                speakerphoneOn = isOngoingVideo || isSpeakerphoneOn()
                updateSpeakerphone(speakerphoneOn)
            }
            else -> { }
        }
    }

    override fun closeAudioState() {
        abandonAudioFocus()
    }

    override fun isSpeakerphoneOn(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn
        }
    }

    override fun toggleSpeakerphone(conf: Conference, enabled: Boolean) {
        speakerphoneOn = enabled
        updateSpeakerphone(enabled)
    }

    private fun updateSpeakerphone(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val targetType = if (enabled) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            devices.find { it.type == targetType }?.let { device ->
                audioManager.setCommunicationDevice(device)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }

        val output = if (enabled) HardwareService.OUTPUT_SPEAKERS else HardwareService.OUTPUT_INTERNAL
        _audioState.value = AudioState(output, getAvailableOutputs())
    }

    @Suppress("DEPRECATION")
    private fun getAvailableOutputs(): List<AudioOutput> {
        val outputs = mutableListOf<AudioOutput>()
        outputs.add(HardwareService.OUTPUT_INTERNAL)

        if (hasSpeakerphone()) {
            outputs.add(HardwareService.OUTPUT_SPEAKERS)
        }

        if (audioManager.isWiredHeadsetOn) {
            outputs.add(HardwareService.OUTPUT_WIRED)
        }

        if (audioManager.isBluetoothScoOn || audioManager.isBluetoothA2dpOn) {
            outputs.add(HardwareService.OUTPUT_BLUETOOTH)
        }

        return outputs
    }

    private fun hasSpeakerphone(): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            return false
        }
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    override fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        _audioState.value = HardwareService.STATE_INTERNAL
    }

    override fun hasMicrophone(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    override fun shouldPlaySpeaker(): Boolean = speakerphoneOn

    // ══════════════════════════════════════════════════════════════════════════
    // Video/Camera Management
    // ══════════════════════════════════════════════════════════════════════════

    override suspend fun initVideo() {
        cameraService.init()
    }

    override val isVideoAvailable: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
                cameraService.hasCamera()

    override fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean) {
        Log.d(TAG, "decodingStarted $id ${width}x${height}")
        synchronized(videoInputs) {
            videoInputs[id] = VideoInputInfo(id, width, height)
        }
        scope.launch {
            _videoEvents.emit(VideoEvent(id, start = true, started = true, width = width, height = height))
        }

        // Try to start video if we have a surface
        synchronized(videoSurfaces) {
            videoSurfaces[id]?.get()?.let { holder ->
                val info = videoInputs[id]
                if (info != null && info.window == 0L) {
                    info.window = startVideo(id, holder.surface, width, height)
                }
            }
        }
    }

    override fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {
        Log.d(TAG, "decodingStopped $id")
        synchronized(videoInputs) {
            videoInputs[id]?.let { info ->
                if (info.window != 0L) {
                    try {
                        stopVideo(id, info.window)
                    } catch (e: Exception) {
                        Log.e(TAG, "decodingStopped error", e)
                    }
                    info.window = 0
                }
            }
            videoInputs.remove(id)
        }
        scope.launch {
            _videoEvents.emit(VideoEvent(id, start = false, started = false))
        }
    }

    override fun hasInput(id: String): Boolean = synchronized(videoInputs) {
        videoInputs.containsKey(id)
    }

    override fun getCameraInfo(camId: String, formats: MutableList<Int>, sizes: MutableList<Int>, rates: MutableList<Int>) {
        val minVideoSize = parseResolution(DEFAULT_RESOLUTION)
        cameraService.getCameraInfo(camId, formats, sizes, rates, minVideoSize)
    }

    override fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {
        Log.d(TAG, "setParameters: $camId, $format, ${width}x${height}, $rate")
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay.rotation
        cameraService.setParameters(camId, format, width, height, rate, rotation)
    }

    override fun startCameraPreview(videoPreview: Boolean) {
        val surface = (if (videoPreview) fullScreenPreviewSurface else cameraPreviewSurface).get()
        if (surface == null || !surface.isAvailable || surface.surfaceTexture == null) {
            Log.e(TAG, "startCameraPreview: Surface not ready")
            return
        }

        val conf = cameraPreviewCall.get()
        val useHardwareCodec = true // Could be from preferences
        previewCamId = cameraService.switchInput(true) ?: return
        val videoParams = cameraService.getParams(previewCamId) ?: return

        cameraService.openCamera(
            videoParams,
            surface,
            object : CameraService.CameraListener {
                override fun onOpened() {
                    Log.d(TAG, "Camera opened successfully")
                    scope.launch {
                        _cameraEvents.emit(VideoEvent(
                            sinkId = videoParams.id,
                            start = true,
                            started = true,
                            width = videoParams.size.width,
                            height = videoParams.size.height
                        ))
                    }
                }

                override fun onError() {
                    Log.e(TAG, "Camera open error")
                    scope.launch {
                        _cameraEvents.emit(VideoEvent(sinkId = videoParams.id, start = false, started = false))
                    }
                }
            },
            useHardwareCodec,
            DEFAULT_RESOLUTION,
            0,
            !videoPreview,
            videoPreview
        )
    }

    override fun cameraCleanup() {
        previewCamId?.let { camId ->
            cameraService.closeCamera(camId)
        }
        previewCamId = null
    }

    override fun startCapture(camId: String?) {
        val id = camId ?: previewCamId ?: return
        val params = cameraService.getParams(id) ?: return
        params.isCapturing = true
        cameraService.startCodec(params)
    }

    override fun stopCapture(camId: String) {
        cameraService.closeCamera(camId)
    }

    override fun requestKeyFrame(camId: String) {
        cameraService.requestKeyFrame(camId)
    }

    override fun setBitrate(camId: String, bitrate: Int) {
        cameraService.setBitrate(camId, bitrate)
    }

    override fun addVideoSurface(id: String, holder: Any) {
        Log.w(TAG, "addVideoSurface $id")
        if (holder !is SurfaceHolder) {
            Log.e(TAG, "addVideoSurface: holder is not SurfaceHolder")
            return
        }

        synchronized(videoSurfaces) {
            videoSurfaces[id] = WeakReference(holder)
        }

        synchronized(videoInputs) {
            val info = videoInputs[id]
            if (info != null && info.window == 0L) {
                info.window = startVideo(id, holder.surface, info.width, info.height)
            } else if (info == null) {
                Log.i(TAG, "addVideoSurface: no video input for $id")
            }
        }
    }

    override fun updateVideoSurfaceId(currentId: String, newId: String) {
        synchronized(videoSurfaces) {
            val surface = videoSurfaces.remove(currentId)
            surface?.let { videoSurfaces[newId] = it }
        }
    }

    override fun removeVideoSurface(id: String) {
        Log.w(TAG, "removeVideoSurface $id")
        synchronized(videoSurfaces) {
            videoSurfaces.remove(id)
        }
        synchronized(videoInputs) {
            videoInputs[id]?.let { info ->
                if (info.window != 0L) {
                    try {
                        stopVideo(id, info.window)
                    } catch (e: Exception) {
                        Log.e(TAG, "removeVideoSurface error", e)
                    }
                    info.window = 0
                }
            }
        }
    }

    override fun addPreviewVideoSurface(holder: Any, conference: Conference?) {
        if (holder is TextureView) {
            cameraPreviewSurface = WeakReference(holder)
            cameraPreviewCall = WeakReference(conference)
        }
    }

    override fun updatePreviewVideoSurface(conference: Conference) {
        cameraPreviewCall = WeakReference(conference)
    }

    override fun removePreviewVideoSurface() {
        cameraPreviewSurface = WeakReference(null)
        cameraPreviewCall = WeakReference(null)
    }

    override fun addFullScreenPreviewSurface(holder: Any) {
        if (holder is TextureView) {
            fullScreenPreviewSurface = WeakReference(holder)
        }
    }

    override fun removeFullScreenPreviewSurface() {
        fullScreenPreviewSurface = WeakReference(null)
    }

    override fun changeCamera(setDefaultCamera: Boolean): String? {
        return cameraService.switchInput(setDefaultCamera)
    }

    override fun setPreviewSettings() {
        // Apply preview settings from preferences
    }

    override fun hasCamera(): Boolean = cameraService.hasCamera()

    override fun cameraCount(): Int = cameraService.getCameraCount()

    override val maxResolutions: Flow<Pair<Int?, Int?>>
        get() = cameraService.maxResolutions

    override val isPreviewFromFrontCamera: Boolean
        get() = cameraService.isPreviewFromFrontCamera

    override fun unregisterCameraDetectionCallback() {
        cameraService.unregisterCameraDetectionCallback()
    }

    override fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> {
        var registered = JamiService.registerVideoCallback(id, windowId)

        return videoEvents
            .filter { it.sinkId == id }
            .map { event ->
                if (!registered && event.start && !event.started) {
                    if (JamiService.registerVideoCallback(id, windowId)) {
                        registered = true
                    }
                } else if (!event.start && event.started) {
                    registered = false
                }
                Pair(event.width, event.height)
            }
            .onStart {
                val initialSize = synchronized(videoInputs) {
                    videoInputs[id]?.let { info -> Pair(info.width, info.height) }
                }
                initialSize?.let { emit(it) }
            }
    }

    override suspend fun getSinkSize(id: String): Pair<Int, Int> {
        return synchronized(videoInputs) {
            videoInputs[id]?.let { Pair(it.width, it.height) } ?: Pair(0, 0)
        }
    }

    override fun setDeviceOrientation(rotation: Int) {
        cameraService.setOrientation(rotation)
    }

    override fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long {
        Log.i(TAG, "startVideo $inputId ${width}x${height}")
        val inputWindow = JamiService.acquireNativeWindow(surface)
        if (inputWindow != 0L) {
            JamiService.setNativeWindowGeometry(inputWindow, width, height)
            JamiService.registerVideoCallback(inputId, inputWindow)
        }
        return inputWindow
    }

    override fun stopVideo(inputId: String, inputWindow: Long) {
        Log.i(TAG, "stopVideo $inputId $inputWindow")
        if (inputWindow != 0L) {
            JamiService.unregisterVideoCallback(inputId, inputWindow)
            JamiService.releaseNativeWindow(inputWindow)
        }
    }

    override fun switchInput(accountId: String, callId: String, uri: String) {
        JamiService.switchInput(accountId, callId, uri)
    }

    override fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>) {
        // Apply camera settings from map
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Media Handlers / Screen Share
    // ══════════════════════════════════════════════════════════════════════════

    override fun startMediaHandler(mediaHandlerId: String?) {
        // TODO: Implement media handler plugin support
    }

    override fun stopMediaHandler() {
        // TODO: Implement media handler plugin support
    }

    override fun setPendingScreenShareProjection(screenCaptureSession: Any?) {
        pendingScreenShareProjection = screenCaptureSession as? MediaProjection
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Logging
    // ══════════════════════════════════════════════════════════════════════════

    override val isLogging: Boolean get() = logging

    override fun startLogs(): Flow<List<String>> {
        logging = true
        return MutableSharedFlow()
    }

    override fun stopLogs() {
        logging = false
    }

    override fun logMessage(message: String) {
        // Log message handling
    }

    override fun saveLoggingState(enabled: Boolean) {
        logging = enabled
        sharedPreferences.edit {
            putBoolean(LOGGING_ENABLED_KEY, enabled)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper Classes
    // ══════════════════════════════════════════════════════════════════════════

    private data class VideoInputInfo(
        val id: String,
        val width: Int,
        val height: Int,
        var window: Long = 0
    )

    companion object {
        private const val TAG = "AndroidHardwareService"
        private const val PREFS_NAME = "jami_hardware"
        private const val LOGGING_ENABLED_KEY = "logging_enabled"
        private const val DEFAULT_RESOLUTION = 720

        private val videoSurfaces = HashMap<String, WeakReference<SurfaceHolder>>()

        private fun parseResolution(resolution: Int): Size {
            return when {
                resolution >= 2160 -> Size(3840, 2160)
                resolution >= 1440 -> Size(2560, 1440)
                resolution >= 1080 -> Size(1920, 1080)
                resolution >= 720 -> Size(1280, 720)
                resolution >= 480 -> Size(720, 480)
                else -> Size(480, 320)
            }
        }
    }
}
