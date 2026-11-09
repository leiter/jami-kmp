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
package net.jami.ui.platform

/**
 * Whether this platform applies the app's own lock-screen visibility setting
 * (`NotificationVisibility`: private / public / secret) to its notifications.
 *
 * Only Android does: it maps the setting onto each notification's `setVisibility` (a838c8e).
 * iOS cannot — whether a notification's content shows on the lock screen is the system's
 * *Settings → Notifications → Show Previews*, per app, and no notification can override it.
 * Elsewhere there is no lock screen to speak of. Settings screens hide the row where this is
 * false, rather than offer a choice that changes nothing; the "show names in notifications"
 * setting (all platforms) is what keeps contact names off notifications there.
 */
expect val appliesLockScreenVisibility: Boolean
