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
package net.jami.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * macOS implementation of [VideoRenderer].
 *
 * Compose Multiplatform has no AppKit interop composable (`UIKitView` is iOS-only), so there is
 * no way to hand an `NSView` to the daemon's sink from Compose the way the iOS actual does.
 * Rendering would need an `AVSampleBufferDisplayLayer`/Metal `SinkTarget`, which does not exist
 * for macOS yet — the same gap `VideoSurface.macos.kt` carries. Until then this is a black
 * placeholder and deliberately registers no surface, so the daemon is not handed a view it
 * cannot draw into.
 */
@Composable
actual fun VideoRenderer(
    modifier: Modifier,
    callId: String,
    isLocalVideo: Boolean
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black))
}
