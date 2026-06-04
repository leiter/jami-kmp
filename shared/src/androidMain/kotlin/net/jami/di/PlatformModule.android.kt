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
package net.jami.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import net.jami.database.DatabaseDriverFactory
import net.jami.database.JamiDatabase
import net.jami.services.*
import net.jami.services.AndroidPictureInPictureManager
import net.jami.services.CameraService
import net.jami.services.DaemonBridgeApi
import net.jami.services.PictureInPictureManager
import net.jami.services.expect.AudioRecorderService
import net.jami.services.expect.HardwareService

/**
 * Android platform module providing Android-specific service implementations.
 *
 * Requires Android Context to be set via `androidContext(context)` in Koin setup.
 *
 * ## Usage
 *
 * ```kotlin
 * // In Application.onCreate()
 * startKoin {
 *     androidContext(this@MyApplication)
 *     modules(jamiModule, platformModule)
 * }
 * ```
 */
actual val platformModule: Module = module {

    // ==================== Daemon Bridge ====================
    // Registered under both types: DaemonBridgeApi (used by services) and
    // DaemonBridge (used by JamiApplication for lifecycle management)

    single { DaemonBridge(androidContext()) }
    single<DaemonBridgeApi> { get<DaemonBridge>() }

    // ==================== Database ====================

    /**
     * SQLDelight database driver for Android (uses Android SQLite).
     */
    single {
        DatabaseDriverFactory(androidContext()).createDriver()
    }

    /**
     * SQLDelight database instance.
     */
    single {
        JamiDatabase(get())
    }

    // ==================== Platform Services ====================

    /**
     * Android device runtime service.
     * Provides file paths, permissions, and device info using Android Context.
     */
    single<DeviceRuntimeService> {
        AndroidDeviceRuntimeService(androidContext())
    }

    /**
     * Android camera service.
     * Provides Camera2 capture, hardware encoding, and screen sharing via MediaProjection.
     */
    single { CameraService(androidContext()) }

    /**
     * Android hardware service.
     * Provides audio management via AudioManager and delegates video capture to CameraService.
     */
    single { HardwareService(androidContext()) }
    single { AudioRecorderService(androidContext()) }

    /**
     * Exposes the same PictureInPictureManager instance under its concrete Android type
     * so that MainActivity can inject AndroidPictureInPictureManager directly for
     * Activity-lifecycle methods (attachActivity, detachActivity, onPipModeChanged).
     */
    single { get<PictureInPictureManager>() as AndroidPictureInPictureManager }

    /**
     * Android notification service.
     * Uses NotificationManager and NotificationCompat for system notifications.
     * Enforces NotificationSettings via NotificationGuard.
     */
    single<NotificationService> {
        AndroidNotificationService(
            context = androidContext(),
            settingsRepository = get(),
            notificationGuard = get()
        )
    }

    /**
     * History service using SQLDelight.
     */
    single<HistoryService> {
        SqlDelightHistoryService(get())
    }

    /**
     * Preferences service for conversation/app preferences.
     * Uses Android SharedPreferences via Settings wrapper.
     */
    single<PreferencesService> {
        AndroidPreferencesService(get())
    }

    /**
     * Android settings using SharedPreferences.
     */
    single {
        Settings(androidContext())
    }

    /**
     * Biometric authentication service.
     * Uses AndroidKeyStore and BiometricPrompt for secure biometric authentication.
     */
    single<BiometricService> {
        BiometricService(androidContext())
    }

    /**
     * Background sync manager.
     * Manages JamiSyncService foreground service for background conversation sync.
     */
    single {
        SyncManager(androidContext())
    }
}
