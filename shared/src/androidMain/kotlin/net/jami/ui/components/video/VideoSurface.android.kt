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

import android.graphics.SurfaceTexture
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.collectLatest
import net.jami.services.HardwareService
import net.jami.utils.Log
import org.koin.compose.koinInject

private const val TAG = "VideoSurface"

/**
 * Android implementation of VideoSurface using SurfaceView.
 *
 * Connects to HardwareService to receive decoded video frames via native rendering.
 */
@Composable
actual fun VideoSurface(
    sinkId: String,
    modifier: Modifier,
    onSurfaceReady: () -> Unit,
    onSizeChanged: (width: Int, height: Int) -> Unit
) {
    val hardwareService: HardwareService = koinInject()
    var aspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var surfaceReady by remember { mutableStateOf(false) }

    LaunchedEffect(sinkId, surfaceReady) {
        if (surfaceReady) {
            hardwareService.videoEvents.collectLatest { event ->
                if (event.sinkId == sinkId && event.width > 0 && event.height > 0) {
                    aspectRatio = event.width.toFloat() / event.height.toFloat()
                    onSizeChanged(event.width, event.height)
                }
            }
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
                .fillMaxSize(),
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            Log.d(TAG, "surfaceCreated for sink: $sinkId")
                            hardwareService.addVideoSurface(sinkId, holder)
                            surfaceReady = true
                            onSurfaceReady()
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            Log.d(TAG, "surfaceChanged for sink: $sinkId ${width}x${height}")
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            Log.d(TAG, "surfaceDestroyed for sink: $sinkId")
                            hardwareService.removeVideoSurface(sinkId)
                            surfaceReady = false
                        }
                    })
                }
            },
            onRelease = {
                hardwareService.removeVideoSurface(sinkId)
            }
        )
    }

    DisposableEffect(sinkId) {
        onDispose {
            hardwareService.removeVideoSurface(sinkId)
        }
    }
}

/**
 * Android implementation of CameraPreview using TextureView.
 *
 * Connects to CameraService via HardwareService for local camera preview.
 */
@Composable
actual fun CameraPreview(
    modifier: Modifier,
    isFrontCamera: Boolean,
    onSurfaceReady: () -> Unit,
    onError: (String) -> Unit
) {
    val hardwareService: HardwareService = koinInject()
    var aspectRatio by remember { mutableFloatStateOf(4f / 3f) }
    var textureView by remember { mutableStateOf<TextureView?>(null) }

    LaunchedEffect(textureView, isFrontCamera) {
        textureView?.let { view ->
            hardwareService.cameraEvents.collectLatest { event ->
                if (event.width > 0 && event.height > 0) {
                    aspectRatio = event.width.toFloat() / event.height.toFloat()
                }
            }
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
                .fillMaxSize(),
            factory = { context ->
                AutoFitTextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            Log.d(TAG, "CameraPreview surface available ${width}x${height}")
                            textureView = this@apply
                            hardwareService.addPreviewVideoSurface(this@apply, null)
                            hardwareService.startCameraPreview(false)
                            onSurfaceReady()
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            Log.d(TAG, "CameraPreview surface size changed ${width}x${height}")
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            Log.d(TAG, "CameraPreview surface destroyed")
                            hardwareService.removePreviewVideoSurface()
                            hardwareService.cameraCleanup()
                            textureView = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            // Frame updated
                        }
                    }
                }
            },
            onRelease = {
                hardwareService.removePreviewVideoSurface()
                hardwareService.cameraCleanup()
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            hardwareService.removePreviewVideoSurface()
            hardwareService.cameraCleanup()
        }
    }
}

/**
 * TextureView that maintains aspect ratio for camera preview.
 */
private class AutoFitTextureView(context: android.content.Context) : TextureView(context) {
    private var ratioWidth = 0
    private var ratioHeight = 0

    fun setAspectRatio(width: Int, height: Int) {
        require(width >= 0 && height >= 0) { "Size cannot be negative" }
        ratioWidth = width
        ratioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        if (ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, height)
        } else {
            if (width < height * ratioWidth / ratioHeight) {
                setMeasuredDimension(width, width * ratioHeight / ratioWidth)
            } else {
                setMeasuredDimension(height * ratioWidth / ratioHeight, height)
            }
        }
    }
}
