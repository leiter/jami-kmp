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
 * Seam letting shared settings code tell the platform that the push configuration is stale.
 *
 * The push token lives in platform code (FCM on Android, APNs on iOS) and cannot be reached from
 * `commonMain`, while the connectivity-mode setting that decides whether to use it lives in a
 * shared ViewModel. Rather than pushing a DI-registered interface through all five platform
 * modules for a single callback, the platform registers itself here at startup and shared code
 * signals a re-apply.
 *
 * A platform that has no push support simply never registers, and [notifyConfigChanged] is a
 * no-op there.
 */
object PushConfigNotifier {

    /** Set by platform startup code; invoked whenever push-relevant settings change. */
    var onConfigChanged: (() -> Unit)? = null

    /** Ask the platform to reconcile its push registration with the current settings. */
    fun notifyConfigChanged() {
        onConfigChanged?.invoke()
    }
}
