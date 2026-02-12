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
 * JavaScript implementation of hash computation using pure Kotlin implementations.
 * Web Crypto API is async, so we use synchronous pure Kotlin implementations.
 *
 * Note: SHA-512 is not supported in the JS implementation due to JavaScript's
 * limited support for 64-bit integer operations.
 */
internal actual fun platformComputeHash(data: ByteArray, algorithm: HashAlgorithm): ByteArray {
    return when (algorithm) {
        HashAlgorithm.MD5 -> md5Hash(data)
        HashAlgorithm.SHA1 -> sha1Hash(data)
        HashAlgorithm.SHA256 -> sha256Hash(data)
        HashAlgorithm.SHA512 -> throw UnsupportedOperationException(
            "SHA-512 is not supported in Kotlin/JS due to 64-bit integer limitations. " +
            "Use SHA-256 instead or implement using Web Crypto API."
        )
    }
}

// ==================== MD5 Implementation ====================

private fun md5Hash(message: ByteArray): ByteArray {
    val s = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    )

    val k = intArrayOf(
        0xd76aa478.toInt(), 0xe8c7b756.toInt(), 0x242070db, 0xc1bdceee.toInt(),
        0xf57c0faf.toInt(), 0x4787c62a, 0xa8304613.toInt(), 0xfd469501.toInt(),
        0x698098d8, 0x8b44f7af.toInt(), 0xffff5bb1.toInt(), 0x895cd7be.toInt(),
        0x6b901122, 0xfd987193.toInt(), 0xa679438e.toInt(), 0x49b40821,
        0xf61e2562.toInt(), 0xc040b340.toInt(), 0x265e5a51, 0xe9b6c7aa.toInt(),
        0xd62f105d.toInt(), 0x02441453, 0xd8a1e681.toInt(), 0xe7d3fbc8.toInt(),
        0x21e1cde6, 0xc33707d6.toInt(), 0xf4d50d87.toInt(), 0x455a14ed,
        0xa9e3e905.toInt(), 0xfcefa3f8.toInt(), 0x676f02d9, 0x8d2a4c8a.toInt(),
        0xfffa3942.toInt(), 0x8771f681.toInt(), 0x6d9d6122, 0xfde5380c.toInt(),
        0xa4beea44.toInt(), 0x4bdecfa9, 0xf6bb4b60.toInt(), 0xbebfbc70.toInt(),
        0x289b7ec6, 0xeaa127fa.toInt(), 0xd4ef3085.toInt(), 0x04881d05,
        0xd9d4d039.toInt(), 0xe6db99e5.toInt(), 0x1fa27cf8, 0xc4ac5665.toInt(),
        0xf4292244.toInt(), 0x432aff97, 0xab9423a7.toInt(), 0xfc93a039.toInt(),
        0x655b59c3, 0x8f0ccc92.toInt(), 0xffeff47d.toInt(), 0x85845dd1.toInt(),
        0x6fa87e4f, 0xfe2ce6e0.toInt(), 0xa3014314.toInt(), 0x4e0811a1,
        0xf7537e82.toInt(), 0xbd3af235.toInt(), 0x2ad7d2bb, 0xeb86d391.toInt()
    )

    var a0 = 0x67452301
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476

    val paddedMessage = padMessage(message, true)

    for (chunkStart in paddedMessage.indices step 64) {
        val m = IntArray(16) { i ->
            (paddedMessage[chunkStart + i * 4].toInt() and 0xFF) or
                    ((paddedMessage[chunkStart + i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((paddedMessage[chunkStart + i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((paddedMessage[chunkStart + i * 4 + 3].toInt() and 0xFF) shl 24)
        }

        var a = a0
        var b = b0
        var c = c0
        var d = d0

        for (i in 0 until 64) {
            val f: Int
            val g: Int
            when {
                i < 16 -> {
                    f = (b and c) or (b.inv() and d)
                    g = i
                }
                i < 32 -> {
                    f = (d and b) or (d.inv() and c)
                    g = (5 * i + 1) % 16
                }
                i < 48 -> {
                    f = b xor c xor d
                    g = (3 * i + 5) % 16
                }
                else -> {
                    f = c xor (b or d.inv())
                    g = (7 * i) % 16
                }
            }

            val temp = d
            d = c
            c = b
            b += (a + f + k[i] + m[g]).rotateLeft(s[i])
            a = temp
        }

        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }

    return intToBytes(a0, true) + intToBytes(b0, true) + intToBytes(c0, true) + intToBytes(d0, true)
}

// ==================== SHA-1 Implementation ====================

private fun sha1Hash(message: ByteArray): ByteArray {
    var h0 = 0x67452301
    var h1 = 0xEFCDAB89.toInt()
    var h2 = 0x98BADCFE.toInt()
    var h3 = 0x10325476
    var h4 = 0xC3D2E1F0.toInt()

    val paddedMessage = padMessage(message, false)

    for (chunkStart in paddedMessage.indices step 64) {
        val w = IntArray(80)
        for (i in 0 until 16) {
            w[i] = ((paddedMessage[chunkStart + i * 4].toInt() and 0xFF) shl 24) or
                    ((paddedMessage[chunkStart + i * 4 + 1].toInt() and 0xFF) shl 16) or
                    ((paddedMessage[chunkStart + i * 4 + 2].toInt() and 0xFF) shl 8) or
                    (paddedMessage[chunkStart + i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16 until 80) {
            w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4

        for (i in 0 until 80) {
            val f: Int
            val k: Int
            when {
                i < 20 -> {
                    f = (b and c) or (b.inv() and d)
                    k = 0x5A827999
                }
                i < 40 -> {
                    f = b xor c xor d
                    k = 0x6ED9EBA1
                }
                i < 60 -> {
                    f = (b and c) or (b and d) or (c and d)
                    k = 0x8F1BBCDC.toInt()
                }
                else -> {
                    f = b xor c xor d
                    k = 0xCA62C1D6.toInt()
                }
            }

            val temp = a.rotateLeft(5) + f + e + k + w[i]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = temp
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    return intToBytes(h0, false) + intToBytes(h1, false) + intToBytes(h2, false) +
            intToBytes(h3, false) + intToBytes(h4, false)
}

// ==================== SHA-256 Implementation ====================

private fun sha256Hash(message: ByteArray): ByteArray {
    val k = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19

    val paddedMessage = padMessage(message, false)

    for (chunkStart in paddedMessage.indices step 64) {
        val w = IntArray(64)
        for (i in 0 until 16) {
            w[i] = ((paddedMessage[chunkStart + i * 4].toInt() and 0xFF) shl 24) or
                    ((paddedMessage[chunkStart + i * 4 + 1].toInt() and 0xFF) shl 16) or
                    ((paddedMessage[chunkStart + i * 4 + 2].toInt() and 0xFF) shl 8) or
                    (paddedMessage[chunkStart + i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7

        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + k[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
    }

    return intToBytes(h0, false) + intToBytes(h1, false) + intToBytes(h2, false) +
            intToBytes(h3, false) + intToBytes(h4, false) + intToBytes(h5, false) +
            intToBytes(h6, false) + intToBytes(h7, false)
}

// ==================== Helper Functions ====================

private fun padMessage(message: ByteArray, littleEndian: Boolean): ByteArray {
    val originalLength = message.size
    val bitLength = originalLength.toLong() * 8

    // Calculate padding
    var paddingLength = 64 - ((originalLength + 9) % 64)
    if (paddingLength == 64) paddingLength = 0

    val padded = ByteArray(originalLength + 1 + paddingLength + 8)

    // Copy original message
    message.copyInto(padded)

    // Append '1' bit
    padded[originalLength] = 0x80.toByte()

    // Append length
    if (littleEndian) {
        for (i in 0 until 8) {
            padded[padded.size - 8 + i] = ((bitLength ushr (i * 8)) and 0xFF).toByte()
        }
    } else {
        for (i in 0 until 8) {
            padded[padded.size - 1 - i] = ((bitLength ushr (i * 8)) and 0xFF).toByte()
        }
    }

    return padded
}

private fun intToBytes(value: Int, littleEndian: Boolean): ByteArray {
    return if (littleEndian) {
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte()
        )
    } else {
        byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }
}

private fun Int.rotateLeft(bits: Int): Int = (this shl bits) or (this ushr (32 - bits))
private fun Int.rotateRight(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
