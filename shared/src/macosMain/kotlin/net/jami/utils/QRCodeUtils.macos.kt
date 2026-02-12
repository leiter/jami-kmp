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
package net.jami.utils

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.Foundation.*

/**
 * macOS implementation of QRCodeUtils using CoreImage CIFilter.
 *
 * Uses CIQRCodeGenerator filter to create QR codes and renders them
 * to pixel data using CIContext and CGBitmapContext.
 *
 * Same implementation as iOS since both platforms use CoreImage.
 */
@OptIn(ExperimentalForeignApi::class)
actual object QRCodeUtils {
    private const val TAG = "QRCodeUtils"
    private const val FILTER_NAME = "CIQRCodeGenerator"
    private const val INPUT_MESSAGE_KEY = "inputMessage"
    private const val INPUT_CORRECTION_LEVEL_KEY = "inputCorrectionLevel"

    actual val DEFAULT_SIZE: Int = 256

    actual fun encodeStringAsQRCodeData(
        input: String,
        foregroundColor: Int,
        backgroundColor: Int,
        size: Int
    ): QRCodeData? {
        if (input.isEmpty()) {
            return null
        }

        return try {
            // Create QR code filter
            val filter = CIFilter.filterWithName(FILTER_NAME) ?: run {
                Log.e(TAG, "Failed to create CIQRCodeGenerator filter")
                return null
            }
            filter.setDefaults()

            // Set input message
            val data = input.encodeToByteArray()
            val nsData = data.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), data.size.toULong())
            }
            filter.setValue(nsData, forKey = INPUT_MESSAGE_KEY)

            // Set error correction level (M = medium, about 15% recovery)
            filter.setValue("M", forKey = INPUT_CORRECTION_LEVEL_KEY)

            // Get output image
            val outputImage = filter.outputImage ?: run {
                Log.e(TAG, "Failed to generate QR code output image")
                return null
            }

            // Scale to desired size
            val extent = outputImage.extent.useContents { this }
            val scaleX = size.toDouble() / extent.size.width
            val scaleY = size.toDouble() / extent.size.height
            val scaledImage = outputImage.imageByApplyingTransform(
                CGAffineTransformMakeScale(scaleX, scaleY)
            )

            // Render to bitmap
            renderCIImageToPixels(scaledImage, size, size, foregroundColor, backgroundColor)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating QR code", e)
            null
        }
    }

    private fun renderCIImageToPixels(
        ciImage: CIImage,
        width: Int,
        height: Int,
        foregroundColor: Int,
        backgroundColor: Int
    ): QRCodeData? {
        memScoped {
            // Create bitmap context
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val bytesPerPixel = 4
            val bytesPerRow = width * bytesPerPixel
            val bufferSize = height * bytesPerRow

            val pixelBuffer = allocArray<UByteVar>(bufferSize)

            val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value

            val context = CGBitmapContextCreate(
                pixelBuffer,
                width.toULong(),
                height.toULong(),
                8u,
                bytesPerRow.toULong(),
                colorSpace,
                bitmapInfo
            )

            if (context == null) {
                CGColorSpaceRelease(colorSpace)
                Log.e(TAG, "Failed to create bitmap context")
                return null
            }

            // Create CIContext and render
            val ciContext = CIContext.contextWithOptions(null)
            val cgImage = ciContext.createCGImage(ciImage, fromRect = ciImage.extent)

            if (cgImage == null) {
                CGContextRelease(context)
                CGColorSpaceRelease(colorSpace)
                Log.e(TAG, "Failed to create CGImage from CIImage")
                return null
            }

            // Draw the image
            val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
            CGContextDrawImage(context, rect, cgImage)

            // Convert to IntArray with color replacement
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val offset = (y * bytesPerRow) + (x * bytesPerPixel)
                    val r = pixelBuffer[offset].toInt() and 0xFF
                    val g = pixelBuffer[offset + 1].toInt() and 0xFF
                    val b = pixelBuffer[offset + 2].toInt() and 0xFF

                    // QR code modules are black (dark), background is white (light)
                    // Use luminance to determine if pixel is foreground or background
                    val luminance = (r * 299 + g * 587 + b * 114) / 1000
                    val pixelIndex = y * width + x
                    pixels[pixelIndex] = if (luminance < 128) foregroundColor else backgroundColor
                }
            }

            // Cleanup
            CGImageRelease(cgImage)
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)

            return QRCodeData(pixels, width, height)
        }
    }
}
