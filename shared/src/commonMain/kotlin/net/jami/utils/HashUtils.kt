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
 * Hash utility functions for cryptographic operations.
 * Uses platform-specific implementations via expect/actual.
 */
object HashUtils {

    /**
     * Computes MD5 hash of a byte array.
     * @return Hex string representation of the hash
     */
    fun md5(data: ByteArray): String = computeHash(data, HashAlgorithm.MD5)

    /**
     * Computes MD5 hash of a string (UTF-8 encoded).
     * @return Hex string representation of the hash
     */
    fun md5(data: String): String = md5(data.encodeToByteArray())

    /**
     * Computes SHA-1 hash of a byte array.
     * @return Hex string representation of the hash
     */
    fun sha1(data: ByteArray): String = computeHash(data, HashAlgorithm.SHA1)

    /**
     * Computes SHA-1 hash of a string (UTF-8 encoded).
     * @return Hex string representation of the hash
     */
    fun sha1(data: String): String = sha1(data.encodeToByteArray())

    /**
     * Computes SHA-256 hash of a byte array.
     * @return Hex string representation of the hash
     */
    fun sha256(data: ByteArray): String = computeHash(data, HashAlgorithm.SHA256)

    /**
     * Computes SHA-256 hash of a string (UTF-8 encoded).
     * @return Hex string representation of the hash
     */
    fun sha256(data: String): String = sha256(data.encodeToByteArray())

    /**
     * Computes SHA-512 hash of a byte array.
     * @return Hex string representation of the hash
     */
    fun sha512(data: ByteArray): String = computeHash(data, HashAlgorithm.SHA512)

    /**
     * Computes SHA-512 hash of a string (UTF-8 encoded).
     * @return Hex string representation of the hash
     */
    fun sha512(data: String): String = sha512(data.encodeToByteArray())

    /**
     * Computes hash using the specified algorithm.
     * @return Raw hash bytes
     */
    fun computeHashBytes(data: ByteArray, algorithm: HashAlgorithm): ByteArray =
        platformComputeHash(data, algorithm)

    /**
     * Computes hash using the specified algorithm.
     * @return Hex string representation of the hash
     */
    fun computeHash(data: ByteArray, algorithm: HashAlgorithm): String =
        bytesToHex(computeHashBytes(data, algorithm))

    /**
     * Converts a byte array to a hexadecimal string.
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    /**
     * Converts a hexadecimal string to a byte array.
     * @throws IllegalArgumentException if the string is not valid hex
     */
    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

/**
 * Supported hash algorithms.
 */
enum class HashAlgorithm(val algorithmName: String) {
    MD5("MD5"),
    SHA1("SHA-1"),
    SHA256("SHA-256"),
    SHA512("SHA-512")
}

/**
 * Platform-specific hash computation.
 */
internal expect fun platformComputeHash(data: ByteArray, algorithm: HashAlgorithm): ByteArray
