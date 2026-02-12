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
     * Android hardware service.
     * Provides audio management via AudioManager.
     */
    single<HardwareService> {
        AndroidHardwareService(androidContext())
    }

    /**
     * Android notification service.
     * TODO: Replace with full Android NotificationManager implementation.
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
     * TODO: Replace with Android SharedPreferences implementation.
     */
    single<PreferencesService> {
        StubPreferencesService()
    }

    /**
     * Android settings using SharedPreferences.
     */
    single {
        Settings(androidContext())
    }
}
