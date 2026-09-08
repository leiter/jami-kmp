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
import net.jami.e2e.protocol.AccountRemoved
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.RemoveAccount

/**
 * Import edge case: **password-protected archive imported with the correct password.**
 *
 * Reuse-first and non-consuming — claim a password-protected asset from the pool and import
 * it with its own (correct) password. The proof is **identity preservation**: the imported
 * account exposes the same Jami fingerprint the archive was captured under. The archive is
 * unchanged, so the asset stays in the pool for the wrong-/no-password scenarios to reuse.
 *
 * Requires a `pw` fixture in the pool (run `seed-pool` first).
 */
object ImportCorrectPasswordScenario : Scenario {
    override val id = "import-correct-password"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        val asset = ctx.memory.claim(hasPassword = true)
            ?: return Verdict(false, "no password-protected asset in pool — run seed-pool first")

        // An import is an onboarding-state operation: prove the device has no account loaded first.
        val clean = ensureNoAccounts(ctx, "A")
        if (!clean.clean) return Verdict(false, "could not reach a no-account state before import")

        ctx.log("importing pw asset ${asset.fingerprint} with its correct password")

        // installAsset pushes the archive and imports it with the asset's own password,
        // awaiting AccountAdded — exactly the correct-password path.
        val accountId = ctx.installAsset("A", asset)
            ?: return Verdict(false, "import with correct password did not add the account")

        try {
            ctx.send("A", GetAccountUri(accountId))
            val uri = (ctx.await("A", 10_000) {
                it is AccountUri && it.accountId == accountId
            } as AccountUri).uri
            ctx.log("imported account $accountId with identity $uri")

            return if (uri == asset.fingerprint) {
                Verdict(true, "correct-password import restored identity ${asset.fingerprint}")
            } else {
                Verdict(false, "identity mismatch: expected ${asset.fingerprint}, got '$uri'")
            }
        } finally {
            // Non-consuming: remove the device copy; the archive stays in the pool.
            runCatching {
                ctx.send("A", RemoveAccount(accountId))
                ctx.await("A", 10_000) { it is AccountRemoved && it.accountId == accountId }
            }
        }
    }
}
