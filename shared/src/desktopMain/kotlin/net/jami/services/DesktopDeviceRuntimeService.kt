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
package net.jami.services

import java.io.File

/**
 * Desktop (JVM) implementation of DeviceRuntimeService.
 *
 * Uses standard Java file paths:
 * - Linux: ~/.local/share/jami
 * - macOS: ~/Library/Application Support/jami
 * - Windows: %APPDATA%/jami
 *
 * Permissions are always granted on desktop platforms.
 */
class DesktopDeviceRuntimeService : DeviceRuntimeService {

    private val dataDir: File by lazy {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        val baseDir = when {
            osName.contains("win") -> {
                System.getenv("APPDATA")?.let { File(it, "jami") }
                    ?: File(userHome, "AppData/Roaming/jami")
            }
            osName.contains("mac") -> {
                File(userHome, "Library/Application Support/jami")
            }
            else -> {
                // Linux and other Unix-like systems
                System.getenv("XDG_DATA_HOME")?.let { File(it, "jami") }
                    ?: File(userHome, ".local/share/jami")
            }
        }
        baseDir.also { it.mkdirs() }
    }

    private val cacheDir: File by lazy {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        val baseDir = when {
            osName.contains("win") -> {
                System.getenv("LOCALAPPDATA")?.let { File(it, "jami/cache") }
                    ?: File(userHome, "AppData/Local/jami/cache")
            }
            osName.contains("mac") -> {
                File(userHome, "Library/Caches/jami")
            }
            else -> {
                // Linux and other Unix-like systems
                System.getenv("XDG_CACHE_HOME")?.let { File(it, "jami") }
                    ?: File(userHome, ".cache/jami")
            }
        }
        baseDir.also { it.mkdirs() }
    }

    private val conversationsDir: File by lazy {
        File(dataDir, "conversations").also { it.mkdirs() }
    }

    override fun getConversationPath(accountId: String, conversationId: String, name: String): String {
        val dir = File(conversationsDir, "$accountId/$conversationId")
        dir.mkdirs()
        return File(dir, name).absolutePath
    }

    override fun getNewConversationPath(accountId: String, conversationId: String, name: String): String {
        var prefix = 0
        var destPath: String
        do {
            val fileName = if (prefix == 0) name else "${prefix}_$name"
            destPath = getConversationPath(accountId, conversationId, fileName)
            prefix++
        } while (File(destPath).exists())
        return destPath
    }

    override fun getTempPath(): String =
        System.getProperty("java.io.tmpdir") ?: cacheDir.absolutePath

    override fun getCachePath(): String =
        cacheDir.absolutePath

    override fun getDataPath(): String =
        dataDir.absolutePath

    // Desktop platforms don't require runtime permissions
    override fun hasStoragePermission(): Boolean = true
    override fun hasCameraPermission(): Boolean = true
    override fun hasMicrophonePermission(): Boolean = true

    override fun fileExists(path: String): Boolean =
        File(path).exists()

    override fun deleteFile(path: String): Boolean =
        File(path).delete()
}
