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
 * macOS platform module providing macOS-specific service implementations.
 *
 * ## Usage
 *
 * ```swift
 * // In Swift AppDelegate
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

    // ==================== Database ====================

    /**
     * SQLDelight database driver for macOS (uses SQLite via native driver).
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
     * macOS device runtime service.
     * Provides file paths using NSFileManager and Foundation APIs.
     */
    single<DeviceRuntimeService> {
        MacOSDeviceRuntimeService()
    }

    /**
     * macOS hardware service.
     * Provides audio management via CoreAudio/AVFoundation.
     */
    single<HardwareService> {
        MacOSHardwareService()
    }

    /**
     * macOS notification service.
     * TODO: Implement with NSUserNotificationCenter.
     */
    single<NotificationService> {
        StubNotificationService()
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
     * macOS settings using NSUserDefaults.
     */
    single {
        Settings()
    }
}
