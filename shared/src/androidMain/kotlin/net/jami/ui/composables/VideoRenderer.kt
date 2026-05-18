package net.jami.ui.composables

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import net.jami.services.CallService
import net.jami.services.DaemonBridge
import net.jami.utils.Log
import org.koin.androidx.compose.inject

@Composable
actual fun VideoRenderer(
    modifier: Modifier,
    callId: String,
    isLocalVideo: Boolean
) {
    val daemonBridge: DaemonBridge by inject()
    val callService: CallService by inject()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                // SurfaceHolder callback might be needed for more complex scenarios,
                // but for simple video rendering, setting the surface directly might work.
            }
        },
        update = { surfaceView ->
            if (isLocalVideo) {
                // For local video, we might need to get the local camera feed directly
                // from the daemon and render it to this surface.
                // This is a placeholder for actual daemon interaction.
                Log.d("VideoRenderer", "Rendering local video for call $callId to SurfaceView")
                // daemonBridge.setLocalVideoDisplay(callId, surfaceView.holder.surface) // Example
            } else {
                // For remote video, instruct the daemon to render the remote participant's video
                // to this surface.
                Log.d("VideoRenderer", "Rendering remote video for call $callId to SurfaceView")
                // daemonBridge.setRemoteVideoDisplay(callId, surfaceView.holder.surface) // Example
            }

            // Alternatively, the CallService might expose a function to attach the view
            // callService.attachVideoView(callId, surfaceView, isLocalVideo)
        }
    )
}
