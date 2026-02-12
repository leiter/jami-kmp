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
 * File utility functions for cross-platform file operations.
 * Uses expect/actual for platform-specific implementations.
 */
object FileUtils {

    /**
     * Copies a file from source to destination.
     * @param sourcePath The source file path
     * @param destinationPath The destination file path
     * @return true if the copy was successful
     */
    fun copyFile(sourcePath: String, destinationPath: String): Boolean =
        platformCopyFile(sourcePath, destinationPath)

    /**
     * Moves a file from source to destination.
     * @param sourcePath The source file path
     * @param destinationPath The destination file path
     * @return true if the move was successful
     */
    fun moveFile(sourcePath: String, destinationPath: String): Boolean =
        platformMoveFile(sourcePath, destinationPath)

    /**
     * Deletes a file.
     * @param path The file path to delete
     * @return true if the deletion was successful
     */
    fun deleteFile(path: String): Boolean =
        platformDeleteFile(path)

    /**
     * Checks if a file exists.
     * @param path The file path to check
     * @return true if the file exists
     */
    fun exists(path: String): Boolean =
        platformFileExists(path)

    /**
     * Creates a directory and any necessary parent directories.
     * @param path The directory path to create
     * @return true if the directory was created or already exists
     */
    fun mkdirs(path: String): Boolean =
        platformMkdirs(path)

    /**
     * Gets the size of a file in bytes.
     * @param path The file path
     * @return The file size in bytes, or -1 if the file doesn't exist
     */
    fun getFileSize(path: String): Long =
        platformGetFileSize(path)

    /**
     * Reads a file as a byte array.
     * @param path The file path
     * @return The file contents as bytes, or null if reading failed
     */
    fun readBytes(path: String): ByteArray? =
        platformReadBytes(path)

    /**
     * Writes bytes to a file.
     * @param path The file path
     * @param data The bytes to write
     * @return true if the write was successful
     */
    fun writeBytes(path: String, data: ByteArray): Boolean =
        platformWriteBytes(path, data)

    /**
     * Reads a file as a string (UTF-8).
     * @param path The file path
     * @return The file contents as a string, or null if reading failed
     */
    fun readText(path: String): String? =
        readBytes(path)?.decodeToString()

    /**
     * Writes a string to a file (UTF-8).
     * @param path The file path
     * @param text The string to write
     * @return true if the write was successful
     */
    fun writeText(path: String, text: String): Boolean =
        writeBytes(path, text.encodeToByteArray())

    /**
     * Joins path components.
     */
    fun joinPath(vararg parts: String): String {
        if (parts.isEmpty()) return ""
        return parts.joinToString(separator) { it.trimEnd('/', '\\') }
    }

    /**
     * Gets the parent directory of a path.
     */
    fun getParent(path: String): String? {
        val normalized = path.trimEnd('/', '\\')
        val lastSep = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
        return if (lastSep > 0) {
            normalized.substring(0, lastSep)
        } else if (lastSep == 0) {
            separator
        } else {
            null
        }
    }

    /**
     * Gets the file name from a path.
     */
    fun getName(path: String): String {
        return StringUtils.getFileName(path)
    }

    /**
     * Gets the file extension from a path.
     */
    fun getExtension(path: String): String {
        return StringUtils.getFileExtension(path)
    }

    /**
     * Platform-specific path separator.
     */
    val separator: String = platformPathSeparator()
}

/**
 * Platform-specific file operations.
 */
internal expect fun platformCopyFile(source: String, destination: String): Boolean
internal expect fun platformMoveFile(source: String, destination: String): Boolean
internal expect fun platformDeleteFile(path: String): Boolean
internal expect fun platformFileExists(path: String): Boolean
internal expect fun platformMkdirs(path: String): Boolean
internal expect fun platformGetFileSize(path: String): Long
internal expect fun platformReadBytes(path: String): ByteArray?
internal expect fun platformWriteBytes(path: String, data: ByteArray): Boolean
internal expect fun platformPathSeparator(): String
