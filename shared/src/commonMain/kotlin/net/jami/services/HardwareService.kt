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

import net.jami.model.Call
import net.jami.model.Conference

/**
 * Service for managing hardware (camera, audio, video preview).
 *
 * This is a stub interface for the ConversationFacade port.
 * Platform-specific implementations will be added via expect/actual.
 *
 * Ported from: jami-client-android libjamiclient
 */
interface HardwareService {

    /**
     * Update audio state for a call.
     *
     * @param conference The conference containing the call
     * @param call The call being updated
     * @param incomingCall Whether this is an incoming call
     * @param hasVideo Whether the call has video
     */
    fun updateAudioState(
        conference: Conference?,
        call: Call,
        incomingCall: Boolean,
        hasVideo: Boolean
    )

    /**
     * Close audio state after a call ends.
     */
    fun closeAudioState()

    /**
     * Set camera preview settings.
     */
    fun setPreviewSettings()

    /**
     * Start video capture.
     */
    fun startCapture()

    /**
     * Stop video capture.
     */
    fun stopCapture()

    /**
     * Check if audio is available.
     */
    fun isAudioAvailable(): Boolean

    /**
     * Check if video is available.
     */
    fun isVideoAvailable(): Boolean

    /**
     * Toggle speaker output.
     */
    fun toggleSpeaker(enabled: Boolean)

    /**
     * Check if speaker is currently on.
     */
    fun isSpeakerOn(): Boolean
}

/**
 * Stub implementation of HardwareService for testing.
 */
class StubHardwareService : HardwareService {
    private var speakerOn = false

    override fun updateAudioState(
        conference: Conference?,
        call: Call,
        incomingCall: Boolean,
        hasVideo: Boolean
    ) {}

    override fun closeAudioState() {}
    override fun setPreviewSettings() {}
    override fun startCapture() {}
    override fun stopCapture() {}
    override fun isAudioAvailable(): Boolean = true
    override fun isVideoAvailable(): Boolean = true
    override fun toggleSpeaker(enabled: Boolean) { speakerOn = enabled }
    override fun isSpeakerOn(): Boolean = speakerOn
}
