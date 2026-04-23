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
package net.jami.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific video rendering surface for displaying remote video streams.
 *
 * On Android: Uses SurfaceView with SurfaceHolder connected to HardwareService
 * On iOS: Uses UIView with CALayer for video rendering
 * On Desktop/Web: Placeholder implementation
 *
 * @param sinkId The video sink identifier (call ID or participant ID)
 * @param modifier Modifier for the composable
 * @param onSurfaceReady Callback when the surface is ready to receive video
 * @param onSizeChanged Callback when the video dimensions change
 */
@Composable
expect fun VideoSurface(
    sinkId: String,
    modifier: Modifier = Modifier,
    onSurfaceReady: () -> Unit = {},
    onSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> }
)

/**
 * Platform-specific camera preview surface for displaying local camera feed.
 *
 * On Android: Uses TextureView connected to CameraService
 * On iOS: Uses AVCaptureVideoPreviewLayer
 * On Desktop/Web: Placeholder implementation
 *
 * @param modifier Modifier for the composable
 * @param isFrontCamera Whether to use front-facing camera
 * @param onSurfaceReady Callback when the preview surface is ready
 * @param onError Callback when camera access fails
 */
@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = true,
    onSurfaceReady: () -> Unit = {},
    onError: (String) -> Unit = {}
)
