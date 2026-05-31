package net.jami.ui.composables

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import net.jami.services.CallService
import net.jami.services.actual.HardwareService
import net.jami.utils.Log
import org.koin.androidx.compose.inject

@Composable
actual fun VideoRenderer(
    modifier: Modifier,
    callId: String,
    isLocalVideo: Boolean
) {
    val callService: CallService by inject()
    val hardwareService: HardwareService by inject()

    DisposableEffect(callId, isLocalVideo) {
        onDispose {
            // Cleanup when the composable is disposed
            if (isLocalVideo) {
                hardwareService.removePreviewVideoSurface()
            } else {
                hardwareService.removeVideoSurface(callId)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        Log.d("VideoRenderer", "Surface texture available: ${if (isLocalVideo) "local" else "remote"} video")
                        if (isLocalVideo) {
                            // For local preview, pass the TextureView itself
                            hardwareService.addPreviewVideoSurface(this@apply, null)
                        } else {
                            // For remote video, pass the TextureView itself
                            hardwareService.addVideoSurface(callId, this@apply)
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        // The daemon should handle surface size changes automatically
                        Log.d("VideoRenderer", "Surface texture size changed: $width x $height")
                    }

                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        Log.d("VideoRenderer", "Surface texture destroyed")
                        if (isLocalVideo) {
                            hardwareService.removePreviewVideoSurface()
                        } else {
                            hardwareService.removeVideoSurface(callId)
                        }
                        return true
                    }

                    override fun onFrameAvailable(surface: android.graphics.SurfaceTexture) {
                        // Frame rendered to surface, no action needed
                    }
                }
            }
        },
        update = { /* The SurfaceTextureListener handles all updates */ }
    )
}
