/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific composable that renders an interactive video player for [filePath].
 *
 * Android: ExoPlayer wrapped in AndroidView with full Media3 PlayerView controls.
 * iOS/macOS: stub placeholder (AVPlayer integration deferred).
 * Desktop/JS: stub placeholder.
 */
@Composable
expect fun VideoPlayerView(filePath: String, fileName: String, modifier: Modifier = Modifier)
