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
import kotlin.test.assertFailsWith

class HashUtilsTest {

    @Test
    fun testMd5String() {
        // Known MD5 hashes
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", HashUtils.md5(""))
        assertEquals("098f6bcd4621d373cade4e832627b4f6", HashUtils.md5("test"))
        assertEquals("5d41402abc4b2a76b9719d911017c592", HashUtils.md5("hello"))
    }

    @Test
    fun testMd5Bytes() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", HashUtils.md5(ByteArray(0)))
        assertEquals("098f6bcd4621d373cade4e832627b4f6", HashUtils.md5("test".encodeToByteArray()))
    }

    @Test
    fun testSha1String() {
        // Known SHA-1 hashes
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", HashUtils.sha1(""))
        assertEquals("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", HashUtils.sha1("test"))
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", HashUtils.sha1("hello"))
    }

    @Test
    fun testSha256String() {
        // Known SHA-256 hashes
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", HashUtils.sha256(""))
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", HashUtils.sha256("test"))
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", HashUtils.sha256("hello"))
    }

    @Test
    fun testSha512String() {
        // Known SHA-512 hash (empty string)
        // Note: SHA-512 is not supported on JS due to 64-bit integer limitations
        try {
            assertEquals(
                "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
                HashUtils.sha512("")
            )
        } catch (e: UnsupportedOperationException) {
            // Expected on JS platform
        }
    }

    @Test
    fun testBytesToHex() {
        assertEquals("", HashUtils.bytesToHex(ByteArray(0)))
        assertEquals("00", HashUtils.bytesToHex(byteArrayOf(0)))
        assertEquals("ff", HashUtils.bytesToHex(byteArrayOf(-1)))
        assertEquals("0102030405", HashUtils.bytesToHex(byteArrayOf(1, 2, 3, 4, 5)))
        assertEquals("deadbeef", HashUtils.bytesToHex(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())))
    }

    @Test
    fun testHexToBytes() {
        assertEquals(0, HashUtils.hexToBytes("").size)
        assertEquals(listOf<Byte>(0), HashUtils.hexToBytes("00").toList())
        assertEquals(listOf<Byte>(-1), HashUtils.hexToBytes("ff").toList())
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), HashUtils.hexToBytes("0102030405").toList())
        assertEquals(
            listOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            HashUtils.hexToBytes("DEADBEEF").toList()
        )
    }

    @Test
    fun testHexToBytesInvalidOddLength() {
        assertFailsWith<IllegalArgumentException> {
            HashUtils.hexToBytes("abc")
        }
    }

    @Test
    fun testRoundTrip() {
        val original = byteArrayOf(1, 2, 3, 0, -1, -128, 127)
        val hex = HashUtils.bytesToHex(original)
        val result = HashUtils.hexToBytes(hex)
        assertEquals(original.toList(), result.toList())
    }

    @Test
    fun testComputeHashWithAlgorithm() {
        assertEquals("098f6bcd4621d373cade4e832627b4f6", HashUtils.computeHash("test".encodeToByteArray(), HashAlgorithm.MD5))
        assertEquals("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", HashUtils.computeHash("test".encodeToByteArray(), HashAlgorithm.SHA1))
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", HashUtils.computeHash("test".encodeToByteArray(), HashAlgorithm.SHA256))
    }
}
