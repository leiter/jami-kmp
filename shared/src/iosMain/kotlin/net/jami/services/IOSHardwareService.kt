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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import net.jami.model.Call
import net.jami.model.Conference
import net.jami.utils.Log
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.AVAudioSessionPortOverride
import platform.AVFAudio.AVAudioSessionPortOverrideNone
import platform.AVFAudio.AVAudioSessionPortOverrideSpeaker
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIView

/**
 * iOS implementation of HardwareService.
 *
 * Integrates IOSCameraService for video capture and uses AVAudioSession
 * for audio routing.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSHardwareService(
    private val daemonBridge: DaemonBridgeApi
) : HardwareService {

    private val tag = "IOSHardwareService"
    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val scope = CoroutineScope(Dispatchers.Main)

    // Camera service
    private val cameraService: IOSCameraService by lazy {
        IOSCameraService(scope, daemonBridge)
    }

    // Event flows
    private val _videoEvents = MutableSharedFlow<VideoEvent>()
    private val _cameraEvents = MutableSharedFlow<VideoEvent>()
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(HardwareService.STATE_INTERNAL)
    private val _connectivityState = MutableStateFlow(true)
    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(1920 to 1080)

    // Video surfaces
    private val videoSurfaces = mutableMapOf<String, UIView>()
    private var previewSurface: UIView? = null

    // Audio state
    private var speakerphoneOn = false
    private var audioSessionActive = false
    private var logging = false

    override val videoEvents: Flow<VideoEvent> = _videoEvents.asSharedFlow()
    override val cameraEvents: Flow<VideoEvent> = _cameraEvents.asSharedFlow()
    override val bluetoothEvents: Flow<BluetoothEvent> = _bluetoothEvents.asSharedFlow()
    override val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    override val connectivityState: Flow<Boolean> = _connectivityState.asStateFlow()

    init {
        logging = userDefaults.boolForKey(LOGGING_ENABLED_KEY)
        observeAudioRouteChanges()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Audio Management
    // ══════════════════════════════════════════════════════════════════════════

    private fun observeAudioRouteChanges() {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            updateAudioStateFromSession()
        }
    }

    private fun updateAudioStateFromSession() {
        val outputs = getAvailableOutputs()
        val currentOutput = if (speakerphoneOn) {
            HardwareService.OUTPUT_SPEAKERS
        } else {
            HardwareService.OUTPUT_INTERNAL
        }
        _audioState.value = AudioState(currentOutput, outputs)
    }

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
            Call.CallStatus.CURRENT -> {
                activateAudioSession()
                speakerphoneOn = isOngoingVideo
                updateAudioOutput()
            }
            Call.CallStatus.RINGING, Call.CallStatus.CONNECTING -> {
                activateAudioSession()
            }
            else -> { }
        }
    }

    private fun activateAudioSession() {
        if (audioSessionActive) return

        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                mode = AVAudioSessionModeVoiceChat,
                options = 0u,
                error = null
            )
            session.setActive(true, error = null)
            audioSessionActive = true
            Log.d(tag, "Audio session activated")
        } catch (e: Exception) {
            Log.e(tag, "Failed to activate audio session: ${e.message}")
        }
    }

    override fun closeAudioState() {
        abandonAudioFocus()
    }

    override fun isSpeakerphoneOn(): Boolean = speakerphoneOn

    override fun toggleSpeakerphone(conf: Conference, enabled: Boolean) {
        speakerphoneOn = enabled
        updateAudioOutput()
    }

    private fun updateAudioOutput() {
        try {
            val session = AVAudioSession.sharedInstance()
            val override: AVAudioSessionPortOverride = if (speakerphoneOn) {
                AVAudioSessionPortOverrideSpeaker
            } else {
                AVAudioSessionPortOverrideNone
            }
            session.overrideOutputAudioPort(override, error = null)

            val output = if (speakerphoneOn) {
                HardwareService.OUTPUT_SPEAKERS
            } else {
                HardwareService.OUTPUT_INTERNAL
            }
            _audioState.value = AudioState(output, getAvailableOutputs())
            Log.d(tag, "Audio output: ${if (speakerphoneOn) "speaker" else "earpiece"}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to update audio output: ${e.message}")
        }
    }

    private fun getAvailableOutputs(): List<AudioOutput> {
        return listOf(
            HardwareService.OUTPUT_INTERNAL,
            HardwareService.OUTPUT_SPEAKERS
        )
    }

    override fun abandonAudioFocus() {
        if (!audioSessionActive) return

        try {
            val session = AVAudioSession.sharedInstance()
            session.setActive(
                false,
                withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                error = null
            )
            audioSessionActive = false
            speakerphoneOn = false
            _audioState.value = HardwareService.STATE_INTERNAL
            Log.d(tag, "Audio session deactivated")
        } catch (e: Exception) {
            Log.e(tag, "Failed to deactivate audio session: ${e.message}")
        }
    }

    override fun hasMicrophone(): Boolean = true

    override fun shouldPlaySpeaker(): Boolean = speakerphoneOn

    // ══════════════════════════════════════════════════════════════════════════
    // Video/Camera Management
    // ══════════════════════════════════════════════════════════════════════════

    override suspend fun initVideo() {
        val devices = cameraService.getVideoDevices()
        devices.devices.forEach { device ->
            daemonBridge.addVideoDevice(device.id)
        }
        if (devices.currentId.isNotEmpty()) {
            daemonBridge.setDefaultDevice(devices.currentId)
        }
        Log.d(tag, "Video initialized with ${devices.devices.size} cameras")
    }

    override val isVideoAvailable: Boolean
        get() = cameraService.getVideoDevices().devices.isNotEmpty()

    override fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean) {
        Log.d(tag, "Decoding started: $id (${width}x${height})")
        scope.launch {
            _videoEvents.emit(
                VideoEvent.DecodingStarted(
                    sinkId = id,
                    width = width,
                    height = height,
                    isMixer = isMixer
                )
            )
        }
    }

    override fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {
        Log.d(tag, "Decoding stopped: $id")
        scope.launch {
            _videoEvents.emit(VideoEvent.DecodingStopped(sinkId = id))
        }
    }

    override fun hasInput(id: String): Boolean {
        return cameraService.currentCameraId.value == id
    }

    override fun getCameraInfo(
        camId: String,
        formats: MutableList<Int>,
        sizes: MutableList<Int>,
        rates: MutableList<Int>
    ) {
        // Return common formats for iOS cameras
        formats.addAll(listOf(0)) // NV12
        sizes.addAll(listOf(1280, 720, 1920, 1080, 640, 480))
        rates.addAll(listOf(30, 60, 24))
    }

    override fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {
        Log.d(tag, "Set camera parameters: $camId (${width}x${height}@${rate}fps)")
    }

    override fun startCameraPreview(videoPreview: Boolean) {
        scope.launch {
            if (cameraService.cameraState.value != CameraState.CAPTURING) {
                cameraService.openCamera()
                cameraService.startCapture()
            }
        }
    }

    override fun cameraCleanup() {
        cameraService.closeCamera()
    }

    override fun startCapture(camId: String?) {
        scope.launch {
            val opened = cameraService.openCamera(camId)
            if (opened) {
                cameraService.startCapture()
                _cameraEvents.emit(VideoEvent.CameraStarted(camId ?: ""))
            }
        }
    }

    override fun stopCapture(camId: String) {
        cameraService.stopCapture()
        scope.launch {
            _cameraEvents.emit(VideoEvent.CameraStopped(camId))
        }
    }

    override fun requestKeyFrame(camId: String) {
        // VideoToolbox encoder handles keyframes
    }

    override fun setBitrate(camId: String, bitrate: Int) {
        // VideoToolbox encoder bitrate
    }

    override fun addVideoSurface(id: String, holder: Any) {
        val view = holder as? UIView ?: return
        synchronized(videoSurfaces) {
            videoSurfaces[id] = view
        }
        Log.d(tag, "Added video surface: $id")
    }

    override fun updateVideoSurfaceId(currentId: String, newId: String) {
        synchronized(videoSurfaces) {
            videoSurfaces.remove(currentId)?.let { view ->
                videoSurfaces[newId] = view
            }
        }
        Log.d(tag, "Updated video surface: $currentId -> $newId")
    }

    override fun removeVideoSurface(id: String) {
        synchronized(videoSurfaces) {
            videoSurfaces.remove(id)
        }
        Log.d(tag, "Removed video surface: $id")
    }

    override fun addPreviewVideoSurface(holder: Any, conference: Conference?) {
        val view = holder as? UIView ?: return
        previewSurface = view
        Log.d(tag, "Added preview surface")
    }

    override fun updatePreviewVideoSurface(conference: Conference) {
        // Preview surface is managed by CameraPreview composable
    }

    override fun removePreviewVideoSurface() {
        previewSurface = null
        Log.d(tag, "Removed preview surface")
    }

    override fun addFullScreenPreviewSurface(holder: Any) {
        addPreviewVideoSurface(holder, null)
    }

    override fun removeFullScreenPreviewSurface() {
        removePreviewVideoSurface()
    }

    override fun changeCamera(setDefaultCamera: Boolean): String? {
        var newCameraId: String? = null
        scope.launch {
            newCameraId = cameraService.switchCamera()
        }
        return newCameraId
    }

    override fun setPreviewSettings() {
        // Preview settings handled by AVCaptureSession
    }

    override fun hasCamera(): Boolean = cameraService.getVideoDevices().devices.isNotEmpty()

    override fun cameraCount(): Int = cameraService.getVideoDevices().devices.size

    override val maxResolutions: Flow<Pair<Int?, Int?>> = _maxResolutions.asStateFlow()

    override val isPreviewFromFrontCamera: Boolean
        get() = cameraService.isFrontCamera

    override fun unregisterCameraDetectionCallback() {
        // Not needed on iOS - cameras are always available
    }

    override fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> = flow {
        emit(1280 to 720)
    }

    override suspend fun getSinkSize(id: String): Pair<Int, Int> {
        return cameraService.currentVideoParams?.let { params ->
            params.width to params.height
        } ?: (1280 to 720)
    }

    override fun setDeviceOrientation(rotation: Int) {
        cameraService.setDeviceOrientation(rotation)
    }

    override fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long {
        Log.d(tag, "Start video: $inputId (${width}x${height})")
        return daemonBridge.acquireNativeWindow(inputId)
    }

    override fun stopVideo(inputId: String, inputWindow: Long) {
        Log.d(tag, "Stop video: $inputId")
        daemonBridge.releaseNativeWindow(inputId, inputWindow)
    }

    override fun switchInput(accountId: String, callId: String, uri: String) {
        daemonBridge.switchVideoInput(accountId, callId, uri)
    }

    override fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>) {
        // Preview settings handled by AVCaptureSession
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Media Handlers / Screen Share
    // ══════════════════════════════════════════════════════════════════════════

    override fun startMediaHandler(mediaHandlerId: String?) {
        Log.d(tag, "Start media handler: $mediaHandlerId")
    }

    override fun stopMediaHandler() {
        Log.d(tag, "Stop media handler")
    }

    override fun setPendingScreenShareProjection(screenCaptureSession: Any?) {
        // Screen sharing via ReplayKit on iOS
        Log.d(tag, "Set screen share projection")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Connectivity
    // ══════════════════════════════════════════════════════════════════════════

    override fun connectivityChanged(isConnected: Boolean) {
        _connectivityState.value = isConnected
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
        Log.d(tag, message)
    }

    override fun saveLoggingState(enabled: Boolean) {
        logging = enabled
        userDefaults.setBool(enabled, LOGGING_ENABLED_KEY)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Cleanup
    // ══════════════════════════════════════════════════════════════════════════

    fun cleanup() {
        cameraService.cleanup()
        closeAudioState()
        videoSurfaces.clear()
        previewSurface = null
    }

    companion object {
        private const val LOGGING_ENABLED_KEY = "hardware_logging_enabled"
    }
}
