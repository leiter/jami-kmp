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

import net.jami.model.settings.ConnectivityMode
import net.jami.ui.platform.LocalPrefKeys
import net.jami.ui.platform.LocalPrefs
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSBundle
import kotlin.concurrent.Volatile

/**
 * iOS counterpart of the Android `PushServiceManager`: owns the push tokens and decides whether
 * to hand them to the daemon.
 *
 * Two tokens exist on iOS and they are not interchangeable:
 *
 * - the **APNs** token from `registerForRemoteNotifications`, which carries message and sync
 *   pushes into the app or its notification extension;
 * - the **PushKit VoIP** token from `PKPushRegistry`, which is the only channel allowed to wake a
 *   suspended app for an incoming call.
 *
 * The daemon holds exactly one token, so [applyCurrentMode] prefers the VoIP one when it is
 * available — waking for calls is the entire reason push exists in a P2P client, and a missed
 * call is far more visible than a delayed message. See `doc/push-notifications.md` for the
 * consequence: a proxy that sends *message* pushes must be configured for the VoIP topic too.
 */
object IOSPushServiceManager : KoinComponent {

    private val accountService: AccountService by inject()
    private val callKitManager: CallKitManagerApi by inject()

    @Volatile
    var apnsToken: String? = null
        private set

    @Volatile
    var voipToken: String? = null
        private set

    /** What was last handed to the daemon, so repeated [applyCurrentMode] calls are cheap. */
    @Volatile
    private var appliedToken: String? = null

    /**
     * Unlike Android there is no Firebase-versus-nothing choice here — APNs is the only push
     * transport iOS offers, so every mode except an explicitly local node uses it.
     */
    private val isPushSelected: Boolean
        get() = connectivityMode() != ConnectivityMode.LOCAL_NODE

    /** APNs topic is the bundle identifier; the proxy needs it to address the push. */
    private val pushTopic: String
        get() = NSBundle.mainBundle.bundleIdentifier ?: ""

    fun onApnsTokenReceived(token: String) {
        Log.d(TAG, "APNs token received")
        apnsToken = token.takeIf { it.isNotEmpty() }
        applyCurrentMode()
    }

    fun onVoipTokenReceived(token: String) {
        Log.d(TAG, "PushKit VoIP token received")
        voipToken = token.takeIf { it.isNotEmpty() }
        applyCurrentMode()
    }

    fun onPushRegistrationFailed(reason: String) {
        Log.e(TAG, "Remote notification registration failed: $reason")
        apnsToken = null
        applyCurrentMode()
    }

    /**
     * Reconcile the daemon's push configuration with the current tokens and connectivity mode.
     * Idempotent — safe to call whenever any input changes.
     */
    fun applyCurrentMode() {
        val desired = if (isPushSelected) (voipToken ?: apnsToken) else null

        if (desired == appliedToken) return
        appliedToken = desired

        if (desired != null) {
            Log.i(TAG, "Registering push token with the daemon (voip=${voipToken != null})")
            accountService.setPushNotificationConfig(
                token = desired,
                topic = pushTopic,
                platform = PUSH_PLATFORM
            )
            // A push wake-up is only useful if the account is reachable through a proxy that can
            // trigger one; this also re-pushes account details so the DHT picks up the new token.
            accountService.setProxyEnabled(true)
        } else {
            Log.i(TAG, "Clearing push token (mode=${connectivityMode()})")
            accountService.setPushNotificationToken("")
        }
    }

    /** Feed a standard (non-VoIP) push into the daemon. */
    fun onPushReceived(payload: Map<String, String>) {
        accountService.pushNotificationReceived("", payload)
    }

    /**
     * Handle a PushKit VoIP push.
     *
     * Order matters and is not negotiable: iOS 13+ kills the app if the push does not produce a
     * `reportNewIncomingCall` before PushKit's completion handler returns, and the daemon needs
     * seconds to reconnect. So CallKit is shown first from the payload's own fields, and the
     * daemon call adopts that placeholder when it eventually arrives.
     */
    fun onVoipPushReceived(payload: Map<String, String>) {
        val peerId = payload["peerId"].orEmpty()
        val displayName = payload["displayName"].orEmpty()
        val hasVideo = payload["hasVideo"]?.lowercase() != "false"

        callKitManager.reportIncomingCallFromPush(peerId, displayName, hasVideo)
        accountService.pushNotificationReceived("", payload)
    }

    private fun connectivityMode(): ConnectivityMode = ConnectivityMode.entries.getOrElse(
        LocalPrefs.getInt(LocalPrefKeys.CONNECTIVITY_MODE, ConnectivityMode.LOCAL_NODE.ordinal)
    ) { ConnectivityMode.LOCAL_NODE }

    private const val PUSH_PLATFORM = "ios"
    private const val TAG = "IOSPushServiceManager"
}
