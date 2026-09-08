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

import net.jami.services.IOSPushServiceManager
import net.jami.services.PushConfigNotifier

/**
 * Swift-facing entry points for push, mirroring how `IOSApplicationHelper` exposes the daemon
 * lifecycle. Swift sees these as `IOSPushHelperKt.<name>`.
 *
 * Everything below is a thin forwarder — the policy lives in [IOSPushServiceManager] so it stays
 * testable Kotlin and stays comparable with the Android side.
 */

/**
 * Call once from `didFinishLaunchingWithOptions`, after `doInitKoin()`.
 *
 * Named setupPush, not initPush: Kotlin/Native exports an `init*` function to Swift as
 * `doInit*`, so `initPush()` was unreachable as written — the Swift call site did not
 * compile. Matches setupKoin / setupNotificationDelegate elsewhere in this module.
 */
fun setupPush() {
    // Re-apply whenever the user changes the connectivity mode in app settings.
    PushConfigNotifier.onConfigChanged = { IOSPushServiceManager.applyCurrentMode() }
    IOSPushServiceManager.applyCurrentMode()
}

/** From `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`, hex-encoded. */
fun onApnsToken(token: String) = IOSPushServiceManager.onApnsTokenReceived(token)

/** From `pushRegistry(_:didUpdate:for:)` for `PKPushType.voIP`, hex-encoded. */
fun onVoipToken(token: String) = IOSPushServiceManager.onVoipTokenReceived(token)

/** From `application(_:didFailToRegisterForRemoteNotificationsWithError:)`. */
fun onPushRegistrationFailed(reason: String) =
    IOSPushServiceManager.onPushRegistrationFailed(reason)

/** From `application(_:didReceiveRemoteNotification:fetchCompletionHandler:)`. */
fun onPushReceived(payload: Map<String, String>) =
    IOSPushServiceManager.onPushReceived(payload)

/**
 * From `pushRegistry(_:didReceiveIncomingPushWith:for:completion:)`.
 *
 * Must be called *before* PushKit's completion handler runs — it reports the incoming call to
 * CallKit synchronously, which iOS 13+ requires on pain of terminating the app.
 */
fun onVoipPushReceived(payload: Map<String, String>) =
    IOSPushServiceManager.onVoipPushReceived(payload)
