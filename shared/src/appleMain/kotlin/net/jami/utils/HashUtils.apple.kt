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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA512
import platform.CoreCrypto.CC_SHA512_DIGEST_LENGTH

/**
 * iOS implementation of hash computation using CommonCrypto.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformComputeHash(data: ByteArray, algorithm: HashAlgorithm): ByteArray {
    val digestLength = when (algorithm) {
        HashAlgorithm.MD5 -> CC_MD5_DIGEST_LENGTH
        HashAlgorithm.SHA1 -> CC_SHA1_DIGEST_LENGTH
        HashAlgorithm.SHA256 -> CC_SHA256_DIGEST_LENGTH
        HashAlgorithm.SHA512 -> CC_SHA512_DIGEST_LENGTH
    }

    val result = UByteArray(digestLength)

    if (data.isEmpty()) {
        // Handle empty input specially
        result.usePinned { resultPinned ->
            when (algorithm) {
                HashAlgorithm.MD5 -> CC_MD5(null, 0u, resultPinned.addressOf(0))
                HashAlgorithm.SHA1 -> CC_SHA1(null, 0u, resultPinned.addressOf(0))
                HashAlgorithm.SHA256 -> CC_SHA256(null, 0u, resultPinned.addressOf(0))
                HashAlgorithm.SHA512 -> CC_SHA512(null, 0u, resultPinned.addressOf(0))
            }
        }
    } else {
        data.usePinned { dataPinned ->
            result.usePinned { resultPinned ->
                val dataPtr: CPointer<UByteVar> = dataPinned.addressOf(0).reinterpret()
                when (algorithm) {
                    HashAlgorithm.MD5 -> CC_MD5(
                        dataPtr,
                        data.size.convert(),
                        resultPinned.addressOf(0)
                    )
                    HashAlgorithm.SHA1 -> CC_SHA1(
                        dataPtr,
                        data.size.convert(),
                        resultPinned.addressOf(0)
                    )
                    HashAlgorithm.SHA256 -> CC_SHA256(
                        dataPtr,
                        data.size.convert(),
                        resultPinned.addressOf(0)
                    )
                    HashAlgorithm.SHA512 -> CC_SHA512(
                        dataPtr,
                        data.size.convert(),
                        resultPinned.addressOf(0)
                    )
                }
            }
        }
    }

    return result.toByteArray()
}

private fun UByteArray.toByteArray(): ByteArray = ByteArray(size) { this[it].toByte() }
