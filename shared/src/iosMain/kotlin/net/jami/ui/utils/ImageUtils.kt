package net.jami.ui.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun ByteArray.toImageBitmap(): ImageBitmap? = try {
    Image.makeFromEncoded(this).toComposeImageBitmap()
} catch (e: Exception) {
    null
}

// Android-first stub: scaling not yet implemented on iOS.
actual fun scaleImageBytes(data: ByteArray, maxSize: Int): ByteArray = data
