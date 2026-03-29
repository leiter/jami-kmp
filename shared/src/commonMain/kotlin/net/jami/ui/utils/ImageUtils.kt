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
