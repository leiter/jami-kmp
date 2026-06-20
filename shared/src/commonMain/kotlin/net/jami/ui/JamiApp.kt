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
package net.jami.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import net.jami.di.getViewModel
import net.jami.ui.navigation.JamiNavigation
import net.jami.ui.platform.WindowSecureEffect
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AppSettingsViewModel
import net.jami.utils.Log

/**
 * Root composable for the Jami application.
 *
 * Wraps the navigation graph in the Jami theme. This is the single
 * entry point that platform apps (Android, Desktop, iOS, Web) should
 * call from their respective host composables.
 */
@Composable
fun JamiApp() {
    Log.i("JAMI_KOIN_APP", "JamiApp composable entered — about to resolve AppSettingsViewModel")
    val appSettingsViewModel = getViewModel<AppSettingsViewModel>()
    val appSettingsState by appSettingsViewModel.state.collectAsState()

    WindowSecureEffect(enabled = appSettingsState.isScreenshotBlocking)

    JamiTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            JamiNavigation()
        }
    }
}
