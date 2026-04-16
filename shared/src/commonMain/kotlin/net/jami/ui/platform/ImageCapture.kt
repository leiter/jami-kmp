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
 * Composable that provides a platform-specific camera image capture.
 *
 * When triggered, this will:
 * 1. Open the device camera
 * 2. Allow the user to take a photo
 * 3. Return the path to the captured image file
 *
 * @param show Whether the camera should be opened.
 * @param onImageCaptured Called with the captured image's absolute path, or null if cancelled.
 */
@Composable
expect fun ImageCaptureEffect(
    show: Boolean,
    onImageCaptured: (path: String?) -> Unit,
)
