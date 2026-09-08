package net.jami.android.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import net.jami.model.settings.ConnectivityMode
import net.jami.services.AccountService
import net.jami.services.NotificationService
import net.jami.services.PushConfigNotifier
import net.jami.ui.platform.LocalPrefKeys
import net.jami.ui.platform.LocalPrefs
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Owns the push-notification token and the decision of whether to hand it to the daemon.
 *
 * The daemon side of push has existed for a while — `AccountService.setPushNotificationConfig` /
 * `pushNotificationReceived` forward straight to libjami — but nothing ever produced a token.
 * This is that producer, plus the policy layer around it.
 *
 * Policy: push is only armed when the user picked [ConnectivityMode.GOOGLE_SERVICES] in
 * app settings. In every other mode the token is cleared in the daemon, which falls back to
 * keeping a DHT connection open from the persistent daemon service.
 *
 * Registration is idempotent — [applyCurrentMode] can be called whenever the inputs change
 * (token arrives, accounts finish loading, user flips the setting) and only pushes to the
 * daemon when something actually differs.
 */
object PushServiceManager : KoinComponent {

    private val accountService: AccountService by inject()
    private val notificationService: NotificationService by inject()

    /** Last token FCM handed us, or null if we never got one. */
    @Volatile
    var token: String? = null
        private set

    /** What we last handed the daemon, so repeated [applyCurrentMode] calls are cheap. */
    @Volatile
    private var appliedToken: String? = null

    /** True once Firebase initialised successfully (i.e. a google-services.json was present). */
    @Volatile
    var isAvailable: Boolean = false
        private set

    private val isPushSelected: Boolean
        get() = connectivityMode() == ConnectivityMode.GOOGLE_SERVICES

    /**
     * Initialise Firebase and request the current token. Safe to call when Firebase is not
     * configured — [FirebaseApp.initializeApp] returns null and push stays disabled.
     */
    fun initialize(context: Context) {
        // Re-apply whenever the user changes the connectivity mode in app settings.
        PushConfigNotifier.onConfigChanged = { applyCurrentMode() }

        val app = try {
            FirebaseApp.initializeApp(context)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialisation failed", e)
            null
        }
        if (app == null) {
            Log.i(TAG, "No Firebase configuration — push disabled, relying on the daemon service")
            isAvailable = false
            return
        }
        isAvailable = true

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { fcmToken ->
                Log.i(TAG, "Obtained FCM token")
                onTokenAvailable(fcmToken)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Could not obtain FCM token", e)
            }
    }

    /** Called on first fetch and from `onNewToken` when FCM rotates the token. */
    fun onTokenAvailable(fcmToken: String?) {
        token = fcmToken?.takeIf { it.isNotEmpty() }
        applyCurrentMode()
    }

    /**
     * Reconcile the daemon's push configuration with the current token and connectivity mode.
     *
     * Call after accounts load and whenever the connectivity setting changes — enabling push
     * also enables the DHT proxy, because a push wake-up is useless if the account is not
     * reachable through a proxy that can send it.
     */
    fun applyCurrentMode() {
        val desired = if (isPushSelected) token else null

        if (desired == appliedToken) return
        appliedToken = desired

        if (desired != null) {
            Log.i(TAG, "Registering push token with the daemon")
            accountService.setPushNotificationConfig(
                token = desired,
                topic = "",
                platform = PUSH_PLATFORM
            )
            accountService.setProxyEnabled(true)
        } else {
            Log.i(TAG, "Clearing push token (mode=${connectivityMode()}, token=${token != null})")
            accountService.setPushNotificationToken("")
        }
    }

    /**
     * Feed a received push into the daemon, then let the notification service surface whatever
     * the daemon decodes out of it (a call, a message, a sync).
     */
    fun onPushReceived(from: String, data: Map<String, String>) {
        accountService.pushNotificationReceived(from, data)
        notificationService.processPush()
    }

    private fun connectivityMode(): ConnectivityMode = ConnectivityMode.entries.getOrElse(
        LocalPrefs.getInt(LocalPrefKeys.CONNECTIVITY_MODE, ConnectivityMode.LOCAL_NODE.ordinal)
    ) { ConnectivityMode.LOCAL_NODE }

    private const val PUSH_PLATFORM = "android"
    private const val TAG = "PushServiceManager"
}
