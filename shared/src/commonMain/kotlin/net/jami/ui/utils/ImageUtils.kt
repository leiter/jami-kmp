/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.utils

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes raw image bytes (JPEG/PNG) into a Compose ImageBitmap.
 * Returns null if decoding fails or is not supported on this platform.
 */
expect fun ByteArray.toImageBitmap(): ImageBitmap?

/**
 * Scale image bytes so the longest side is ≤ [maxSize] pixels, then re-encode
 * as JPEG at quality 88. Returns the original bytes unchanged if already within
 * bounds or if decoding fails.
 *
 * Android: full scaling via BitmapFactory + inSampleSize + createScaledBitmap.
 * Other platforms: stub — returns [data] unchanged until platform support is added.
 */
expect fun scaleImageBytes(data: ByteArray, maxSize: Int = 512): ByteArray
