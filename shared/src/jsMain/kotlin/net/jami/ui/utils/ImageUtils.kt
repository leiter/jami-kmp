package net.jami.ui.utils

import androidx.compose.ui.graphics.ImageBitmap

actual fun ByteArray.toImageBitmap(): ImageBitmap? = null

// Android-first stub: scaling not supported in JS environment.
actual fun scaleImageBytes(data: ByteArray, maxSize: Int): ByteArray = data
