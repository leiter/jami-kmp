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

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Desktop (JVM) implementation of QRCodeUtils using ZXing library.
 *
 * Same implementation as Android since both use JVM and ZXing.
 */
actual object QRCodeUtils {
    private const val TAG = "QRCodeUtils"
    private const val QRCODE_IMAGE_PADDING = 1

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

        val qrWriter = QRCodeWriter()
        val qrImageMatrix = try {
            val hints = HashMap<EncodeHintType, Int>()
            hints[EncodeHintType.MARGIN] = QRCODE_IMAGE_PADDING
            qrWriter.encode(input, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (e: WriterException) {
            Log.e(TAG, "Error while encoding QR", e)
            return null
        }

        val qrImageWidth = qrImageMatrix.width
        val qrImageHeight = qrImageMatrix.height
        val pixels = IntArray(qrImageWidth * qrImageHeight)

        for (row in 0 until qrImageHeight) {
            val offset = row * qrImageWidth
            for (column in 0 until qrImageWidth) {
                pixels[offset + column] = if (qrImageMatrix[column, row]) foregroundColor else backgroundColor
            }
        }

        return QRCodeData(pixels, qrImageWidth, qrImageHeight)
    }
}
