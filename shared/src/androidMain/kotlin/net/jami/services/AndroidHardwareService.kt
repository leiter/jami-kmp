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
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.view.Surface
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.model.Call
import net.jami.model.Conference

/**
 * Android implementation of HardwareService.
 *
 * Provides audio management via AudioManager and basic connectivity monitoring.
 * Camera operations are delegated to a CameraService (to be implemented separately).
 *
 * This is a simplified implementation. For full camera support, a dedicated
 * CameraService using Camera2 API would be needed.
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

    // Event flows
    private val _videoEvents = MutableSharedFlow<VideoEvent>()
    private val _cameraEvents = MutableSharedFlow<VideoEvent>()
    private val _bluetoothEvents = MutableSharedFlow<BluetoothEvent>()
    private val _audioState = MutableStateFlow(HardwareService.STATE_INTERNAL)
    private val _connectivityState = MutableStateFlow(isNetworkConnected())
    private val _maxResolutions = MutableStateFlow<Pair<Int?, Int?>>(null to null)

    private var speakerphoneOn = false
    private var logging = false

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

    private fun isNetworkConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

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
    // Video/Camera Management (Simplified - Requires CameraService for full impl)
    // ══════════════════════════════════════════════════════════════════════════

    override suspend fun initVideo() {
        // Camera initialization would be done by CameraService
    }

    override val isVideoAvailable: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    override fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean) {
        // Forward to video event flow
    }

    override fun decodingStopped(id: String, shmPath: String, isMixer: Boolean) {
        // Forward to video event flow
    }

    override fun hasInput(id: String): Boolean = false

    override fun getCameraInfo(camId: String, formats: MutableList<Int>, sizes: MutableList<Int>, rates: MutableList<Int>) {
        // Requires CameraService implementation
    }

    override fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int) {
        // Requires CameraService implementation
    }

    override fun startCameraPreview(videoPreview: Boolean) {
        // Requires CameraService implementation
    }

    override fun cameraCleanup() {
        // Requires CameraService implementation
    }

    override fun startCapture(camId: String?) {
        // Requires CameraService implementation
    }

    override fun stopCapture(camId: String) {
        // Requires CameraService implementation
    }

    override fun requestKeyFrame(camId: String) {
        // Requires CameraService implementation
    }

    override fun setBitrate(camId: String, bitrate: Int) {
        // Requires CameraService implementation
    }

    override fun addVideoSurface(id: String, holder: Any) {
        // Requires video surface management
    }

    override fun updateVideoSurfaceId(currentId: String, newId: String) {
        // Requires video surface management
    }

    override fun removeVideoSurface(id: String) {
        // Requires video surface management
    }

    override fun addPreviewVideoSurface(holder: Any, conference: Conference?) {
        // Requires CameraService implementation
    }

    override fun updatePreviewVideoSurface(conference: Conference) {
        // Requires CameraService implementation
    }

    override fun removePreviewVideoSurface() {
        // Requires CameraService implementation
    }

    override fun addFullScreenPreviewSurface(holder: Any) {
        // Requires CameraService implementation
    }

    override fun removeFullScreenPreviewSurface() {
        // Requires CameraService implementation
    }

    override fun changeCamera(setDefaultCamera: Boolean): String? = null

    override fun setPreviewSettings() {
        // Requires CameraService implementation
    }

    override fun hasCamera(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    override fun cameraCount(): Int = 0

    override val maxResolutions: Flow<Pair<Int?, Int?>> = _maxResolutions.asStateFlow()

    override val isPreviewFromFrontCamera: Boolean = true

    override fun unregisterCameraDetectionCallback() {
        // Requires CameraService implementation
    }

    override fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> =
        MutableStateFlow(0 to 0)

    override suspend fun getSinkSize(id: String): Pair<Int, Int> = 0 to 0

    override fun setDeviceOrientation(rotation: Int) {
        // Requires CameraService implementation
    }

    override fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long = 0L

    override fun stopVideo(inputId: String, inputWindow: Long) {
        // Video output management
    }

    override fun switchInput(accountId: String, callId: String, uri: String) {
        // Requires CameraService implementation
    }

    override fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>) {
        // Requires CameraService implementation
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Media Handlers / Screen Share
    // ══════════════════════════════════════════════════════════════════════════

    override fun startMediaHandler(mediaHandlerId: String?) {
        // Requires MediaHandler plugin support
    }

    override fun stopMediaHandler() {
        // Requires MediaHandler plugin support
    }

    override fun setPendingScreenShareProjection(screenCaptureSession: Any?) {
        // Requires MediaProjection support
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

    companion object {
        private const val PREFS_NAME = "jami_hardware"
        private const val LOGGING_ENABLED_KEY = "logging_enabled"
    }
}
