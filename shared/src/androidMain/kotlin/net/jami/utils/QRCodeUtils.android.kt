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
 * Android implementation of QRCodeUtils.
 *
 * TODO: Implement using ZXing when the dependency is added:
 * ```kotlin
 * implementation("com.google.zxing:core:3.5.3")
 * ```
 *
 * Example implementation:
 * ```kotlin
 * val qrWriter = QRCodeWriter()
 * val hints = mapOf(EncodeHintType.MARGIN to 1)
 * val matrix = qrWriter.encode(input, BarcodeFormat.QR_CODE, size, size, hints)
 * // Convert BitMatrix to pixel data...
 * ```
 */
actual object QRCodeUtils {
    actual val DEFAULT_SIZE: Int = 256

    actual fun encodeStringAsQRCodeData(
        input: String,
        foregroundColor: Int,
        backgroundColor: Int,
        size: Int
    ): QRCodeData? {
        // TODO: Implement using ZXing library when dependency is added
        Log.d("QRCodeUtils", "QR code generation not implemented for Android (requires ZXing dependency)")
        return null
    }
}
