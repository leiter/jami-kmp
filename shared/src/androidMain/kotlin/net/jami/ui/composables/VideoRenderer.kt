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
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        Log.d("VideoRenderer", "Surface available: ${if (isLocalVideo) "local" else "remote"}")
                        if (isLocalVideo) {
                            hardwareService.addPreviewVideoSurface(this@apply, null)
                        } else {
                            hardwareService.addVideoSurface(callId, this@apply)
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        if (isLocalVideo) hardwareService.removePreviewVideoSurface()
                        else hardwareService.removeVideoSurface(callId)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                }
            }
        },
        update = {}
    )
}
