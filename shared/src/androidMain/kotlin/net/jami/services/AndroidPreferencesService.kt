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

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import net.jami.model.Uri

/**
 * Android implementation of PreferencesService using Settings wrapper.
 */
class AndroidPreferencesService(private val settings: Settings) : PreferencesService {

    override fun getConversationPreferences(accountId: String, conversationUri: Uri): Map<String, String> {
        val key = "conv_${accountId}:${conversationUri.uri}"
        val json = settings.getString(key, "")
        return if (json.isEmpty()) {
            emptyMap()
        } else {
            try {
                Json.decodeFromString<Map<String, String>>(json)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    override fun setConversationPreferences(
        accountId: String,
        conversationUri: Uri,
        preferences: Map<String, String>
    ) {
        val key = "conv_${accountId}:${conversationUri.uri}"
        val json = Json.encodeToString(preferences)
        settings.setString(key, json)
    }

    override fun removeRequestPreferences(accountId: String, contactId: String) {
        val key = "req_${accountId}:${contactId}"
        settings.remove(key)
    }

    override fun getMaxFileAutoAccept(accountId: String): Int {
        return settings.getInt("max_file_auto_accept_$accountId", 20 * 1024 * 1024) // 20MB default
    }

    override fun isNotificationsEnabled(): Boolean {
        return settings.getBoolean("notifications_enabled", true)
    }

    override fun isCallNotificationsEnabled(): Boolean {
        return settings.getBoolean("call_notifications_enabled", true)
    }

    override fun isMessageNotificationsEnabled(): Boolean {
        return settings.getBoolean("message_notifications_enabled", true)
    }

    override fun getRingtonePath(): String? {
        val path = settings.getString("ringtone_path", "")
        return if (path.isEmpty()) null else path
    }

    override fun isDarkTheme(): Boolean {
        return settings.getBoolean("dark_theme", false)
    }
}
