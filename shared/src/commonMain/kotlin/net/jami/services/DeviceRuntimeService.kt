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

/**
 * Service for device runtime operations (file paths, permissions).
 *
 * This is a stub interface for the ConversationFacade port.
 * Platform-specific implementations will be added via expect/actual.
 *
 * Ported from: jami-client-android libjamiclient
 */
interface DeviceRuntimeService {

    /**
     * Get the path for storing conversation files.
     *
     * @param accountId The account ID
     * @param conversationId The conversation ID
     * @param name The file name
     * @return The full file path
     */
    fun getConversationPath(accountId: String, conversationId: String, name: String): String

    /**
     * Get a new path for a conversation file (with conflict resolution).
     *
     * @param accountId The account ID
     * @param conversationId The conversation ID
     * @param name The file name
     * @return The full file path with unique name if needed
     */
    fun getNewConversationPath(accountId: String, conversationId: String, name: String): String

    /**
     * Get the temporary file directory.
     */
    fun getTempPath(): String

    /**
     * Get the cache directory.
     */
    fun getCachePath(): String

    /**
     * Get the data directory.
     */
    fun getDataPath(): String

    /**
     * Check if storage permission is granted.
     */
    fun hasStoragePermission(): Boolean

    /**
     * Check if camera permission is granted.
     */
    fun hasCameraPermission(): Boolean

    /**
     * Check if microphone permission is granted.
     */
    fun hasMicrophonePermission(): Boolean

    /**
     * Check if a file exists.
     */
    fun fileExists(path: String): Boolean

    /**
     * Delete a file.
     */
    fun deleteFile(path: String): Boolean
}

/**
 * Stub implementation of DeviceRuntimeService for testing.
 */
class StubDeviceRuntimeService : DeviceRuntimeService {
    override fun getConversationPath(accountId: String, conversationId: String, name: String): String {
        return "/tmp/jami/$accountId/$conversationId/$name"
    }

    override fun getNewConversationPath(accountId: String, conversationId: String, name: String): String {
        return getConversationPath(accountId, conversationId, name)
    }

    override fun getTempPath(): String = "/tmp/jami"
    override fun getCachePath(): String = "/cache/jami"
    override fun getDataPath(): String = "/data/jami"
    override fun hasStoragePermission(): Boolean = true
    override fun hasCameraPermission(): Boolean = true
    override fun hasMicrophonePermission(): Boolean = true
    override fun fileExists(path: String): Boolean = false
    override fun deleteFile(path: String): Boolean = true
}
