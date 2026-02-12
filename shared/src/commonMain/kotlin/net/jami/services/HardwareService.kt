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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.model.Call
import net.jami.model.Conference

/**
 * Service for managing hardware (camera, audio, video preview).
 *
 * Ported from: jami-client-android libjamiclient HardwareService.kt
 * Converted from RxJava to Kotlin Flow.
 *
 * Platform-specific implementations:
 * - Android: Camera2/CameraX, AudioManager, MediaCodec
 * - iOS/macOS: AVFoundation, AVCaptureSession
 * - Desktop: JavaSound, webcam libraries
 * - Web: WebRTC getUserMedia
 */
interface HardwareService {

    // ══════════════════════════════════════════════════════════════════════════
    // Event Flows
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Flow of video rendering events (sink started/stopped/resized).
     */
    val videoEvents: Flow<VideoEvent>

    /**
     * Flow of camera events (capture started/stopped).
     */
    val cameraEvents: Flow<VideoEvent>

    /**
     * Flow of Bluetooth headset events.
     */
    val bluetoothEvents: Flow<BluetoothEvent>

    /**
     * Flow of audio output state changes.
     */
    val audioState: StateFlow<AudioState>

    /**
     * Flow of network connectivity changes.
     */
    val connectivityState: Flow<Boolean>

    // ══════════════════════════════════════════════════════════════════════════
    // Audio Management
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Get current audio state for a conference.
     */
    fun getAudioState(conf: Conference): Flow<AudioState>

    /**
     * Update audio state for a call.
     *
     * @param conference The conference containing the call
     * @param call The call being updated
     * @param incomingCall Whether this is an incoming call
     * @param isOngoingVideo Whether the call has ongoing video
     */
    fun updateAudioState(
        conference: Conference?,
        call: Call,
        incomingCall: Boolean,
        isOngoingVideo: Boolean
    )

    /**
     * Close audio state after a call ends.
     */
    fun closeAudioState()

    /**
     * Check if speakerphone is currently on.
     */
    fun isSpeakerphoneOn(): Boolean

    /**
     * Toggle speakerphone on/off.
     */
    fun toggleSpeakerphone(conf: Conference, enabled: Boolean)

    /**
     * Abandon audio focus (release audio resources).
     */
    fun abandonAudioFocus()

    /**
     * Check if microphone is available.
     */
    fun hasMicrophone(): Boolean

    /**
     * Check if speaker should be used for playback.
     */
    fun shouldPlaySpeaker(): Boolean

    // ══════════════════════════════════════════════════════════════════════════
    // Video/Camera Management
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Initialize video subsystem.
     */
    suspend fun initVideo()

    /**
     * Check if video is available on this device.
     */
    val isVideoAvailable: Boolean

