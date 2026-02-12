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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-agnostic settings storage interface.
 *
 * Platform implementations:
 * - Android: SharedPreferences
 * - iOS/macOS: NSUserDefaults
 * - Desktop: java.util.prefs.Preferences
 * - JS: localStorage
 */
expect class Settings {
    /**
     * Get a string value.
     */
    fun getString(key: String, defaultValue: String = ""): String

    /**
     * Set a string value.
     */
    fun setString(key: String, value: String)

    /**
     * Get an integer value.
     */
    fun getInt(key: String, defaultValue: Int = 0): Int

    /**
     * Set an integer value.
     */
    fun setInt(key: String, value: Int)

    /**
     * Get a long value.
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long

    /**
     * Set a long value.
     */
    fun setLong(key: String, value: Long)

    /**
     * Get a boolean value.
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    /**
     * Set a boolean value.
     */
    fun setBoolean(key: String, value: Boolean)

    /**
     * Get a float value.
     */
    fun getFloat(key: String, defaultValue: Float = 0f): Float

    /**
     * Set a float value.
     */
    fun setFloat(key: String, value: Float)

    /**
     * Check if a key exists.
     */
    fun contains(key: String): Boolean

    /**
     * Remove a key.
     */
    fun remove(key: String)

    /**
     * Clear all settings.
     */
    fun clear()

    /**
     * Get all keys.
     */
    fun getAllKeys(): Set<String>
}

/**
 * Common settings keys used across Jami.
 */
object SettingsKey {
    // General
    const val DARK_THEME = "dark_theme"
    const val LANGUAGE = "language"

    // Notifications
    const val NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val CALL_NOTIFICATIONS = "call_notifications"
    const val MESSAGE_NOTIFICATIONS = "message_notifications"
    const val NOTIFICATION_SOUND = "notification_sound"
    const val VIBRATE_ON_NOTIFICATION = "vibrate_on_notification"

    // Media
    const val VIDEO_ENABLED = "video_enabled"
    const val HARDWARE_ACCELERATION = "hardware_acceleration"
    const val RINGTONE_PATH = "ringtone_path"

    // Privacy
    const val BLOCK_UNKNOWN_CONTACTS = "block_unknown_contacts"
    const val TYPING_INDICATOR = "typing_indicator"
    const val READ_RECEIPTS = "read_receipts"

    // File Transfer
    const val MAX_FILE_AUTO_ACCEPT = "max_file_auto_accept"
    const val AUTO_DOWNLOAD_MOBILE = "auto_download_mobile"
    const val AUTO_DOWNLOAD_WIFI = "auto_download_wifi"

    // Account-specific prefix
    fun accountKey(accountId: String, key: String): String = "account_${accountId}_$key"

    // Conversation-specific prefix
    fun conversationKey(accountId: String, conversationId: String, key: String): String =
        "conversation_${accountId}_${conversationId}_$key"
}

/**
 * User settings with reactive updates.
 */
class UserSettings(private val settings: Settings) {
    private val _darkTheme = MutableStateFlow(settings.getBoolean(SettingsKey.DARK_THEME, false))
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(settings.getBoolean(SettingsKey.NOTIFICATIONS_ENABLED, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _videoEnabled = MutableStateFlow(settings.getBoolean(SettingsKey.VIDEO_ENABLED, true))
    val videoEnabled: StateFlow<Boolean> = _videoEnabled.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        settings.setBoolean(SettingsKey.DARK_THEME, enabled)
        _darkTheme.value = enabled
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        settings.setBoolean(SettingsKey.NOTIFICATIONS_ENABLED, enabled)
        _notificationsEnabled.value = enabled
    }

    fun setVideoEnabled(enabled: Boolean) {
        settings.setBoolean(SettingsKey.VIDEO_ENABLED, enabled)
        _videoEnabled.value = enabled
    }

    fun isCallNotificationsEnabled(): Boolean =
        settings.getBoolean(SettingsKey.CALL_NOTIFICATIONS, true)

    fun setCallNotificationsEnabled(enabled: Boolean) {
        settings.setBoolean(SettingsKey.CALL_NOTIFICATIONS, enabled)
    }

    fun isMessageNotificationsEnabled(): Boolean =
        settings.getBoolean(SettingsKey.MESSAGE_NOTIFICATIONS, true)

    fun setMessageNotificationsEnabled(enabled: Boolean) {
        settings.setBoolean(SettingsKey.MESSAGE_NOTIFICATIONS, enabled)
    }

    fun getMaxFileAutoAccept(): Int =
        settings.getInt(SettingsKey.MAX_FILE_AUTO_ACCEPT, 20 * 1024 * 1024) // 20MB default

    fun setMaxFileAutoAccept(bytes: Int) {
        settings.setInt(SettingsKey.MAX_FILE_AUTO_ACCEPT, bytes)
    }

    fun getRingtonePath(): String? {
        val path = settings.getString(SettingsKey.RINGTONE_PATH, "")
        return path.ifEmpty { null }
    }

    fun setRingtonePath(path: String?) {
        settings.setString(SettingsKey.RINGTONE_PATH, path ?: "")
    }

    fun isTypingIndicatorEnabled(): Boolean =
        settings.getBoolean(SettingsKey.TYPING_INDICATOR, true)

    fun setTypingIndicatorEnabled(enabled: Boolean) {
        settings.setBoolean(SettingsKey.TYPING_INDICATOR, enabled)
    }

    fun isReadReceiptsEnabled(): Boolean =
        settings.getBoolean(SettingsKey.READ_RECEIPTS, true)

    fun setReadReceiptsEnabled(enabled: Boolean) {
        settings.setBoolean(SettingsKey.READ_RECEIPTS, enabled)
    }

    fun isBlockUnknownContactsEnabled(): Boolean =
        settings.getBoolean(SettingsKey.BLOCK_UNKNOWN_CONTACTS, false)

    fun setBlockUnknownContactsEnabled(enabled: Boolean) {
        settings.setBoolean(SettingsKey.BLOCK_UNKNOWN_CONTACTS, enabled)
    }
}
