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

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Desktop (JVM) implementation of file operations using java.io.
 */
internal actual fun platformCopyFile(source: String, destination: String): Boolean {
    return try {
        val sourceFile = File(source)
        val destFile = File(destination)

        // Create parent directories if needed
        destFile.parentFile?.mkdirs()

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to copy file: ${e.message}")
        false
    }
}

internal actual fun platformMoveFile(source: String, destination: String): Boolean {
    return try {
        val sourceFile = File(source)
        val destFile = File(destination)

        // Create parent directories if needed
        destFile.parentFile?.mkdirs()

        // Try rename first (faster if on same filesystem)
        if (sourceFile.renameTo(destFile)) {
            return true
        }

        // Fall back to copy + delete
        if (platformCopyFile(source, destination)) {
            sourceFile.delete()
            true
        } else {
            false
        }
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to move file: ${e.message}")
        false
    }
}

internal actual fun platformDeleteFile(path: String): Boolean {
    return try {
        File(path).delete()
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to delete file: ${e.message}")
        false
    }
}

internal actual fun platformFileExists(path: String): Boolean {
    return File(path).exists()
}

internal actual fun platformMkdirs(path: String): Boolean {
    return try {
        val dir = File(path)
        dir.exists() || dir.mkdirs()
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to create directories: ${e.message}")
        false
    }
}

internal actual fun platformGetFileSize(path: String): Long {
    return try {
        val file = File(path)
        if (file.exists()) file.length() else -1L
    } catch (e: Exception) {
        -1L
    }
}

internal actual fun platformReadBytes(path: String): ByteArray? {
    return try {
        File(path).readBytes()
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to read file: ${e.message}")
        null
    }
}

internal actual fun platformWriteBytes(path: String, data: ByteArray): Boolean {
    return try {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
        true
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to write file: ${e.message}")
        false
    }
}

internal actual fun platformPathSeparator(): String = File.separator
