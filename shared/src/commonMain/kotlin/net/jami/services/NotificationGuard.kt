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
     * Enforces: enabled, callNotifications.
     *
     * Quiet hours deliberately do **not** suppress a call notification. They used to, which
     * meant an incoming call during quiet hours produced no notification at all on either
     * platform — the user could not receive calls during that window, and nothing told them
     * why. Quiet hours now silence the call instead: see [shouldSilenceCallNotification].
     * Blocking a call outright is the job of the system's own Do Not Disturb, which users
     * already understand and which the calls channel is free to be excluded from.
     */
    fun shouldShowCallNotification(): Boolean {
        val settings = settingsRepository.notificationSettings.value
        return settings.enabled &&
               settings.callNotifications
    }

    /**
     * Whether a call notification should be posted without sound or vibration.
     *
     * True during quiet hours. The notification itself is still shown — on Android via a
     * sound-free channel, since from API 26 the channel owns the sound and no builder call
     * can override it — so the user can still see and answer the call.
     */
    fun shouldSilenceCallNotification(): Boolean =
        isInQuietHours(settingsRepository.notificationSettings.value)

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
     * Whether a notification may name the contact and quote the message.
     *
     * False means the platform services post a generic title and no body: the user is told
     * something arrived, not what or from whom. Distinct from the lock-screen visibility
     * setting, which only governs what a locked phone shows.
     */
    fun shouldShowNames(): Boolean =
        settingsRepository.privacySettings.value.showNamesInNotifications

    /**
     * Check if current time falls within quiet hours.
     * Handles midnight wraparound (e.g., 23:00 to 07:00).
     *
     * @param settings NotificationSettings containing quiet hours config
     * @return true if currently in quiet hours period
     */
    private fun isInQuietHours(settings: NotificationSettings): Boolean =
        QuietHours.isActiveNow(settings.quietHoursEnabled, settings.quietHoursStart, settings.quietHoursEnd)

    /**
     * What the sound settings add up to: off, the platform default, or a chosen URI.
     *
     * Prefer this to [getSoundUri], which cannot express "off" — see [NotificationSound].
     */
    fun notificationSound(): NotificationSound {
        val settings = settingsRepository.notificationSettings.value
        return when {
            !settings.soundEnabled -> NotificationSound.Silent
            settings.soundUri.isNotEmpty() -> NotificationSound.Custom(settings.soundUri)
            else -> NotificationSound.Default
        }
    }

    /**
     * Get notification sound URI.
     * Returns custom sound if set, null for system default.
     *
     * **Ambiguous — use [notificationSound] instead.** `null` here means both "the user turned
     * sound off" and "no custom sound chosen", which are opposite instructions to a platform
     * that reads a null sound as *play the default*.
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

/**
 * The quiet-hours window as a pure rule, so it can be asked without a [SettingsRepository].
 *
 * iOS needs that: a call woken by a VoIP push must be reported to CallKit before the daemon
 * has loaded any account, so it reads a copy of the window from `NSUserDefaults` rather than
 * the repository (see `IOSCallRinging`).
 */
object QuietHours {

    /**
     * Whether [minuteOfDay] (0 until 1440) falls inside the window from [start] to [end], both
     * minutes of the day. Handles midnight wraparound (e.g. 23:00 to 07:00); [end] itself is
     * outside the window.
     */
    fun isActive(enabled: Boolean, start: Int, end: Int, minuteOfDay: Int): Boolean {
        if (!enabled) return false
        return if (start < end) {
            // Normal range (e.g., 08:00 to 22:00)
            minuteOfDay in start until end
        } else {
            // Wraps midnight (e.g., 23:00 to 07:00)
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    /** [isActive] for the current local time. */
    fun isActiveNow(enabled: Boolean, start: Int, end: Int): Boolean {
        if (!enabled) return false
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return isActive(enabled, start, end, now.hour * 60 + now.minute)
    }
}
