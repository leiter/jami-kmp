package net.jami.services.expect

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.WindowManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.daemon.JamiService
import net.jami.model.Call
import net.jami.model.Call.CallStatus
import net.jami.model.Conference
import net.jami.model.VideoDevices
import net.jami.services.CameraService
import net.jami.services.DaemonBridge
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.ref.WeakReference

actual class HardwareService(private val context: Context) : KoinComponent, OnAudioFocusChangeListener {

    private val _videoEvents = MutableSharedFlow<VideoEvent>()
    private val _cameraEvents = MutableSharedFlow<VideoEvent>()
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(AudioState(AudioOutput(AudioOutputType.INTERNAL)))
    private val _connectivityState = MutableStateFlow(true)
    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(null to null)

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val daemonBridge: DaemonBridge by inject()
    private val cameraService: CameraService by inject()
    private var currentFocusRequest: AudioFocusRequest? = null
    private var mShouldSpeakerphone = false
    private val mHasSpeakerPhone: Boolean by lazy { hasSpeakerphone() }
    private var logging = false

    private var pendingScreenShareProjection: MediaProjection? = null
    private val _screenShareRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _screenShareReady = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Camera preview state
    private var mCameraPreviewSurface = WeakReference<TextureView>(null)
    private var mFullScreenPreviewSurface = WeakReference<TextureView>(null)
    private var mCameraPreviewCall = WeakReference<Conference>(null)
    private var mPreviewCamId: String? = null
    private val shouldCapture = mutableSetOf<String>()
    private val pendingStartCodec = mutableSetOf<String>()

    // Decode-side state: tracks native windows for sinks registered before decodingStarted
    private val videoWindows = mutableMapOf<String, Long>()
    // Dimensions reported by decodingStarted, needed when the surface arrives after the daemon event
    private val videoSizes = mutableMapOf<String, Pair<Int, Int>>()

    // Handler for posting camera operations to the camera thread
    private val uiHandler: Handler by lazy {
        Handler(context.mainLooper)
    }

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

    private fun muteRingtoneForRingerMode() {
        val shouldMute = audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
        Log.d(TAG, "muteRingtoneForRingerMode: ringerMode=${audioManager.ringerMode} mute=$shouldMute")
        daemonBridge.muteRingtone(shouldMute)
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
                        muteRingtoneForRingerMode()
                        setAudioRouting(true)
                    } else {
                        setAudioRouting(isOngoingVideo)
                    }
                }
                state == CallStatus.CURRENT -> {
                    daemonBridge.muteRingtone(true)
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
        daemonBridge.muteRingtone(true)
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

    actual suspend fun initVideo() {
        cameraService.init()
    }

    actual val isVideoAvailable: Boolean get() = cameraService.getCameraCount() > 0

    actual fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean) {
        Log.i(TAG, "decodingStarted() $id ${width}x$height")
        videoSizes[id] = width to height
        val window = videoWindows[id]
        if (window != null && window != 0L) {
            daemonBridge.setNativeWindowGeometry(window, width, height)
        }
        _videoEvents.tryEmit(VideoEvent(sinkId = id, started = true, width = width, height = height))
    }

    actual fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {
        Log.i(TAG, "decodingStopped() $id")
        videoSizes.remove(id)
        _videoEvents.tryEmit(VideoEvent(sinkId = id))
    }

    actual fun hasInput(id: String): Boolean = false

    actual fun getCameraInfo(camId: String, formats: MutableList<Int>, sizes: MutableList<Int>, rates: MutableList<Int>) {
        Log.d(TAG, "getCameraInfo: $camId")
        cameraService.getCameraInfo(camId, formats, sizes, rates, CameraService.VIDEO_SIZE_DEFAULT)
    }

    actual fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {
        Log.d(TAG, "setParameters: $camId $format ${width}x$height @$rate")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val rotation = wm.defaultDisplay.rotation
        cameraService.setParameters(camId, format, width, height, rate, rotation)
    }

    actual fun startCameraPreview(videoPreview: Boolean) {
        val surface = (if (videoPreview) mFullScreenPreviewSurface else mCameraPreviewSurface).get()
        if (surface == null || !surface.isAvailable || surface.surfaceTexture == null) {
            Log.e(TAG, "startCameraPreview: surface not ready")
            return
        }
        val camId = cameraService.switchInput(true) ?: return
        mPreviewCamId = camId
        val params = cameraService.getParams(camId) ?: return
        openCameraPreview(params, surface, codecStart = false, videoPreview = videoPreview)
    }

    actual fun cameraCleanup() {
        val camId = mPreviewCamId ?: return
        val params = cameraService.getParams(camId) ?: return
        if (params.camera == null && params.cameraSession == null) return
        pendingStartCodec.remove(camId)
        cameraService.closeCamera(camId)
    }

    private fun openCameraPreview(
        videoParams: net.jami.services.AndroidVideoParams,
        previewSurface: TextureView?,
        codecStart: Boolean,
        videoPreview: Boolean,
        onOpened: (() -> Unit)? = null
    ) {
        videoParams.isCapturing = true
        val safeSurface = previewSurface ?: return
        uiHandler.post {
            cameraService.openCamera(
                videoParams,
                safeSurface,
                object : CameraService.CameraListener {
                    override fun onOpened() {
                        onOpened?.invoke()
                    }
                    override fun onError() {
                        pendingStartCodec.remove(videoParams.id)
                        stopCapture(videoParams.id)
                    }
                },
                hwAccel = true,
                resolution = 0,
                bitrate = 0,
                codecStart = codecStart,
                videoPreview = videoPreview
            )
        }
        _cameraEvents.tryEmit(VideoEvent(
            sinkId = videoParams.id,
            started = true,
            width = videoParams.size.width,
            height = videoParams.size.height
        ))
    }

    actual val screenShareRequest: SharedFlow<Unit> = _screenShareRequest.asSharedFlow()
    actual val screenShareReady: SharedFlow<Unit> = _screenShareReady.asSharedFlow()

    actual fun requestScreenSharePermission() {
        _screenShareRequest.tryEmit(Unit)
    }

    actual fun startCapture(camId: String?) {
        val rawId = camId?.substringAfter("camera://", missingDelimiterValue = camId ?: "")
        val cam = rawId?.takeIf { it.isNotEmpty() } ?: cameraService.switchInput(true) ?: return
        Log.d(TAG, "startCapture: $cam (original: $camId)")

        if (cam == VideoDevices.SCREEN_SHARING) {
            val projection = pendingScreenShareProjection ?: run {
                Log.w(TAG, "startCapture(SCREEN_SHARING): no pending projection")
                return
            }
            pendingScreenShareProjection = null
            val params = cameraService.getParams(cam) ?: run {
                Log.w(TAG, "startCapture(SCREEN_SHARING): no params for $cam")
                return
            }
            val surface = mCameraPreviewSurface.get() ?: run {
                Log.w(TAG, "startCapture(SCREEN_SHARING): no preview surface registered")
                projection.stop()
                return
            }
            if (!cameraService.startScreenSharing(params, projection, surface, context.resources.displayMetrics)) {
                projection.stop()
            }
            return
        }

        // Regular camera
        shouldCapture.add(cam)
        val videoParams = cameraService.getParams(cam) ?: return
        val surface = mCameraPreviewSurface.get()
        if (surface == null) {
            Log.w(TAG, "startCapture $cam: no preview surface yet, will retry when surface registers")
            _cameraEvents.tryEmit(VideoEvent(sinkId = cam, started = true))
            return
        }

        if (cam != mPreviewCamId || !videoParams.isCapturing) {
            mPreviewCamId = cam
            openCameraPreview(videoParams, surface, codecStart = true, videoPreview = false)
        } else {
            val sessionReady = videoParams.camera != null && videoParams.cameraSession != null
            if (sessionReady) {
                cameraService.startCodec(videoParams)
            } else {
                pendingStartCodec.add(cam)
            }
        }
    }

    actual fun stopCapture(camId: String) {
        Log.d(TAG, "stopCapture: $camId")
        val cam = camId.substringAfter("camera://", missingDelimiterValue = camId)
        shouldCapture.remove(cam)
        cameraService.closeCamera(cam)
        _cameraEvents.tryEmit(VideoEvent(sinkId = cam))
    }

    actual fun requestKeyFrame(camId: String) {
        cameraService.requestKeyFrame(camId)
    }

    actual fun setBitrate(camId: String, bitrate: Int) {
        cameraService.setBitrate(camId, bitrate)
    }

    actual fun addVideoSurface(id: String, holder: Any) {
        val surface: android.view.Surface? = when (holder) {
            is SurfaceHolder -> holder.surface
            is android.view.Surface -> holder
            else -> null
        }
        if (surface == null) {
            Log.e(TAG, "addVideoSurface: unsupported holder type ${holder::class.simpleName} for id=$id")
            return
        }
        val window = daemonBridge.acquireNativeWindow(surface)
        Log.i(TAG, "addVideoSurface id=$id window=$window storedSize=${videoSizes[id]}")
        if (window == 0L) {
            Log.e(TAG, "addVideoSurface: acquireNativeWindow returned 0 for id=$id")
            return
        }
        videoWindows[id] = window
        // Apply geometry before registering so the daemon knows output dimensions.
        // decodingStarted normally fires before the surface is ready; if it already did,
        // the stored size is available here.
        videoSizes[id]?.let { (w, h) -> daemonBridge.setNativeWindowGeometry(window, w, h) }
        daemonBridge.registerVideoCallback(id, window)
    }

    actual fun updateVideoSurfaceId(currentId: String, newId: String) {
        videoWindows.remove(currentId)?.let { window -> videoWindows[newId] = window }
    }

    actual fun removeVideoSurface(id: String) {
        videoSizes.remove(id)
        videoWindows.remove(id)?.let { window ->
            if (window != 0L) {
                daemonBridge.unregisterVideoCallback(id, window)
                daemonBridge.releaseNativeWindow(window)
            }
        }
    }

    actual fun addPreviewVideoSurface(holder: Any, conference: Conference?) {
        if (holder !is TextureView) return
        if (mCameraPreviewSurface.get() === holder) return
        Log.d(TAG, "addPreviewVideoSurface")
        mCameraPreviewSurface = WeakReference(holder)
        mCameraPreviewCall = WeakReference(conference)
        for (cam in shouldCapture.toList()) {
            uiHandler.post { startCapture(cam) }
        }
    }

    actual fun removePreviewVideoSurface() {
        mCameraPreviewSurface.clear()
    }

    actual fun addFullScreenPreviewSurface(holder: Any) {
        if (holder !is TextureView) return
        if (mFullScreenPreviewSurface.get() === holder) return
        mFullScreenPreviewSurface = WeakReference(holder)
    }

    actual fun removeFullScreenPreviewSurface() {
        mFullScreenPreviewSurface.clear()
    }

    actual fun updatePreviewVideoSurface(conference: Conference) {
        val old = mCameraPreviewCall.get()
        mCameraPreviewCall = WeakReference(conference)
        if (old !== conference) {
            for (camId in shouldCapture.toList()) {
                cameraService.closeCamera(camId)
                startCapture(camId)
            }
        }
    }

    actual fun changeCamera(setDefaultCamera: Boolean): String? {
        val newCamId = cameraService.switchInput(setDefaultCamera) ?: return null
        mPreviewCamId = newCamId
        for (cam in shouldCapture.toList()) {
            cameraService.closeCamera(cam)
            startCapture(newCamId)
        }
        return newCamId
    }

    actual fun setPreviewSettings() {}

    actual fun hasCamera(): Boolean = cameraService.hasCamera()

    actual fun cameraCount(): Int = cameraService.getCameraCount()

    actual val maxResolutions: Flow<Pair<Int?, Int?>> = _maxResolutions.asStateFlow()

    actual val isPreviewFromFrontCamera: Boolean get() = cameraService.isPreviewFromFrontCamera

    actual fun unregisterCameraDetectionCallback() {
        cameraService.unregisterCameraDetectionCallback()
    }

    actual fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> = MutableStateFlow(0 to 0)

    actual suspend fun getSinkSize(id: String): Pair<Int, Int> = 0 to 0

    actual fun setDeviceOrientation(rotation: Int) {
        cameraService.setOrientation(rotation)
    }

    actual fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long {
        Log.i(TAG, "startVideo $inputId ${width}x$height")
        val window = daemonBridge.acquireNativeWindow(surface)
        if (window != 0L) {
            daemonBridge.setNativeWindowGeometry(window, width, height)
            daemonBridge.registerVideoCallback(inputId, window)
        }
        return window
    }

    actual fun stopVideo(inputId: String, inputWindow: Long) {
        Log.i(TAG, "stopVideo $inputId $inputWindow")
        if (inputWindow != 0L) {
            daemonBridge.unregisterVideoCallback(inputId, inputWindow)
            daemonBridge.releaseNativeWindow(inputWindow)
        }
    }

    actual fun switchInput(accountId: String, callId: String, uri: String) {
        Log.d(TAG, "switchInput: $accountId $callId $uri")
        daemonBridge.switchVideoInput(accountId, callId, uri)
    }

    actual fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>) {}

    actual fun startMediaHandler(mediaHandlerId: String?) {}
    actual fun stopMediaHandler() {}

    actual fun setPendingScreenShareProjection(screenCaptureSession: Any?) {
        pendingScreenShareProjection = screenCaptureSession as MediaProjection?
        Log.d(TAG, "setPendingScreenShareProjection: ${if (pendingScreenShareProjection != null) "set" else "cleared"}")
        if (pendingScreenShareProjection != null) {
            _screenShareReady.tryEmit(Unit)
        }
    }

    actual fun connectivityChanged(isConnected: Boolean) { _connectivityState.value = isConnected }

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
