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

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Simple VCard data class for KMP.
 *
 * Supports basic VCard properties needed by Jami:
 * - Formatted Name (FN)
 * - UID
 * - Photo (PHOTO)
 */
data class VCard(
    var formattedName: String? = null,
    var uid: String? = null,
    var photo: ByteArray? = null,
    var photoType: String? = null,
    val version: String = "2.1"
) {
    val isEmpty: Boolean
        get() = formattedName.isNullOrEmpty() && photo == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VCard) return false
        return formattedName == other.formattedName &&
                uid == other.uid &&
                photo?.contentEquals(other.photo) ?: (other.photo == null) &&
                photoType == other.photoType
    }

    override fun hashCode(): Int {
        var result = formattedName?.hashCode() ?: 0
        result = 31 * result + (uid?.hashCode() ?: 0)
        result = 31 * result + (photo?.contentHashCode() ?: 0)
        result = 31 * result + (photoType?.hashCode() ?: 0)
        return result
    }
}

/**
 * VCard parsing and writing utilities for KMP.
 *
 * This is a simplified VCard parser that handles the subset of VCard
 * functionality needed by Jami (names, photos, UIDs).
 *
 * Ported from: jami-client-android libjamiclient (replaced ez-vcard)
 */
object VCardUtils {
    const val VCARD_KEY_MIME_TYPE = "mimeType"
    const val ACCOUNT_PROFILE_NAME = "profile"
    const val LOCAL_USER_VCARD_NAME = "$ACCOUNT_PROFILE_NAME.vcf"
    private const val VCARD_MAX_SIZE = 1024L * 1024L * 8

    /**
     * Read display name and photo from a VCard.
     */
    fun readData(vcard: VCard?): Pair<String?, ByteArray?> {
        return Pair(vcard?.formattedName, vcard?.photo)
    }

    /**
     * Create a VCard with the given data.
     */
    fun writeData(uri: String?, displayName: String?, picture: ByteArray?): VCard {
        return VCard(
            formattedName = displayName,
            uid = uri,
            photo = picture,
            photoType = if (picture != null) "JPEG" else null
        )
    }

    /**
     * Parse a VCard string into a VCard object.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun parseVCard(vcardString: String): VCard? {
        if (!vcardString.contains("BEGIN:VCARD", ignoreCase = true)) {
            return null
        }

        val vcard = VCard()
        val lines = vcardString.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("FN:", ignoreCase = true) -> {
                    vcard.formattedName = line.substringAfter(":").trim()
                }

                line.startsWith("UID:", ignoreCase = true) -> {
                    vcard.uid = line.substringAfter(":").trim()
                }

                line.startsWith("VERSION:", ignoreCase = true) -> {
                    // Version is already set to default 2.1
                }

                line.startsWith("PHOTO", ignoreCase = true) -> {
                    // Parse photo with optional parameters
                    // Format: PHOTO;ENCODING=BASE64;TYPE=JPEG:base64data
                    // Or: PHOTO;ENCODING=b;TYPE=JPEG:base64data (VCard 3.0)
                    val colonIndex = line.indexOf(':')
                    if (colonIndex != -1) {
                        val params = line.substring(0, colonIndex)
                        var base64Data = line.substring(colonIndex + 1)

                        // Extract photo type
                        vcard.photoType = extractParameter(params, "TYPE")
                            ?: extractParameter(params, "MEDIATYPE")?.substringAfter("/")?.uppercase()

                        // Check for continuation lines (base64 data may span multiple lines)
                        i++
                        while (i < lines.size) {
                            val nextLine = lines[i]
                            // Continuation lines start with whitespace or are pure base64
                            if (nextLine.startsWith(" ") || nextLine.startsWith("\t")) {
                                base64Data += nextLine.trim()
                                i++
                            } else if (!nextLine.contains(":") && !nextLine.startsWith("END:") && nextLine.isNotBlank()) {
                                base64Data += nextLine.trim()
                                i++
                            } else {
                                i-- // Back up so the outer loop processes this line
                                break
                            }
                        }

                        // Decode base64 photo
                        try {
                            vcard.photo = Base64.decode(base64Data.replace("\\s".toRegex(), ""))
                        } catch (e: Exception) {
                            Log.w("VCardUtils", "Failed to decode photo: ${e.message}")
                        }
                    }
                }
            }
            i++
        }

        return vcard
    }

    /**
     * Convert a VCard to a string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun vcardToString(vcard: VCard?): String? {
        if (vcard == null) return null

        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:${vcard.version}")

            vcard.formattedName?.let {
                appendLine("FN:$it")
            }

            vcard.uid?.let {
                appendLine("UID:$it")
            }

            vcard.photo?.let { photoData ->
                val type = vcard.photoType ?: "JPEG"
                val base64 = Base64.encode(photoData)
                // VCard 2.1 format
                appendLine("PHOTO;ENCODING=BASE64;TYPE=$type:")
                // Split base64 into 75-character lines (VCard spec)
                base64.chunked(75).forEach { chunk ->
                    appendLine(" $chunk")
                }
            }

            appendLine("END:VCARD")
        }
    }

    /**
     * Parse the "elements" of the mime attributes to build a proper hashtable.
     *
     * @param mime the mimetype as returned by the daemon
     * @return a correct hashtable, empty if invalid input
     */
    fun parseMimeAttributes(mime: String): Map<String, String> {
        val elements = mime.split(";")
        val messageKeyValue = mutableMapOf<String, String>()

        if (elements.size < 2) {
            return messageKeyValue
        }

        messageKeyValue[VCARD_KEY_MIME_TYPE] = elements[0]
        val pairs = elements[1].split(",")

        for (pair in pairs) {
            val kv = pair.split("=")
            if (kv.size >= 2) {
                messageKeyValue[kv[0].trim()] = kv[1]
            }
        }

        return messageKeyValue
    }

    /**
     * Get the picture type string from a MIME type.
     */
    fun pictureTypeFromMime(mimeType: String?): String = when (mimeType?.lowercase()) {
        null -> ""
        "image/jpeg" -> "JPEG"
        "image/png" -> "PNG"
        "image/gif" -> "GIF"
        else -> "JPEG"
    }

    /**
     * Check if a VCard is empty (no name and no photo).
     */
    fun isEmpty(vCard: VCard): Boolean {
        return vCard.formattedName.isNullOrEmpty() && vCard.photo == null
    }

    /**
     * Create a default profile VCard with just a UID.
     */
    fun defaultProfile(accountId: String): VCard = VCard(uid = accountId)

    /**
     * Extract a parameter value from a VCard property line.
     * e.g., "PHOTO;ENCODING=BASE64;TYPE=JPEG" -> extractParameter(..., "TYPE") = "JPEG"
     */
    private fun extractParameter(params: String, name: String): String? {
        val regex = Regex("$name=([^;]+)", RegexOption.IGNORE_CASE)
        return regex.find(params)?.groupValues?.get(1)
    }
}
