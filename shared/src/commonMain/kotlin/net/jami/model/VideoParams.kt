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
package net.jami.model

/**
 * Video capture parameters for a camera or screen share source.
 *
 * Platform-agnostic representation of video source configuration.
 * Platform-specific implementations (AndroidVideoParams, IOSVideoParams) may
 * extend this with additional fields for native resources.
 *
 * Ported from: jami-client-android CameraService.VideoParams
 */
data class VideoParams(
    val id: String,
    val width: Int,
    val height: Int,
    val rate: Int,
    var rotation: Int = 0,
    var codec: String? = null,
    var isCapturing: Boolean = false
) {
    val inputUri: String
        get() = "camera://$id"

    val isScreenShare: Boolean
        get() = id == SCREEN_SHARING_ID

    fun toMap(): Map<String, String> = mapOf(
        "size" to "${width}x${height}",
        "rate" to rate.toString()
    )

    companion object {
        const val SCREEN_SHARING_ID = "desktop"

        fun fromSize(id: String, width: Int, height: Int, rate: Int): VideoParams =
            VideoParams(id, width, height, rate)
    }
}

/**
 * Camera device characteristics from the system.
 *
 * Contains native camera information retrieved during device enumeration.
 * Used to select appropriate capture parameters.
 */
data class DeviceParams(
    val width: Int = 0,
    val height: Int = 0,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val rate: Long = 0,
    val facing: CameraFacing = CameraFacing.UNKNOWN,
    val orientation: Int = 0
) {
    fun toMap(): Map<String, String> = mapOf(
        "size" to "${width}x${height}",
        "rate" to rate.toString()
    )

    val surface: Int
        get() = width * height

    val maxSurface: Int
        get() = maxWidth * maxHeight
}

/**
 * Camera facing direction.
 */
enum class CameraFacing {
    UNKNOWN,
    FRONT,
    BACK,
    EXTERNAL
}

/**
 * Container for available video devices on the system.
 */
data class VideoDevices(
    val cameras: MutableList<String> = mutableListOf(),
    var currentId: String? = null,
    var currentIndex: Int = 0,
    var cameraFront: String? = null,
    var cameraBack: String? = null
) {
    fun switchInput(setDefaultCamera: Boolean): String? {
        if (setDefaultCamera && cameras.isNotEmpty()) {
            currentId = cameras[0]
        } else if (cameras.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % cameras.size
            currentId = cameras[currentIndex]
        } else {
            currentId = null
        }
        return currentId
    }

    fun addCamera(cameraId: String) {
        if (!cameras.contains(cameraId)) {
            cameras.add(cameraId)
        }
    }

    fun removeCamera(cameraId: String) {
        cameras.remove(cameraId)
        if (cameraFront == cameraId) cameraFront = null
        if (cameraBack == cameraId) cameraBack = null
    }

    val isEmpty: Boolean
        get() = cameras.isEmpty()

    val hasMultipleCameras: Boolean
        get() = cameras.size > 1

    companion object {
        const val SCREEN_SHARING = "desktop"
    }
}
