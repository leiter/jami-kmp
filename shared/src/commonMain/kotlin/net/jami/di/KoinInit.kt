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

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * Initialize Koin with Jami modules.
 *
 * Call this once at application startup. Platform apps can provide
 * additional modules for app-specific dependencies.
 *
 * ## Android Usage
 *
 * ```kotlin
 * class JamiApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         initKoin {
 *             androidContext(this@JamiApplication)
 *         }
 *     }
 * }
 * ```
 *
 * ## iOS/macOS Usage (Swift)
 *
 * ```swift
 * @main
 * struct JamiApp: App {
 *     init() {
 *         KoinInitKt.doInitKoin()
 *     }
 * }
 * ```
 *
 * ## Desktop Usage
 *
 * ```kotlin
 * fun main() = application {
 *     initKoin()
 *     Window(onCloseRequest = ::exitApplication, title = "Jami") {
 *         App()
 *     }
 * }
 * ```
 *
 * ## Web Usage
 *
 * ```kotlin
 * fun main() {
 *     initKoin()
 *     renderComposable("root") {
 *         App()
 *     }
 * }
 * ```
 *
 * @param additionalModules Extra modules to include (e.g., ViewModels, UI dependencies)
 * @param appDeclaration Additional Koin configuration (e.g., androidContext)
 * @return The KoinApplication instance
 */
fun initKoin(
    additionalModules: List<Module> = emptyList(),
    appDeclaration: KoinApplication.() -> Unit = {}
): KoinApplication {
    return startKoin {
        appDeclaration()
        modules(jamiModule, platformModule)
        modules(additionalModules)
    }
}

/**
 * Initialize Koin with default configuration.
 *
 * Convenience function for platforms that don't need additional setup.
 * For iOS/macOS, this is exported as `doInitKoin()` for Swift interop.
 */
fun initKoin(): KoinApplication = initKoin(emptyList()) {}
