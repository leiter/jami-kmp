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
import platform.Foundation.NSUserDefaults

/**
 * macOS implementation of HardwareService.
 *
 * Audio management uses basic state tracking.
 * Camera/video support requires AVFoundation integration at the UI layer.
 *
 * For full camera support, the macOS app should use:
 * - AVCaptureSession for camera capture
 * - AVAudioEngine or CoreAudio for audio
 */
class MacOSHardwareService : HardwareService {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    // Event flows
    private val _videoEvents = MutableSharedFlow<VideoEvent>()
    private val _cameraEvents = MutableSharedFlow<VideoEvent>()
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(HardwareService.STATE_INTERNAL)
    private val _connectivityState = MutableStateFlow(true) // Assume connected on desktop
    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(null to null)

    private var speakerphoneOn = false
    private var logging = false

    override val videoEvents: Flow<VideoEvent> = _videoEvents.asSharedFlow()
    override val cameraEvents: Flow<VideoEvent> = _cameraEvents.asSharedFlow()
    override val bluetoothEvents: Flow<BluetoothEvent> = _bluetoothEvents.asSharedFlow()
    override val audioState: StateFlow<AudioState> = _audioState.asStateFlow()
    override val connectivityState: Flow<Boolean> = _connectivityState.asStateFlow()

    init {
        logging = userDefaults.boolForKey(LOGGING_ENABLED_KEY)
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
            Call.CallStatus.CURRENT -> {
                speakerphoneOn = isOngoingVideo
                updateAudioOutput()
            }
            else -> { }
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
        val output = if (speakerphoneOn) {
            HardwareService.OUTPUT_SPEAKERS
        } else {
            HardwareService.OUTPUT_INTERNAL
        }
        _audioState.value = AudioState(output, getAvailableOutputs())
    }

    private fun getAvailableOutputs(): List<AudioOutput> {
        // macOS audio devices would require CoreAudio enumeration
        // Return standard outputs for now
        return listOf(
            HardwareService.OUTPUT_INTERNAL,
            HardwareService.OUTPUT_SPEAKERS
        )
    }

    override fun abandonAudioFocus() {
        speakerphoneOn = false
        _audioState.value = HardwareService.STATE_INTERNAL
    }

    override fun hasMicrophone(): Boolean = true // Macs typically have microphones

    override fun shouldPlaySpeaker(): Boolean = speakerphoneOn

    // ══════════════════════════════════════════════════════════════════════════
    // Video/Camera Management (Requires AVFoundation at UI layer)
    // ══════════════════════════════════════════════════════════════════════════

    override suspend fun initVideo() {
        // Camera initialization handled by AVCaptureSession in macOS app
    }

    override val isVideoAvailable: Boolean = true // Most Macs have cameras

    override fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean) {
        // Video decoding events
    }

    override fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {
        // Video decoding events
    }

    override fun hasInput(id: String): Boolean = false

    override fun getCameraInfo(camId: String, formats: MutableList<Int>, sizes: MutableList<Int>, rates: MutableList<Int>) {
        // Requires AVCaptureDevice enumeration
    }

    override fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {
        // Requires AVCaptureSession configuration
    }

    override fun startCameraPreview(videoPreview: Boolean) {
        // Requires AVCaptureSession
    }

    override fun cameraCleanup() {
        // Requires AVCaptureSession
    }

    override fun startCapture(camId: String?) {
        // Requires AVCaptureSession
    }

    override fun stopCapture(camId: String) {
        // Requires AVCaptureSession
    }

    override fun requestKeyFrame(camId: String) {
        // Requires encoder
    }

    override fun setBitrate(camId: String, bitrate: Int) {
        // Requires encoder
    }

    override fun addVideoSurface(id: String, holder: Any) {
        // Video surface management
    }

    override fun updateVideoSurfaceId(currentId: String, newId: String) {
        // Video surface management
    }

    override fun removeVideoSurface(id: String) {
        // Video surface management
    }

    override fun addPreviewVideoSurface(holder: Any, conference: Conference?) {
        // Preview surface management
    }

    override fun updatePreviewVideoSurface(conference: Conference) {
        // Preview surface management
    }

    override fun removePreviewVideoSurface() {
        // Preview surface management
    }

    override fun addFullScreenPreviewSurface(holder: Any) {
        // Full screen preview
    }

    override fun removeFullScreenPreviewSurface() {
        // Full screen preview
    }

    override fun changeCamera(setDefaultCamera: Boolean): String? = null

    override fun setPreviewSettings() {
        // Preview settings
    }

    override fun hasCamera(): Boolean = true // Most Macs have FaceTime camera

    override fun cameraCount(): Int = 1 // Typically one built-in camera

    override val maxResolutions: Flow<Pair<Int?, Int?>> = _maxResolutions.asStateFlow()

    override val isPreviewFromFrontCamera: Boolean = true

    override fun unregisterCameraDetectionCallback() {
        // Camera detection
    }

    override fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> =
        MutableStateFlow(0 to 0)

    override suspend fun getSinkSize(id: String): Pair<Int, Int> = 0 to 0

    override fun setDeviceOrientation(rotation: Int) {
        // Device orientation (less relevant on macOS)
    }

    override fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long = 0L

    override fun stopVideo(inputId: String, inputWindow: Long) {
        // Video output
    }

    override fun switchInput(accountId: String, callId: String, uri: String) {
        // Input switching
    }

    override fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>) {
        // Camera settings
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Media Handlers / Screen Share
    // ══════════════════════════════════════════════════════════════════════════

    override fun startMediaHandler(mediaHandlerId: String?) {
        // Media handler support
    }

    override fun stopMediaHandler() {
        // Media handler support
    }

    override fun setPendingScreenShareProjection(screenCaptureSession: Any?) {
        // Screen sharing via ScreenCaptureKit on macOS
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
        // Log message handling
    }

    override fun saveLoggingState(enabled: Boolean) {
        logging = enabled
        userDefaults.setBool(enabled, LOGGING_ENABLED_KEY)
    }

    companion object {
        private const val LOGGING_ENABLED_KEY = "hardware_logging_enabled"
    }
}
