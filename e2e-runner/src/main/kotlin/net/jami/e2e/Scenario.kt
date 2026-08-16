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

import net.jami.e2e.memory.AccountAsset
import net.jami.e2e.memory.MemoryStore
import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.Directive
import net.jami.e2e.scenarios.AccountCreationBareScenario
import net.jami.e2e.scenarios.AccountCreationScenario
import net.jami.e2e.scenarios.AccountCreationUsernameScenario
import net.jami.e2e.scenarios.AccountEnableDisableScenario
import net.jami.e2e.scenarios.AccountReuseScenario
import net.jami.e2e.scenarios.ChangePasswordScenario
import net.jami.e2e.scenarios.DeviceRenameScenario
import net.jami.e2e.scenarios.ImportCorrectPasswordScenario
import net.jami.e2e.scenarios.ImportNoPasswordScenario
import net.jami.e2e.scenarios.ImportWrongPasswordScenario
import net.jami.e2e.scenarios.NameLookupScenario
import net.jami.e2e.scenarios.PingScenario
import net.jami.e2e.scenarios.RegisterNameTakenScenario
import net.jami.e2e.scenarios.SeedPoolScenario
import net.jami.e2e.scenarios.SendMessageScenario
import net.jami.e2e.scenarios.TwoDeviceContactScenario
import java.nio.file.Path

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

    /**
     * Capture a screenshot of [role]'s device into the run directory and record it on the
     * timeline. [label] is a short human tag (e.g. "after-create"). Returns the written PNG
     * path, or null if capture failed — diagnostics never fail a scenario.
     */
    suspend fun snapshot(role: String, label: String): Path?

    /**
     * Pull an artifact (e.g. an exported `account.gz`) from [role]'s device — read from the
     * harness app's private `filesDir` — into the run directory as [localName]. Returns the
     * written path, or null on failure. Out-of-band: carries no Jami payload.
     */
    suspend fun pullArtifact(role: String, deviceFileName: String, localName: String = deviceFileName): Path?

    /**
     * Push an artifact from the run directory ([localName]) to [role]'s device, into the
     * harness app's private `filesDir` as [deviceFileName] — e.g. to restore an exported
     * account before [net.jami.e2e.protocol.ImportAccount]. Returns true on success.
     */
    suspend fun pushArtifact(role: String, localName: String, deviceFileName: String = localName): Boolean

    /** The persistent asset registry — claim reusable accounts before creating new ones. */
    val memory: MemoryStore

    /**
     * Export the live account [accountId] on [role], pull its archive into the registry, and
     * record it as a reusable asset. [password] is the archive password to export with
     * (`""` = unprotected). Returns the stored asset, or null if export/pull failed.
     */
    suspend fun captureAsset(
        role: String,
        accountId: String,
        registeredName: String?,
        password: String,
    ): AccountAsset?

    /**
     * Push [asset]'s archive to [role] and import it (reusing its identity). Returns the
     * on-device accountId of the imported account, or null on failure.
     */
    suspend fun installAsset(role: String, asset: AccountAsset): String?

    /**
     * Push [asset]'s archive to [role]'s device as [deviceFileName] **without importing it** —
     * the low-level half of [installAsset], for scenarios that must drive
     * [net.jami.e2e.protocol.ImportAccount] themselves (e.g. importing with a deliberately
     * wrong or empty password). Returns true on success. Non-consuming: the asset is unchanged.
     */
    suspend fun pushAsset(role: String, asset: AccountAsset, deviceFileName: String): Boolean
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
        listOf<Scenario>(
            PingScenario,
            SeedPoolScenario,
            AccountCreationScenario,
            AccountCreationBareScenario,
            AccountCreationUsernameScenario,
            RegisterNameTakenScenario,
            AccountReuseScenario,
            ChangePasswordScenario,
            AccountEnableDisableScenario,
            DeviceRenameScenario,
            NameLookupScenario,
            ImportCorrectPasswordScenario,
            ImportWrongPasswordScenario,
            ImportNoPasswordScenario,
            TwoDeviceContactScenario,
            SendMessageScenario,
        ).associateBy { it.id }
}
