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
 * JavaScript implementation of file operations.
 *
 * Note: In browser environments, direct file system access is restricted.
 * This implementation uses an in-memory cache for basic operations.
 * For Node.js, this could be extended to use the 'fs' module.
 */

// In-memory file storage for browser environment
private val memoryFileSystem = mutableMapOf<String, ByteArray>()

internal actual fun platformCopyFile(source: String, destination: String): Boolean {
    return try {
        val data = memoryFileSystem[source] ?: return false
        memoryFileSystem[destination] = data.copyOf()
        true
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to copy file: ${e.message}")
        false
    }
}

internal actual fun platformMoveFile(source: String, destination: String): Boolean {
    return try {
        val data = memoryFileSystem.remove(source) ?: return false
        memoryFileSystem[destination] = data
        true
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to move file: ${e.message}")
        false
    }
}

internal actual fun platformDeleteFile(path: String): Boolean {
    return memoryFileSystem.remove(path) != null
}

internal actual fun platformFileExists(path: String): Boolean {
    return memoryFileSystem.containsKey(path)
}

internal actual fun platformMkdirs(path: String): Boolean {
    // In memory system, directories are implicit
    return true
}

internal actual fun platformGetFileSize(path: String): Long {
    return memoryFileSystem[path]?.size?.toLong() ?: -1L
}

internal actual fun platformReadBytes(path: String): ByteArray? {
    return memoryFileSystem[path]?.copyOf()
}

internal actual fun platformWriteBytes(path: String, data: ByteArray): Boolean {
    return try {
        memoryFileSystem[path] = data.copyOf()
        true
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to write file: ${e.message}")
        false
    }
}

internal actual fun platformPathSeparator(): String = "/"
