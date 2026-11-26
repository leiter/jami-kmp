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

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.ui.viewmodel.CallMode
import net.jami.utils.Log
import platform.AVFoundation.AVSampleBufferDisplayLayer
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerContentSource
import platform.AVKit.create
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.AVKit.AVPictureInPictureSampleBufferPlaybackDelegateProtocol
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRange
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.CMVideoDimensions
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.setValue
import platform.darwin.NSObject

/**
 * Picture-in-picture for a video call (I6), as jami-client-ios's PiPController does it.
 *
 * The controller is built around the remote video's own [AVSampleBufferDisplayLayer] — the one
 * the bridge already enqueues decoded frames on ([bindDisplayLayer], from
 * HardwareService.addVideoSurface) — through a sample-buffer content source with a playback
 * delegate that reports a live, never-paused stream. No second layer and no frame copy.
 *
 * It starts by itself when the app leaves the foreground during a video call
 * (`canStartPictureInPictureAutomaticallyFromInline`, only while [attachCallState] reports an
 * ongoing call with remote video) or on [enterPipMode]. Needs the "audio" background mode and
 * the PlayAndRecord session a call already has.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSPictureInPictureManager : PictureInPictureManager {

    private val tag = "IOSPipManager"

    private var pipController: AVPictureInPictureController? = null
    private var boundLayer: AVSampleBufferDisplayLayer? = null
    private var autoStart = false

    private val _pipState = MutableStateFlow(
        PipState(
            isInPipMode = false,
            isSupported = AVPictureInPictureController.isPictureInPictureSupported()
        )
    )
    override val pipState: StateFlow<PipState> = _pipState.asStateFlow()

    private val _pipActions = MutableSharedFlow<PipAction>(extraBufferCapacity = 10)
    override val pipActions: Flow<PipAction> = _pipActions.asSharedFlow()

    private val pipDelegate = object : NSObject(), AVPictureInPictureControllerDelegateProtocol {
        override fun pictureInPictureControllerDidStartPictureInPicture(
            pictureInPictureController: AVPictureInPictureController
        ) {
            _pipState.value = _pipState.value.copy(isInPipMode = true)
            Log.d(tag, "Did start PiP")
        }

        override fun pictureInPictureControllerDidStopPictureInPicture(
            pictureInPictureController: AVPictureInPictureController
        ) {
            _pipState.value = _pipState.value.copy(isInPipMode = false)
            Log.d(tag, "Did stop PiP")
            // The layer went away while PiP showed it: the controller goes with it.
            if (boundLayer == null) release()
        }

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            failedToStartPictureInPictureWithError: NSError
        ) {
            Log.e(tag, "Failed to start PiP: ${failedToStartPictureInPictureWithError.localizedDescription}")
        }

        /** Tapping PiP's "back to app" button: the app comes forward by itself, nothing to restore. */
        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            restoreUserInterfaceForPictureInPictureStopWithCompletionHandler: (Boolean) -> Unit
        ) {
            restoreUserInterfaceForPictureInPictureStopWithCompletionHandler(true)
        }
    }

    /** A call is live: no timeline, nothing to pause, nothing to skip. */
    private val playbackDelegate = object : NSObject(), AVPictureInPictureSampleBufferPlaybackDelegateProtocol {
        override fun pictureInPictureController(pictureInPictureController: AVPictureInPictureController, setPlaying: Boolean) {}

        override fun pictureInPictureControllerTimeRangeForPlayback(
            pictureInPictureController: AVPictureInPictureController
        ): CValue<CMTimeRange> = CMTimeRangeMake(kCMTimeZero.readValue(), CMTimeMake(value = 3600L * 24, timescale = 1))

        override fun pictureInPictureControllerIsPlaybackPaused(pictureInPictureController: AVPictureInPictureController): Boolean = false

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            didTransitionToRenderSize: CValue<CMVideoDimensions>
        ) {}

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            skipByInterval: CValue<CMTime>,
            completionHandler: () -> Unit
        ) {
            completionHandler()
        }
    }

    /**
     * The remote video's layer, or null when it goes away. A new layer replaces the controller,
     * since a content source cannot be re-pointed.
     */
    fun bindDisplayLayer(layer: AVSampleBufferDisplayLayer?) {
        if (layer === boundLayer) return
        boundLayer = layer
        val controller = pipController
        if (controller != null && controller.pictureInPictureActive) {
            // Stopping comes back through didStop, which releases it once the layer is gone.
            controller.stopPictureInPicture()
            if (layer == null) return
        }
        release()
        if (layer == null || !isSupported()) return
        val source = AVPictureInPictureControllerContentSource.create(
            sampleBufferDisplayLayer = layer,
            playbackDelegate = playbackDelegate,
        )
        pipController = AVPictureInPictureController(contentSource = source).apply {
            delegate = pipDelegate
            requiresLinearPlayback = true
            canStartPictureInPictureAutomaticallyFromInline = autoStart
            // Hides the playback controls a live call has no use for, as the native client does.
            runCatching { setValue(NSNumber(int = 1), forKey = "controlsStyle") }
        }
        Log.d(tag, "PiP bound to the remote video")
    }

    private fun release() {
        pipController?.delegate = null
        pipController = null
    }

    override fun isSupported(): Boolean = AVPictureInPictureController.isPictureInPictureSupported()

    override fun enterPipMode(aspectRatioWidth: Int, aspectRatioHeight: Int): Boolean {
        val controller = pipController ?: run {
            Log.w(tag, "No remote video to show in PiP")
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
        // The window follows the frames' own size.
        _pipState.value = _pipState.value.copy(
            aspectRatioWidth = aspectRatioWidth,
            aspectRatioHeight = aspectRatioHeight
        )
    }

    override fun setAutoEnterEnabled(enabled: Boolean) {
        autoStart = enabled
        pipController?.canStartPictureInPictureAutomaticallyFromInline = enabled
    }

    override fun isInPipMode(): Boolean = _pipState.value.isInPipMode

    override fun setSourceRectHint(left: Int, top: Int, right: Int, bottom: Int) {
        // The animation starts from the layer's own frame.
    }

    override fun configurePipActions(actions: List<PipAction>) {
        // The system's PiP window has no custom actions on iOS.
    }

    /** Leaving the app opens PiP only during an ongoing call that shows the peer's video. */
    override fun attachCallState(callState: net.jami.ui.viewmodel.CallState) {
        setAutoEnterEnabled(callState.callMode is CallMode.OnGoing && callState.hasRemoteVideo)
    }

    override fun detachCallState() {
        setAutoEnterEnabled(false)
    }

    fun cleanup() {
        bindDisplayLayer(null)
        release()
    }
}

/**
 * Create iOS PiP manager.
 */
actual fun createPictureInPictureManager(): PictureInPictureManager {
    return IOSPictureInPictureManager()
}
