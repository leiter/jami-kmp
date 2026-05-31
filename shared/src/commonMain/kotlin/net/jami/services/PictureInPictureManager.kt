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
import kotlinx.coroutines.flow.StateFlow

/**
 * PiP action types for remote actions.
 */
enum class PipAction {
    TOGGLE_MUTE,
    TOGGLE_VIDEO,
    HANG_UP,
    SWITCH_CAMERA
}

/**
 * PiP state for tracking picture-in-picture mode.
 */
data class PipState(
    val isInPipMode: Boolean = false,
    val isSupported: Boolean = false,
    val aspectRatioWidth: Int = 16,
    val aspectRatioHeight: Int = 9
)

/**
 * Platform-agnostic interface for Picture-in-Picture support.
 *
 * Android uses PictureInPictureParams with Activity.
 * iOS uses AVPictureInPictureController.
 */
interface PictureInPictureManager {

    /**
     * Current PiP state flow.
     */
    val pipState: StateFlow<PipState>

    /**
     * Flow of PiP actions (from remote controls).
     */
    val pipActions: Flow<PipAction>

    /**
     * Check if PiP mode is supported on this device.
     */
    fun isSupported(): Boolean

    /**
     * Enter PiP mode with the current video.
     *
     * @param aspectRatioWidth Width component of aspect ratio
     * @param aspectRatioHeight Height component of aspect ratio
     * @return true if PiP was entered successfully
     */
    fun enterPipMode(aspectRatioWidth: Int = 16, aspectRatioHeight: Int = 9): Boolean

    /**
     * Exit PiP mode and return to full screen.
     */
    fun exitPipMode()

    /**
     * Update PiP parameters (e.g., aspect ratio changed).
     */
    fun updatePipParams(aspectRatioWidth: Int, aspectRatioHeight: Int)

    /**
     * Set whether auto-enter PiP is enabled when user navigates away.
     */
    fun setAutoEnterEnabled(enabled: Boolean)

    /**
     * Check if currently in PiP mode.
     */
    fun isInPipMode(): Boolean

    /**
     * Set the source rectangle hint for smooth PiP animation.
     * The rectangle should be the bounds of the video view in screen coordinates.
     */
    fun setSourceRectHint(left: Int, top: Int, right: Int, bottom: Int)

    /**
     * Configure PiP actions (remote control buttons).
     *
     * @param actions List of actions to show (max 3 on most platforms)
     */
    fun configurePipActions(actions: List<PipAction>)

    /**
     * Attach call state to PiP manager so it can make informed decisions about PiP entry.
     *
     * @param callState The current call state
     */
    fun attachCallState(callState: net.jami.ui.viewmodel.CallState)

    /**
     * Detach call state when the call screen is disposed.
     */
    fun detachCallState()
}

/**
 * Expect declaration for platform-specific PiP manager.
 */
expect fun createPictureInPictureManager(): PictureInPictureManager
