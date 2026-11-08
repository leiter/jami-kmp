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
 * When an account's `Account.ringtonePath` has to be rewritten.
 *
 * The daemon does not ship a usable default for this app. Its compiled-in fallback is the
 * *official* client's data directory — on a device this app was seen asking for
 * `/data/data/cx.ring/files/ringtones/default.opus`, a package that need not even be
 * installed — so unless the client sets the path, no Jami ringtone can ever play. The failure
 * is quiet: the daemon logs "is not a valid file" at account load and the call simply rings
 * with whatever the notification channel provides, so it looks like it works.
 *
 * Pure string/predicate logic, kept in common code so it can be tested without a device; the
 * copying and the daemon write live in the platform layer.
 */
object RingtonePolicy {

    /** The file name the bundled ringtones are reached by. */
    const val DEFAULT_RINGTONE_FILE = "default.opus"

    /** Directory, relative to the app's private files directory, holding bundled ringtones. */
    const val RINGTONE_DIR = "ringtones"

    /**
     * Whether [configuredPath] needs replacing with the bundled default.
     *
     * True when nothing is configured, and also when the configured file is absent: the stale
     * `cx.ring` default is a path that exists in the config of every account created so far,
     * so "already set" is not good enough a test — it has to resolve to a real file.
     *
     * @param fileExists resolves a path against the real filesystem; injected to keep this
     *   testable.
     */
    fun needsDefaultRingtone(configuredPath: String?, fileExists: (String) -> Boolean): Boolean =
        configuredPath.isNullOrBlank() || !fileExists(configuredPath)
}
