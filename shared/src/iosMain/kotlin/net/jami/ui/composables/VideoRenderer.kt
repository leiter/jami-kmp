package net.jami.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.services.expect.HardwareService
import org.koin.compose.koinInject
import platform.UIKit.UIView

/**
 * A remote sink or the local camera preview, as a UIKit view.
 *
 * Not interactive: the call screen toggles its controls with a tap on the composable behind
 * the video. An interactive interop view swallowed that tap, so once the controls hid after
 * 5 s nothing could bring them back (iPhone, 2026-11-18).
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun VideoRenderer(
    modifier: Modifier,
    callId: String,
    isLocalVideo: Boolean
) {
    val hardwareService: HardwareService = koinInject()
    val host = remember(callId, isLocalVideo) { UIView().apply { userInteractionEnabled = false } }

    DisposableEffect(callId, isLocalVideo) {
        if (isLocalVideo) {
            hardwareService.addPreviewVideoSurface(host, null)
        } else {
            hardwareService.addVideoSurface(callId, host)
        }
        onDispose {
            if (isLocalVideo) {
                // Only its own: the next preview may already be attached.
                hardwareService.removePreviewVideoSurface(host)
            } else {
                hardwareService.removeVideoSurface(callId)
            }
        }
    }

    UIKitView(
        factory = { host },
        modifier = modifier,
        properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = false),
    )
}
