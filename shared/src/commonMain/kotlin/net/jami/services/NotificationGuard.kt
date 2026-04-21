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

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.jami.model.settings.NotificationSettings
import net.jami.repository.SettingsRepository

/**
 * Centralized notification settings enforcement logic.
 *
 * Guards all notification methods by checking NotificationSettings stored in
 * SettingsRepository. Enforces:
 * - Global enabled flag
 * - Per-notification-type flags (call, message, request)
 * - Quiet hours with midnight wraparound support
 * - Sound/vibration/LED preferences
 *
 * Mirrors: jami-android-client settings enforcement pattern
 */
class NotificationGuard(
    private val settingsRepository: SettingsRepository
) {
    /**
     * Check if call notifications should be shown.
     * Enforces: enabled, callNotifications, quiet hours.
     */
    fun shouldShowCallNotification(): Boolean {
        val settings = settingsRepository.notificationSettings.value
        return settings.enabled &&
               settings.callNotifications &&
               !isInQuietHours(settings)
    }

    /**
     * Check if message notifications should be shown.
     * Enforces: enabled, messageNotifications, quiet hours.
     */
    fun shouldShowMessageNotification(): Boolean {
        val settings = settingsRepository.notificationSettings.value
        return settings.enabled &&
               settings.messageNotifications &&
               !isInQuietHours(settings)
    }

    /**
     * Check if contact request notifications should be shown.
     * Enforces: enabled, contactRequestNotifications, quiet hours.
     */
    fun shouldShowRequestNotification(): Boolean {
        val settings = settingsRepository.notificationSettings.value
        return settings.enabled &&
               settings.contactRequestNotifications &&
               !isInQuietHours(settings)
    }

    /**
     * Check if current time falls within quiet hours.
     * Handles midnight wraparound (e.g., 23:00 to 07:00).
     *
     * @param settings NotificationSettings containing quiet hours config
     * @return true if currently in quiet hours period
     */
    private fun isInQuietHours(settings: NotificationSettings): Boolean {
        if (!settings.quietHoursEnabled) return false

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentMinutes = now.hour * 60 + now.minute
        val start = settings.quietHoursStart
        val end = settings.quietHoursEnd

        return if (start < end) {
            // Normal range (e.g., 08:00 to 22:00)
            currentMinutes in start until end
        } else {
            // Wraps midnight (e.g., 23:00 to 07:00)
            currentMinutes >= start || currentMinutes < end
        }
    }

    /**
     * Get notification sound URI.
     * Returns custom sound if set, null for system default.
     *
     * @return Sound URI string or null for default
     */
    fun getSoundUri(): String? {
        val settings = settingsRepository.notificationSettings.value
        return if (settings.soundEnabled && settings.soundUri.isNotEmpty()) {
            settings.soundUri
        } else null
    }

    /**
     * Check if vibration should be enabled.
     *
     * @return true if vibration is enabled in settings
     */
    fun shouldVibrate(): Boolean {
        return settingsRepository.notificationSettings.value.vibrationEnabled
    }

    /**
     * Get LED color (Android only).
     *
     * @return LED color value (0 = default)
     */
    fun getLedColor(): Int {
        return settingsRepository.notificationSettings.value.ledColor
    }
}
