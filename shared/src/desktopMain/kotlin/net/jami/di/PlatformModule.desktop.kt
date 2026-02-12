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
 * Desktop (JVM) platform module providing desktop-specific service implementations.
 *
 * ## Usage
 *
 * ```kotlin
 * // In main()
 * startKoin {
 *     modules(jamiModule, platformModule)
 * }
 * ```
 */
actual val platformModule: Module = module {

    // ==================== Database ====================

    /**
     * SQLDelight database driver for Desktop (uses SQLite JDBC).
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
     * Desktop device runtime service.
     * Provides file paths using XDG on Linux, AppData on Windows, ~/Library on macOS.
     */
    single<DeviceRuntimeService> {
        DesktopDeviceRuntimeService()
    }

    /**
     * Desktop hardware service.
     * Provides audio management via Java Sound API.
     */
    single<HardwareService> {
        DesktopHardwareService()
    }

    /**
     * Desktop notification service.
     * TODO: Implement with system tray or native notifications.
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
     * Desktop settings using java.util.prefs.Preferences.
     */
    single {
        Settings()
    }
}
