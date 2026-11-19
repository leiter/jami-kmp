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

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.jami.testHardwareService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Construction order inside [AccountService].
 *
 * Its `init` block collects `hardwareService.connectivityState`, a `StateFlow`, which therefore
 * emits its current value as soon as the collector attaches; the handler calls
 * `setAccountsActive`, which touches `explicitlyDeactivatedAccounts`. That property used to be
 * declared ~300 lines *below* the `init` block, and Kotlin runs property initialisers and init
 * blocks in declaration order — so whenever the launched coroutine got to run before
 * construction finished, the set was still null:
 *
 *     NullPointerException: Cannot invoke "java.util.Set.clear()"
 *     because "this.explicitlyDeactivatedAccounts" is null
 *
 * A dispatcher that defers the launch past construction hides it, which is every production
 * path and most tests. It surfaced as four `CallServiceTest` failures in one full-suite run
 * and none in the three after it, i.e. as a flaky test rather than as the ordering bug it is.
 *
 * `Dispatchers.Unconfined` makes it deterministic: `launch` runs its body inline, so the
 * collector attaches and the flow emits before the constructor returns — the exact interleaving
 * the bug needs.
 */
class AccountServiceInitOrderTest {

    @Test
    fun `the connectivity collector can run during construction`() {
        // The failure never reaches the constructor: it happens inside the coroutine the init
        // block launches, so it goes to the scope's uncaught-exception handler. That is why it
        // showed up as `UncaughtExceptionsBeforeTest` in whichever test ran next rather than as
        // a failure where it was caused. The handler here is what makes it observable.
        val caught = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, t -> caught += t }
        // Unconfined, so the launch executes inline and the StateFlow's current value reaches
        // the collector before the constructor returns — the interleaving the bug needs.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + handler)
        try {
            val service = AccountService(
                StubDaemonBridge(),
                testHardwareService(),
                StubDeviceRuntimeService(),
                scope,
            )
            assertEquals(
                emptyList(),
                caught.map { "${it::class.simpleName}: ${it.message}" },
                "the init block's collector threw while the service was still being built",
            )
            // Proves the set was initialised rather than merely never touched.
            assertTrue(service.deactivatedAccounts.isEmpty())
        } finally {
            scope.cancel()
        }
    }
}
