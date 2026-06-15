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
import net.jami.services.expect.HardwareService
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// iOS equivalent of JamiApplication.onCreate() / onTerminate() on Android.
// Called from Swift AppDelegate after initKoin().
private object JamiLifecycle : KoinComponent {
    private val daemonBridge: DaemonBridgeApi by inject()
    private val daemonCallbacks: DaemonCallbacks by inject()
    private val accountService: AccountService by inject()
    private val hardwareService: HardwareService by inject()

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
fun stopJami() = JamiLifecycle.stop()
