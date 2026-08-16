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
 * Entry point for the e2e test harness runner (the "brain").
 *
 * Args: `<scenarioId> [devicesCsv]`  or  `--list`.
 * Exit code: 0 = pass, 1 = fail, 2 = usage error.
 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "--list") {
        printScenarios()
        return
    }

    val scenarioId = args[0]
    val devicesCsv = args.getOrNull(1)?.takeIf { it.isNotBlank() }
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

    val controllers = serials.take(scenario.requiredRoles).map { DeviceController(it) }

    val stamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    val runDir = File("harness-memory/runs/${stamp}__$scenarioId").apply { mkdirs() }
    println("Run artifacts → ${runDir.absolutePath}")

    val memory = MemoryStore()

    val verdict = runBlocking {
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

        val ctx = ScenarioContextImpl(conns, roleControllers, ledger, runDir, memory, server)
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
