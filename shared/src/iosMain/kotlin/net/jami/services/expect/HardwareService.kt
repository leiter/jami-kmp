package net.jami.services.expect

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import net.jami.model.Call
import net.jami.model.Conference
import net.jami.services.CameraState
import net.jami.services.DaemonBridgeApi
import net.jami.services.IOSCameraService
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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

private val STATE_INTERNAL = AudioState(AudioOutput(AudioOutputType.INTERNAL))
private val OUTPUT_INTERNAL = AudioOutput(AudioOutputType.INTERNAL)
private val OUTPUT_SPEAKERS = AudioOutput(AudioOutputType.SPEAKERS)

@OptIn(ExperimentalForeignApi::class)
actual class HardwareService : KoinComponent {

    private val daemonBridge: DaemonBridgeApi by inject()
    private val cameraService: IOSCameraService by inject()

    private val tag = "HardwareService"
    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _videoEvents = MutableSharedFlow<VideoEvent>()
    private val _cameraEvents = MutableSharedFlow<VideoEvent>()
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(STATE_INTERNAL)
    private val _connectivityState = MutableStateFlow(true)
    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(1920 to 1080)

    private val videoSurfaces = mutableMapOf<String, UIView>()
    private var previewSurface: UIView? = null
    private var speakerphoneOn = false
    private var audioSessionActive = false
    private var logging = false
    private var screenShareSession: Any? = null
    private var isScreenSharing = false

    actual val videoEvents: Flow<VideoEvent> = _videoEvents.asSharedFlow()
    actual val cameraEvents: Flow<VideoEvent> = _cameraEvents.asSharedFlow()
    actual val bluetoothEvents: Flow<BluetoothEvent> = _bluetoothEvents.asSharedFlow()
    actual val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    actual val connectivityState: Flow<Boolean> = _connectivityState.asStateFlow()

    init {
        logging = userDefaults.boolForKey(LOGGING_ENABLED_KEY)
        observeAudioRouteChanges()
    }

    private fun observeAudioRouteChanges() {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> updateAudioStateFromSession() }
    }

    private fun updateAudioStateFromSession() {
        val current = if (speakerphoneOn) OUTPUT_SPEAKERS else OUTPUT_INTERNAL
        _audioState.value = AudioState(current, listOf(OUTPUT_INTERNAL, OUTPUT_SPEAKERS))
    }

    actual fun getAudioState(conf: Conference): Flow<AudioState> = audioState

    actual fun updateAudioState(conference: Conference?, call: Call, incomingCall: Boolean, isOngoingVideo: Boolean) {
        val state = call.callStatus
        val ended = state == Call.CallStatus.HUNGUP || state == Call.CallStatus.FAILURE || state == Call.CallStatus.OVER
        if (ended) { closeAudioState(); return }
        when (state) {
            Call.CallStatus.CURRENT -> { activateAudioSession(); speakerphoneOn = isOngoingVideo; updateAudioOutput() }
            Call.CallStatus.RINGING, Call.CallStatus.CONNECTING -> activateAudioSession()
            else -> {}
        }
    }

    private fun activateAudioSession() {
        if (audioSessionActive) return
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayAndRecord, mode = AVAudioSessionModeVoiceChat, options = 0u, error = null)
            session.setActive(true, error = null)
            audioSessionActive = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to activate audio session: ${e.message}")
        }
    }

    actual fun closeAudioState() { abandonAudioFocus() }
    actual fun isSpeakerphoneOn(): Boolean = speakerphoneOn
    actual fun toggleSpeakerphone(conf: Conference, enabled: Boolean) { speakerphoneOn = enabled; updateAudioOutput() }

    private fun updateAudioOutput() {
        try {
            val session = AVAudioSession.sharedInstance()
            val override: AVAudioSessionPortOverride = if (speakerphoneOn) AVAudioSessionPortOverrideSpeaker else AVAudioSessionPortOverrideNone
            session.overrideOutputAudioPort(override, error = null)
            _audioState.value = AudioState(if (speakerphoneOn) OUTPUT_SPEAKERS else OUTPUT_INTERNAL, listOf(OUTPUT_INTERNAL, OUTPUT_SPEAKERS))
        } catch (e: Exception) {
            Log.e(tag, "Failed to update audio output: ${e.message}")
        }
    }

    actual fun abandonAudioFocus() {
        if (!audioSessionActive) return
        try {
            val session = AVAudioSession.sharedInstance()
            session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
            audioSessionActive = false
            speakerphoneOn = false
            _audioState.value = STATE_INTERNAL
        } catch (e: Exception) {
            Log.e(tag, "Failed to deactivate audio session: ${e.message}")
        }
    }

    actual fun hasMicrophone(): Boolean = true
    actual fun shouldPlaySpeaker(): Boolean = speakerphoneOn

    actual suspend fun initVideo() {
        val devices = cameraService.getVideoDevices()
        devices.devices.forEach { device -> daemonBridge.addVideoDevice(device.id) }
        if (devices.currentId.isNotEmpty()) daemonBridge.setDefaultDevice(devices.currentId)
    }

    actual val isVideoAvailable: Boolean get() = cameraService.getVideoDevices().devices.isNotEmpty()

    actual fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean) {
        scope.launch { _videoEvents.emit(VideoEvent(sinkId = id, started = true, width = width, height = height)) }
    }

    actual fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {
        scope.launch { _videoEvents.emit(VideoEvent(sinkId = id)) }
    }

    actual fun hasInput(id: String): Boolean = cameraService.currentCameraId.value == id

    actual fun getCameraInfo(camId: String, formats: MutableList<Int>, sizes: MutableList<Int>, rates: MutableList<Int>) {
        formats.add(0)
        sizes.addAll(listOf(1280, 720, 1920, 1080, 640, 480))
        rates.addAll(listOf(30, 60, 24))
    }

    actual fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {}

    actual fun startCameraPreview(videoPreview: Boolean) {
        scope.launch {
            if (cameraService.cameraState.value != CameraState.CAPTURING) {
                cameraService.openCamera()
                cameraService.startCapture()
            }
        }
    }

    actual fun cameraCleanup() { cameraService.closeCamera() }

    actual fun startCapture(camId: String?) {
        scope.launch {
            if (cameraService.openCamera(camId)) {
                cameraService.startCapture()
                _cameraEvents.emit(VideoEvent(sinkId = camId ?: "", start = true))
            }
        }
    }

    actual fun stopCapture(camId: String) {
        cameraService.stopCapture()
        scope.launch { _cameraEvents.emit(VideoEvent(sinkId = camId)) }
    }

    actual fun requestKeyFrame(camId: String) {}
    actual fun setBitrate(camId: String, bitrate: Int) {}

    actual fun addVideoSurface(id: String, holder: Any) {
        (holder as? UIView)?.let { videoSurfaces[id] = it }
    }

    actual fun updateVideoSurfaceId(currentId: String, newId: String) {
        videoSurfaces.remove(currentId)?.let { videoSurfaces[newId] = it }
    }

    actual fun removeVideoSurface(id: String) { videoSurfaces.remove(id) }

    actual fun addPreviewVideoSurface(holder: Any, conference: Conference?) {
        previewSurface = holder as? UIView
    }

    actual fun updatePreviewVideoSurface(conference: Conference) {}
    actual fun removePreviewVideoSurface() { previewSurface = null }
    actual fun addFullScreenPreviewSurface(holder: Any) { addPreviewVideoSurface(holder, null) }
    actual fun removeFullScreenPreviewSurface() { removePreviewVideoSurface() }

    actual fun changeCamera(setDefaultCamera: Boolean): String? {
        var result: String? = null
        scope.launch { result = cameraService.switchCamera() }
        return result
    }

    actual fun setPreviewSettings() {}
    actual fun hasCamera(): Boolean = cameraService.getVideoDevices().devices.isNotEmpty()
    actual fun cameraCount(): Int = cameraService.getVideoDevices().devices.size
    actual val maxResolutions: Flow<Pair<Int?, Int?>> = _maxResolutions.asStateFlow()
    actual val isPreviewFromFrontCamera: Boolean get() = cameraService.isFrontCamera
    actual fun unregisterCameraDetectionCallback() {}

    actual fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> = flow { emit(1280 to 720) }

    actual suspend fun getSinkSize(id: String): Pair<Int, Int> =
        cameraService.currentVideoParams?.let { it.width to it.height } ?: (1280 to 720)

    actual fun setDeviceOrientation(rotation: Int) { cameraService.setDeviceOrientation(rotation) }

    actual fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long =
        daemonBridge.acquireNativeWindow(inputId)

    actual fun stopVideo(inputId: String, inputWindow: Long) {
        daemonBridge.releaseNativeWindow(inputWindow)
    }

    actual fun switchInput(accountId: String, callId: String, uri: String) {
        when {
            uri == "camera://desktop" -> {
                if (!isScreenSharing && screenShareSession != null) {
                    isScreenSharing = true
                    daemonBridge.switchVideoInput(accountId, callId, uri)
                }
            }
            uri.startsWith("camera://") -> {
                if (isScreenSharing) { isScreenSharing = false; screenShareSession = null }
                daemonBridge.switchVideoInput(accountId, callId, uri)
            }
            else -> daemonBridge.switchVideoInput(accountId, callId, uri)
        }
    }

    actual fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>) {}
    actual fun startMediaHandler(mediaHandlerId: String?) {}
    actual fun stopMediaHandler() {}

    actual fun setPendingScreenShareProjection(screenCaptureSession: Any?) {
        screenShareSession = screenCaptureSession
        if (screenCaptureSession == null) isScreenSharing = false
    }

    actual fun connectivityChanged(isConnected: Boolean) { _connectivityState.value = isConnected }

    actual val isLogging: Boolean get() = logging

    actual fun startLogs(): Flow<List<String>> {
        logging = true
        return MutableSharedFlow()
    }

    actual fun stopLogs() { logging = false }
    actual fun logMessage(message: String) { Log.d(tag, message) }

    actual fun saveLoggingState(enabled: Boolean) {
        logging = enabled
        userDefaults.setBool(enabled, LOGGING_ENABLED_KEY)
    }

    actual val screenShareRequest: SharedFlow<Unit> = MutableSharedFlow()
    actual val screenShareReady: SharedFlow<Unit> = MutableSharedFlow()
    actual fun requestScreenSharePermission() {}

    companion object {
        private const val LOGGING_ENABLED_KEY = "hardware_logging_enabled"
    }
}
