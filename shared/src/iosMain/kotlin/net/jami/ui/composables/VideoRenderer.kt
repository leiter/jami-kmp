package net.jami.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.services.expect.HardwareService
import org.koin.compose.koinInject
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
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

    UIKitView(
        modifier = modifier,
        factory = {
            UIView().apply {
                if (isLocalVideo) {
                    hardwareService.addPreviewVideoSurface(this, null)
                } else {
                    hardwareService.addVideoSurface(callId, this)
                }
            }
        },
        update = { /* The view is managed via factory and onDispose */ }
    )
}
