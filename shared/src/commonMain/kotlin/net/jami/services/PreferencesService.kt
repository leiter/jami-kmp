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

import net.jami.model.Uri

/**
 * Service for managing user preferences.
 *
 * This is a stub interface for the ConversationFacade port.
 * Platform-specific implementations will be added via expect/actual.
 *
 * Ported from: jami-client-android libjamiclient
 */
interface PreferencesService {

    /**
     * Get conversation-specific preferences.
     *
     * @param accountId The account ID
     * @param conversationUri The conversation URI
     * @return Map of preference key-value pairs
     */
    fun getConversationPreferences(accountId: String, conversationUri: Uri): Map<String, String>

    /**
     * Set conversation-specific preferences.
     *
     * @param accountId The account ID
     * @param conversationUri The conversation URI
     * @param preferences Map of preference key-value pairs to set
     */
    fun setConversationPreferences(accountId: String, conversationUri: Uri, preferences: Map<String, String>)

    /**
     * Remove trust request preferences.
     *
     * @param accountId The account ID
     * @param contactId The contact ID (raw ring ID)
     */
    fun removeRequestPreferences(accountId: String, contactId: String)

    /**
     * Get the maximum file size for auto-accept.
     *
     * @param accountId The account ID
     * @return Maximum file size in bytes
     */
    fun getMaxFileAutoAccept(accountId: String): Int

    /**
     * Check if notifications are enabled globally.
     */
    fun isNotificationsEnabled(): Boolean

    /**
     * Check if call notifications are enabled.
     */
    fun isCallNotificationsEnabled(): Boolean

    /**
     * Check if message notifications are enabled.
     */
    fun isMessageNotificationsEnabled(): Boolean

    /**
     * Get the default ringtone path.
     */
    fun getRingtonePath(): String?

    /**
     * Check if dark theme is enabled.
     */
    fun isDarkTheme(): Boolean
}

/**
 * Stub implementation of PreferencesService for testing.
 */
class StubPreferencesService : PreferencesService {
    private val conversationPrefs = mutableMapOf<String, MutableMap<String, String>>()

    override fun getConversationPreferences(accountId: String, conversationUri: Uri): Map<String, String> {
        val key = "$accountId:${conversationUri.uri}"
        return conversationPrefs[key] ?: emptyMap()
    }

    override fun setConversationPreferences(accountId: String, conversationUri: Uri, preferences: Map<String, String>) {
        val key = "$accountId:${conversationUri.uri}"
        conversationPrefs.getOrPut(key) { mutableMapOf() }.putAll(preferences)
    }

    override fun removeRequestPreferences(accountId: String, contactId: String) {}

    override fun getMaxFileAutoAccept(accountId: String): Int = 20 * 1024 * 1024 // 20MB default

    override fun isNotificationsEnabled(): Boolean = true
    override fun isCallNotificationsEnabled(): Boolean = true
    override fun isMessageNotificationsEnabled(): Boolean = true
    override fun getRingtonePath(): String? = null
    override fun isDarkTheme(): Boolean = false
}
