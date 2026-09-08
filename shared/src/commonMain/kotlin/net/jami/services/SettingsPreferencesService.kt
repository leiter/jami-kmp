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
 * [PreferencesService] backed by the multiplatform [Settings] wrapper.
 *
 * Every target has a real `actual class Settings` (SharedPreferences on Android,
 * NSUserDefaults on iOS/macOS, java.util.prefs on desktop, localStorage on JS), so this
 * one implementation persists preferences everywhere. Platform Koin modules bind it
 * directly; [StubPreferencesService] remains for tests that want an in-memory map.
 */
class SettingsPreferencesService(private val settings: Settings) : PreferencesService {

    override fun getConversationPreferences(accountId: String, conversationUri: Uri): Map<String, String> {
        val json = settings.getString(conversationKey(accountId, conversationUri), "")
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
        settings.setString(conversationKey(accountId, conversationUri), Json.encodeToString(preferences))
    }

    override fun removeRequestPreferences(accountId: String, contactId: String) {
        settings.remove("req_${accountId}:${contactId}")
    }

    override fun getMaxFileAutoAccept(accountId: String): Int =
        settings.getInt("max_file_auto_accept_$accountId", DEFAULT_MAX_FILE_AUTO_ACCEPT)

    override fun isNotificationsEnabled(): Boolean =
        settings.getBoolean("notifications_enabled", true)

    override fun isCallNotificationsEnabled(): Boolean =
        settings.getBoolean("call_notifications_enabled", true)

    override fun isMessageNotificationsEnabled(): Boolean =
        settings.getBoolean("message_notifications_enabled", true)

    override fun getRingtonePath(): String? =
        settings.getString("ringtone_path", "").ifEmpty { null }

    override fun isDarkTheme(): Boolean =
        settings.getBoolean("dark_theme", false)

    private fun conversationKey(accountId: String, conversationUri: Uri) =
        "conv_${accountId}:${conversationUri.uri}"

    private companion object {
        const val DEFAULT_MAX_FILE_AUTO_ACCEPT = 20 * 1024 * 1024 // 20 MB
    }
}
