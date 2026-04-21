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
 * Platform-specific manager for background synchronization.
 *
 * Keeps the daemon alive and syncing when the app is backgrounded.
 * Implementation varies by platform:
 * - Android: Foreground service with notification (can run indefinitely)
 * - iOS: Background tasks (limited to ~30 seconds by platform)
 * - Desktop: No-op (apps typically stay running)
 *
 * Reference: jami-android-client SyncService.kt
 */
expect class SyncManager {
    /**
     * Start background synchronization.
     * Should be called when app goes to background if an account is active.
     */
    fun startBackgroundSync()

    /**
     * Stop background synchronization.
     * Should be called when app comes to foreground (daemon already alive in app process).
     */
    fun stopBackgroundSync()

    /**
     * Start background sync with automatic timeout.
     *
     * @param timeoutMs Duration in milliseconds before sync auto-stops. Default: 2 hours.
     */
    fun startBackgroundSyncWithTimeout(timeoutMs: Long = 7200000L)

    /**
     * Check if background sync is currently active.
     */
    val isBackgroundSyncActive: Boolean
}
