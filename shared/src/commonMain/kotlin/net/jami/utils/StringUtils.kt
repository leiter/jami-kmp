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
 * String utility functions for the Jami KMP project.
 * These are KMP-compatible implementations of common string operations.
 */
object StringUtils {

    /**
     * Capitalizes the first character of the string.
     */
    fun capitalize(input: String): String {
        if (input.isEmpty()) return input
        return input[0].uppercaseChar() + input.substring(1)
    }

    /**
     * Converts a string to a password display (masked with dots).
     * @param input The string to mask
     * @param maskChar The character to use for masking (default: bullet point)
     */
    fun toPassword(input: String, maskChar: Char = '\u2022'): String {
        return maskChar.toString().repeat(input.length)
    }

    /**
     * Extracts only numeric characters from a string.
     */
    fun toNumber(input: String): String {
        return input.filter { it.isDigit() }
    }

    /**
     * Gets the file extension from a filename or path.
     * @return The extension without the dot, or empty string if none
     */
    fun getFileExtension(filename: String): String {
        val name = getFileName(filename)
        val lastDot = name.lastIndexOf('.')

        // Ensure the dot is not the first character (dotfiles like .gitignore have no extension)
        // and there's at least one character after the dot
        return if (lastDot > 0 && lastDot < name.length - 1) {
            name.substring(lastDot + 1)
        } else {
            ""
        }
    }

    /**
     * Gets the filename without extension.
     */
    fun getFileNameWithoutExtension(filename: String): String {
        val name = getFileName(filename)
        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0) {
            name.substring(0, lastDot)
        } else {
            name
        }
    }

    /**
     * Gets just the filename from a path.
     */
    fun getFileName(path: String): String {
        val lastSeparator = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        return if (lastSeparator >= 0 && lastSeparator < path.length - 1) {
            path.substring(lastSeparator + 1)
        } else {
            path
        }
    }

    /**
     * Checks if a string contains only emoji characters.
     * Uses Unicode ranges for common emoji blocks.
     */
    fun isOnlyEmoji(text: String): Boolean {
        if (text.isEmpty()) return false

        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            if (!isEmoji(codePoint)) {
                return false
            }
            i += Character.charCount(codePoint)
        }
        return true
    }

    /**
     * Counts the number of emoji in a string.
     */
    fun countEmoji(text: String): Int {
        if (text.isEmpty()) return 0

        var count = 0
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            if (isEmoji(codePoint)) {
                count++
            }
            i += Character.charCount(codePoint)
        }
        return count
    }

    /**
     * Checks if a code point represents an emoji.
     * Covers common emoji Unicode ranges.
     */
    private fun isEmoji(codePoint: Int): Boolean {
        return when (codePoint) {
            // Emoticons
            in 0x1F600..0x1F64F -> true
            // Miscellaneous Symbols and Pictographs
            in 0x1F300..0x1F5FF -> true
            // Transport and Map Symbols
            in 0x1F680..0x1F6FF -> true
            // Supplemental Symbols and Pictographs
            in 0x1F900..0x1F9FF -> true
            // Symbols and Pictographs Extended-A
            in 0x1FA00..0x1FA6F -> true
            // Symbols and Pictographs Extended-B
            in 0x1FA70..0x1FAFF -> true
            // Dingbats
            in 0x2700..0x27BF -> true
            // Miscellaneous Symbols
            in 0x2600..0x26FF -> true
            // Regional Indicator Symbols (flags)
            in 0x1F1E0..0x1F1FF -> true
            // Variation Selectors (used in emoji sequences)
            0xFE0F -> true
            // Zero Width Joiner (used in compound emoji)
            0x200D -> true
            else -> false
        }
    }

    /**
     * Truncates a string to a maximum length, adding ellipsis if truncated.
     * If maxLength is smaller than ellipsis length, just truncates without ellipsis.
     * If maxLength equals ellipsis length, returns only ellipsis.
     */
    fun truncate(text: String, maxLength: Int, ellipsis: String = "..."): String {
        if (text.length <= maxLength) return text
        if (maxLength < ellipsis.length) return text.take(maxLength)
        if (maxLength == ellipsis.length) return ellipsis
        return text.take(maxLength - ellipsis.length) + ellipsis
    }

    /**
     * Checks if a string is null or blank (empty or only whitespace).
     */
    fun isNullOrBlank(text: String?): Boolean {
        return text.isNullOrBlank()
    }

    /**
     * Checks if a string is null or empty.
     */
    fun isNullOrEmpty(text: String?): Boolean {
        return text.isNullOrEmpty()
    }

    /**
     * Returns the string if not blank, or null otherwise.
     */
    fun emptyToNull(text: String?): String? {
        return if (text.isNullOrBlank()) null else text
    }

    /**
     * Returns the string if not null, or empty string otherwise.
     */
    fun nullToEmpty(text: String?): String {
        return text ?: ""
    }

    /**
     * Removes all whitespace from a string.
     */
    fun removeWhitespace(text: String): String {
        return text.filterNot { it.isWhitespace() }
    }

    /**
     * Normalizes whitespace in a string (collapses multiple spaces to single space).
     */
    fun normalizeWhitespace(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Checks if a string is a valid Jami identifier (64 hex characters).
     */
    fun isJamiId(text: String): Boolean {
        return text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    /**
     * Formats a Jami URI, ensuring it has the jami: prefix.
     */
    fun toJamiUri(identifier: String): String {
        return if (identifier.startsWith("jami:")) {
            identifier
        } else {
            "jami:$identifier"
        }
    }

    /**
     * Removes the jami: prefix from a URI if present.
     */
    fun fromJamiUri(uri: String): String {
        return if (uri.startsWith("jami:")) {
            uri.removePrefix("jami:")
        } else {
            uri
        }
    }
}

/**
 * Extension function for String.codePointAt that works in KMP.
 */
private fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return Character.toCodePoint(high, low)
        }
    }
    return high.code
}

/**
 * Utility object for Unicode character operations.
 */
private object Character {
    fun charCount(codePoint: Int): Int {
        return if (codePoint >= 0x10000) 2 else 1
    }

    fun toCodePoint(high: Char, low: Char): Int {
        return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
    }
}
