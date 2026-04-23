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
import net.jami.database.DatabaseDriverFactory
import net.jami.database.JamiDatabase
import net.jami.services.*

/**
 * iOS platform module providing iOS-specific service implementations.
 *
 * ## Usage
 *
 * ```swift
 * // In Swift AppDelegate or SwiftUI App
 * JamiKoinKt.doInitKoin()
 * ```
 *
 * Or from Kotlin:
 * ```kotlin
 * startKoin {
 *     modules(jamiModule, platformModule)
 * }
 * ```
 */
actual val platformModule: Module = module {

    // ==================== Daemon Bridge ====================

    single<DaemonBridgeApi> { DaemonBridge() }

    // ==================== Database ====================

    /**
     * SQLDelight database driver for iOS (uses SQLite via native driver).
     */
    single {
        DatabaseDriverFactory().createDriver()
    }

    /**
     * SQLDelight database instance.
     */
    single {
        JamiDatabase(get())
    }

    // ==================== Platform Services ====================

    /**
     * iOS device runtime service.
     * Provides file paths using NSFileManager and Foundation APIs.
     */
    single<DeviceRuntimeService> {
        IOSDeviceRuntimeService()
    }

    /**
     * iOS camera service.
     * Provides camera capture using AVCaptureSession.
     */
    single {
        IOSCameraService(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
            daemonBridge = get()
        )
    }

    /**
     * iOS hardware service.
     * Provides audio session management via AVFoundation and camera integration.
     */
    single<HardwareService> {
        IOSHardwareService(daemonBridge = get())
    }

    /**
     * iOS notification service.
     * Uses UNUserNotificationCenter for local notifications.
     * Enforces NotificationSettings via NotificationGuard.
     */
    single<NotificationService> {
        IOSNotificationService(
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
     */
    single<PreferencesService> {
        StubPreferencesService()
    }

    /**
     * iOS settings using NSUserDefaults.
     */
    single {
        Settings()
    }

    /**
     * Biometric authentication service.
     * Uses LocalAuthentication framework and Keychain for secure biometric authentication.
     */
    single<BiometricService> {
        BiometricService()
    }

    /**
     * Background sync manager.
     * Uses background tasks (limited to ~30 seconds by iOS).
     */
    single {
        SyncManager()
    }
}
