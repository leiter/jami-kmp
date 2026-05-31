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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import net.jami.services.expect.HardwareService
import net.jami.services.IOSCameraService
import net.jami.utils.Log
import org.koin.compose.koinInject
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVSampleBufferDisplayLayer
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CALayer
import platform.QuartzCore.kCALayerHeightSizable
import platform.QuartzCore.kCALayerWidthSizable
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

/**
 * iOS implementation of VideoSurface using AVSampleBufferDisplayLayer.
 *
 * This composable creates a UIView with an AVSampleBufferDisplayLayer sublayer
 * for rendering incoming video frames from the daemon.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(
    sinkId: String,
    modifier: Modifier,
    onSurfaceReady: () -> Unit,
    onSizeChanged: (width: Int, height: Int) -> Unit
) {
    val tag = "VideoSurface.iOS"
    val hardwareService = koinInject<HardwareService>()

    var displayLayer by remember { mutableStateOf<AVSampleBufferDisplayLayer?>(null) }

    DisposableEffect(sinkId) {
        Log.d(tag, "VideoSurface attached for sink: $sinkId")
        onDispose {
            Log.d(tag, "VideoSurface detached for sink: $sinkId")
            displayLayer?.removeFromSuperlayer()
            hardwareService.removeVideoSurface(sinkId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        UIKitView(
            factory = {
                val containerView = VideoContainerView().apply {
                    backgroundColor = UIColor.blackColor
                    autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
                }

                // Create AVSampleBufferDisplayLayer for video rendering
                val layer = AVSampleBufferDisplayLayer().apply {
                    videoGravity = AVLayerVideoGravityResizeAspect
                    backgroundColor = UIColor.blackColor.CGColor
                    autoresizingMask = kCALayerWidthSizable or kCALayerHeightSizable
                }

                containerView.layer.addSublayer(layer)
                displayLayer = layer

                // Register with hardware service
                hardwareService.addVideoSurface(sinkId, containerView)
                onSurfaceReady()

                containerView
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Update layer frame when view size changes
                displayLayer?.let { layer ->
                    view.bounds.useContents {
                        layer.frame = CGRectMake(0.0, 0.0, size.width, size.height)
                        if (size.width > 0 && size.height > 0) {
                            onSizeChanged(size.width.toInt(), size.height.toInt())
                        }
                    }
                }
            },
            properties = UIKitInteropProperties(
                isInteractive = false,
                isNativeAccessibilityEnabled = false
            )
        )
    }
}

/**
 * iOS implementation of CameraPreview using AVCaptureVideoPreviewLayer.
 *
 * This composable creates a UIView with an AVCaptureVideoPreviewLayer
 * showing the live camera feed.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    isFrontCamera: Boolean,
    onSurfaceReady: () -> Unit,
    onError: (String) -> Unit
) {
    val tag = "CameraPreview.iOS"
    val cameraService = koinInject<IOSCameraService>()

    var previewLayer by remember { mutableStateOf<AVCaptureVideoPreviewLayer?>(null) }
    var hasStarted by remember { mutableStateOf(false) }

    // Open camera and start capture
    LaunchedEffect(isFrontCamera) {
        try {
            if (!cameraService.hasCameraPermission()) {
                val granted = cameraService.requestCameraPermission()
                if (!granted) {
                    onError("Camera permission denied")
                    return@LaunchedEffect
                }
            }

            val cameraId = if (isFrontCamera) {
                cameraService.getVideoDevices().devices.find { it.facing == net.jami.model.CameraFacing.FRONT }?.id
            } else {
                cameraService.getVideoDevices().devices.find { it.facing == net.jami.model.CameraFacing.BACK }?.id
            }

            val opened = cameraService.openCamera(cameraId)
            if (opened) {
                cameraService.startCapture()
                hasStarted = true
                Log.d(tag, "Camera preview started")
            } else {
                onError("Failed to open camera")
            }
        } catch (e: Exception) {
            Log.e(tag, "Camera error: ${e.message}")
            onError(e.message ?: "Unknown camera error")
        }
    }

    // Handle camera switch
    LaunchedEffect(isFrontCamera, hasStarted) {
        if (hasStarted && cameraService.isFrontCamera != isFrontCamera) {
            cameraService.switchCamera()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(tag, "CameraPreview disposed")
            cameraService.stopCapture()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        UIKitView(
            factory = {
                val containerView = PreviewContainerView().apply {
                    backgroundColor = UIColor.blackColor
                    autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
                }

                // Get or create preview layer from camera service
                cameraService.createPreviewLayer()?.let { layer ->
                    layer.videoGravity = AVLayerVideoGravityResizeAspectFill
                    layer.autoresizingMask = kCALayerWidthSizable or kCALayerHeightSizable
                    containerView.layer.addSublayer(layer)
                    previewLayer = layer
                }

                onSurfaceReady()
                containerView
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Update layer frame when view size changes
                previewLayer?.let { layer ->
                    view.bounds.useContents {
                        layer.frame = CGRectMake(0.0, 0.0, size.width, size.height)
                    }
                }

                // Update preview layer if camera service has a new one
                if (previewLayer == null) {
                    cameraService.getPreviewLayer()?.let { layer ->
                        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
                        layer.autoresizingMask = kCALayerWidthSizable or kCALayerHeightSizable
                        view.layer.addSublayer(layer)
                        previewLayer = layer
                        view.bounds.useContents {
                            layer.frame = CGRectMake(0.0, 0.0, size.width, size.height)
                        }
                    }
                }
            },
            properties = UIKitInteropProperties(
                isInteractive = false,
                isNativeAccessibilityEnabled = false
            )
        )
    }
}

/**
 * Custom UIView for video container with proper layer management.
 */
@OptIn(ExperimentalForeignApi::class)
private class VideoContainerView : UIView(CGRectMake(0.0, 0.0, 100.0, 100.0)) {
    override fun layoutSubviews() {
        super.layoutSubviews()
        bounds.useContents {
            layer.sublayers?.forEach { sublayer ->
                (sublayer as? CALayer)?.frame = CGRectMake(0.0, 0.0, size.width, size.height)
            }
        }
    }
}

/**
 * Custom UIView for camera preview with proper layer management.
 */
@OptIn(ExperimentalForeignApi::class)
private class PreviewContainerView : UIView(CGRectMake(0.0, 0.0, 100.0, 100.0)) {
    override fun layoutSubviews() {
        super.layoutSubviews()
        bounds.useContents {
            layer.sublayers?.forEach { sublayer ->
                (sublayer as? CALayer)?.frame = CGRectMake(0.0, 0.0, size.width, size.height)
            }
        }
    }
}
