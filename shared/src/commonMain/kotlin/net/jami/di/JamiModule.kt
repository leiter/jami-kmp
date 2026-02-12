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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import net.jami.services.*

/**
 * Common Koin module providing shared services.
 *
 * This module defines bindings for all cross-platform services.
 * Platform-specific services are provided by [platformModule].
 *
 * ## Usage
 *
 * ```kotlin
 * // In your application entry point
 * startKoin {
 *     modules(jamiModule, platformModule)
 * }
 *
 * // Then inject services
 * val accountService: AccountService by inject()
 * ```
 */
val jamiModule = module {
    // ==================== Coroutine Scope ====================

    /**
     * Application-wide coroutine scope for service operations.
     * Uses SupervisorJob so child coroutine failures don't cancel siblings.
     */
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // ==================== Daemon Bridge ====================

    /**
     * Platform-specific daemon bridge (JNI, cinterop, or REST).
     */
    single { DaemonBridge() }

    // ==================== Core Services ====================

    /**
     * Account management service.
     */
    single {
        AccountService(
            daemonBridge = get(),
            scope = get()
        )
    }

    /**
     * Call handling service.
     */
    single {
        CallService(
            daemonBridge = get(),
            accountService = get(),
            scope = get()
        )
    }

    /**
     * Contact management service.
     */
    single {
        ContactService(
            scope = get(),
            accountService = get(),
            daemonBridge = get()
        )
    }

    /**
     * Conversation and messaging service.
     */
    single {
        ConversationFacade(
            historyService = get(),
            callService = get(),
            accountService = get(),
            contactService = get(),
            notificationService = get(),
            hardwareService = get(),
            deviceRuntimeService = get(),
            preferencesService = get(),
            scope = get()
        )
    }

    // ==================== Daemon Callbacks ====================

    /**
     * Callback orchestrator that routes daemon events to services.
     */
    single {
        DaemonCallbacksImpl(
            accountService = get(),
            callService = get(),
            contactService = get(),
            conversationFacade = get(),
            scope = get()
        )
    }
}

/**
 * Platform-specific module providing services that vary by platform.
 *
 * Each platform (Android, iOS, macOS, Desktop, Web) provides its own
 * implementation of this module with appropriate service implementations.
 *
 * Services provided:
 * - [DeviceRuntimeService] - File paths, permissions
 * - [HardwareService] - Camera, audio hardware
 * - [NotificationService] - System notifications
 * - [HistoryService] - Database operations
 * - [Settings] - Preferences storage
 */
expect val platformModule: Module
