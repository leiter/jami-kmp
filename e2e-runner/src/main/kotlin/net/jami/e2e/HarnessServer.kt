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

    /** Wait for the next device to connect. Used to bind a role to the serial just launched. */
    suspend fun awaitNextConnection(timeoutMillis: Long): DeviceConnection =
        withTimeout(timeoutMillis) { connected.receive() }

    /**
     * The **live** connection for [role], or null if not currently connected. Unlike a frozen
     * snapshot taken at scenario start, this reflects reconnects — needed after a mid-scenario
     * app restart (e.g. [DeviceController.clearAppData] + relaunch for a fixture restore).
     */
    fun connection(role: String): DeviceConnection? = conns[role]

    fun stop() {
        engine?.stop(500, 1000)
    }
}

/** Default [ScenarioContext] backed by live device connections + the ledger. */
class ScenarioContextImpl(
    private val conns: Map<String, DeviceConnection>,
    private val controllers: Map<String, DeviceController>,
    private val ledger: Ledger,
    private val runDir: java.io.File,
    override val memory: net.jami.e2e.memory.MemoryStore,
    private val server: HarnessServer,
    override val runConfig: RunConfig = RunConfig(),
) : ScenarioContext {
    private val commandCounter = AtomicInteger(0)
    private val snapshotCounter = AtomicInteger(0)

    // Resolved live off the server, not the frozen connect-time snapshot — a mid-scenario app
    // restart (fixture restore) reconnects under the same role, and this must see the new one.
    private fun conn(role: String): DeviceConnection =
        server.connection(role) ?: conns[role] ?: error("No device connected for role '$role'")

    private suspend fun awaitReconnect(role: String, timeoutMillis: Long) {
        withTimeout(timeoutMillis) {
            while (server.connection(role) == null) kotlinx.coroutines.delay(200)
        }
    }

    /**
     * Relaunch [role]'s app + agent and wait for it to reconnect under the same role — the
     * launch half of any mid-scenario device restart. Callers handle whatever on-disk mutation
     * (or none) happens before this; this only brings the process back up and rebinds the
     * WebSocket. Does not wait for `REGISTERED` — callers with daemon-state expectations await
     * that themselves afterward.
     */
    private suspend fun restartRole(role: String, ctrl: DeviceController, timeoutMillis: Long = 30_000) {
        ctrl.startApp()
        ctrl.startAgent(role)
        awaitReconnect(role, timeoutMillis)
    }

    /** Extract a tar (as produced by [DeviceController.pullConversationRepo]) into [destDir]. */
    private fun extractTar(tarFile: java.io.File, destDir: java.io.File): Boolean = try {
        destDir.mkdirs()
        val p = ProcessBuilder("tar", "-xf", tarFile.absolutePath, "-C", destDir.absolutePath)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0) ledger.record("tar", "extract exit=$code: ${out.trim()}")
        code == 0
    } catch (e: Exception) {
        ledger.record("tar", "extract failed: ${e.message}")
        false
    }

    /** Re-tar [srcDir]'s `files/` subtree back into [tarFile], matching [pullConversationRepo]'s layout. */
    private fun createTar(srcDir: java.io.File, tarFile: java.io.File): Boolean = try {
        val p = ProcessBuilder("tar", "-cf", tarFile.absolutePath, "files")
            .directory(srcDir)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0) ledger.record("tar", "create exit=$code: ${out.trim()}")
        code == 0
    } catch (e: Exception) {
        ledger.record("tar", "create failed: ${e.message}")
        false
    }

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

    override suspend fun snapshot(role: String, label: String): java.nio.file.Path? {
        val ctrl = controllers[role] ?: run {
            ledger.record("snapshot", "no controller for role '$role'")
            return null
        }
        val n = snapshotCounter.incrementAndGet()
        val safe = label.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = java.io.File(runDir, "snap-%02d-%s-%s.png".format(n, safe, role))
        return if (ctrl.screenshot(file)) {
            ledger.record("snapshot", "role=$role label='$label' → ${file.name}")
            file.toPath()
        } else {
            ledger.record("snapshot", "FAILED role=$role label='$label'")
            null
        }
    }

    /** Capture every connected role under [label] (e.g. on failure). Best-effort. */
    suspend fun snapshotAll(label: String): List<java.nio.file.Path> =
        controllers.keys.mapNotNull { snapshot(it, label) }

    override suspend fun captureAsset(
        role: String,
        accountId: String,
        registeredName: String?,
        password: String,
    ): net.jami.e2e.memory.AccountAsset? {
        val ctrl = controllers[role] ?: run {
            ledger.record("asset", "no controller for role '$role'")
            return null
        }
        // Identity (fingerprint) keys the stored blob.
        send(role, net.jami.e2e.protocol.GetAccountUri(accountId))
        val fingerprint = (await(role, 10_000) {
            it is net.jami.e2e.protocol.AccountUri && it.accountId == accountId
        } as net.jami.e2e.protocol.AccountUri).uri
        if (fingerprint.isBlank()) {
            ledger.record("asset", "capture aborted: blank fingerprint for $accountId")
            return null
        }
        val deviceFile = "capture.gz"
        send(role, net.jami.e2e.protocol.ExportAccount(accountId, deviceFile, password))
        val exported = await(role, 20_000) {
            it is net.jami.e2e.protocol.AccountExported && it.fileName == deviceFile
        } as net.jami.e2e.protocol.AccountExported
        if (!exported.success) {
            ledger.record("asset", "capture aborted: on-device export failed for $accountId")
            return null
        }
        val local = java.io.File(runDir, "$fingerprint.gz")
        if (!ctrl.pull(deviceFile, local)) {
            ledger.record("asset", "capture aborted: pull failed for $fingerprint")
            return null
        }
        val asset = memory.addAsset(fingerprint, registeredName, password, local)
        ledger.record("asset", "captured ${if (asset.named) "named ${asset.registeredName}" else "unnamed"} " +
            "${if (asset.hasPassword) "pw" else "nopw"} $fingerprint")
        return asset
    }

    override suspend fun installAsset(role: String, asset: net.jami.e2e.memory.AccountAsset): String? {
        val ctrl = controllers[role] ?: run {
            ledger.record("asset", "no controller for role '$role'")
            return null
        }
        val deviceFile = "install.gz"
        if (!ctrl.push(memory.blobFile(asset), deviceFile)) {
            ledger.record("asset", "install aborted: push failed for ${asset.fingerprint}")
            return null
        }
        send(role, net.jami.e2e.protocol.ImportAccount(deviceFile, asset.password))
        val added = await(role, 20_000) { it is net.jami.e2e.protocol.AccountAdded }
            as net.jami.e2e.protocol.AccountAdded
        memory.touch(asset.fingerprint)
        ledger.record("asset", "installed ${asset.fingerprint} → account ${added.accountId}")
        return added.accountId
    }

    override suspend fun pushAsset(
        role: String,
        asset: net.jami.e2e.memory.AccountAsset,
        deviceFileName: String,
    ): Boolean {
        val ctrl = controllers[role] ?: run {
            ledger.record("asset", "no controller for role '$role'")
            return false
        }
        val ok = ctrl.push(memory.blobFile(asset), deviceFileName)
        ledger.record("asset", "pushed ${asset.fingerprint} → $deviceFileName ($ok)")
        return ok
    }

    override suspend fun pullArtifact(
        role: String,
        deviceFileName: String,
        localName: String,
    ): java.nio.file.Path? {
        val ctrl = controllers[role] ?: run {
            ledger.record("artifact", "no controller for role '$role'")
            return null
        }
        val local = java.io.File(runDir, localName)
        return if (ctrl.pull(deviceFileName, local)) {
            ledger.record("artifact", "pull role=$role $deviceFileName → ${local.name}")
            local.toPath()
        } else {
            ledger.record("artifact", "PULL FAILED role=$role $deviceFileName")
            null
        }
    }

    override suspend fun pushArtifact(
        role: String,
        localName: String,
        deviceFileName: String,
    ): Boolean {
        val ctrl = controllers[role] ?: run {
            ledger.record("artifact", "no controller for role '$role'")
            return false
        }
        val local = java.io.File(runDir, localName)
        return if (ctrl.push(local, deviceFileName)) {
            ledger.record("artifact", "push role=$role ${local.name} → $deviceFileName")
            true
        } else {
            ledger.record("artifact", "PUSH FAILED role=$role ${local.name}")
            false
        }
    }

    override suspend fun captureConversationPairAsset(
        roleA: String,
        roleB: String,
        label: String,
        fingerprintA: String,
        fingerprintB: String,
        nameA: String,
        nameB: String,
        avatarSet: Boolean,
        conversationId: String,
        messageCount: Int,
    ): net.jami.e2e.memory.ConversationPairAsset? {
        val ctrlA = controllers[roleA] ?: run { ledger.record("pair", "no controller for role '$roleA'"); return null }
        val ctrlB = controllers[roleB] ?: run { ledger.record("pair", "no controller for role '$roleB'"); return null }
        val localA = java.io.File(runDir, "pair-$label-A.tar")
        val localB = java.io.File(runDir, "pair-$label-B.tar")
        if (!ctrlA.snapshotAppData(localA) || !ctrlB.snapshotAppData(localB)) {
            ledger.record("pair", "capture aborted: snapshot failed for '$label'")
            return null
        }
        val pair = memory.addConversationPair(
            label, fingerprintA, fingerprintB, nameA, nameB, avatarSet, messageCount, conversationId, localA, localB,
        )
        ledger.record("pair", "captured conversation-pair '$label' ($fingerprintA <-> $fingerprintB, $messageCount msgs)")
        return pair
    }

    override suspend fun installConversationPairAsset(
        pair: net.jami.e2e.memory.ConversationPairAsset,
        roleA: String,
        roleB: String,
    ): Boolean {
        for ((role, tag) in listOf(roleA to "A", roleB to "B")) {
            val ctrl = controllers[role] ?: run { ledger.record("pair", "no controller for role '$role'"); return false }
            ctrl.clearAppData()
            if (!ctrl.restoreAppData(memory.pairBlobFile(pair, tag))) {
                ledger.record("pair", "restore FAILED role=$role for '${pair.label}'")
                return false
            }
            try {
                restartRole(role, ctrl)
            } catch (e: Exception) {
                ledger.record("pair", "role=$role did not reconnect after restore: ${e.message}")
                return false
            }
        }
        for (role in listOf(roleA, roleB)) {
            try {
                await(role, 60_000) {
                    it is net.jami.e2e.protocol.RegistrationStateChanged && it.state == "REGISTERED"
                }
            } catch (e: Exception) {
                ledger.record("pair", "role=$role did not re-register after restore: ${e.message}")
                return false
            }
        }
        ledger.record("pair", "installed conversation-pair '${pair.label}' on $roleA/$roleB")
        return true
    }

    override suspend fun rewindConversation(
        role: String,
        accountId: String,
        conversationId: String,
        commitsBack: Int,
    ): Int {
        val ctrl = controllers[role] ?: run { ledger.record("rewind", "no controller for role '$role'"); return 0 }
        val pulledTar = java.io.File(runDir, "convrepo-$role-pull.tar")
        val pushedTar = java.io.File(runDir, "convrepo-$role-push.tar")
        val extractDir = java.io.File(runDir, "convrepo-$role-extract").apply { deleteRecursively(); mkdirs() }

        ctrl.forceStopSelf()
        if (!ctrl.pullConversationRepo(accountId, conversationId, pulledTar)) {
            ledger.record("rewind", "pull failed for role=$role conversation=$conversationId")
            return 0
        }
        if (!extractTar(pulledTar, extractDir)) {
            ledger.record("rewind", "local extract failed for role=$role")
            return 0
        }
        val repoDir = java.io.File(extractDir, "files/$accountId/conversations/$conversationId")
        if (!repoDir.exists()) {
            ledger.record("rewind", "extracted repo dir missing: ${repoDir.absolutePath}")
            return 0
        }
        val reverted = net.jami.e2e.LocalGit.resetHardBack(repoDir, commitsBack)
        if (reverted == 0) {
            ledger.record("rewind", "nothing to revert (repo too short or commitsBack<=0) for role=$role")
            return 0
        }
        if (!createTar(extractDir, pushedTar)) {
            ledger.record("rewind", "local re-tar failed for role=$role")
            return 0
        }
        if (!ctrl.pushConversationRepo(accountId, conversationId, pushedTar)) {
            ledger.record("rewind", "push failed for role=$role")
            return 0
        }
        restartRole(role, ctrl)
        ledger.record("rewind", "role=$role conversation=$conversationId rewound $reverted commit(s), relaunched")
        return reverted
    }

    override suspend fun captureConversationRepoAsset(
        role: String,
        accountId: String,
        conversationId: String,
        label: String,
    ): net.jami.e2e.memory.ConversationRepositoryAsset? {
        val ctrl = controllers[role] ?: run { ledger.record("repo", "no controller for role '$role'"); return null }
        send(role, net.jami.e2e.protocol.GetAccountUri(accountId))
        val fingerprint = (await(role, 10_000) {
            it is net.jami.e2e.protocol.AccountUri && it.accountId == accountId
        } as net.jami.e2e.protocol.AccountUri).uri
        val local = java.io.File(runDir, "repo-$label-$role.tar")
        if (!ctrl.pullConversationRepo(accountId, conversationId, local)) {
            ledger.record("repo", "capture aborted: pull failed for '$label' role=$role")
            return null
        }
        val asset = memory.addConversationRepo(label, accountId, conversationId, fingerprint, local)
        ledger.record("repo", "captured conversation-repo '$label' (account=$accountId conversation=$conversationId)")
        return asset
    }

    override suspend fun installConversationRepoAsset(
        asset: net.jami.e2e.memory.ConversationRepositoryAsset,
        role: String,
        accountId: String,
        conversationId: String,
    ): Boolean {
        val ctrl = controllers[role] ?: run { ledger.record("repo", "no controller for role '$role'"); return false }
        ctrl.forceStopSelf()
        if (!ctrl.pushConversationRepo(accountId, conversationId, memory.repoBlobFile(asset))) {
            ledger.record("repo", "install FAILED role=$role for '${asset.label}'")
            return false
        }
        try {
            restartRole(role, ctrl)
        } catch (e: Exception) {
            ledger.record("repo", "role=$role did not reconnect after restore: ${e.message}")
            return false
        }
        ledger.record("repo", "installed conversation-repo '${asset.label}' on role=$role")
        return true
    }
}
