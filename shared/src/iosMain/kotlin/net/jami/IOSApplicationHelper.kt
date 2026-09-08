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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.jami.services.AccountService
import net.jami.services.DaemonBridgeApi
import net.jami.services.DaemonCallbacks
import net.jami.services.IOSNotificationDelegate
import net.jami.services.SyncManager
import net.jami.services.expect.HardwareService
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.UserNotifications.UNUserNotificationCenter

// iOS equivalent of JamiApplication.onCreate() / onTerminate() on Android.
// Called from Swift AppDelegate after initKoin().
private object JamiLifecycle : KoinComponent {
    private val daemonBridge: DaemonBridgeApi by inject()
    private val daemonCallbacks: DaemonCallbacks by inject()
    private val accountService: AccountService by inject()
    private val hardwareService: HardwareService by inject()
    private val syncManager: SyncManager by inject()

    fun start() {
        try {
            if (daemonBridge.init(daemonCallbacks)) {
                if (daemonBridge.start()) {
                    accountService.loadAccountsFromDaemon(isConnected = true)
                    CoroutineScope(Dispatchers.Default).launch {
                        hardwareService.initVideo()
                    }
                } else {
                    Log.e(TAG, "Failed to start Jami daemon")
                }
            } else {
                Log.e(TAG, "Failed to initialize Jami daemon")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during daemon initialization: $e")
        }
    }

    /**
     * Strong reference to the notification delegate.
     *
     * `UNUserNotificationCenter.delegate` is a weak property, so without holding it here the
     * delegate would be collected immediately and every notification action would silently
     * stop working.
     */
    private var notificationDelegate: IOSNotificationDelegate? = null

    fun setupNotificationDelegate() {
        val delegate = notificationDelegate ?: IOSNotificationDelegate().also { notificationDelegate = it }
        UNUserNotificationCenter.currentNotificationCenter().setDelegate(delegate)
        Log.d(TAG, "Notification delegate installed")
    }

    /**
     * Called when the app leaves the foreground.
     *
     * Requests the extra execution window iOS allows (~30s) so the daemon can finish
     * in-flight work rather than being suspended mid-operation. This is as far as the
     * platform permits without a registered BGTask.
     */
    fun enterBackground() {
        try {
            syncManager.startBackgroundSync()
        } catch (e: Exception) {
            Log.e(TAG, "Exception entering background: $e")
        }
    }

    /**
     * Called when the app returns to the foreground: ends the background window and
     * tells the daemon connectivity is available again, so accounts re-register
     * promptly instead of waiting for their own timers.
     */
    fun enterForeground() {
        try {
            syncManager.stopBackgroundSync()
            hardwareService.connectivityChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Exception entering foreground: $e")
        }
    }

    fun stop() {
        try {
            daemonBridge.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during daemon shutdown: $e")
        }
    }

    private const val TAG = "IOSApplicationHelper"
}

fun startJami() = JamiLifecycle.start()

/**
 * Installs the Kotlin [IOSNotificationDelegate] as the UNUserNotificationCenter delegate.
 *
 * Must be called from the Swift AppDelegate after `doInitKoin()`, since the delegate
 * resolves CallService / ConversationFacade / AccountService from Koin on first use.
 */
fun setupNotificationDelegate() = JamiLifecycle.setupNotificationDelegate()
fun stopJami() = JamiLifecycle.stop()

/** Called from the Swift AppDelegate when the app backgrounds. */
fun jamiDidEnterBackground() = JamiLifecycle.enterBackground()

/** Called from the Swift AppDelegate when the app returns to the foreground. */
fun jamiWillEnterForeground() = JamiLifecycle.enterForeground()
