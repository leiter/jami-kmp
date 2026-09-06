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
import net.jami.e2e.memory.ConversationPairAsset
import net.jami.e2e.memory.ConversationRepositoryAsset
import net.jami.e2e.memory.MemoryStore
import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.Directive
import net.jami.e2e.scenarios.AccountCreationBareScenario
import net.jami.e2e.scenarios.AccountCreationScenario
import net.jami.e2e.scenarios.AccountCreationUsernameScenario
import net.jami.e2e.scenarios.AccountEnableDisableScenario
import net.jami.e2e.scenarios.AccountReuseScenario
import net.jami.e2e.scenarios.BuildConversationFixtureScenario
import net.jami.e2e.scenarios.CaptureAccountScenario
import net.jami.e2e.scenarios.CaptureLivePairScenario
import net.jami.e2e.scenarios.ChangePasswordScenario
import net.jami.e2e.scenarios.ChatConversationGitRewindResyncScenario
import net.jami.e2e.scenarios.DefaultOneOnOneConversationScenario
import net.jami.e2e.scenarios.DeviceRenameScenario
import net.jami.e2e.scenarios.ImportCorrectPasswordScenario
import net.jami.e2e.scenarios.ImportNoPasswordScenario
import net.jami.e2e.scenarios.ImportWrongPasswordScenario
import net.jami.e2e.scenarios.NameLookupScenario
import net.jami.e2e.scenarios.PingScenario
import net.jami.e2e.scenarios.RegisterNameOnAccountScenario
import net.jami.e2e.scenarios.RegisterNameTakenScenario
import net.jami.e2e.scenarios.SeedPoolScenario
import net.jami.e2e.scenarios.SendMessageScenario
import net.jami.e2e.scenarios.SendReplyRoundtripScenario
import net.jami.e2e.scenarios.TwoDeviceContactScenario
import java.nio.file.Path

/** Roles are addressed by name; the runner maps connected devices to A, B, … in order. */
val ROLE_NAMES = listOf("A", "B", "C", "D")

/** Outcome of a scenario run. */
data class Verdict(val pass: Boolean, val reason: String)

/**
 * Run-level flags set from the command line (`-PkeepAccounts`, `-PaccountState`), not owned by
 * any one scenario. [keepAccounts] tells a scenario's teardown to skip its `ensureNoAccounts`
 * sweep (and any other cleanup) so accounts/contacts/conversations survive past the run — for
 * hand-driven investigation between runs. [accountState] names a conversation-pair fixture a
 * scenario should restore (if it already exists in the registry) or build-and-save-as (if it
 * doesn't) — see [ScenarioContext.captureConversationPairAsset]/[installConversationPairAsset].
 * Both default to today's behavior (wipe on finish, no persisted label).
 */
data class RunConfig(
    val keepAccounts: Boolean = false,
    val accountState: String? = null,
    /** Custom username for a scenario that registers a caller-given name (`-Pusername=<name>`). */
    val username: String? = null,
    /**
     * A caller-known conversationId for a scenario that captures/inspects a specific already-
     * established conversation rather than establishing a fresh one (`-PconversationId=<id>`) —
     * e.g. `capture-live-pair`, rescuing a live pair left behind by a run that failed after the
     * handshake but before its own capture step.
     */
    val conversationId: String? = null,
    /**
     * Turn on the daemon's continuous connection/ICE/TURN state dump into logcat on every
     * device for the whole run (`-PdaemonMonitor=true` → `JamiService.monitor(true)`). Default
     * off: the trace is verbose enough to evict useful lines from logcat's ring buffer, so it's
     * opt-in for when a run is being debugged (e.g. investigating NAT-traversal / swarm-channel
     * failures).
     */
    val daemonMonitor: Boolean = false,
)

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

    /** Run-level flags set from the command line — see [RunConfig]. */
    val runConfig: RunConfig

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

    /**
     * Capture a full **conversation-pair fixture**: snapshot each role's entire on-device app
     * data (identity + profile + local seeded history — see
     * [net.jami.e2e.DeviceController.snapshotAppData]) and record it in the registry under
     * [label]. The caller supplies the metadata it already knows from driving the setup
     * (fingerprints, names, whether avatars were set, the real swarm [conversationId], and how
     * many messages were seeded). Returns the stored pair, or null on failure.
     */
    suspend fun captureConversationPairAsset(
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
    ): ConversationPairAsset?

    /**
     * Hard-reset [roleA]/[roleB] to exactly the state captured in [pair]: wipes each device's
     * app data ([net.jami.e2e.DeviceController.clearAppData]), restores the matching tar, then
     * relaunches the app + agent and waits for both roles to reconnect and re-register on the
     * DHT. Non-consuming — [pair]'s stored tars are never mutated. Returns true once both
     * devices are confirmed back up.
     */
    suspend fun installConversationPairAsset(
        pair: ConversationPairAsset,
        roleA: String,
        roleB: String,
    ): Boolean

    /**
     * Simulate [role]'s device losing recent history for one conversation, then relaunch it so
     * a scenario can observe whether the daemon's normal peer sync self-heals: force-stops the
     * app, pulls just [conversationId]'s on-disk git repo, rewinds it by [commitsBack] commits
     * (clamped to the repo's own root — never fewer than its first commit), pushes the rewound
     * repo back, and relaunches the app under the same role (reconnected, but the caller still
     * awaits its own post-relaunch readiness signals, e.g. `RegistrationStateChanged`). Returns
     * the number of commits actually reverted — 0 means nothing was reverted (repo too short,
     * or a pull/push step failed), which the caller should treat as a setup failure, not a
     * sync-behavior result.
     */
    suspend fun rewindConversation(
        role: String,
        accountId: String,
        conversationId: String,
        commitsBack: Int,
    ): Int

    /**
     * Capture just **one conversation's** raw on-disk swarm git repo — independent of the
     * whole-app-tar [captureConversationPairAsset] — and record it under [label] in the
     * registry. For scenarios that want a repeatable, pristine starting point for a single
     * conversation (e.g. rewind/resync experiments) without re-running the whole account/
     * contact/message setup each time. Returns the stored asset, or null on failure.
     */
    suspend fun captureConversationRepoAsset(
        role: String,
        accountId: String,
        conversationId: String,
        label: String,
    ): ConversationRepositoryAsset?

    /**
     * Replace [role]'s on-disk copy of [conversationId] (under [accountId]) with [asset]'s
     * captured repo, then relaunch the app under the same role. [accountId]/[conversationId]
     * are given explicitly (rather than taken from [asset]) since restoring onto a different
     * live account/conversation than the one it was captured from is a legitimate use. Returns
     * true once the device is confirmed back up. Non-consuming — [asset]'s stored archive is
     * never mutated.
     */
    suspend fun installConversationRepoAsset(
        asset: ConversationRepositoryAsset,
        role: String,
        accountId: String,
        conversationId: String,
    ): Boolean
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
            CaptureAccountScenario,
            CaptureLivePairScenario,
            RegisterNameOnAccountScenario,
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
            SendReplyRoundtripScenario,
            BuildConversationFixtureScenario,
            ChatConversationGitRewindResyncScenario,
            DefaultOneOnOneConversationScenario,
        ).associateBy { it.id }
}
