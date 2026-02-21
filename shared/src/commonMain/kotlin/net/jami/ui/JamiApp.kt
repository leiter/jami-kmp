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

import androidx.compose.runtime.Composable
import net.jami.ui.navigation.JamiNavigation
import net.jami.ui.theme.JamiTheme

/**
 * Root composable for the Jami application.
 *
 * Wraps the navigation graph in the Jami theme. This is the single
 * entry point that platform apps (Android, Desktop, iOS, Web) should
 * call from their respective host composables.
 */
@Composable
fun JamiApp() {
    JamiTheme {
        JamiNavigation()
    }
}
