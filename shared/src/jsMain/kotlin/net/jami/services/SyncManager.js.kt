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

/**
 * Web/JS implementation of SyncManager.
 *
 * Web platform doesn't support background execution.
 * This is a no-op implementation.
 */
actual class SyncManager {

    actual fun startBackgroundSync() {
        Log.d(TAG, "startBackgroundSync (not supported on Web)")
    }

    actual fun stopBackgroundSync() {
        Log.d(TAG, "stopBackgroundSync (not supported on Web)")
    }

    actual fun startBackgroundSyncWithTimeout(timeoutMs: Long) {
        Log.d(TAG, "startBackgroundSyncWithTimeout: ${timeoutMs}ms (not supported on Web)")
    }

    actual val isBackgroundSyncActive: Boolean
        get() = false

    companion object {
        private const val TAG = "SyncManager"
    }
}
