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
 * Runtime permissions that the app may need to request.
 */
enum class AppPermission {
    Camera,
    Microphone,
    Contacts,
    Notifications,
}

/**
 * Composable side-effect that requests a runtime permission when [request] flips to true.
 *
 * Usage:
 * ```kotlin
 * var requestCamera by remember { mutableStateOf(false) }
 * PermissionRequesterEffect(AppPermission.Camera, requestCamera) { granted ->
 *     requestCamera = false
 *     if (granted) startCamera()
 * }
 * Button(onClick = { requestCamera = true }) { Text("Open camera") }
 * ```
 *
 * @param permission The permission to request.
 * @param request Set to true to trigger the permission dialog. The caller should reset it to
 *   false after the result arrives (in [onResult]).
 * @param onResult Called with true if the permission was granted, false if denied.
 */
@Composable
expect fun PermissionRequesterEffect(
    permission: AppPermission,
    request: Boolean,
    onResult: (granted: Boolean) -> Unit,
)
