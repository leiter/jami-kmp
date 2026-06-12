/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

actual fun ByteArray.toImageBitmap(): ImageBitmap? =
    BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()

actual suspend fun extractVideoThumbnail(filePath: String): ImageBitmap? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val bitmap = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bitmap?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

actual fun scaleImageBytes(data: ByteArray, maxSize: Int): ByteArray {
    // Decode bounds only — no pixel allocation.
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    val srcW = opts.outWidth
    val srcH = opts.outHeight
    if (srcW <= 0 || srcH <= 0) return data
    if (srcW <= maxSize && srcH <= maxSize) return data  // already within bounds

    // Compute power-of-2 sub-sampling so the decoded size is just above maxSize.
    var sampleSize = 1
    var tw = srcW
    var th = srcH
    while (tw / 2 >= maxSize && th / 2 >= maxSize) {
        sampleSize *= 2
        tw /= 2
        th /= 2
    }

    val decOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val sampled = BitmapFactory.decodeByteArray(data, 0, data.size, decOpts) ?: return data

    // Fine-scale to exactly maxSize on the longest side.
    val scale = maxSize.toFloat() / maxOf(sampled.width, sampled.height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            sampled,
            (sampled.width * scale).toInt().coerceAtLeast(1),
            (sampled.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        sampled
    }

    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
    return out.toByteArray()
}
