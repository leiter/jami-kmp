package net.jami.services.actual

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.model.Call
import net.jami.model.Conference
import net.jami.services.expect.*

actual class HardwareService {
    private val _videoEvents = MutableSharedFlow<VideoEvent>()
    private val _cameraEvents = MutableSharedFlow<VideoEvent>()
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(AudioState(AudioOutputType.INTERNAL))
    private val _connectivityState = MutableStateFlow(true)
    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(null to null)

    private var speakerOn = false
    private var logging = false

    actual val videoEvents: Flow<VideoEvent> = _videoEvents.asSharedFlow()
    actual val cameraEvents: Flow<VideoEvent> = _cameraEvents.asSharedFlow()
    actual val bluetoothEvents: Flow<BluetoothEvent> = _bluetoothEvents.asSharedFlow()
    actual val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    actual val connectivityState: Flow<Boolean> = _connectivityState.asStateFlow()

    actual fun getAudioState(conf: Conference): Flow<AudioState> = audioState
    actual fun updateAudioState(conference: Conference?, call: Call, incomingCall: Boolean, isOngoingVideo: Boolean) {}
    actual fun closeAudioState() {}
    actual fun isSpeakerphoneOn(): Boolean = speakerOn
    actual fun toggleSpeakerphone(conf: Conference, enabled: Boolean) { speakerOn = enabled }
    actual fun abandonAudioFocus() {}
    actual fun hasMicrophone(): Boolean = true
    actual fun shouldPlaySpeaker(): Boolean = false

    actual suspend fun initVideo() {}
    actual val isVideoAvailable: Boolean = false
    actual fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean) {}
    actual fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {}
    actual fun hasInput(id: String): Boolean = false
    actual fun getCameraInfo(camId: String, formats: MutableList<Int>, sizes: MutableList<Int>, rates: MutableList<Int>) {}
    actual fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {}
    actual fun startCameraPreview(videoPreview: Boolean) {}
    actual fun cameraCleanup() {}
    actual fun startCapture(camId: String?) {}
    actual fun stopCapture(camId: String) {}
    actual fun requestKeyFrame(camId: String) {}
    actual fun setBitrate(camId: String, bitrate: Int) {}
    actual fun addVideoSurface(id: String, holder: Any) {}
    actual fun updateVideoSurfaceId(currentId: String, newId: String) {}
    actual fun removeVideoSurface(id: String) {}
    actual fun addPreviewVideoSurface(holder: Any, conference: Conference?) {}
    actual fun updatePreviewVideoSurface(conference: Conference) {}
    actual fun removePreviewVideoSurface() {}
    actual fun addFullScreenPreviewSurface(holder: Any) {}
    actual fun removeFullScreenPreviewSurface() {}
    actual fun changeCamera(setDefaultCamera: Boolean): String? = null
    actual fun setPreviewSettings() {}
    actual fun hasCamera(): Boolean = false
    actual fun cameraCount(): Int = 0
    actual val maxResolutions: Flow<Pair<Int?, Int?>> = _maxResolutions.asStateFlow()
    actual val isPreviewFromFrontCamera: Boolean = true
    actual fun unregisterCameraDetectionCallback() {}
    actual fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> = MutableStateFlow(0 to 0)
    actual suspend fun getSinkSize(id: String): Pair<Int, Int> = 0 to 0
    actual fun setDeviceOrientation(rotation: Int) {}
    actual fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long = 0L
    actual fun stopVideo(inputId: String, inputWindow: Long) {}
    actual fun switchInput(accountId: String, callId: String, uri: String) {}
    actual fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>) {}
    actual fun startMediaHandler(mediaHandlerId: String?) {}
    actual fun stopMediaHandler() {}
    actual fun setPendingScreenShareProjection(screenCaptureSession: Any?) {}
    actual fun connectivityChanged(isConnected: Boolean) { _connectivityState.value = isConnected }
    actual val isLogging: Boolean get() = logging
    actual fun startLogs(): Flow<List<String>> {
        logging = true
        return MutableSharedFlow()
    }
    actual fun stopLogs() { logging = false }
    actual fun logMessage(message: String) {}
    actual fun saveLoggingState(enabled: Boolean) { logging = enabled }
}
