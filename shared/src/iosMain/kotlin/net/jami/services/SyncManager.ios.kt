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

import net.jami.utils.Log
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSinceNow
import platform.UIKit.UIApplication

/**
 * iOS implementation of SyncManager using background tasks.
 *
 * Note: iOS has strict background limitations (~30 seconds of execution time).
 * This is fundamentally different from Android's indefinite foreground service.
 *
 * Background task approach:
 * - Use beginBackgroundTaskWithName to request extra execution time
 * - Platform allows ~30 seconds before task is killed
 * - Cannot match Android's continuous sync capability
 *
 * Future enhancement: Could use BGTaskScheduler for periodic background refresh.
 */
actual class SyncManager {

    @kotlin.concurrent.Volatile
    private var backgroundTaskId: platform.UIKit.UIBackgroundTaskIdentifier? = null

    actual fun startBackgroundSync() {
        Log.d(TAG, "startBackgroundSync (iOS - limited to ~30 seconds)")

        val app = UIApplication.sharedApplication

        backgroundTaskId = app.beginBackgroundTaskWithName("JamiSync") {
            // Task expiration handler - called when time limit is reached
            Log.w(TAG, "Background sync task expired")
            this.stopBackgroundSync()
        }

        if (backgroundTaskId == platform.UIKit.UIBackgroundTaskInvalid) {
            Log.e(TAG, "Failed to start background task")
        } else {
            Log.d(TAG, "Background task started: $backgroundTaskId")
        }
    }

    actual fun stopBackgroundSync() {
        Log.d(TAG, "stopBackgroundSync")

        backgroundTaskId?.let { taskId ->
            if (taskId != platform.UIKit.UIBackgroundTaskInvalid) {
                UIApplication.sharedApplication.endBackgroundTask(taskId)
                Log.d(TAG, "Background task ended: $taskId")
            }
            backgroundTaskId = null
        }
    }

    actual fun startBackgroundSyncWithTimeout(timeoutMs: Long) {
        // iOS doesn't support custom timeouts - platform enforces ~30 seconds
        // Just start background sync and log the limitation
        Log.d(TAG, "startBackgroundSyncWithTimeout: ${timeoutMs}ms (ignored on iOS - platform limit ~30s)")
        startBackgroundSync()
    }

    actual val isBackgroundSyncActive: Boolean
        get() = backgroundTaskId != null && backgroundTaskId != platform.UIKit.UIBackgroundTaskInvalid

    companion object {
        private const val TAG = "SyncManager"
    }
}
