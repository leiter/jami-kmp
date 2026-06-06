package net.jami.ui.composables

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import net.jami.services.expect.HardwareService
import net.jami.utils.Log
import org.koin.compose.koinInject

@Composable
actual fun VideoRenderer(
    modifier: Modifier,
    callId: String,
    isLocalVideo: Boolean
) {
    val hardwareService: HardwareService = koinInject()

    DisposableEffect(callId, isLocalVideo) {
        onDispose {
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
            // For remote video we wrap the SurfaceTexture in a Surface so that
            // HardwareService.addVideoSurface can hand it to the JNI native window.
            // The Surface is owned here and released when the texture is destroyed.
            var remoteSurface: android.view.Surface? = null
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        Log.d("VideoRenderer", "Surface available: ${if (isLocalVideo) "local" else "remote"} id=$callId")
                        if (isLocalVideo) {
                            hardwareService.addPreviewVideoSurface(this@apply, null)
                        } else {
                            val surface = android.view.Surface(surfaceTexture)
                            remoteSurface = surface
                            hardwareService.addVideoSurface(callId, surface)
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        if (isLocalVideo) {
                            hardwareService.removePreviewVideoSurface()
                        } else {
                            hardwareService.removeVideoSurface(callId)
                            remoteSurface?.release()
                            remoteSurface = null
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                }
            }
        },
        update = {}
    )
}
