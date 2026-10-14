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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DaemonLifecycleServiceTest {

    private fun TestScope.serviceScope(): CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob())

    private fun TestScope.lifecycle(
        bridge: StubDaemonBridge
    ): DaemonLifecycleService = DaemonLifecycleService(
        daemonBridge = bridge,
        daemonCallbacks = NoOpDaemonCallbacks,
        scope = serviceScope(),
    )

    @Test
    fun ensureRunningStartsTheDaemonAndRunsTheStartupSequence() = runTest {
        val bridge = StubDaemonBridge()
        val service = lifecycle(bridge)
        var startupRuns = 0
        service.setStartupSequence { startupRuns++ }

        assertTrue(service.ensureRunning())

        assertTrue(bridge.isRunning())
        assertEquals(1, startupRuns)
        assertEquals(1, service.startCount)
        assertIs<DaemonLifecycleState.Running>(service.state.value)
    }

    /** A second call must not re-init a healthy daemon or re-run the startup sequence. */
    @Test
    fun ensureRunningIsANoOpWhenTheDaemonIsAlreadyUp() = runTest {
        val bridge = StubDaemonBridge()
        val service = lifecycle(bridge)
        var startupRuns = 0
        service.setStartupSequence { startupRuns++ }

        service.ensureRunning()
        service.ensureRunning()

        assertEquals(1, bridge.initCalls)
        assertEquals(1, startupRuns)
        assertEquals(1, service.startCount)
    }

    /**
     * The failure this finding is about: a start that fails used to be logged once and forgotten.
     */
    @Test
    fun aFailedStartIsReportedAndRetried() = runTest {
        val bridge = StubDaemonBridge()
        bridge.startSucceeds = false
        val service = lifecycle(bridge)

        assertFalse(service.ensureRunning())

        val failed = service.state.value
        assertIs<DaemonLifecycleState.Failed>(failed)
        assertEquals(1, failed.attempts)
        assertTrue(failed.reason.contains("start()"))

        // Backoff retries run on the service scope, on virtual time.
        advanceUntilIdle()
        assertEquals(DaemonLifecycleService.MAX_RETRIES, bridge.startCalls)
        assertIs<DaemonLifecycleState.Failed>(service.state.value)
    }

    /** Retrying stops rather than looping forever when the daemon simply cannot run. */
    @Test
    fun retriesAreBounded() = runTest {
        val bridge = StubDaemonBridge()
        bridge.initSucceeds = false
        val service = lifecycle(bridge)

        service.ensureRunning()
        advanceUntilIdle()

        assertEquals(DaemonLifecycleService.MAX_RETRIES, bridge.initCalls)
        val failed = service.state.value
        assertIs<DaemonLifecycleState.Failed>(failed)
        assertEquals(DaemonLifecycleService.MAX_RETRIES, failed.attempts)
    }

    /** A retry that succeeds clears the failure and wires the app up. */
    @Test
    fun aRetryThatSucceedsRecoversAndRunsTheStartupSequence() = runTest {
        val bridge = StubDaemonBridge()
        bridge.startSucceeds = false
        val service = lifecycle(bridge)
        var startupRuns = 0
        service.setStartupSequence { startupRuns++ }

        service.ensureRunning()
        bridge.startSucceeds = true
        advanceUntilIdle()

        assertTrue(bridge.isRunning())
        assertEquals(1, startupRuns)
        assertIs<DaemonLifecycleState.Running>(service.state.value)
    }

    /** A native library that throws must be handled like any other failed start. */
    @Test
    fun anExceptionDuringInitIsTreatedAsAFailedStart() = runTest {
        val bridge = StubDaemonBridge()
        bridge.initThrows = true
        val service = lifecycle(bridge)

        assertFalse(service.ensureRunning())

        val failed = service.state.value
        assertIs<DaemonLifecycleState.Failed>(failed)
        assertTrue(failed.reason.contains("native library"))
    }

    /** A startup sequence that throws must not make a good start look like a failure. */
    @Test
    fun aThrowingStartupSequenceLeavesTheDaemonRunning() = runTest {
        val bridge = StubDaemonBridge()
        val service = lifecycle(bridge)
        service.setStartupSequence { throw IllegalStateException("account load failed") }

        assertTrue(service.ensureRunning())
        assertIs<DaemonLifecycleState.Running>(service.state.value)
    }

    @Test
    fun connectivityReturningStartsADeadDaemon() = runTest {
        val bridge = StubDaemonBridge()
        bridge.startSucceeds = false
        val service = lifecycle(bridge)
        val connectivity = MutableStateFlow(false)
        service.observeConnectivity(connectivity)

        service.ensureRunning()
        advanceUntilIdle()
        assertFalse(bridge.isRunning())

        bridge.startSucceeds = true
        connectivity.value = true
        advanceUntilIdle()

        assertTrue(bridge.isRunning())
    }

    /** Connectivity events must not re-init a daemon that is already up. */
    @Test
    fun connectivityDoesNotRestartAHealthyDaemon() = runTest {
        val bridge = StubDaemonBridge()
        val service = lifecycle(bridge)
        val connectivity = MutableStateFlow(false)
        service.observeConnectivity(connectivity)
        service.ensureRunning()

        connectivity.value = true
        advanceUntilIdle()

        assertEquals(1, bridge.initCalls)
    }

    /** A deliberate stop must not be undone by a retry that was already scheduled. */
    @Test
    fun stopCancelsAPendingRetry() = runTest {
        val bridge = StubDaemonBridge()
        bridge.startSucceeds = false
        val service = lifecycle(bridge)

        service.ensureRunning()
        val attemptsBeforeStop = bridge.startCalls
        service.stop()
        bridge.startSucceeds = true
        advanceUntilIdle()

        assertEquals(attemptsBeforeStop, bridge.startCalls)
        assertFalse(bridge.isRunning())
        assertEquals(DaemonLifecycleState.Stopped, service.state.value)
    }
}
