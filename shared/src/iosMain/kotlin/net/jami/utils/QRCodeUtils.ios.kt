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
 *
 * Implementation approach:
 * 1. Create CIFilter with name "CIQRCodeGenerator"
 * 2. Set inputMessage (NSData from UTF-8 string)
 * 3. Set inputCorrectionLevel ("L", "M", "Q", or "H")
 * 4. Get outputImage (CIImage)
 * 5. Scale to desired size using CGAffineTransformMakeScale
 * 6. Render to CGImage using CIContext
 * 7. Extract pixel data using CGBitmapContext
 *
 * For iOS apps using SwiftUI/UIKit, consider using the CIImage directly
 * with UIImage for display rather than extracting pixels.
 */
actual object QRCodeUtils {
    private const val TAG = "QRCodeUtils"

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

        // TODO: Implement CoreImage QR generation
        // The full implementation requires:
        // - CIFilter("CIQRCodeGenerator")
        // - CIContext for rendering
        // - CGBitmapContext for pixel extraction
        // See jami-client-ios for reference implementation
        Log.w(TAG, "QR code generation not yet implemented for iOS")
        return null
    }
}
