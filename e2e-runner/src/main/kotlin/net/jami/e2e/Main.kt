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

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.jami.e2e.memory.MemoryStore
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

const val HARNESS_PORT = 8080
private const val CONNECT_TIMEOUT_MS = 60_000L

/**
 * Settle window after `installHarnessDebug` before touching the app/daemon at all — see the
 * `delay(INSTALL_SETTLE_MS)` call site in [main] for why. 3s comfortably covers the recursive
 * restorecon this Pixel 2/Android 11 lab device needed in the confirming experiment
 * (2026-08-17); bump this if a future device still exhibits the stalled-git-write symptom.
 */
private const val INSTALL_SETTLE_MS = 3_000L

/**
 * Entry point for the e2e test harness runner (the "brain").
 *
 * Args: `<scenarioId> [devicesCsv] [keepAccounts] [accountState] [username] [conversationId]`,
 * `--list`, or `--list-account-states`.
 * Exit code: 0 = pass, 1 = fail, 2 = usage error.
 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "--list") {
        printScenarios()
        return
    }
    if (args[0] == "--list-account-states") {
        printAccountStates()
        return
    }

    val scenarioId = args[0]
    val devicesCsv = args.getOrNull(1)?.takeIf { it.isNotBlank() }
    val runConfig = RunConfig(
        keepAccounts = args.getOrNull(2)?.toBoolean() ?: false,
        accountState = args.getOrNull(3)?.takeIf { it.isNotBlank() },
        username = args.getOrNull(4)?.takeIf { it.isNotBlank() },
        conversationId = args.getOrNull(5)?.takeIf { it.isNotBlank() },
    )
    val scenario = ScenarioRegistry.scenarios[scenarioId] ?: run {
        System.err.println("Unknown scenario '$scenarioId'. Available:")
        printScenarios()
        exitProcess(2)
    }

    val serials = resolveDevices(devicesCsv, scenario.requiredRoles)
    if (serials.size < scenario.requiredRoles) {
        System.err.println(
            "Scenario '$scenarioId' needs ${scenario.requiredRoles} device(s); found ${serials.size}: $serials"
        )
        exitProcess(2)
    }

    val ledger = Ledger()
    val server = HarnessServer(HARNESS_PORT, ledger)
    val roles = ROLE_NAMES.take(scenario.requiredRoles)
    server.expectRoles(roles)
    server.start()
    println("Harness server listening on :$HARNESS_PORT — scenario '$scenarioId', devices=$serials")
    if (runConfig.keepAccounts) println("keepAccounts=true — teardown sweeps will be skipped")
    if (runConfig.accountState != null) println("accountState='${runConfig.accountState}'")
    if (runConfig.username != null) println("username='${runConfig.username}'")
    if (runConfig.conversationId != null) println("conversationId='${runConfig.conversationId}'")

    val controllers = serials.take(scenario.requiredRoles).map { DeviceController(it) }

    val stamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    val runDir = File("harness-memory/runs/${stamp}__$scenarioId").apply { mkdirs() }
    println("Run artifacts → ${runDir.absolutePath}")

    val memory = MemoryStore()

    val verdict = runBlocking {
        // Settle window after installHarnessDebug (the Gradle task dependency that already ran
        // before this process started): a fresh install triggers installd to run a recursive
        // SELinux restorecon over the app's private data dir ("Detected label change ... running
        // recursive restorecon" in logcat). If the daemon starts writing the swarm conversation
        // git repo (libgit2 lock files via link()) before that relabel finishes, the writes are
        // silently SELinux-denied and the conversation stalls mid-bootstrap forever — no error
        // surfaces anywhere in the Kotlin/daemon layers, it just looks like a hung handshake or a
        // message that never arrives. Confirmed via direct experiment 2026-08-17 (doc/TODO.md,
        // "Infra Finding"): skipping the reinstall entirely turned a reliably-hanging run into a
        // clean pass. This fixed delay is the permanent fix — works even on a first-time install,
        // unlike the `-x installHarnessDebug` workaround used to diagnose it.
        delay(INSTALL_SETTLE_MS)

        // Precondition: the standard jami-kmp app and jami-android-client must not be running —
        // both drive a real daemon against the real DHT, and left running they're a source of
        // resource contention and crosstalk with the harness's own daemon session on the same
        // physical device/network.
        controllers.forEach { it.stopCompetingApps() }

        // Bring every device up first (these adb calls return immediately).
        controllers.forEach { ctrl ->
            ctrl.adbReverse(HARNESS_PORT)
            ctrl.startApp()
        }
        // Start agents one at a time, binding each connection to the serial just launched.
        // Roles are assigned in connect order, so sequential start makes role ↔ serial
        // deterministic — needed so a snapshot of role "A" always hits A's physical device.
        val conns = LinkedHashMap<String, DeviceConnection>()
        val roleControllers = LinkedHashMap<String, DeviceController>()
        for (ctrl in controllers) {
            ctrl.startAgent()
            val conn = try {
                server.awaitNextConnection(CONNECT_TIMEOUT_MS)
            } catch (e: Exception) {
                return@runBlocking Verdict(
                    false,
                    "device ${ctrl.serial} did not connect within ${CONNECT_TIMEOUT_MS}ms: ${e.message}",
                )
            }
            conns[conn.role] = conn
            roleControllers[conn.role] = ctrl
        }

        val ctx = ScenarioContextImpl(conns, roleControllers, ledger, runDir, memory, server, runConfig)
        val result = try {
            scenario.run(ctx)
        } catch (e: Exception) {
            Verdict(false, "scenario threw: ${e.message}")
        }
        // Auto-capture every device on failure — highest-value diagnostic, attributable per role.
        if (!result.pass) {
            ctx.log("failure — capturing screenshots of all roles")
            ctx.snapshotAll("fail")
        }
        result
    }

    controllers.forEach { it.adbReverseRemove(HARNESS_PORT) }
    server.stop()

    ledger.print()
    println(if (verdict.pass) "PASS — ${verdict.reason}" else "FAIL — ${verdict.reason}")
    exitProcess(if (verdict.pass) 0 else 1)
}

private fun resolveDevices(devicesCsv: String?, required: Int): List<String> {
    if (devicesCsv != null) {
        return devicesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val online = DeviceController.listDevices()
    return online.take(required)
}

private fun printScenarios() {
    println("Available scenarios:")
    ScenarioRegistry.scenarios.values
        .sortedBy { it.id }
        .forEach { println("  ${it.id.padEnd(20)} (roles: ${it.requiredRoles})") }
}

/** `--list-account-states`: dump named conversation-pair fixtures for `-PaccountState=<label>`. */
private fun printAccountStates() {
    val memory = MemoryStore()
    val pairs = memory.allConversationPairs()
    val repos = memory.allConversationRepos()
    if (pairs.isEmpty() && repos.isEmpty()) {
        println("No named account states yet. Create one with:")
        println("  ./gradlew :e2e-runner:e2e -Pscenario=default-one-on-one-conversation " +
            "-Pdevices=<a>,<b> -PaccountState=<name>")
        return
    }
    if (pairs.isNotEmpty()) {
        println("Named account states (-PaccountState=<label>), whole-app-tar pairs:")
        println("  %-30s %-18s %-18s %-40s %s".format("label", "A", "B", "conversationId", "messages"))
        pairs.sortedBy { it.label }.forEach { p ->
            val a = if (p.nameA.isBlank()) "(unnamed)" else p.nameA
            val b = if (p.nameB.isBlank()) "(unnamed)" else p.nameB
            println("  %-30s %-18s %-18s %-40s %d".format(p.label, a, b, p.conversationId, p.messageCount))
        }
    }
    if (repos.isNotEmpty()) {
        println("Standalone conversation-repo fixtures:")
        println("  %-30s %-18s %-40s".format("label", "accountId", "conversationId"))
        repos.sortedBy { it.label }.forEach { r ->
            println("  %-30s %-18s %-40s".format(r.label, r.accountId.take(16), r.conversationId))
        }
    }
}
