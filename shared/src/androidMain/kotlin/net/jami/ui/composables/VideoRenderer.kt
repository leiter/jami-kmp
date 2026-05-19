package net.jami.ui.composables

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import net.jami.services.CallService
import net.jami.services.actual.HardwareService
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
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        if (isLocalVideo) {
                            hardwareService.addPreviewVideoSurface(holder, null)
                        } else {
                            hardwareService.addVideoSurface(callId, holder)
                        }
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        // The daemon should handle surface size changes automatically
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        if (isLocalVideo) {
                            hardwareService.removePreviewVideoSurface()
                        } else {
                            hardwareService.removeVideoSurface(callId)
                        }
                    }
                })
            }
        },
        update = { /* The SurfaceHolder.Callback handles all updates */ }
    )
}
