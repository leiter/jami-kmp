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
package net.jami.ui.platform

import androidx.compose.runtime.Composable

/**
 * Applies or removes the platform's screenshot/screen-recording block on the current window.
 *
 * Android: adds/clears FLAG_SECURE on the Activity window.
 * Other platforms: no-op.
 *
 * Must be called from a composable that is inside an Activity-backed window (not a dialog
 * or popup window — those inherit the flag from the parent automatically on Android).
 */
@Composable
expect fun WindowSecureEffect(enabled: Boolean)
