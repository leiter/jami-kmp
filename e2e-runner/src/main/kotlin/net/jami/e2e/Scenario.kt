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

import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.Directive
import net.jami.e2e.scenarios.AccountCreationScenario
import net.jami.e2e.scenarios.PingScenario
import net.jami.e2e.scenarios.TwoDeviceContactScenario

/** Roles are addressed by name; the runner maps connected devices to A, B, … in order. */
val ROLE_NAMES = listOf("A", "B", "C", "D")

/** Outcome of a scenario run. */
data class Verdict(val pass: Boolean, val reason: String)

/**
 * The control + observation surface a scenario uses to drive devices. The brain
 * (scenario logic) lives here on the host; devices are thin executors.
 */
interface ScenarioContext {
    /** Issue a command to a role's device. Returns the generated commandId. */
    suspend fun send(role: String, directive: Directive): String

    /**
     * Suspend until the role's device reports an event matching [predicate], or fail
     * with a timeout. Event-driven — never a fixed sleep.
     */
    suspend fun await(
        role: String,
        timeoutMillis: Long,
        predicate: (DomainEvent) -> Boolean,
    ): DomainEvent

    /** Append a human-readable line to the run timeline. */
    fun log(message: String)
}

/** A host-side, device-agnostic test definition. */
interface Scenario {
    val id: String
    val requiredRoles: Int
    suspend fun run(ctx: ScenarioContext): Verdict
}

/** Registry of available scenarios, keyed by id. */
object ScenarioRegistry {
    val scenarios: Map<String, Scenario> =
        listOf<Scenario>(PingScenario, AccountCreationScenario, TwoDeviceContactScenario)
            .associateBy { it.id }
}
