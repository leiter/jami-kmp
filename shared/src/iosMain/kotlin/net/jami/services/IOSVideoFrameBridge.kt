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

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVSampleBufferDisplayLayer
import platform.CoreVideo.CVImageBufferRef

/**
 * The iOS video frame paths, which have no place in [DaemonBridgeApi] (they pass Apple types).
 * Implemented by the iOS [DaemonBridge]; callers reach it with `daemonBridge as? IOSVideoFrameBridge`.
 *
 * It is an interface, rather than a cast to [DaemonBridge], for the test binary: that binary does
 * not link the JamiBridge library, and Kotlin/Native keeps only reachable code, so a direct
 * reference to [DaemonBridge] from HardwareService or IOSCameraService pulls the whole bridge
 * into the test link and fails it on undefined `JamiBridgeWrapper` symbols. A type check against
 * an interface keeps the bridge unreachable unless it is actually instantiated.
 */
@OptIn(ExperimentalForeignApi::class)
interface IOSVideoFrameBridge {
    /**
     * Hands one captured camera frame to the daemon's encoder, zero-copy. [videoInputId] is the
     * daemon input ("camera://<device>"); a frame for an input the daemon is not capturing from
     * is dropped. [angle] is the rotation, in degrees, the receiver applies to show the frame
     * upright. Safe from the capture queue.
     */
    fun writeOutgoingVideoFrame(pixelBuffer: CVImageBufferRef, angle: Int, videoInputId: String)

    /** Renders [sinkId]'s decoded frames into [layer]; null detaches it. */
    fun attachVideoLayer(sinkId: String, layer: AVSampleBufferDisplayLayer?)

    /** Size of the last frame decoded for [sinkId], or null before the first one. */
    fun videoSinkSize(sinkId: String): Pair<Int, Int>?

    /**
     * Sizes [layer] to its superlayer, turned by the rotation of the frames rendered into it.
     * Use instead of setting the layer's frame, which is undefined on a rotated layer.
     * Main thread only.
     */
    fun layoutVideoLayer(layer: AVSampleBufferDisplayLayer)
}