    /**
     * Called when video decoding starts for a remote stream.
     */
    fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean)

    /**
     * Called when video decoding stops for a remote stream.
     */
    fun decodingStopped(id: String, shmPath: String, isMixer: Boolean)

    /**
     * Check if a video input is currently active.
     */
    fun hasInput(id: String): Boolean

    /**
     * Get camera information (formats, sizes, rates).
     */
    fun getCameraInfo(camId: String, formats: MutableList<Int>, sizes: MutableList<Int>, rates: MutableList<Int>)

    /**
     * Set camera parameters.
     */
    fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int)

    /**
     * Start camera preview.
     */
    fun startCameraPreview(videoPreview: Boolean)

    /**
     * Cleanup camera resources.
     */
    fun cameraCleanup()

    /**
     * Start video capture from camera.
     *
     * @param camId Camera ID to use, or null for default
     */
    fun startCapture(camId: String?)

    /**
     * Stop video capture.
     */
    fun stopCapture(camId: String)

    /**
     * Request a keyframe from the encoder.
     */
    fun requestKeyFrame(camId: String)

    /**
     * Set video encoding bitrate.
     */
    fun setBitrate(camId: String, bitrate: Int)

    /**
     * Add a video surface for rendering remote video.
     */
    fun addVideoSurface(id: String, holder: Any)

    /**
     * Update video surface ID (when surface changes).
     */
    fun updateVideoSurfaceId(currentId: String, newId: String)

    /**
     * Remove a video surface.
     */
    fun removeVideoSurface(id: String)

    /**
     * Add preview surface for local camera.
     */
    fun addPreviewVideoSurface(holder: Any, conference: Conference?)

    /**
     * Update preview video surface.
     */
    fun updatePreviewVideoSurface(conference: Conference)

    /**
     * Remove preview video surface.
     */
    fun removePreviewVideoSurface()

    /**
     * Add fullscreen preview surface.
     */
    fun addFullScreenPreviewSurface(holder: Any)

    /**
     * Remove fullscreen preview surface.
     */
    fun removeFullScreenPreviewSurface()

    /**
     * Switch between front and back camera.
     *
     * @param setDefaultCamera If true, reset to default camera
     * @return The new camera ID, or null if failed
     */
    fun changeCamera(setDefaultCamera: Boolean = false): String?

    /**
     * Set camera preview settings.
     */
    fun setPreviewSettings()

    /**
     * Check if device has a camera.
     */
    fun hasCamera(): Boolean

    /**
     * Get number of cameras on device.
     */
    fun cameraCount(): Int

    /**
     * Flow of maximum resolution changes.
     */
    val maxResolutions: Flow<Pair<Int?, Int?>>

    /**
     * Check if current preview is from front camera.
     */
    val isPreviewFromFrontCamera: Boolean

    /**
     * Unregister camera detection callback.
     */
    fun unregisterCameraDetectionCallback()

    /**
     * Connect to a video sink and receive size updates.
     *
     * @param id Sink ID
     * @param windowId Window ID for rendering
     * @return Flow of width/height pairs
     */
    fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>>

    /**
     * Get current size of a video sink.
     */
    suspend fun getSinkSize(id: String): Pair<Int, Int>

    /**
     * Set device orientation for camera rotation.
     */
    fun setDeviceOrientation(rotation: Int)

    // ══════════════════════════════════════════════════════════════════════════
    // Video I/O
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Start video output to a native surface.
     *
     * @param inputId Video input ID
     * @param surface Native surface (platform-specific)
     * @param width Surface width
     * @param height Surface height
     * @return Native window handle
     */
    fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long

    /**
     * Stop video output.
     */
    fun stopVideo(inputId: String, inputWindow: Long)

    /**
     * Switch video input source.
     */
    fun switchInput(accountId: String, callId: String, uri: String)

    /**
     * Apply camera settings.
     */
    fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>)

    // ══════════════════════════════════════════════════════════════════════════
    // Media Handlers / Screen Share
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Start a media handler plugin.
     */
    fun startMediaHandler(mediaHandlerId: String?)

    /**
     * Stop current media handler.
     */
    fun stopMediaHandler()

    /**
     * Set pending screen share projection (Android).
     */
    fun setPendingScreenShareProjection(screenCaptureSession: Any?)

    // ══════════════════════════════════════════════════════════════════════════
    // Connectivity
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Notify daemon of connectivity change.
     */
    fun connectivityChanged(isConnected: Boolean)

    // ══════════════════════════════════════════════════════════════════════════
    // Logging
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Check if logging is currently enabled.
     */
    val isLogging: Boolean

    /**
     * Start daemon log collection.
     *
     * @return Flow of log message batches
     */
    fun startLogs(): Flow<List<String>>

    /**
     * Stop daemon log collection.
     */
    fun stopLogs()

    /**
     * Log a message.
     */
    fun logMessage(message: String)

    /**
     * Save logging state preference.
     */
    fun saveLoggingState(enabled: Boolean)

    companion object {
        val OUTPUT_SPEAKERS = AudioOutput(AudioOutputType.SPEAKERS)
        val OUTPUT_INTERNAL = AudioOutput(AudioOutputType.INTERNAL)
        val OUTPUT_WIRED = AudioOutput(AudioOutputType.WIRED)
        val OUTPUT_BLUETOOTH = AudioOutput(AudioOutputType.BLUETOOTH)
        val STATE_INTERNAL = AudioState(OUTPUT_INTERNAL)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Data Classes
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Video rendering event.
 */
data class VideoEvent(
    val sinkId: String,
    val start: Boolean = false,
    val started: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0
)

/**
 * Bluetooth headset connection event.
 */
data class BluetoothEvent(val connected: Boolean)

/**
 * Audio output type.
 */
enum class AudioOutputType {
    /** Internal earpiece */
    INTERNAL,
    /** Wired headset */
    WIRED,
    /** Built-in speaker */
    SPEAKERS,
    /** Bluetooth headset/speaker */
    BLUETOOTH
}

/**
 * Audio output device.
 */
data class AudioOutput(
    val type: AudioOutputType,
    val outputName: String? = null,
    val outputId: String? = null
)

/**
 * Current audio state including selected output and available outputs.
 */
data class AudioState(
    val output: AudioOutput,
    val availableOutputs: List<AudioOutput> = emptyList()
)

// ══════════════════════════════════════════════════════════════════════════════
// Stub Implementation
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Stub implementation of HardwareService for testing and platforms without hardware access.
 */
class StubHardwareService : HardwareService {
    private val _videoEvents = MutableSharedFlow<VideoEvent>()
    private val _cameraEvents = MutableSharedFlow<VideoEvent>()
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(HardwareService.STATE_INTERNAL)
    private val _connectivityState = MutableStateFlow(true)
    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(null to null)

    private var speakerOn = false
    private var logging = false

    override val videoEvents: Flow<VideoEvent> = _videoEvents.asSharedFlow()
    override val cameraEvents: Flow<VideoEvent> = _cameraEvents.asSharedFlow()
    override val bluetoothEvents: Flow<BluetoothEvent> = _bluetoothEvents.asSharedFlow()
    override val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    override val connectivityState: Flow<Boolean> = _connectivityState.asStateFlow()

    override fun getAudioState(conf: Conference): Flow<AudioState> = audioState
    override fun updateAudioState(conference: Conference?, call: Call, incomingCall: Boolean, isOngoingVideo: Boolean) {}
    override fun closeAudioState() {}
    override fun isSpeakerphoneOn(): Boolean = speakerOn
    override fun toggleSpeakerphone(conf: Conference, enabled: Boolean) { speakerOn = enabled }
    override fun abandonAudioFocus() {}
    override fun hasMicrophone(): Boolean = true
    override fun shouldPlaySpeaker(): Boolean = false

    override suspend fun initVideo() {}
    override val isVideoAvailable: Boolean = false
    override fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean) {}
    override fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {}
    override fun hasInput(id: String): Boolean = false
    override fun getCameraInfo(camId: String, formats: MutableList<Int>, sizes: MutableList<Int>, rates: MutableList<Int>) {}
    override fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {}
    override fun startCameraPreview(videoPreview: Boolean) {}
    override fun cameraCleanup() {}
    override fun startCapture(camId: String?) {}
    override fun stopCapture(camId: String) {}
    override fun requestKeyFrame(camId: String) {}
    override fun setBitrate(camId: String, bitrate: Int) {}
    override fun addVideoSurface(id: String, holder: Any) {}
    override fun updateVideoSurfaceId(currentId: String, newId: String) {}
    override fun removeVideoSurface(id: String) {}
    override fun addPreviewVideoSurface(holder: Any, conference: Conference?) {}
    override fun updatePreviewVideoSurface(conference: Conference) {}
    override fun removePreviewVideoSurface() {}
    override fun addFullScreenPreviewSurface(holder: Any) {}
    override fun removeFullScreenPreviewSurface() {}
    override fun changeCamera(setDefaultCamera: Boolean): String? = null
    override fun setPreviewSettings() {}
    override fun hasCamera(): Boolean = false
    override fun cameraCount(): Int = 0
    override val maxResolutions: Flow<Pair<Int?, Int?>> = _maxResolutions.asStateFlow()
    override val isPreviewFromFrontCamera: Boolean = true
    override fun unregisterCameraDetectionCallback() {}
    override fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> = MutableStateFlow(0 to 0)
    override suspend fun getSinkSize(id: String): Pair<Int, Int> = 0 to 0
    override fun setDeviceOrientation(rotation: Int) {}
    override fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long = 0L
    override fun stopVideo(inputId: String, inputWindow: Long) {}
    override fun switchInput(accountId: String, callId: String, uri: String) {}
    override fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>) {}
    override fun startMediaHandler(mediaHandlerId: String?) {}
    override fun stopMediaHandler() {}
    override fun setPendingScreenShareProjection(screenCaptureSession: Any?) {}
    override fun connectivityChanged(isConnected: Boolean) { _connectivityState.value = isConnected }
    override val isLogging: Boolean get() = logging
    override fun startLogs(): Flow<List<String>> {
        logging = true
        return MutableSharedFlow()
    }
    override fun stopLogs() { logging = false }
    override fun logMessage(message: String) {}
    override fun saveLoggingState(enabled: Boolean) { logging = enabled }
}
