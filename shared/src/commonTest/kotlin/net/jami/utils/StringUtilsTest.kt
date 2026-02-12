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

class StringUtilsTest {

    @Test
    fun testCapitalize() {
        assertEquals("Hello", StringUtils.capitalize("hello"))
        assertEquals("Hello", StringUtils.capitalize("Hello"))
        assertEquals("", StringUtils.capitalize(""))
        assertEquals("A", StringUtils.capitalize("a"))
    }

    @Test
    fun testToPassword() {
        assertEquals("\u2022\u2022\u2022\u2022", StringUtils.toPassword("test"))
        assertEquals("****", StringUtils.toPassword("test", '*'))
        assertEquals("", StringUtils.toPassword(""))
    }

    @Test
    fun testToNumber() {
        assertEquals("123", StringUtils.toNumber("abc123def"))
        assertEquals("", StringUtils.toNumber("abc"))
        assertEquals("12345", StringUtils.toNumber("12345"))
        assertEquals("123", StringUtils.toNumber("+1 (23)"))
    }

    @Test
    fun testGetFileExtension() {
        assertEquals("txt", StringUtils.getFileExtension("file.txt"))
        assertEquals("jpg", StringUtils.getFileExtension("image.jpg"))
        assertEquals("gz", StringUtils.getFileExtension("archive.tar.gz"))
        assertEquals("", StringUtils.getFileExtension("noextension"))
        assertEquals("txt", StringUtils.getFileExtension("/path/to/file.txt"))
        assertEquals("", StringUtils.getFileExtension(".gitignore"))
    }

    @Test
    fun testGetFileName() {
        assertEquals("file.txt", StringUtils.getFileName("/path/to/file.txt"))
        assertEquals("file.txt", StringUtils.getFileName("file.txt"))
        assertEquals("file.txt", StringUtils.getFileName("C:\\path\\to\\file.txt"))
    }

    @Test
    fun testGetFileNameWithoutExtension() {
        assertEquals("file", StringUtils.getFileNameWithoutExtension("file.txt"))
        assertEquals("archive.tar", StringUtils.getFileNameWithoutExtension("archive.tar.gz"))
        assertEquals("noextension", StringUtils.getFileNameWithoutExtension("noextension"))
    }

    @Test
    fun testTruncate() {
        assertEquals("Hello...", StringUtils.truncate("Hello World", 8))
        assertEquals("Hello", StringUtils.truncate("Hello", 10))
        assertEquals("...", StringUtils.truncate("Hello", 3))
        assertEquals("He", StringUtils.truncate("Hello", 2))
    }

    @Test
    fun testIsNullOrBlank() {
        assertTrue(StringUtils.isNullOrBlank(null))
        assertTrue(StringUtils.isNullOrBlank(""))
        assertTrue(StringUtils.isNullOrBlank("   "))
        assertFalse(StringUtils.isNullOrBlank("hello"))
    }

    @Test
    fun testIsNullOrEmpty() {
        assertTrue(StringUtils.isNullOrEmpty(null))
        assertTrue(StringUtils.isNullOrEmpty(""))
        assertFalse(StringUtils.isNullOrEmpty("   "))
        assertFalse(StringUtils.isNullOrEmpty("hello"))
    }

    @Test
    fun testEmptyToNull() {
        assertEquals(null, StringUtils.emptyToNull(null))
        assertEquals(null, StringUtils.emptyToNull(""))
        assertEquals(null, StringUtils.emptyToNull("   "))
        assertEquals("hello", StringUtils.emptyToNull("hello"))
    }

    @Test
    fun testNullToEmpty() {
        assertEquals("", StringUtils.nullToEmpty(null))
        assertEquals("", StringUtils.nullToEmpty(""))
        assertEquals("hello", StringUtils.nullToEmpty("hello"))
    }

    @Test
    fun testRemoveWhitespace() {
        assertEquals("helloworld", StringUtils.removeWhitespace("hello world"))
        assertEquals("abc", StringUtils.removeWhitespace("a b c"))
        assertEquals("test", StringUtils.removeWhitespace("  test  "))
    }

    @Test
    fun testNormalizeWhitespace() {
        assertEquals("hello world", StringUtils.normalizeWhitespace("hello   world"))
        assertEquals("a b c", StringUtils.normalizeWhitespace("  a   b   c  "))
    }

    @Test
    fun testIsJamiId() {
        // Valid 64 hex char ID
        assertTrue(StringUtils.isJamiId("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
        assertTrue(StringUtils.isJamiId("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"))

        // Invalid IDs
        assertFalse(StringUtils.isJamiId("short"))
        assertFalse(StringUtils.isJamiId("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeg")) // 'g' invalid
        assertFalse(StringUtils.isJamiId(""))
    }

    @Test
    fun testToJamiUri() {
        assertEquals("jami:abc123", StringUtils.toJamiUri("abc123"))
        assertEquals("jami:abc123", StringUtils.toJamiUri("jami:abc123"))
    }

    @Test
    fun testFromJamiUri() {
        assertEquals("abc123", StringUtils.fromJamiUri("jami:abc123"))
        assertEquals("abc123", StringUtils.fromJamiUri("abc123"))
    }

    @Test
    fun testIsOnlyEmojiSingleEmoji() {
        assertTrue(StringUtils.isOnlyEmoji("\uD83D\uDE00")) // 😀
    }

    @Test
    fun testIsOnlyEmojiMultipleEmoji() {
        assertTrue(StringUtils.isOnlyEmoji("\uD83D\uDE00\uD83D\uDE01")) // 😀😁
    }

    @Test
    fun testIsOnlyEmojiMixedContent() {
        assertFalse(StringUtils.isOnlyEmoji("hello\uD83D\uDE00"))
        assertFalse(StringUtils.isOnlyEmoji("hello"))
    }

    @Test
    fun testIsOnlyEmojiEmpty() {
        assertFalse(StringUtils.isOnlyEmoji(""))
    }

    @Test
    fun testCountEmoji() {
        assertEquals(0, StringUtils.countEmoji(""))
        assertEquals(0, StringUtils.countEmoji("hello"))
        assertEquals(1, StringUtils.countEmoji("\uD83D\uDE00"))
        assertEquals(2, StringUtils.countEmoji("\uD83D\uDE00\uD83D\uDE01"))
        assertEquals(1, StringUtils.countEmoji("hello\uD83D\uDE00"))
    }
}
