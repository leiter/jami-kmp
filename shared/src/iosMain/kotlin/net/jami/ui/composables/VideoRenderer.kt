package net.jami.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.services.CallService
import net.jami.services.DaemonBridge
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoRenderer(
    modifier: Modifier,
    callId: String,
    isLocalVideo: Boolean
) {
    val daemonBridge: DaemonBridge by inject()
    val callService: CallService by inject()

    UIKitView(
        modifier = modifier,
        factory = {
            // This is a placeholder UIView.
            // In a real implementation, this UIView would be backed by a video layer
            // (e.g., AVSampleBufferDisplayLayer) that the Jami daemon can render to.
            // The Jami daemon would provide a handle or a function to set this layer.
            val videoView = UIView()
            Log.d("VideoRenderer", "Creating UIView for video rendering for call $callId, local: $isLocalVideo")
            videoView
        },
        update = { videoView ->
            // Update logic if needed, e.g., re-attaching the video stream
            // if callId changes or other properties change.
            if (isLocalVideo) {
                // Example: daemonBridge.setLocalVideoDisplay(callId, videoView)
            } else {
                // Example: daemonBridge.setRemoteVideoDisplay(callId, videoView)
            }
        },
        onRelease = { videoView ->
            // Clean up resources if necessary when the view is removed from composition.
            Log.d("VideoRenderer", "Releasing UIView for video rendering for call $callId, local: $isLocalVideo")
            // Example: daemonBridge.clearVideoDisplay(callId, videoView)
        }
    )
}
