package net.jami.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

/**
 * iOS video player, mirroring the Android implementation's behaviour: native transport
 * controls, autoplay, and release on dispose.
 *
 * Uses [AVPlayerViewController] rather than a bare `AVPlayerLayer` so the standard scrubber,
 * play/pause, volume and full-screen controls come for free — the counterpart of Media3's
 * `PlayerView` with `useController = true`.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayerView(filePath: String, fileName: String, modifier: Modifier) {
    val controller = remember(filePath) {
        AVPlayerViewController().apply {
            // filePath is a local path, not a URL string, so fileURLWithPath is correct here;
            // NSURL.URLWithString would return null for paths containing spaces.
            player = AVPlayer.playerWithURL(NSURL.fileURLWithPath(filePath))
            showsPlaybackControls = true
        }
    }

    DisposableEffect(controller) {
        controller.player?.play()
        onDispose {
            controller.player?.pause()
            controller.player = null
        }
    }

    UIKitViewController(
        factory = { controller },
        modifier = modifier,
    )
}
