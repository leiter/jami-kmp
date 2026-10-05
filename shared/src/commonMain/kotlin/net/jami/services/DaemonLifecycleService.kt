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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.jami.utils.Log
import net.jami.utils.currentTimeMillis

/** What the app believes the daemon is doing. */
sealed class DaemonLifecycleState {
    /** Not started yet, or stopped on purpose. */
    data object Stopped : DaemonLifecycleState()

    /** A start attempt is in flight. */
    data object Starting : DaemonLifecycleState()

    data class Running(val since: Long) : DaemonLifecycleState()

    /**
     * Every start attempt failed. [attempts] counts them since the last success; [reason] is the
     * last failure's description.
     */
    data class Failed(val attempts: Int, val reason: String) : DaemonLifecycleState()
}

/**
 * Keeps the daemon running, and makes it visible when it is not.
 *
 * Before this, `init()`/`start()` were called once at app start and a failure was logged and
 * forgotten: the app ran on with a dead daemon, nothing retried, and no screen could tell. The
 * post-start sequence (loading accounts, applying the push token, registering cameras) only ran
 * on that one successful path, so even a manual restart would have left the app half-wired —
 * which is why [setStartupSequence] exists and both the first start and every recovery run
 * through it.
 *
 * What this cannot do: an in-process daemon offers no liveness probe. `DaemonBridgeApi.isRunning`
 * reports whether *this app* initialised it, not whether the native side is healthy, and calling
 * into a fini'd daemon is unsafe rather than merely wrong. So recovery is driven by the moments
 * that matter — app start, connectivity returning, a sync wakeup — rather than by polling.
 */
class DaemonLifecycleService(
    private val daemonBridge: DaemonBridgeApi,
    private val daemonCallbacks: DaemonCallbacks,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<DaemonLifecycleState>(DaemonLifecycleState.Stopped)
    val state: StateFlow<DaemonLifecycleState> = _state.asStateFlow()

    /** Serialises start attempts so a connectivity change and a sync wakeup cannot race. */
    private val startMutex = Mutex()

    private var startupSequence: (suspend () -> Unit)? = null
    private var retryJob: Job? = null
    private var failedAttempts = 0

    /** Total successful starts, including recoveries. Surfaced for diagnostics. */
    var startCount: Int = 0
        private set

    /**
     * Work to run after every successful start: load accounts, apply the push token, register
     * video devices. Set once at app start, before the first [ensureRunning].
     */
    fun setStartupSequence(block: suspend () -> Unit) {
        startupSequence = block
    }

    /**
     * Start the daemon unless it is already running.
     *
     * Safe to call from anywhere, repeatedly: a no-op when the daemon is up, and never runs two
     * attempts at once. Returns whether the daemon is running when the call completes.
     */
    suspend fun ensureRunning(): Boolean = startMutex.withLock {
        if (daemonBridge.isRunning()) {
            // Keep the state honest if something else started it.
            if (_state.value !is DaemonLifecycleState.Running) {
                _state.value = DaemonLifecycleState.Running(currentTimeMillis())
            }
            return@withLock true
        }
        attemptStart()
    }

    /**
     * Give up on the daemon on purpose (app shutdown). Cancels any pending retry so a scheduled
     * attempt cannot resurrect it afterwards.
     */
    fun stop() {
        retryJob?.cancel()
        retryJob = null
        try {
            daemonBridge.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stop() failed: ${e.message}")
        }
        _state.value = DaemonLifecycleState.Stopped
    }

    /**
     * Re-check the daemon whenever connectivity returns. A daemon that failed to start while
     * offline is worth another attempt once there is a network, and this is also the point where
     * a restarted process would otherwise sit idle.
     */
    fun observeConnectivity(connectivity: Flow<Boolean>) {
        scope.launch {
            connectivity.collect { isConnected ->
                if (isConnected && !daemonBridge.isRunning()) {
                    Log.i(TAG, "connectivity returned with no daemon — attempting start")
                    ensureRunning()
                }
            }
        }
    }

    /** Must be called with [startMutex] held. */
    private suspend fun attemptStart(): Boolean {
        _state.value = DaemonLifecycleState.Starting
        val failure = try {
            when {
                !daemonBridge.init(daemonCallbacks) -> "init() returned false"
                !daemonBridge.start() -> "start() returned false"
                else -> null
            }
        } catch (e: Exception) {
            // A missing native library or a bad CA bundle throws rather than returning false.
            "${e::class.simpleName}: ${e.message}"
        }

        if (failure != null) {
            failedAttempts++
            Log.e(TAG, "daemon start failed (attempt $failedAttempts): $failure")
            _state.value = DaemonLifecycleState.Failed(failedAttempts, failure)
            scheduleRetry()
            return false
        }

        failedAttempts = 0
        startCount++
        _state.value = DaemonLifecycleState.Running(currentTimeMillis())
        Log.i(TAG, "daemon started (start #$startCount)")
        runStartupSequence()
        return true
    }

    /**
     * The post-start work is best-effort: if loading accounts throws, the daemon itself is still
     * up and the app is better off running than being told the start failed.
     */
    private suspend fun runStartupSequence() {
        val block = startupSequence ?: return
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "startup sequence failed after a successful start: ${e.message}")
        }
    }

    /**
     * Retry with exponential backoff, bounded: when the daemon cannot start at all (no native
     * library) retrying forever would just burn battery, so after [MAX_RETRIES] the state stays
     * Failed until something calls [ensureRunning] again — app foreground, connectivity, a sync
     * wakeup or the debug screen's restart action.
     */
    private fun scheduleRetry() {
        if (failedAttempts >= MAX_RETRIES) {
            Log.w(TAG, "giving up after $failedAttempts attempts; waiting for an external trigger")
            return
        }
        retryJob?.cancel()
        val delayMs = INITIAL_RETRY_DELAY_MS shl (failedAttempts - 1)
        val backoff = delayMs.coerceAtMost(MAX_RETRY_DELAY_MS)
        retryJob = scope.launch {
            delay(backoff)
            ensureRunning()
        }
    }

    companion object {
        private const val TAG = "DaemonLifecycle"
        internal const val MAX_RETRIES = 5
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 60_000L
    }
}
