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
package net.jami.e2e.scenarios

import net.jami.e2e.Scenario
import net.jami.e2e.ScenarioContext
import net.jami.e2e.Verdict
import net.jami.e2e.protocol.AccountAdded
import net.jami.e2e.protocol.AccountExported
import net.jami.e2e.protocol.AccountRemoved
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.CreateBareAccount
import net.jami.e2e.protocol.ExportAccount
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.ImportAccount
import net.jami.e2e.protocol.RemoveAccount

/**
 * Single-device account **save / restore round-trip** — the foundation for account reuse
 * (a pre-created fixture pool instead of ephemeral-per-run accounts).
 *
 * Flow: create a bare account → export its archive on-device → pull the `.gz` to the host
 * over the coordination channel → remove the account → push the archive back → import it.
 * The proof is **identity preservation**: the re-imported account exposes the same Jami
 * fingerprint, so a saved archive genuinely restores the same identity.
 *
 * All archive transfer is out-of-band (adb `run-as`), never daemon traffic.
 */
object AccountReuseScenario : Scenario {
    override val id = "account-reuse"
    override val requiredRoles = 1

    private const val ARCHIVE = "reuse.gz"

    override suspend fun run(ctx: ScenarioContext): Verdict {
        // Round-trip must start from a proven no-account state (a stray would confound the
        // AccountAdded/identity checks below).
        if (!ensureNoAccounts(ctx, "A").clean) {
            return Verdict(false, "could not reach a no-account state before the round-trip")
        }

        // 1. Create a bare account and capture its identity.
        ctx.send("A", CreateBareAccount(displayName = "harness_reuse_${System.currentTimeMillis() / 1000}"))
        val originalId = (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
        ctx.send("A", GetAccountUri(originalId))
        val originalUri = (ctx.await("A", 10_000) {
            it is AccountUri && it.accountId == originalId
        } as AccountUri).uri
        ctx.log("created account $originalId with identity $originalUri")
        if (originalUri.isBlank()) return Verdict(false, "empty account URI before export")

        // 2. Export the archive on-device, then pull it to the host.
        ctx.send("A", ExportAccount(originalId, ARCHIVE))
        val exported = ctx.await("A", 20_000) {
            it is AccountExported && it.fileName == ARCHIVE
        } as AccountExported
        if (!exported.success) return Verdict(false, "on-device export failed")
        ctx.pullArtifact("A", ARCHIVE)
            ?: return Verdict(false, "failed to pull archive from device")

        // 3. Remove the account — the identity now lives only in the pulled archive.
        ctx.send("A", RemoveAccount(originalId))
        ctx.await("A", 10_000) { it is AccountRemoved && it.accountId == originalId }
        ctx.log("removed original account; archive held only on host")

        var restoredId: String? = null
        try {
            // 4. Push the archive back and import it.
            if (!ctx.pushArtifact("A", ARCHIVE)) return Verdict(false, "failed to push archive to device")
            ctx.send("A", ImportAccount(ARCHIVE))
            restoredId = (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
            ctx.send("A", GetAccountUri(restoredId))
            val restoredUri = (ctx.await("A", 10_000) {
                it is AccountUri && it.accountId == restoredId
            } as AccountUri).uri
            ctx.log("re-imported account $restoredId with identity $restoredUri")

            return if (restoredUri.isNotBlank() && restoredUri == originalUri) {
                Verdict(true, "save/restore round-trip preserved identity $originalUri")
            } else {
                Verdict(false, "identity mismatch after restore: '$originalUri' vs '$restoredUri'")
            }
        } finally {
            // Cleanup — ephemeral account isolation. Never flips the verdict. Skippable via
            // -PkeepAccounts=true to leave the restored account on-device for inspection.
            if (!ctx.runConfig.keepAccounts) {
                restoredId?.let { id ->
                    runCatching {
                        ctx.send("A", RemoveAccount(id))
                        ctx.await("A", 10_000) { it is AccountRemoved && it.accountId == id }
                    }
                }
                // Hardened sweep — guarantee the device is left with no account loaded.
                runCatching { ensureNoAccounts(ctx, "A") }
            } else {
                ctx.log("keepAccounts=true — skipping teardown, leaving account on-device")
            }
        }
    }
}
