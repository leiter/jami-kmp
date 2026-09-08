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
package net.jami.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.utils.Log
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.UIKit.UIScreenCapturedDidChangeNotification
import platform.UIKit.UIView
import platform.UIKit.UIWindow

private const val TAG = "WindowSecure"

/**
 * iOS counterpart of Android's `FLAG_SECURE`.
 *
 * iOS has no supported equivalent, so this combines the three mechanisms that are
 * available. All of them use public API:
 *
 * 1. **Screen recording and mirroring** — [UIScreen.isCaptured] reports when the display is
 *    being captured. While it is true an opaque cover is placed over the window, so the
 *    recording shows the cover rather than the conversation.
 * 2. **App-switcher snapshot** — iOS photographs the window when the app resigns active.
 *    The same cover is applied for that snapshot and removed on becoming active again.
 * 3. **Screenshots** — cannot be prevented through public API, and are *not* blocked here.
 *    See the note below.
 *
 * ## Why screenshots are not blocked
 *
 * The usual trick is to reparent the app's layer inside the private canvas view of a
 * `UITextField` with `isSecureTextEntry = true`, which iOS omits from screenshots. It
 * depends on an undocumented view hierarchy (`_UITextLayoutCanvasView`), and applied to
 * Compose's hosting window it risks blanking the entire UI if the internals differ from
 * what it expects. That trade — a chance of an invisible app in exchange for screenshot
 * blocking — is not worth taking blind, so it is deliberately omitted.
 *
 * This is therefore **weaker than Android's FLAG_SECURE**, which does block screenshots.
 * The setting's description should not promise more than this delivers.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun WindowSecureEffect(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (!enabled) {
            ScreenCaptureGuard.disable()
            onDispose { }
        } else {
            ScreenCaptureGuard.enable()
            onDispose { ScreenCaptureGuard.disable() }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private object ScreenCaptureGuard {

    private var coverView: UIView? = null
    private var observers = mutableListOf<Any>()
    private var active = false

    fun enable() {
        if (active) return
        active = true
        observers += NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIScreenCapturedDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> applyForCaptureState() }
        observers += NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> showCover() }
        observers += NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> applyForCaptureState() }
        applyForCaptureState()
        Log.d(TAG, "Screen capture protection enabled")
    }

    fun disable() {
        if (!active) return
        active = false
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        hideCover()
        Log.d(TAG, "Screen capture protection disabled")
    }

    /** Cover while the screen is being recorded or mirrored; uncover otherwise. */
    private fun applyForCaptureState() {
        if (UIScreen.mainScreen.isCaptured()) showCover() else hideCover()
    }

    private fun keyWindow(): UIWindow? =
        UIApplication.sharedApplication.windows
            .filterIsInstance<UIWindow>()
            .firstOrNull { it.isKeyWindow() }
            ?: UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().firstOrNull()

    private fun showCover() {
        if (!active) return
        val window = keyWindow() ?: run {
            Log.w(TAG, "No window available to cover")
            return
        }
        if (coverView != null) return
        // Opaque rather than a blur: this is a privacy control, and a blur can still leave
        // large shapes and colours legible in a recording.
        val cover = UIView(frame = window.bounds)
        cover.backgroundColor = UIColor.blackColor
        // Track the window if it resizes (rotation, split view).
        cover.setAutoresizingMask((1uL shl 1) or (1uL shl 4)) // FlexibleWidth | FlexibleHeight
        window.addSubview(cover)
        window.bringSubviewToFront(cover)
        coverView = cover
    }

    private fun hideCover() {
        coverView?.removeFromSuperview()
        coverView = null
    }
}
