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

object AppSettingsContract {

    @Immutable
    data class State(
        val isDarkTheme: Boolean = false,
        val isTypingIndicators: Boolean = true,
        val isLinkPreview: Boolean = true,
        val isScreenshotBlocking: Boolean = false,
        val isStartOnBoot: Boolean = false,
        val isRunInBackground: Boolean = false,
        val isPushNotifications: Boolean = true,
    )

    sealed interface Action {
        data object ToggleDarkTheme : Action
        data object ToggleTypingIndicators : Action
        data object ToggleLinkPreview : Action
        data object ToggleScreenshotBlocking : Action
        data object ToggleStartOnBoot : Action
        data object ToggleRunInBackground : Action
        data object TogglePushNotifications : Action
    }
}
