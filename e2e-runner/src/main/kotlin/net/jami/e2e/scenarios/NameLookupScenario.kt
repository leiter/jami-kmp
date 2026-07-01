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
import net.jami.e2e.protocol.LookupName
import net.jami.e2e.protocol.NameLookupResult
import net.jami.e2e.protocol.RegistrationStateChanged

/**
 * Name-server **read** side — `findRegistrationByName`.
 *
 * The registration scenarios prove the *write* side (burning a name); this proves the query
 * side, and it is completely **non-consuming** (a pure lookup, no account or name-server
 * mutation). Reuse-first: claim any pool asset, install it, and query from that account.
 *
 * Two checks, using the harness's own audit log of previously-burned names as ground truth:
 *  - **positive** — look up a name we know we registered; the daemon must resolve it to that
 *    exact owner fingerprint (`state=0` Success, `address == burned.fingerprint`);
 *  - **negative** — look up a name we never registered; the daemon must report `state=2`
 *    NotFound.
 *
 * `LookupState`: 0=Success, 1=Invalid, 2=NotFound, 3=NetworkError; `-1` is the harness sentinel
 * for "no daemon answer within the handler timeout".
 *
 * Requires a burned name in the registry (run `account-creation-username` first).
 */
object NameLookupScenario : Scenario {
    override val id = "name-lookup"
    override val requiredRoles = 1

    private const val SUCCESS = 0
    private const val NOT_FOUND = 2

    override suspend fun run(ctx: ScenarioContext): Verdict {
        val burned = ctx.memory.burnedNames().firstOrNull()
            ?: return Verdict(false, "no burned name in the registry — run account-creation-username first")

        if (!ensureNoAccounts(ctx, "A").clean) {
            return Verdict(false, "could not reach a no-account state before name-lookup")
        }

        // Any asset will do — the lookup is issued from its account context, unmodified.
        val asset = ctx.memory.claim()
        val accountId: String = if (asset != null) {
            ctx.log("reusing asset ${asset.fingerprint} from pool (non-consuming)")
            ctx.installAsset("A", asset) ?: return Verdict(false, "failed to install claimed asset")
        } else {
            ctx.log("pool empty — creating a bare account to query from")
            ctx.send("A", CreateBareAccount(displayName = "harness_lookup_${System.currentTimeMillis() / 1000}"))
            (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
        }

        try {
            // The name server is reached through the account — wait until it is announced.
            ctx.await("A", 60_000) {
                it is RegistrationStateChanged && it.accountId == accountId && it.state == "REGISTERED"
            }
            ctx.log("account REGISTERED — name server reachable")

            // Positive: a name we know we burned must resolve to its exact owner fingerprint.
            val hit = lookup(ctx, accountId, burned.name)
            ctx.log("lookup '${burned.name}' → state=${hit.state} address=${hit.address}")
            if (hit.state != SUCCESS) {
                return Verdict(false, "known name '${burned.name}' did not resolve (state=${hit.state})")
            }
            if (hit.address != burned.fingerprint) {
                return Verdict(false, "name '${burned.name}' resolved to '${hit.address}', expected owner ${burned.fingerprint}")
            }
            ctx.log("positive: '${burned.name}' correctly resolved to owner ${burned.fingerprint}")

            // Negative: a name we never registered must come back NotFound.
            val bogus = "e2e-nonexistent-${System.currentTimeMillis()}"
            val miss = lookup(ctx, accountId, bogus)
            ctx.log("lookup '$bogus' → state=${miss.state}")
            if (miss.state != NOT_FOUND) {
                return Verdict(false, "unregistered name '$bogus' returned state=${miss.state}, expected NotFound(2)")
            }
            ctx.log("negative: unregistered name correctly reported NotFound")

            return Verdict(true, "name server resolved '${burned.name}' → ${burned.fingerprint} and reported an unknown name NotFound")
        } finally {
            // Non-consuming: remove the phone copy; the host blob is never rewritten.
            runCatching { ensureNoAccounts(ctx, "A") }
        }
    }

    /** Issue a lookup and await its [NameLookupResult] (matched by account + query). */
    private suspend fun lookup(ctx: ScenarioContext, accountId: String, name: String): NameLookupResult {
        ctx.send("A", LookupName(accountId, name))
        return ctx.await("A", 30_000) {
            it is NameLookupResult && it.accountId == accountId && it.query == name
        } as NameLookupResult
    }
}
