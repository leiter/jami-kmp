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
package net.jami.android.harness

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.jami.di.JamiKoinHolder
import net.jami.e2e.protocol.AccountAdded
import net.jami.e2e.protocol.AccountRemoved
import net.jami.e2e.protocol.CommandFrame
import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.Envelope
import net.jami.e2e.protocol.HarnessJson
import net.jami.e2e.protocol.Hello
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.ReportFrame
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import org.koin.core.Koin

/**
 * On-device executor for the e2e test harness. Connects to the host runner over the
 * out-of-band localhost WebSocket (reached via `adb reverse`), reports app state derived
 * from the existing service Flows, and applies inbound commands. Carries no Jami payload.
 *
 * Thin by design: it holds no scenario knowledge — the host runner is the brain.
 */
class HarnessAgent(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "HarnessAgent"
        private const val HOST = "127.0.0.1"
        private const val PORT = 8080
        private const val RETRY_MS = 2_000L
    }

    suspend fun run() {
        // Start order is irrelevant: wait until the app has initialized Koin.
        while (JamiKoinHolder.koin == null) delay(50)
        val koin: Koin = JamiKoinHolder.koin!!
        val accountService = koin.get<AccountService>()
        val handler = CommandHandler(koin)
        val client = HttpClient(OkHttp) { install(WebSockets) }

        while (scope.isActive) {
            try {
                client.webSocket(host = HOST, port = PORT, path = "/") {
                    Log.i(TAG, "connected to runner at ws://$HOST:$PORT")
                    val sendMutex = Mutex()
                    suspend fun emit(event: DomainEvent) {
                        val frame = ReportFrame(event = event, deviceTsMillis = System.currentTimeMillis())
                        sendMutex.withLock { send(HarnessJson.encodeToString<Envelope>(frame)) }
                    }

                    send(HarnessJson.encodeToString<Envelope>(Hello()))

                    val observers = listOf(
                        scope.launch { observeAccounts(accountService, ::emit) },
                        scope.launch { observeRegistration(accountService, ::emit) },
                    )
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val env = HarnessJson.decodeFromString<Envelope>(frame.readText())
                            if (env is CommandFrame) {
                                Log.d(TAG, "command ${env.commandId}: ${env.directive}")
                                scope.launch { handler.handle(env.directive) { ev -> emit(ev) } }
                            }
                        }
                    } finally {
                        observers.forEach { it.cancel() }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "websocket error: ${e.message}; retrying in ${RETRY_MS}ms")
                delay(RETRY_MS)
            }
        }
    }

    /** Single source of account add/remove events: diff the accounts StateFlow. */
    private suspend fun observeAccounts(
        accountService: AccountService,
        emit: suspend (DomainEvent) -> Unit,
    ) {
        var known = accountService.accounts.value.map { it.accountId }.toSet()
        accountService.accounts.collect { list ->
            val current = list.map { it.accountId }.toSet()
            (current - known).forEach { emit(AccountAdded(it)) }
            (known - current).forEach { emit(AccountRemoved(it)) }
            known = current
        }
    }

    private suspend fun observeRegistration(
        accountService: AccountService,
        emit: suspend (DomainEvent) -> Unit,
    ) {
        accountService.accountEvents.collect { ev ->
            if (ev is AccountEvent.RegistrationStateChanged) {
                emit(RegistrationStateChanged(ev.accountId, ev.state, ev.code))
            }
        }
    }
}
