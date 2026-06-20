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

import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import net.jami.utils.Log

/**
 * Holds the Koin instance produced by [initKoin].
 *
 * On Kotlin/Native we have observed `KoinPlatform.getKoin()` (the global default context)
 * returning a *different*, empty Koin instance than the one [startKoin] populated, which
 * caused NoDefinitionFoundException at startup. Resolving from this explicitly-captured
 * instance — which is the exact KoinApplication.koin that modules were loaded into — avoids
 * any dependence on Koin's global context. This holder is single-instance because it lives
 * in our own code, compiled once.
 */
object JamiKoinHolder {
    var koin: Koin? = null
}

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
/** Named crash markers — surface in the (message-less) TestFlight crash backtrace. */
private fun jamiCrash_InitEmptyRegistry(): Nothing =
    error("JAMI_KOIN_INIT startKoin completed but registry is EMPTY")

private fun jamiCrash_InitContextDiverged(): Nothing =
    error("JAMI_KOIN_INIT global context koin != startKoin koin")

@OptIn(KoinInternalApi::class)
fun initKoin(
    additionalModules: List<Module> = emptyList(),
    appDeclaration: KoinApplication.() -> Unit = {}
): KoinApplication {
    Log.i("JAMI_KOIN_INIT", "startKoin: begin (additionalModules=${additionalModules.size})")
    val app = startKoin {
        appDeclaration()
        modules(jamiModule, platformModule)
        modules(additionalModules)
    }
    val koin = app.koin
    JamiKoinHolder.koin = koin
    val registrySize = koin.instanceRegistry.instances.size
    val globalKoin = org.koin.mp.KoinPlatform.getKoinOrNull()
    val globalSize = globalKoin?.instanceRegistry?.instances?.size
    Log.i(
        "JAMI_KOIN_INIT",
        "startKoin: done appKoin=${koin.hashCode()} registrySize=$registrySize " +
            "globalKoin=${globalKoin?.hashCode()} globalSize=$globalSize same=${globalKoin === koin}"
    )
    // Assert the populated instance is also the one the global context returns. If either
    // of these fires, the failure is proven at init time (not at the later UI lookup).
    if (registrySize == 0) jamiCrash_InitEmptyRegistry()
    if (globalKoin !== koin) jamiCrash_InitContextDiverged()
    return app
}

/**
 * Initialize Koin with default configuration.
 *
 * Convenience function for platforms that don't need additional setup.
 * For iOS/macOS, this is exported as `doInitKoin()` for Swift interop.
 *
 * NOTE: intentionally NOT annotated @Throws — we do not want Swift to be able to
 * swallow an init failure. Any exception propagates as an unhandled Kotlin exception
 * so the crash report carries the original throwing stack.
 */
fun initKoin(): KoinApplication = initKoin(emptyList()) {}
