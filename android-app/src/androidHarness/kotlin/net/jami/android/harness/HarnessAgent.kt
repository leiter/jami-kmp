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
import net.jami.e2e.protocol.ContactAdded
import net.jami.e2e.protocol.ConversationMemberEvent
import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.Envelope
import net.jami.e2e.protocol.HarnessJson
import net.jami.e2e.protocol.Hello
import net.jami.e2e.protocol.IncomingContactRequest
import net.jami.e2e.protocol.MessageReceived
import net.jami.e2e.protocol.NameRegistrationEnded
import net.jami.e2e.protocol.ProfileUpdated
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.ReportFrame
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.ConversationEvent
import net.jami.services.ConversationFacade
import org.koin.core.Koin

/**
 * On-device executor for the e2e test harness. Connects to the host runner over the
 * out-of-band localhost WebSocket (reached via `adb reverse`), reports app state derived
 * from the existing service Flows, and applies inbound commands. Carries no Jami payload.
 *
 * Thin by design: it holds no scenario knowledge — the host runner is the brain.
 */
class HarnessAgent(
    private val scope: CoroutineScope,
    private val requestedRole: String? = null,
    /**
     * When true (`-PdaemonMonitor=true` on the runner), enable the daemon's continuous
     * connection/ICE/TURN state dump into logcat for this session — the same call the
     * reference client makes behind its "enable logs" setting. Off by default: the trace is
     * verbose enough to evict useful lines from logcat's ring buffer, so it's opt-in for when
     * a run is being debugged.
     */
    private val daemonMonitor: Boolean = false,
) {

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

        // Opt-in (-PdaemonMonitor=true): stream the daemon's connection/ICE/TURN state into
        // logcat for the whole session — invaluable for diagnosing NAT-traversal / swarm-channel
        // failures, too noisy to leave on by default.
        if (daemonMonitor) {
            try {
                net.jami.daemon.JamiService.monitor(true)
                Log.i(TAG, "daemonMonitor=true: JamiService.monitor(true) enabled")
            } catch (e: Throwable) {
                Log.w(TAG, "daemonMonitor: JamiService.monitor(true) failed: ${e.message}")
            }
        }

        val accountService = koin.get<AccountService>()
        val conversationFacade = koin.get<ConversationFacade>()
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

                    send(HarnessJson.encodeToString<Envelope>(Hello(requestedRole = requestedRole)))

                    val observers = listOf(
                        scope.launch { observeAccounts(accountService, ::emit) },
                        scope.launch { observeRegistration(accountService, ::emit) },
                        scope.launch { observeNameRegistration(accountService, ::emit) },
                        scope.launch { observeContacts(accountService, ::emit) },
                        scope.launch { observeMessages(conversationFacade, ::emit) },
                        scope.launch { observeMembership(conversationFacade, ::emit) },
                        scope.launch { observeProfile(accountService, ::emit) },
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

    /** Name-server registration outcome (the username path's DHT-async confirmation). */
    private suspend fun observeNameRegistration(
        accountService: AccountService,
        emit: suspend (DomainEvent) -> Unit,
    ) {
        accountService.accountEvents.collect { ev ->
            if (ev is AccountEvent.NameRegistrationEnded) {
                emit(NameRegistrationEnded(ev.accountId, ev.state, ev.name))
            }
        }
    }

    /** Contact handshake side of the real DHT channel: incoming requests and confirmations. */
    private suspend fun observeContacts(
        accountService: AccountService,
        emit: suspend (DomainEvent) -> Unit,
    ) {
        accountService.accountEvents.collect { ev ->
            when (ev) {
                is AccountEvent.IncomingTrustRequest ->
                    emit(IncomingContactRequest(ev.accountId, ev.request.from.rawRingId, ev.request.conversationUri.uri))
                is AccountEvent.ContactAdded ->
                    emit(ContactAdded(ev.accountId, ev.uri, ev.confirmed))
                else -> {}
            }
        }
    }

    /**
     * Messaging: fires for both the receiver's inbound copy and the sender's own echo (the
     * daemon reports a sent message back through the same swarm callback once its commit is
     * confirmed) — the scenario disambiguates by comparing [MessageReceived.authorUri] against
     * the known peer. `ConversationEvent.MessageReceived` fires for **every** swarm commit type
     * (member/invite/vote events included, not just chat messages) via the same generic
     * callback — those arrive with an empty `textContent`, so only `text/plain` is forwarded;
     * otherwise a scenario awaiting a specific message text can match a same-conversation
     * system commit instead and hang until timeout.
     */
    private suspend fun observeMessages(
        conversationFacade: ConversationFacade,
        emit: suspend (DomainEvent) -> Unit,
    ) {
        conversationFacade.conversationEvents.collect { ev ->
            if (ev is ConversationEvent.MessageReceived && ev.message.type == "text/plain") {
                val msg = ev.message
                emit(MessageReceived(ev.accountId, ev.conversationId, msg.author, msg.textContent))
            }
        }
    }

    /**
     * Peer-visible conversation membership changes (join/leave/ban/unban) — see
     * [ConversationMemberEvent] for why this, not [ContactAdded]/`ConversationReady`, is the
     * non-racy signal a scenario should await to confirm a member's join is actually visible to
     * this device's daemon.
     */
    private suspend fun observeMembership(
        conversationFacade: ConversationFacade,
        emit: suspend (DomainEvent) -> Unit,
    ) {
        conversationFacade.conversationEvents.collect { ev ->
            if (ev is ConversationEvent.MemberEvent) {
                emit(ConversationMemberEvent(ev.accountId, ev.conversationId, ev.memberUri, ev.action))
            }
        }
    }

    /** Echo of the account's own profile after [net.jami.e2e.protocol.SetProfile]. */
    private suspend fun observeProfile(
        accountService: AccountService,
        emit: suspend (DomainEvent) -> Unit,
    ) {
        accountService.accountEvents.collect { ev ->
            if (ev is AccountEvent.ProfileReceived) {
                emit(ProfileUpdated(ev.accountId, ev.name, ev.photo.isNotEmpty()))
            }
        }
    }
}
