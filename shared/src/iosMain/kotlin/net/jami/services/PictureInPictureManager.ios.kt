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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.utils.Log
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.AVKit.AVPictureInPictureControllerIsPictureInPictureSupported
import platform.AVFoundation.AVPlayerLayer
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * iOS implementation of PictureInPictureManager.
 *
 * Uses AVPictureInPictureController for PiP mode on iOS 9+.
 * Note: PiP for custom video content requires AVSampleBufferDisplayLayer
 * wrapped in AVPictureInPictureVideoCallViewController (iOS 15+).
 */
@OptIn(ExperimentalForeignApi::class)
class IOSPictureInPictureManager : PictureInPictureManager {

    private val tag = "IOSPipManager"

    private var pipController: AVPictureInPictureController? = null

    private val _pipState = MutableStateFlow(
        PipState(
            isInPipMode = false,
            isSupported = AVPictureInPictureControllerIsPictureInPictureSupported()
        )
    )
    override val pipState: StateFlow<PipState> = _pipState.asStateFlow()

    private val _pipActions = MutableSharedFlow<PipAction>(extraBufferCapacity = 10)
    override val pipActions: Flow<PipAction> = _pipActions.asSharedFlow()

    private val pipDelegate = object : NSObject(), AVPictureInPictureControllerDelegateProtocol {
        override fun pictureInPictureControllerWillStartPictureInPicture(
            pictureInPictureController: AVPictureInPictureController
        ) {
            Log.d(tag, "Will start PiP")
        }

        override fun pictureInPictureControllerDidStartPictureInPicture(
            pictureInPictureController: AVPictureInPictureController
        ) {
            _pipState.value = _pipState.value.copy(isInPipMode = true)
            Log.d(tag, "Did start PiP")
        }

        override fun pictureInPictureControllerWillStopPictureInPicture(
            pictureInPictureController: AVPictureInPictureController
        ) {
            Log.d(tag, "Will stop PiP")
        }

        override fun pictureInPictureControllerDidStopPictureInPicture(
            pictureInPictureController: AVPictureInPictureController
        ) {
            _pipState.value = _pipState.value.copy(isInPipMode = false)
            Log.d(tag, "Did stop PiP")
        }

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            failedToStartPictureInPictureWithError: NSError
        ) {
            Log.e(tag, "Failed to start PiP: ${failedToStartPictureInPictureWithError.localizedDescription}")
        }
    }

    /**
     * Configure PiP with an AVPlayerLayer.
     * For video calls, use AVPictureInPictureVideoCallViewController on iOS 15+.
     */
    fun configurePipController(playerLayer: AVPlayerLayer) {
        if (!isSupported()) {
            Log.w(tag, "PiP not supported on this device")
            return
        }

        pipController = AVPictureInPictureController(playerLayer = playerLayer)
        pipController?.delegate = pipDelegate
        Log.d(tag, "PiP controller configured")
    }

    override fun isSupported(): Boolean {
        return AVPictureInPictureControllerIsPictureInPictureSupported()
    }

    override fun enterPipMode(aspectRatioWidth: Int, aspectRatioHeight: Int): Boolean {
        val controller = pipController ?: run {
            Log.w(tag, "PiP controller not configured")
            return false
        }

        if (!controller.pictureInPicturePossible) {
            Log.w(tag, "PiP not possible right now")
            return false
        }

        controller.startPictureInPicture()
        _pipState.value = _pipState.value.copy(
            aspectRatioWidth = aspectRatioWidth,
            aspectRatioHeight = aspectRatioHeight
        )
        return true
    }

    override fun exitPipMode() {
        pipController?.stopPictureInPicture()
    }

    override fun updatePipParams(aspectRatioWidth: Int, aspectRatioHeight: Int) {
        _pipState.value = _pipState.value.copy(
            aspectRatioWidth = aspectRatioWidth,
            aspectRatioHeight = aspectRatioHeight
        )
    }

    override fun setAutoEnterEnabled(enabled: Boolean) {
        // iOS handles this automatically when the app goes to background
        // during an active video call with AVPictureInPictureVideoCallViewController
    }

    override fun isInPipMode(): Boolean = _pipState.value.isInPipMode

    override fun setSourceRectHint(left: Int, top: Int, right: Int, bottom: Int) {
        // iOS handles source rect automatically from the player layer
    }

    override fun configurePipActions(actions: List<PipAction>) {
        // iOS PiP doesn't support custom actions in the same way as Android
        // The system provides standard playback controls
    }

    override fun attachCallState(callState: net.jami.ui.viewmodel.CallState) {
        Log.d(tag, "Call state attached: ${callState.callMode}")
    }

    override fun detachCallState() {
        Log.d(tag, "Call state detached")
    }

    fun cleanup() {
        pipController?.delegate = null
        pipController = null
    }
}

/**
 * Create iOS PiP manager.
 */
actual fun createPictureInPictureManager(): PictureInPictureManager {
    return IOSPictureInPictureManager()
}
