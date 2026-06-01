package net.jami.services.expect

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.RingtoneManager
import android.os.Build
import android.view.SurfaceHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.daemon.JamiService
import net.jami.model.Call
import net.jami.model.Call.CallStatus
import net.jami.model.Conference
import net.jami.services.DaemonBridge
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class HardwareService(private val context: Context) : KoinComponent, OnAudioFocusChangeListener {

    private val _videoEvents = MutableSharedFlow<VideoEvent>()
    private val _cameraEvents = MutableSharedFlow<VideoEvent>()
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(AudioState(AudioOutput(AudioOutputType.INTERNAL)))
    private val _connectivityState = MutableStateFlow(true)
    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(null to null)

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var currentFocusRequest: AudioFocusRequest? = null
    private var mShouldSpeakerphone = false
    private val mHasSpeakerPhone: Boolean by lazy { hasSpeakerphone() }
    private var ringtone: android.media.Ringtone? = null
    private var logging = false

    private fun buildFocusRequest(usage: Int, contentType: Int, gain: Int): AudioFocusRequest =
        AudioFocusRequest.Builder(gain)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(contentType)
                    .build()
            )
            .setOnAudioFocusChangeListener(this)
            .build()

    private val RINGTONE_REQUEST: AudioFocusRequest by lazy {
        buildFocusRequest(
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
            AudioAttributes.CONTENT_TYPE_SONIFICATION,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
    }

    private val CALL_REQUEST: AudioFocusRequest by lazy {
        buildFocusRequest(
            AudioAttributes.USAGE_VOICE_COMMUNICATION,
            AudioAttributes.CONTENT_TYPE_SPEECH,
            AudioManager.AUDIOFOCUS_GAIN
        )
    }

    private fun getFocus(request: AudioFocusRequest) {
        if (currentFocusRequest === request) return
        currentFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        currentFocusRequest = null
        if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            currentFocusRequest = request
        }
    }

    private fun hasSpeakerphone(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    private fun routeToSpeaker() {
        audioManager.isSpeakerphoneOn = true
        _audioState.value = AudioState(AudioOutput(AudioOutputType.SPEAKERS))
    }

    private fun resetAudio() {
        audioManager.isSpeakerphoneOn = false
        _audioState.value = AudioState(AudioOutput(AudioOutputType.INTERNAL))
    }

    private fun setAudioRouting(requestSpeakerOn: Boolean) {
        if (mHasSpeakerPhone && requestSpeakerOn && !audioManager.isWiredHeadsetOn) {
            routeToSpeaker()
        } else {
            resetAudio()
        }
    }

    private fun startRingtone() {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(context, uri)?.also { rt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rt.isLooping = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            rt.play()
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    actual val videoEvents: Flow<VideoEvent> = _videoEvents.asSharedFlow()
    actual val cameraEvents: Flow<VideoEvent> = _cameraEvents.asSharedFlow()
    actual val bluetoothEvents: Flow<BluetoothEvent> = _bluetoothEvents.asSharedFlow()
    actual val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    actual val connectivityState: Flow<Boolean> = _connectivityState.asStateFlow()

    actual fun getAudioState(conf: Conference): Flow<AudioState> = audioState

    @Synchronized
    actual fun updateAudioState(conference: Conference?, call: Call, incomingCall: Boolean, isOngoingVideo: Boolean) {
        Log.d(TAG, "updateAudioState: status=${call.callStatus} incoming=$incomingCall video=$isOngoingVideo")
        try {
            val state = call.callStatus
            val callEnded = state == CallStatus.HUNGUP || state == CallStatus.FAILURE || state == CallStatus.OVER
            when {
                callEnded -> closeAudioState()
                state == CallStatus.RINGING -> {
                    getFocus(RINGTONE_REQUEST)
                    if (incomingCall) {
                        audioManager.mode = AudioManager.MODE_RINGTONE
                        startRingtone()
                        setAudioRouting(true)
                    } else {
                        setAudioRouting(isOngoingVideo)
                    }
                }
                state == CallStatus.CURRENT -> {
                    stopRingtone()
                    getFocus(CALL_REQUEST)
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    mShouldSpeakerphone = isOngoingVideo || isSpeakerphoneOn()
                    setAudioRouting(mShouldSpeakerphone)
                }
                state == CallStatus.HOLD || state == CallStatus.INACTIVE -> { /* no-op */ }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateAudioState error", e)
        }
    }

    actual fun closeAudioState() {
        stopRingtone()
        abandonAudioFocus()
    }

    actual fun isSpeakerphoneOn(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            audioManager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        else audioManager.isSpeakerphoneOn

    @Synchronized
    actual fun toggleSpeakerphone(conf: Conference, enabled: Boolean) {
        Log.d(TAG, "toggleSpeakerphone: $enabled")
        JamiService.setAudioPlugin(JamiService.getCurrentAudioOutputPlugin())
        mShouldSpeakerphone = enabled
        if (mHasSpeakerPhone && enabled) routeToSpeaker() else resetAudio()
    }

    @Synchronized
    actual fun abandonAudioFocus() {
        currentFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            currentFocusRequest = null
        }
        if (audioManager.isSpeakerphoneOn) audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    actual fun hasMicrophone(): Boolean = true

    actual fun shouldPlaySpeaker(): Boolean = mShouldSpeakerphone

    override fun onAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "onAudioFocusChange: $focusChange")
    }

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

    private val daemonBridge: DaemonBridge by inject()
    private val videoWindows = mutableMapOf<String, Long>()

    actual fun addVideoSurface(id: String, holder: Any) {
        if (holder !is SurfaceHolder) return
        val window = daemonBridge.acquireNativeWindow(holder.surface)
        if (window != 0L) {
            videoWindows[id] = window
            daemonBridge.registerVideoCallback(id, window)
        }
    }

    actual fun updateVideoSurfaceId(currentId: String, newId: String) {
        videoWindows.remove(currentId)?.let { window -> videoWindows[newId] = window }
    }

    actual fun removeVideoSurface(id: String) {
        videoWindows.remove(id)?.let { window ->
            if (window != 0L) {
                daemonBridge.unregisterVideoCallback(id, window)
                daemonBridge.releaseNativeWindow(window)
            }
        }
    }

    actual fun addPreviewVideoSurface(holder: Any, conference: Conference?) {
        Log.d("HardwareService", "addPreviewVideoSurface: not fully implemented")
    }

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
    actual fun updatePreviewVideoSurface(conference: Conference) {}
    actual val isLogging: Boolean get() = logging
    actual fun startLogs(): Flow<List<String>> {
        logging = true
        return MutableSharedFlow()
    }
    actual fun stopLogs() { logging = false }
    actual fun logMessage(message: String) {}
    actual fun saveLoggingState(enabled: Boolean) { logging = enabled }

    companion object {
        private const val TAG = "HardwareService"
    }
}
