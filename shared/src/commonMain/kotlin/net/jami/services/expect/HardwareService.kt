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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import net.jami.model.Call
import net.jami.model.Conference

expect class HardwareService {
    val videoEvents: Flow<VideoEvent>
    val cameraEvents: Flow<VideoEvent>
    val bluetoothEvents: Flow<BluetoothEvent>
    val audioState: StateFlow<AudioState>
    val connectivityState: Flow<Boolean>

    fun getAudioState(conf: Conference): Flow<AudioState>
    fun updateAudioState(
        conference: Conference?,
        call: Call,
        incomingCall: Boolean,
        isOngoingVideo: Boolean
    )
    fun closeAudioState()
    fun isSpeakerphoneOn(): Boolean
    fun toggleSpeakerphone(conf: Conference, enabled: Boolean)
    fun abandonAudioFocus()
    fun hasMicrophone(): Boolean
    fun shouldPlaySpeaker(): Boolean

    suspend fun initVideo()
    val isVideoAvailable: Boolean
    fun decodingStarted(id: String, shmPath: String, width: Int, height: Int, isMixer: Boolean)
    fun decodingStopped(id: String, shmPath: String, isMixer: Boolean)
    fun hasInput(id: String): Boolean
    fun getCameraInfo(camId: String, formats: MutableList<Int>, sizes: MutableList<Int>, rates: MutableList<Int>)
    fun setParameters(camId: String, format: Int, width: Int, height: Int, rate: Int)
    fun startCameraPreview(videoPreview: Boolean)
    fun cameraCleanup()
    fun startCapture(camId: String?)
    fun stopCapture(camId: String)
    fun requestKeyFrame(camId: String)
    fun setBitrate(camId: String, bitrate: Int)
    fun addVideoSurface(id: String, holder: Any)
    fun updateVideoSurfaceId(currentId: String, newId: String)
    fun removeVideoSurface(id: String)
    fun addPreviewVideoSurface(holder: Any, conference: Conference?)
    fun updatePreviewVideoSurface(conference: Conference)
    fun removePreviewVideoSurface()
    fun addFullScreenPreviewSurface(holder: Any)
    fun removeFullScreenPreviewSurface()
    fun changeCamera(setDefaultCamera: Boolean = false): String?
    fun setPreviewSettings()
    fun hasCamera(): Boolean
    fun cameraCount(): Int
    val maxResolutions: Flow<Pair<Int?, Int?>>
    val isPreviewFromFrontCamera: Boolean
    fun unregisterCameraDetectionCallback()
    fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>>
    suspend fun getSinkSize(id: String): Pair<Int, Int>
    fun setDeviceOrientation(rotation: Int)

    fun startVideo(inputId: String, surface: Any, width: Int, height: Int): Long
    fun stopVideo(inputId: String, inputWindow: Long)
    fun switchInput(accountId: String, callId: String, uri: String)
    fun setPreviewSettings(cameraMaps: Map<String, Map<String, String>>)

    fun startMediaHandler(mediaHandlerId: String?)
    fun stopMediaHandler()
    fun setPendingScreenShareProjection(screenCaptureSession: Any?)

    val screenShareRequest: SharedFlow<Unit>
    val screenShareReady: SharedFlow<Unit>
    fun requestScreenSharePermission()

    fun connectivityChanged(isConnected: Boolean)

    val isLogging: Boolean
    fun startLogs(): Flow<List<String>>
    fun stopLogs()
    fun logMessage(message: String)
    fun saveLoggingState(enabled: Boolean)
}

data class VideoEvent(
    val sinkId: String,
    val start: Boolean = false,
    val started: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0
)

data class BluetoothEvent(val connected: Boolean)

enum class AudioOutputType {
    INTERNAL,
    WIRED,
    SPEAKERS,
    BLUETOOTH
}

data class AudioOutput(
    val type: AudioOutputType,
    val outputName: String? = null,
    val outputId: String? = null
)

data class AudioState(
    val output: AudioOutput,
    val availableOutputs: List<AudioOutput> = emptyList()
)
