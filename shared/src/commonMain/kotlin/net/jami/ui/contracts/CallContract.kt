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
package net.jami.ui.contracts

import androidx.compose.runtime.Immutable

object CallContract {

    @Immutable
    data class PeerState(
        val peerName: String = "",
        val peerUri: String = "",
        val callStatus: String = "",
        val isIncoming: Boolean = false,
    )

    @Immutable
    data class ControlsState(
        val isAudioMuted: Boolean = false,
        val isVideoMuted: Boolean = false,
        val isSpeakerOn: Boolean = false,
    )

    @Immutable
    data class TimerState(
        val duration: Long = 0L,
    )

    sealed interface Action {
        data object ToggleMute : Action
        data object ToggleVideo : Action
        data object ToggleSpeaker : Action
        data object AcceptCall : Action
        data object EndCall : Action
    }
}
