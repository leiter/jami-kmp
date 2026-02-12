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
 * iOS implementation of QRCodeUtils.
 *
 * TODO: Implement using CoreImage CIFilter ("CIQRCodeGenerator").
 * Example implementation:
 * ```
 * val filter = CIFilter.filterWithName("CIQRCodeGenerator")
 * filter.setValue(data, forKey: "inputMessage")
 * filter.setValue("M", forKey: "inputCorrectionLevel")
 * val outputImage = filter.outputImage
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
        // TODO: Implement using CoreImage CIFilter
        Log.d("QRCodeUtils", "QR code generation not implemented for iOS")
        return null
    }
}
