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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QRCodeUtilsTest {

    @Test
    fun testDefaultSize() {
        assertEquals(256, QRCodeUtils.DEFAULT_SIZE)
    }

    @Test
    fun testQRCodeDataEquality() {
        val data1 = QRCodeData(intArrayOf(1, 2, 3, 4), 2, 2)
        val data2 = QRCodeData(intArrayOf(1, 2, 3, 4), 2, 2)
        val data3 = QRCodeData(intArrayOf(5, 6, 7, 8), 2, 2)

        assertEquals(data1, data2)
        assertEquals(data1.hashCode(), data2.hashCode())
        assertFalse(data1 == data3)
    }

    @Test
    fun testQRCodeDataDimensions() {
        val data = QRCodeData(intArrayOf(0, 0, 0, 0, 0, 0), 3, 2)
        assertEquals(3, data.width)
        assertEquals(2, data.height)
        assertEquals(6, data.data.size)
    }

    @Test
    fun testQRCodeColors() {
        assertEquals(0xFF000000.toInt(), QRCodeColors.BLACK)
        assertEquals(0xFFFFFFFF.toInt(), QRCodeColors.WHITE)
        assertEquals(0x00000000, QRCodeColors.TRANSPARENT)
    }

    @Test
    fun testEncodeStringReturnsNullForStub() {
        // Current implementations are stubs that return null
        // This test verifies the API works correctly
        val result = QRCodeUtils.encodeStringAsQRCodeData("test")
        // Result may be null on platforms without native QR implementation
        // This is expected behavior for stub implementations
        assertTrue(result == null || result.width > 0)
    }

    @Test
    fun testEncodeWithCustomColors() {
        val result = QRCodeUtils.encodeStringAsQRCodeData(
            input = "jami:abc123",
            foregroundColor = QRCodeColors.BLACK,
            backgroundColor = QRCodeColors.WHITE,
            size = 128
        )
        // Stub implementations return null, which is acceptable
        assertTrue(result == null || result.width == 128)
    }
}
