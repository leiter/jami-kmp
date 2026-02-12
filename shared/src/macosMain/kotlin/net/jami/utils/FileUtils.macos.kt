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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy
import platform.posix.stat
import platform.posix.S_IFDIR
import platform.posix.S_IFMT

/**
 * macOS implementation of file operations using Foundation APIs.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformCopyFile(source: String, destination: String): Boolean {
    return try {
        val fileManager = NSFileManager.defaultManager

        // Create parent directory if needed
        val parent = FileUtils.getParent(destination)
        if (parent != null && !fileManager.fileExistsAtPath(parent)) {
            fileManager.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
        }

        // Remove existing destination if it exists
        if (fileManager.fileExistsAtPath(destination)) {
            fileManager.removeItemAtPath(destination, error = null)
        }

        fileManager.copyItemAtPath(source, toPath = destination, error = null)
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to copy file: ${e.message}")
        false
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformMoveFile(source: String, destination: String): Boolean {
    return try {
        val fileManager = NSFileManager.defaultManager

        // Create parent directory if needed
        val parent = FileUtils.getParent(destination)
        if (parent != null && !fileManager.fileExistsAtPath(parent)) {
            fileManager.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
        }

        // Remove existing destination if it exists
        if (fileManager.fileExistsAtPath(destination)) {
            fileManager.removeItemAtPath(destination, error = null)
        }

        fileManager.moveItemAtPath(source, toPath = destination, error = null)
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to move file: ${e.message}")
        false
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformDeleteFile(path: String): Boolean {
    return try {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to delete file: ${e.message}")
        false
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformFileExists(path: String): Boolean {
    return NSFileManager.defaultManager.fileExistsAtPath(path)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformMkdirs(path: String): Boolean {
    return try {
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(path)) {
            return true
        }
        fileManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to create directories: ${e.message}")
        false
    }
}

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual fun platformGetFileSize(path: String): Long {
    return try {
        memScoped {
            val statBuf = alloc<stat>()
            if (stat(path, statBuf.ptr) == 0) {
                // Check if it's a regular file (not a directory)
                if ((statBuf.st_mode.toInt() and S_IFMT) != S_IFDIR) {
                    statBuf.st_size
                } else {
                    -1L
                }
            } else {
                -1L
            }
        }
    } catch (e: Exception) {
        -1L
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformReadBytes(path: String): ByteArray? {
    return try {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        data.toByteArray()
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to read file: ${e.message}")
        null
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformWriteBytes(path: String, data: ByteArray): Boolean {
    return try {
        val parent = FileUtils.getParent(path)
        if (parent != null) {
            platformMkdirs(parent)
        }

        val nsData = data.toNSData()
        nsData.writeToFile(path, atomically = true)
    } catch (e: Exception) {
        Log.e("FileUtils", "Failed to write file: ${e.message}")
        false
    }
}

internal actual fun platformPathSeparator(): String = "/"

// Extension functions for NSData <-> ByteArray conversion
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)

    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, length.toULong())
    }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
