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
import kotlin.test.assertNull

class FileUtilsTest {

    @Test
    fun testJoinPath() {
        assertEquals("a${FileUtils.separator}b${FileUtils.separator}c", FileUtils.joinPath("a", "b", "c"))
        assertEquals("path${FileUtils.separator}to${FileUtils.separator}file", FileUtils.joinPath("path", "to", "file"))
        assertEquals("", FileUtils.joinPath())
        assertEquals("single", FileUtils.joinPath("single"))
    }

    @Test
    fun testJoinPathWithTrailingSlash() {
        // Trailing slashes should be trimmed
        assertEquals("a${FileUtils.separator}b", FileUtils.joinPath("a/", "b"))
        assertEquals("a${FileUtils.separator}b", FileUtils.joinPath("a\\", "b"))
    }

    @Test
    fun testGetParent() {
        assertEquals("/path/to", FileUtils.getParent("/path/to/file.txt"))
        assertEquals("/path", FileUtils.getParent("/path/to"))
        assertEquals("/", FileUtils.getParent("/file.txt"))
        assertNull(FileUtils.getParent("file.txt"))
    }

    @Test
    fun testGetParentWithTrailingSlash() {
        assertEquals("/path/to", FileUtils.getParent("/path/to/dir/"))
    }

    @Test
    fun testGetName() {
        assertEquals("file.txt", FileUtils.getName("/path/to/file.txt"))
        assertEquals("file.txt", FileUtils.getName("file.txt"))
        assertEquals("file.txt", FileUtils.getName("C:\\path\\to\\file.txt"))
    }

    @Test
    fun testGetExtension() {
        assertEquals("txt", FileUtils.getExtension("file.txt"))
        assertEquals("jpg", FileUtils.getExtension("/path/to/image.jpg"))
        assertEquals("gz", FileUtils.getExtension("archive.tar.gz"))
        assertEquals("", FileUtils.getExtension("noextension"))
    }
}
