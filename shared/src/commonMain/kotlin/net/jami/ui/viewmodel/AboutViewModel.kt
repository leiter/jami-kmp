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
package net.jami.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State for the about screen.
 */
data class AboutState(
    val version: String = VERSION,
    val copyright: String = COPYRIGHT,
    val description: String = DESCRIPTION
) {
    companion object {
        const val VERSION = "1.0.0"
        const val COPYRIGHT = "Copyright (C) 2004-2025 Savoir-faire Linux Inc."
        const val DESCRIPTION =
            "Jami is free software for universal communication that respects the " +
            "freedom and privacy of its users. Jami is a GNU project."
    }
}

/**
 * ViewModel for the about screen.
 *
 * Provides static application information such as version, copyright,
 * and description. No service dependencies are required.
 */
class AboutViewModel(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _state = MutableStateFlow(AboutState())
    val state: StateFlow<AboutState> = _state.asStateFlow()

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
