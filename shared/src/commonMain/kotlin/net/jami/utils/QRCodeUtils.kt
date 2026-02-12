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

/**
 * QR code data containing pixel information.
 *
 * @property data Pixel data as ARGB integers (row-major order)
 * @property width Width in pixels
 * @property height Height in pixels
 */
data class QRCodeData(
    val data: IntArray,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QRCodeData) return false
        return data.contentEquals(other.data) && width == other.width && height == other.height
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * Utility for generating QR codes.
 *
 * Platform implementations:
 * - Android/Desktop: ZXing library
 * - iOS/macOS: CoreImage CIFilter
 * - Web: Stub (would need JS library)
 */
expect object QRCodeUtils {
    /**
     * Default QR code image size in pixels.
     */
    val DEFAULT_SIZE: Int

    /**
     * Encode a string as QR code pixel data.
     *
     * @param input The string to encode
     * @param foregroundColor ARGB color for QR code modules (typically black)
     * @param backgroundColor ARGB color for background (typically white)
     * @param size QR code size in pixels (default: DEFAULT_SIZE)
     * @return QRCodeData containing the pixel data, or null on error
     */
    fun encodeStringAsQRCodeData(
        input: String,
        foregroundColor: Int = 0xFF000000.toInt(),
        backgroundColor: Int = 0xFFFFFFFF.toInt(),
        size: Int = DEFAULT_SIZE
    ): QRCodeData?
}

/**
 * Common color constants for QR codes.
 */
object QRCodeColors {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val TRANSPARENT = 0x00000000
}
