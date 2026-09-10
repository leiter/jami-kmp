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
package net.jami

import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * iOS-wide constants shared between the app process and its app extensions
 * (notification service extension, share extension).
 *
 * The native `jami-client-ios` keeps the equivalent values in `Constants.swift`
 * (`appGroupIdentifier`, the `CFString` Darwin-notification names, and the
 * `containerURL(forSecurityApplicationGroupIdentifier:)`-derived paths). Anything an
 * extension and the app both need to agree on lives here so there is one source of truth.
 */
object IOSConstants {

    /**
     * App Group container identifier.
     *
     * Every process that must see the same libjami working directory — the app and each
     * extension — has to declare this exact string under `com.apple.security.application-groups`
     * in its `.entitlements`, and the App ID in the developer portal must have the App Groups
     * capability enabled with this group registered.
     *
     * Chosen to be independent of any single bundle id (the app is `net.jami.iosapp`, an
     * extension would be `net.jami.iosapp.NotificationService`) so it reads correctly from all
     * of them.
     */
    const val APP_GROUP_IDENTIFIER = "group.net.jami.kmp"

    /**
     * Darwin (`CFNotificationCenter`) notification names used for the app <-> extension
     * handshake. When the main app is running it must handle the push itself (it holds the
     * live daemon); the extension posts [DARWIN_QUERY_APP_ACTIVE] and waits briefly for
     * [DARWIN_APP_ACTIVE_RESPONSE] before deciding to spin up its own daemon instance.
     */
    const val DARWIN_QUERY_APP_ACTIVE = "net.jami.kmp.notificationExtension.queryAppActive"
    const val DARWIN_APP_ACTIVE_RESPONSE = "net.jami.kmp.jami.appActive"
    const val DARWIN_EXTENSION_IS_ACTIVE = "net.jami.kmp.notificationExtension.isActive"

    /** `UserDefaults(suiteName:)` key under which the extension leaves data for the app to sync. */
    const val SHARED_DEFAULTS_PENDING_NOTIFICATIONS = "pendingNotifications"

    /**
     * Absolute path to the libjami working directory inside the App Group container, or `null`
     * when the container is unavailable (entitlement missing, provisioning profile without the
     * App Groups capability, or a simulator without the group provisioned).
     *
     * Callers must fall back to the per-app sandbox path when this is `null` — see
     * `DaemonBridge.ios.kt`.
     */
    fun appGroupDataPath(): String? {
        val container: NSURL = NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_IDENTIFIER)
            ?: return null
        // Mirrors the native client's `<container>/Documents` split; the daemon is handed this
        // single root and creates `jami/` beneath it.
        return container.URLByAppendingPathComponent("jami")?.path
    }
}
