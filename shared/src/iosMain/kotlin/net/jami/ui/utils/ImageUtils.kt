package net.jami.ui.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.jami.utils.Log
import org.jetbrains.skia.Image
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetImageGenerator
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

private const val TAG = "ImageUtils"

/** JPEG quality used when re-encoding a downscaled image. */
private const val SCALE_JPEG_QUALITY = 0.85

actual fun ByteArray.toImageBitmap(): ImageBitmap? = try {
    Image.makeFromEncoded(this).toComposeImageBitmap()
} catch (e: Exception) {
    null
}

/**
 * Downscales [data] so its longest edge is at most [maxSize], re-encoding as JPEG.
 *
 * Returns the input untouched when it already fits, or when decoding fails — callers treat
 * this as best-effort, and sending the original is better than sending nothing.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun scaleImageBytes(data: ByteArray, maxSize: Int): ByteArray {
    if (data.isEmpty() || maxSize <= 0) return data
    return try {
        val image = UIImage.imageWithData(data.toNSData()) ?: return data
        val width = image.size.useContents { width }
        val height = image.size.useContents { height }
        val longest = maxOf(width, height)
        if (longest <= maxSize.toDouble()) return data

        val ratio = maxSize.toDouble() / longest
        val targetWidth = width * ratio
        val targetHeight = height * ratio

        // scale = 1.0 so the output is exactly targetWidth x targetHeight in pixels rather
        // than being multiplied by the screen's scale factor.
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
        val scaled = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        val encoded = scaled?.let { UIImageJPEGRepresentation(it, SCALE_JPEG_QUALITY) } ?: return data
        encoded.toByteArray()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to scale image: ${e.message}")
        data
    }
}

/**
 * Extracts a still frame from a video file for use as a thumbnail.
 *
 * Grabs the frame at 1 second rather than 0, since the first frame of many recordings is
 * black. [AVAssetImageGenerator] clamps to the end of shorter clips.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun extractVideoThumbnail(filePath: String): ImageBitmap? =
    withContext(Dispatchers.Default) {
        try {
            val url = NSURL.fileURLWithPath(filePath)
            val generator = AVAssetImageGenerator(AVAsset.assetWithURL(url)).apply {
                appliesPreferredTrackTransform = true
            }
            val cgImage = generator.copyCGImageAtTime(
                requestedTime = CMTimeMake(value = 1, timescale = 1),
                actualTime = null,
                error = null,
            ) ?: return@withContext null
            val png = UIImagePNGRepresentation(UIImage.imageWithCGImage(cgImage))
                ?: return@withContext null
            png.toByteArray().toImageBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video thumbnail: ${e.message}")
            null
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, size.toULong()) }
    return bytes
}
