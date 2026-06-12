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
package net.jami.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton that carries incoming share payload from the platform layer (e.g. Android
 * ACTION_SEND) into the Compose navigation graph.
 *
 * Lifecycle:
 *  1. Platform code populates [pendingText] / [pendingFilePaths] / [mimeType].
 *  2. Platform code calls [signalSharePicker] to trigger navigation to SharePickerScreen.
 *  3. SharePickerScreen (or JamiNavigation) consumes [consumeNavSignal] to reset the flag.
 *  4. ChatScreen calls [consumeText] / [consumeFiles] once the conversation is ready.
 */
object ShareState {
    private val _navigateToSharePicker = MutableStateFlow(false)
    val navigateToSharePicker: StateFlow<Boolean> = _navigateToSharePicker.asStateFlow()

    var pendingText: String? = null
    var pendingFilePaths: List<String> = emptyList()
    var mimeType: String? = null

    fun hasPending(): Boolean = pendingText != null || pendingFilePaths.isNotEmpty()

    /** Called by the platform layer after populating the pending payload. */
    fun signalSharePicker() {
        _navigateToSharePicker.value = true
    }

    /** Consumed by JamiNavigation once it has navigated to SharePicker. */
    fun consumeNavSignal() {
        _navigateToSharePicker.value = false
    }

    /** Returns and clears the pending text. */
    fun consumeText(): String? {
        val t = pendingText
        pendingText = null
        return t
    }

    /** Returns and clears the pending file paths. */
    fun consumeFiles(): List<String> {
        val f = pendingFilePaths
        pendingFilePaths = emptyList()
        return f
    }
}
