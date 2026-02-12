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

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

/**
 * macOS implementation of DeviceRuntimeService.
 *
 * Uses Foundation APIs for file paths.
 * Permission checking returns true by default - apps should use native macOS APIs
 * to request and check permissions at the UI level.
 */
@OptIn(ExperimentalForeignApi::class)
class MacOSDeviceRuntimeService : DeviceRuntimeService {

    private val fileManager: NSFileManager = NSFileManager.defaultManager

    private val applicationSupportDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true
        )
        val baseDir = (paths.firstOrNull() as? String) ?: ""
        val jamiDir = "$baseDir/Jami"
        createDirectoryIfNeeded(jamiDir)
        jamiDir
    }

    private val cachesDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        )
        val baseDir = (paths.firstOrNull() as? String) ?: ""
        val jamiDir = "$baseDir/Jami"
        createDirectoryIfNeeded(jamiDir)
        jamiDir
    }

    private val conversationsDir: String by lazy {
        val path = "$applicationSupportDir/conversations"
        createDirectoryIfNeeded(path)
        path
    }

    private fun createDirectoryIfNeeded(path: String) {
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(
                path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
    }

    override fun getConversationPath(accountId: String, conversationId: String, name: String): String {
        val dir = "$conversationsDir/$accountId/$conversationId"
        createDirectoryIfNeeded(dir)
        return "$dir/$name"
    }

    override fun getNewConversationPath(accountId: String, conversationId: String, name: String): String {
        var prefix = 0
        var destPath: String
        do {
            val fileName = if (prefix == 0) name else "${prefix}_$name"
            destPath = getConversationPath(accountId, conversationId, fileName)
            prefix++
        } while (fileManager.fileExistsAtPath(destPath))
        return destPath
    }

    override fun getTempPath(): String =
        NSTemporaryDirectory()

    override fun getCachePath(): String =
        cachesDir

    override fun getDataPath(): String =
        applicationSupportDir

    override fun hasStoragePermission(): Boolean =
        true // macOS doesn't require explicit storage permissions for app sandbox

    override fun hasCameraPermission(): Boolean {
        // Permission checking should be done at the UI layer using native macOS APIs
        // AVCaptureDevice.authorizationStatus(for: .video)
        // Returning true here - UI layer should handle actual permission requests
        return true
    }

    override fun hasMicrophonePermission(): Boolean {
        // Permission checking should be done at the UI layer using native macOS APIs
        // AVCaptureDevice.authorizationStatus(for: .audio)
        // Returning true here - UI layer should handle actual permission requests
        return true
    }

    override fun fileExists(path: String): Boolean =
        fileManager.fileExistsAtPath(path)

    override fun deleteFile(path: String): Boolean =
        fileManager.removeItemAtPath(path, error = null)
}
