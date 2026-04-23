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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Abstraction for video rendering surface management.
 *
 * Platform implementations connect native surfaces (SurfaceHolder on Android,
 * CALayer on iOS) to the daemon's video pipeline.
 *
 * Ported from: jami-client-android HardwareServiceImpl Shm/videoSurfaces pattern
 */
interface VideoSinkManager {
    /**
     * Register a native surface for receiving decoded video frames.
     *
     * @param id Video sink identifier (call ID or participant ID)
     * @param surface Platform-specific surface object
     * @param width Initial surface width
     * @param height Initial surface height
     * @return Native window handle for the surface
     */
    fun addVideoSurface(id: String, surface: Any, width: Int, height: Int): Long

    /**
     * Unregister a video surface.
     *
     * @param id Video sink identifier
     */
    fun removeVideoSurface(id: String)

    /**
     * Connect to a video sink and receive size updates.
     *
     * @param id Sink identifier
     * @param windowId Native window handle
     * @return Flow emitting (width, height) pairs when size changes
     */
    fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>>

    /**
     * Get current dimensions of a video sink.
     *
     * @param id Sink identifier
     * @return Current (width, height) or (0, 0) if not available
     */
    suspend fun getSinkSize(id: String): Pair<Int, Int>

    /**
     * Check if a video input/sink exists.
     */
    fun hasInput(id: String): Boolean
}

/**
 * Shared memory video input tracking.
 *
 * Tracks active video streams and their native window handles.
 * Used to manage the lifecycle of video rendering surfaces.
 */
data class VideoInput(
    val id: String,
    val width: Int,
    val height: Int,
    var windowHandle: Long = 0
) {
    val isActive: Boolean
        get() = windowHandle != 0L
}

/**
 * Default implementation of VideoSinkManager using VideoEvent flows.
 *
 * Platform-specific services can extend this or implement VideoSinkManager directly.
 */
abstract class BaseVideoSinkManager : VideoSinkManager {
    protected val videoInputs: MutableMap<String, VideoInput> = mutableMapOf()
    protected val _videoEvents = MutableSharedFlow<VideoEvent>()

    val videoEvents: Flow<VideoEvent> = _videoEvents

    override fun connectSink(id: String, windowId: Long): Flow<Pair<Int, Int>> {
        var registered = registerVideoCallback(id, windowId)

        return videoEvents
            .filter { it.sinkId == id }
            .map { event ->
                if (!registered && event.start && !event.started) {
                    if (registerVideoCallback(id, windowId)) {
                        registered = true
                    }
                } else if (!event.start && event.started) {
                    registered = false
                }
                Pair(event.width, event.height)
            }
            .onStart {
                videoInputs[id]?.let { input ->
                    emit(Pair(input.width, input.height))
                }
            }
    }

    override suspend fun getSinkSize(id: String): Pair<Int, Int> {
        return synchronized(videoInputs) {
            videoInputs[id]?.let { Pair(it.width, it.height) } ?: Pair(0, 0)
        }
    }

    override fun hasInput(id: String): Boolean = synchronized(videoInputs) {
        videoInputs.containsKey(id)
    }

    protected fun addVideoInput(id: String, width: Int, height: Int) {
        synchronized(videoInputs) {
            videoInputs[id] = VideoInput(id, width, height)
        }
    }

    protected fun removeVideoInput(id: String) {
        synchronized(videoInputs) {
            videoInputs.remove(id)
        }
    }

    protected fun getVideoInput(id: String): VideoInput? = synchronized(videoInputs) {
        videoInputs[id]
    }

    protected abstract fun registerVideoCallback(id: String, windowId: Long): Boolean
    protected abstract fun unregisterVideoCallback(id: String, windowId: Long)
}

/**
 * Video resolution constants.
 */
object VideoResolution {
    val LOW = Pair(480, 320)
    val SD = Pair(720, 480)
    val HD = Pair(1280, 720)
    val FULL_HD = Pair(1920, 1080)
    val QUAD_HD = Pair(2560, 1440)
    val ULTRA_HD = Pair(3840, 2160)

    val DEFAULT = SD

    val ALL = listOf(ULTRA_HD, QUAD_HD, FULL_HD, HD, SD, LOW)

    fun parseResolution(maxHeight: Int): Pair<Int, Int> {
        return ALL.firstOrNull { it.second <= maxHeight } ?: DEFAULT
    }
}
