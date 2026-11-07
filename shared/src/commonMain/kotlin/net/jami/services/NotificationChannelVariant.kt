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
 * What the user's notification-sound settings add up to, as three cases rather than a nullable
 * string.
 *
 * `getSoundUri()` returned `null` both for "the user turned sound off" and for "sound is on,
 * with no custom sound chosen" — and on Android a `null` sound means *play the default*, so the
 * first case played a sound. The two cases are separated here.
 */
sealed interface NotificationSound {
    /** The user turned notification sound off. Post silently. */
    data object Silent : NotificationSound

    /** Sound is on with no custom sound chosen: the platform default. */
    data object Default : NotificationSound

    /** Sound is on with [uri] chosen by the user. */
    data class Custom(val uri: String) : NotificationSound
}

/**
 * Derives notification-channel ids that encode the settings the channel was created with.
 *
 * Android settles a channel's sound and vibration at creation time and ignores every later
 * attempt to change them — including the `Notification.Builder` calls, from API 26, and
 * including delete-then-recreate, because a channel recreated under an id it has held before
 * comes back with its *old* settings (that is how the platform preserves a user's own edits).
 * The only way a settings change can take effect is a channel id the device has not seen.
 *
 * So each base channel has a family of variant ids, one per combination of settings, and the
 * service posts to the variant that matches the settings now. [isVariantOf] lets it delete the
 * stale siblings, so the system settings screen keeps showing one entry per base channel rather
 * than a growing pile.
 *
 * Android-specific in purpose, but pure string work, so it lives in common code where it can be
 * tested without a device.
 */
object NotificationChannelIds {

    /** Separates a base channel id from its settings suffix. Never appears in a base id. */
    private const val SEPARATOR = "__"

    /**
     * The channel id for [base] under the given settings. Stable: the same settings always
     * produce the same id, so a user who turns a setting off and on again lands back on the
     * channel they had, with any edits they made to it in system settings intact.
     */
    fun variant(base: String, sound: NotificationSound, vibrate: Boolean): String =
        "$base$SEPARATOR${soundTag(sound)}_v${if (vibrate) 1 else 0}"

    /** True when [id] is one of [base]'s variants — including, deliberately, [base] itself. */
    fun isVariantOf(base: String, id: String): Boolean =
        id == base || id.startsWith("$base$SEPARATOR")

    private fun soundTag(sound: NotificationSound): String = when (sound) {
        NotificationSound.Silent -> "soff"
        NotificationSound.Default -> "sdef"
        // Hashed rather than embedded: a content:// ringtone URI is long, and a channel id is
        // persisted per device forever. Collisions only mean two different custom sounds share
        // a channel, and the hash is taken over the whole URI, so that is vanishingly unlikely.
        is NotificationSound.Custom -> "s" + sound.uri.hashCode().toUInt().toString(16)
    }
}
