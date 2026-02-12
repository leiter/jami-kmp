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

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * Web (JavaScript) implementation of DeviceRuntimeService.
 *
 * Uses virtual paths since web browsers are sandboxed.
 * File storage uses localStorage for metadata and IndexedDB would be needed for actual files.
 * Permissions use the Web Permissions API.
 */
class WebDeviceRuntimeService : DeviceRuntimeService {

    // Virtual file system tracking
    private val virtualFiles = mutableSetOf<String>()

    companion object {
        private const val FILES_KEY = "jami_virtual_files"
        private const val BASE_PATH = "/jami"
    }

    init {
        // Load virtual file list from localStorage
        localStorage[FILES_KEY]?.split(",")?.filter { it.isNotEmpty() }?.forEach {
            virtualFiles.add(it)
        }
    }

    private fun saveVirtualFiles() {
        localStorage[FILES_KEY] = virtualFiles.joinToString(",")
    }

    override fun getConversationPath(accountId: String, conversationId: String, name: String): String =
        "$BASE_PATH/conversations/$accountId/$conversationId/$name"

    override fun getNewConversationPath(accountId: String, conversationId: String, name: String): String {
        var prefix = 0
        var destPath: String
        do {
            val fileName = if (prefix == 0) name else "${prefix}_$name"
            destPath = getConversationPath(accountId, conversationId, fileName)
            prefix++
        } while (virtualFiles.contains(destPath))
        return destPath
    }

    override fun getTempPath(): String =
        "$BASE_PATH/temp"

    override fun getCachePath(): String =
        "$BASE_PATH/cache"

    override fun getDataPath(): String =
        "$BASE_PATH/data"

    override fun hasStoragePermission(): Boolean =
        true // Web storage is sandboxed per origin

    override fun hasCameraPermission(): Boolean {
        // Check via Permissions API (async, so we return cached state)
        // In practice, permission should be checked asynchronously before use
        return checkPermissionFromStorage("camera")
    }

    override fun hasMicrophonePermission(): Boolean {
        // Check via Permissions API (async, so we return cached state)
        return checkPermissionFromStorage("microphone")
    }

    override fun fileExists(path: String): Boolean =
        virtualFiles.contains(path)

    override fun deleteFile(path: String): Boolean {
        val existed = virtualFiles.remove(path)
        if (existed) {
            saveVirtualFiles()
            // Also remove any associated data from localStorage
            localStorage.removeItem("file_$path")
        }
        return existed
    }

    /**
     * Check permission from localStorage cache.
     * The actual permission check should be done asynchronously via:
     * navigator.permissions.query({name: "camera"}).then(result => ...)
     */
    private fun checkPermissionFromStorage(permission: String): Boolean {
        val key = "permission_$permission"
        return localStorage[key] == "granted"
    }

    /**
     * Update permission cache. Call this from JS after checking permissions.
     */
    fun updatePermissionCache(permission: String, granted: Boolean) {
        localStorage["permission_$permission"] = if (granted) "granted" else "denied"
    }

    /**
     * Register a virtual file (for tracking).
     */
    fun registerFile(path: String) {
        virtualFiles.add(path)
        saveVirtualFiles()
    }
}
