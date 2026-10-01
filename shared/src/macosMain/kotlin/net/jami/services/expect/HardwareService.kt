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
import net.jami.services.DaemonBridgeApi
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.uniqueID
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.Foundation.NSUserDefaults
import platform.darwin.dispatch_get_main_queue

private val STATE_INTERNAL = AudioState(AudioOutput(AudioOutputType.INTERNAL))
private val OUTPUT_INTERNAL = AudioOutput(AudioOutputType.INTERNAL)
private val OUTPUT_SPEAKERS = AudioOutput(AudioOutputType.SPEAKERS)

/**
 * macOS implementation of [HardwareService].
 *
 * This mirrors the iOS actual wherever the API exists on both, and deliberately diverges where
 * it does not:
 *
 * - **Audio.** `AVAudioSession` is iOS-only; there is no macOS equivalent that routes a call the
 *   way the iOS implementation does. Route selection on macOS belongs to the user (System
 *   Settings / the menu-bar output picker) and to the daemon's own CoreAudio layer, so this
 *   class only tracks the speaker/internal flag the UI binds to, and does not attempt to force
 *   a route. Enumerating real devices would need CoreAudio `AudioObjectGetPropertyData`.
 * - **Camera.** Device *enumeration* is real, via [AVCaptureDevice] (available on macOS), so the
 *   daemon is told about the cameras that actually exist. Capture itself is not: there is no
 *   `MacOSCameraService` counterpart to `IOSCameraService`, so preview/capture are no-ops and
 *   outgoing video does not work. See the macOS gap note in CLAUDE.md.
 * - **Connectivity.** Real, and identical to iOS: `nw_path_monitor` (the Network framework is
 *   on both) drives [connectivityChanged].
 */
