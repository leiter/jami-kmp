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
import net.jami.e2e.protocol.CreateBareAccount
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.SetAccountEnabled

/**
 * Account **enable / disable** — the registration toggle.
 *
 * `setAccountEnabled` (`sendRegister`) takes an account off and back onto the DHT. Reuse-first
 * and **non-consuming**: claim an unprotected pool asset, install it, and toggle registration
 * on the phone copy — nothing in the archive changes and the host blob is never rewritten, so
 * the fixture is untouched.
 *
 * The proof is the observed registration lifecycle over the real daemon Flow:
 *  - baseline `REGISTERED` after install;
 *  - **disable** → `UNREGISTERED` (account left the DHT);
 *  - **enable**  → `REGISTERED` again (account rejoined).
 *
 * Requires an unprotected fixture in the pool (run `seed-pool` first); falls back to creating
 * a bare account.
 */
object AccountEnableDisableScenario : Scenario {
    override val id = "account-enable-disable"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        if (!ensureNoAccounts(ctx, "A").clean) {
            return Verdict(false, "could not reach a no-account state before enable/disable")
        }

        // Reuse an unprotected asset (install needs no password); fall back to a bare account.
        val asset = ctx.memory.claim(hasPassword = false)
        val accountId: String = if (asset != null) {
            ctx.log("reusing asset ${asset.fingerprint} from pool (non-consuming)")
            ctx.installAsset("A", asset) ?: return Verdict(false, "failed to install claimed asset")
        } else {
            ctx.log("no unprotected asset in pool — creating a bare account")
            ctx.send("A", CreateBareAccount(displayName = "harness_toggle_${System.currentTimeMillis() / 1000}"))
            (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
        }

        try {
            // Baseline: the account must be announced on the DHT before we can prove it leaves.
            awaitState(ctx, accountId, "REGISTERED", 60_000)
            ctx.log("baseline: account REGISTERED on the DHT")

            // Disable → the account unregisters and drops off the DHT.
            ctx.send("A", SetAccountEnabled(accountId, false))
            awaitState(ctx, accountId, "UNREGISTERED", 30_000)
            ctx.log("disable → UNREGISTERED (account left the DHT)")

            // Enable → the account re-registers and rejoins.
            ctx.send("A", SetAccountEnabled(accountId, true))
            awaitState(ctx, accountId, "REGISTERED", 60_000)
            ctx.log("enable → REGISTERED (account rejoined the DHT)")

            return Verdict(true, "registration toggled off (UNREGISTERED) and back on (REGISTERED)")
        } finally {
            // Non-consuming: remove the phone copy; the host blob is never rewritten.
            // Skippable via -PkeepAccounts=true to leave the account on-device for inspection.
            if (!ctx.runConfig.keepAccounts) {
                runCatching { ensureNoAccounts(ctx, "A") }
            } else {
                ctx.log("keepAccounts=true — skipping teardown, leaving account on-device")
            }
        }
    }

    /** Await a specific registration [state] for [accountId] on the real daemon Flow. */
    private suspend fun awaitState(
        ctx: ScenarioContext,
        accountId: String,
        state: String,
        timeoutMillis: Long,
    ) {
        ctx.await("A", timeoutMillis) {
            it is RegistrationStateChanged && it.accountId == accountId && it.state == state
        }
    }
}
