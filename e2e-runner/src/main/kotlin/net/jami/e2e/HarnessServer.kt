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
package net.jami.e2e

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import net.jami.e2e.protocol.CommandFrame
import net.jami.e2e.protocol.Directive
import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.Envelope
import net.jami.e2e.protocol.HarnessJson
import net.jami.e2e.protocol.Hello
import net.jami.e2e.protocol.ReportFrame
import java.util.Collections
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** A merged, server-clock-stamped timeline of commands issued and events received. */
class Ledger {
    data class Entry(val tsMillis: Long, val source: String, val detail: String)

    private val entries = Collections.synchronizedList(mutableListOf<Entry>())
    private val t0 = System.currentTimeMillis()

    fun record(source: String, detail: String) {
        entries.add(Entry(System.currentTimeMillis(), source, detail))
    }

    fun print() {
        println("\n========== run timeline ==========")
        synchronized(entries) {
            for (e in entries) {
                val rel = e.tsMillis - t0
                println("+%6dms  %-12s  %s".format(rel, e.source, e.detail))
            }
        }
        println("==================================\n")
    }
}

/** A single connected device session, addressed by role. */
class DeviceConnection(
    val role: String,
    private val session: io.ktor.server.websocket.DefaultWebSocketServerSession,
    val events: Channel<DomainEvent>,
) {
    suspend fun send(frame: CommandFrame) {
        session.send(HarnessJson.encodeToString<Envelope>(frame))
    }
}

/**
 * Out-of-band coordination WebSocket server (host side). Devices connect, announce
 * themselves with [Hello], then stream [ReportFrame]s; the runner pushes [CommandFrame]s.
 * Carries no Jami payload.
 */
class HarnessServer(private val port: Int, private val ledger: Ledger) {
    private val pendingRoles = ArrayDeque<String>()
    private val connected = Channel<DeviceConnection>(Channel.UNLIMITED)
    private val conns = ConcurrentHashMap<String, DeviceConnection>()

    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    /** Roles assigned, in order, to devices as they connect (unless Hello requests one). */
    fun expectRoles(roles: List<String>) {
        pendingRoles.clear()
        pendingRoles.addAll(roles)
    }

    fun start() {
        engine = embeddedServer(CIO, port = port) {
            install(WebSockets)
            routing {
                webSocket("/") {
                    val helloText = (incoming.receive() as Frame.Text).readText()
                    val hello = HarnessJson.decodeFromString<Envelope>(helloText) as Hello
                    val role = hello.requestedRole
                        ?: synchronized(pendingRoles) { pendingRoles.pollFirst() }
                        ?: "?"
                    val conn = DeviceConnection(role, this, Channel(Channel.UNLIMITED))
                    conns[role] = conn
                    ledger.record("device[$role]", "connected")
                    connected.send(conn)
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val env = HarnessJson.decodeFromString<Envelope>(frame.readText())
                            if (env is ReportFrame) {
                                ledger.record("device[$role]", "event ${env.event}")
                                conn.events.send(env.event)
                            }
                        }
                    } finally {
                        ledger.record("device[$role]", "disconnected")
                        conns.remove(role)
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    /** Wait until [n] devices have connected; returns role → connection. */
    suspend fun awaitRoles(n: Int, timeoutMillis: Long): Map<String, DeviceConnection> =
        withTimeout(timeoutMillis) {
            val result = LinkedHashMap<String, DeviceConnection>()
            repeat(n) {
                val conn = connected.receive()
                result[conn.role] = conn
            }
            result
        }

    fun stop() {
        engine?.stop(500, 1000)
    }
}

/** Default [ScenarioContext] backed by live device connections + the ledger. */
class ScenarioContextImpl(
    private val conns: Map<String, DeviceConnection>,
    private val ledger: Ledger,
) : ScenarioContext {
    private val commandCounter = AtomicInteger(0)

    private fun conn(role: String): DeviceConnection =
        conns[role] ?: error("No device connected for role '$role'")

    override suspend fun send(role: String, directive: Directive): String {
        val commandId = "cmd-${commandCounter.incrementAndGet()}"
        ledger.record("runner→$role", "command $commandId: $directive")
        conn(role).send(CommandFrame(commandId, directive))
        return commandId
    }

    override suspend fun await(
        role: String,
        timeoutMillis: Long,
        predicate: (DomainEvent) -> Boolean,
    ): DomainEvent {
        val c = conn(role)
        return withTimeout(timeoutMillis) {
            while (true) {
                val ev = c.events.receive()
                if (predicate(ev)) return@withTimeout ev
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }

    override fun log(message: String) {
        ledger.record("scenario", message)
    }
}