@OptIn(ExperimentalForeignApi::class)
actual class HardwareService : KoinComponent {

    private val daemonBridge: DaemonBridgeApi by inject()

    private val tag = "HardwareService"
    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _videoEvents = MutableSharedFlow<VideoEvent>(extraBufferCapacity = 1)
    private val _cameraEvents = MutableSharedFlow<VideoEvent>(extraBufferCapacity = 1)
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(STATE_INTERNAL)
    private val _connectivityState = MutableStateFlow(true)
    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(1920 to 1080)

    private val videoSurfaces = mutableMapOf<String, Any>()
    private var previewSurface: Any? = null
    private var speakerphoneOn = false
    private var pathMonitor: nw_path_monitor_t = null
    private var logging = false
    private var screenShareSession: Any? = null
    private var isScreenSharing = false
    private var currentCameraId: String? = null

    actual val videoEvents: Flow<VideoEvent> = _videoEvents.asSharedFlow()
    actual val cameraEvents: Flow<VideoEvent> = _cameraEvents.asSharedFlow()
    actual val bluetoothEvents: Flow<BluetoothEvent> = _bluetoothEvents.asSharedFlow()
    actual val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    actual val connectivityState: Flow<Boolean> = _connectivityState.asStateFlow()

    init {
        logging = userDefaults.boolForKey(LOGGING_ENABLED_KEY)
    }

    // ── Audio ────────────────────────────────────────────────────────────────

    actual fun getAudioState(conf: Conference): Flow<AudioState> = audioState

    actual fun updateAudioState(
        conference: Conference?,
        call: Call,
        incomingCall: Boolean,
        isOngoingVideo: Boolean
    ) {
        val state = call.callStatus
        val ended = state == Call.CallStatus.HUNGUP ||
            state == Call.CallStatus.FAILURE ||
            state == Call.CallStatus.OVER
        if (ended) {
            closeAudioState()
            return
        }
        if (state == Call.CallStatus.CURRENT) {
            speakerphoneOn = isOngoingVideo
            updateAudioOutput()
        }
    }

    actual fun closeAudioState() = abandonAudioFocus()

    actual fun isSpeakerphoneOn(): Boolean = speakerphoneOn

    actual fun toggleSpeakerphone(conf: Conference, enabled: Boolean) {
        speakerphoneOn = enabled
        updateAudioOutput()
    }

    private fun updateAudioOutput() {
        val current = if (speakerphoneOn) OUTPUT_SPEAKERS else OUTPUT_INTERNAL
        _audioState.value = AudioState(current, listOf(OUTPUT_INTERNAL, OUTPUT_SPEAKERS))
    }

    actual fun abandonAudioFocus() {
        speakerphoneOn = false
        _audioState.value = STATE_INTERNAL
    }

    actual fun hasMicrophone(): Boolean = true

    actual fun shouldPlaySpeaker(): Boolean = speakerphoneOn

    // ── Camera / video ───────────────────────────────────────────────────────

    /** Video capture devices as reported by AVFoundation, newest-API-first. */
    private fun videoDevices(): List<AVCaptureDevice> {
        val mediaType = AVMediaTypeVideo ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (AVCaptureDevice.devicesWithMediaType(mediaType) as? List<AVCaptureDevice>)
            ?: emptyList()
    }

    private fun videoDeviceIds(): List<String> = videoDevices().mapNotNull { it.uniqueID }

    /**
     * Unlike iOS, this does **not** register the cameras with the daemon.
     *
     * `libjami::addVideoDevice`/`removeVideoDevice` only exist on Android and iOS —
     * `videomanager_interface.h` guards them with
     * `#if defined(__ANDROID__) || (defined(TARGET_OS_IOS) && TARGET_OS_IOS)` — because on
     * those platforms the client owns the camera. On macOS the daemon enumerates capture
     * devices itself, so pushing them in is both impossible and unnecessary; the ObjC
     * wrapper's `addVideoDevice:` is a no-op here. The enumeration below is still used to
     * answer [hasCamera]/[cameraCount]/[isVideoAvailable] for the UI, and to pick a default.
     */
    actual suspend fun initVideo() {
        val ids = videoDeviceIds()
        ids.firstOrNull()?.let {
            currentCameraId = it
            daemonBridge.setDefaultDevice(it)
        }
        Log.d(tag, "initVideo: ${ids.size} capture device(s) (daemon-side enumeration)")
    }

    actual val isVideoAvailable: Boolean get() = videoDeviceIds().isNotEmpty()

    actual fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean) {
        scope.launch {
            _videoEvents.emit(VideoEvent(sinkId = id, started = true, width = width, height = height))
        }
    }

    actual fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {
        scope.launch { _videoEvents.emit(VideoEvent(sinkId = id)) }
    }

    actual fun hasInput(id: String): Boolean = currentCameraId == id

    actual fun getCameraInfo(
        camId: String,
        formats: MutableList<Int>,
        sizes: MutableList<Int>,
        rates: MutableList<Int>
    ) {
        // Without a capture session to interrogate, report the resolutions every macOS
        // FaceTime/USB camera supports rather than reporting none (which would leave the
        // daemon with no usable format at all).
        formats.add(0)
        sizes.addAll(listOf(1920, 1080, 1280, 720, 640, 480))
        rates.addAll(listOf(30, 24))
    }

    actual fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {}

    // Capture is not implemented on macOS — there is no MacOSCameraService. Outgoing video is
    // therefore unavailable; incoming video and audio are unaffected.
    actual fun startCameraPreview(videoPreview: Boolean) {}
    actual fun cameraCleanup() {}

    actual fun startCapture(camId: String?) {
        Log.w(tag, "startCapture($camId): camera capture is not implemented on macOS")
    }

    actual fun stopCapture(camId: String) {}
    actual fun requestKeyFrame(camId: String) {}
    actual fun setBitrate(camId: String, bitrate: Int) {}

    actual fun addVideoSurface(id: String, holder: Any) {
        videoSurfaces[id] = holder
    }

    actual fun updateVideoSurfaceId(currentId: String, newId: String) {
        videoSurfaces.remove(currentId)?.let { videoSurfaces[newId] = it }
    }

    actual fun removeVideoSurface(id: String) {
        videoSurfaces.remove(id)
    }

    actual fun addPreviewVideoSurface(holder: Any, conference: Conference?) {
        previewSurface = holder
    }

    actual fun updatePreviewVideoSurface(conference: Conference) {}

    actual fun removePreviewVideoSurface() {
        previewSurface = null
    }

    actual fun addFullScreenPreviewSurface(holder: Any) = addPreviewVideoSurface(holder, null)

    actual fun removeFullScreenPreviewSurface() = removePreviewVideoSurface()

    /**
     * Returns the camera being switched to, or null when there is no second camera.
     *
     * Enumeration is real, so this reports the correct id; the hardware switch itself is a
     * no-op until capture exists.
     */
    actual fun changeCamera(setDefaultCamera: Boolean): String? {
        val ids = videoDeviceIds()
        if (ids.isEmpty()) return null
        val target = if (setDefaultCamera) {
            ids.first()
        } else {
            val next = ids.indexOf(currentCameraId) + 1
            ids[next % ids.size]
        }
        currentCameraId = target
        return target
    }

    actual fun setPreviewSettings() {}

    actual fun hasCamera(): Boolean = videoDeviceIds().isNotEmpty()

    actual fun cameraCount(): Int = videoDeviceIds().size

    actual val maxResolutions: Flow<Pair<Int?, Int?>> = _maxResolutions.asStateFlow()

    /** macOS built-in cameras face the user; there is no rear camera to distinguish. */
    actual val isPreviewFromFrontCamera: Boolean = true

    actual fun unregisterCameraDetectionCallback() {}

    actual fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> = flow { emit(1280 to 720) }

    actual suspend fun getSinkSize(id: String): Pair<Int, Int> = 1280 to 720

    /** Display orientation is fixed on macOS. */
    actual fun setDeviceOrientation(rotation: Int) {}

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
                if (isScreenSharing) {
                    isScreenSharing = false
                    screenShareSession = null
                }
                daemonBridge.switchVideoInput(accountId, callId, uri)
            }
            else -> daemonBridge.switchVideoInput(accountId, callId, uri)
        }
    }

    actual fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>) {}

    // ── Media handlers / screen share ────────────────────────────────────────

    actual fun startMediaHandler(mediaHandlerId: String?) {}
    actual fun stopMediaHandler() {}

    actual fun setPendingScreenShareProjection(screenCaptureSession: Any?) {
        screenShareSession = screenCaptureSession
        if (screenCaptureSession == null) isScreenSharing = false
    }

    actual val screenShareRequest: SharedFlow<Unit> = MutableSharedFlow()
    actual val screenShareReady: SharedFlow<Unit> = MutableSharedFlow()

    /** Screen capture on macOS would go through ScreenCaptureKit; not implemented. */
    actual fun requestScreenSharePermission() {}

    // ── Connectivity ─────────────────────────────────────────────────────────

    actual fun connectivityChanged(isConnected: Boolean) {
        _connectivityState.value = isConnected
        daemonBridge.connectivityChanged()
    }

    /**
     * Starts watching for network changes and feeds them into [connectivityChanged].
     *
     * Same implementation as iOS — the Network framework is available on both — so macOS gets
     * correct `setAccountsActive()` behaviour across network transitions. Idempotent.
     */
    fun startConnectivityMonitoring() {
        if (pathMonitor != null) return
        val monitor = nw_path_monitor_create() ?: run {
            Log.e(tag, "Failed to create network path monitor")
            return
        }
        pathMonitor = monitor
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path ->
            val connected = path != null && nw_path_get_status(path) == nw_path_status_satisfied
            if (_connectivityState.value != connected) {
                Log.d(tag, "Connectivity changed: connected=$connected")
                connectivityChanged(connected)
            }
        }
        nw_path_monitor_start(monitor)
        Log.d(tag, "Network path monitoring started")
    }

    // ── Logging ──────────────────────────────────────────────────────────────

    actual val isLogging: Boolean get() = logging

    actual fun startLogs(): Flow<List<String>> {
        logging = true
        return MutableSharedFlow()
    }

    actual fun stopLogs() {
        logging = false
    }

    actual fun logMessage(message: String) = Log.d(tag, message)

    actual fun saveLoggingState(enabled: Boolean) {
        logging = enabled
        userDefaults.setBool(enabled, LOGGING_ENABLED_KEY)
    }

    companion object {
        private const val LOGGING_ENABLED_KEY = "hardware_logging_enabled"
    }
}
