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
import net.jami.services.*

/**
 * Web (Kotlin/JS) platform module providing browser-specific service implementations.
 *
 * ## Usage
 *
 * ```kotlin
 * // In main()
 * startKoin {
 *     modules(jamiModule, platformModule)
 * }
 * ```
 *
 * Or from JavaScript:
 * ```javascript
 * JamiKoin.initKoin();
 * ```
 */
actual val platformModule: Module = module {

    // ==================== Platform Services ====================

    /**
     * Web device runtime service.
     * Provides virtual file system using IndexedDB or in-memory storage.
     */
    single<DeviceRuntimeService> {
        WebDeviceRuntimeService()
    }

    /**
     * Web hardware service.
     * Provides audio/video via WebRTC getUserMedia.
     */
    single<HardwareService> {
        WebHardwareService()
    }

    /**
     * Web notification service.
     * Uses Web Notifications API for browser notifications.
     */
    single<NotificationService> {
        WebNotificationService()
    }

    /**
     * In-memory history service for web.
     * Note: SQLDelight with sql.js could be used for persistence.
     */
    single<HistoryService> {
        StubHistoryService()
    }

    /**
     * Preferences service for conversation/app preferences.
     */
    single<PreferencesService> {
        StubPreferencesService()
    }

    /**
     * Web settings using localStorage.
     */
    single {
        Settings()
    }
}
