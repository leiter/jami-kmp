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
import kotlinx.coroutines.delay
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.AccountsSnapshot
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.GetAccounts
import net.jami.e2e.protocol.LookupName
import net.jami.e2e.protocol.NameLookupResult
import net.jami.e2e.protocol.NameRegistrationEnded
import net.jami.e2e.protocol.RegisterName
import net.jami.e2e.protocol.RegistrationStateChanged

/**
 * Registers a **caller-given** name (`-Pusername=<name>`) on whatever account is already loaded
 * on device[A] — the companion to `capture-account` for the same "operate on a live, already-
 * provisioned account without wiping it" use case. Unlike `account-creation-username`, this does
 * not run `ensureNoAccounts` first, does not auto-generate the name via [MemorableNames], and
 * does not pick an arbitrary unnamed pool asset — it targets the specific account a prior
 * `-PkeepAccounts=true` run (or a manual import) already left live on the device.
 *
 * Note the daemon **lowercases** the name before registering it
 * (`NameDirectory::registerName` → `toLower`) — `-Pusername=Main_Harness_Account` registers as
 * `main_harness_account` on the real name server. Consuming and irreversible, like every other
 * name registration in this harness: the (lowercased) name is burned globally once `state == 0`.
 *
 * `NameRegistrationEnded(state=0)` alone is a **self-reported** success signal from the same
 * account that made the request — not independent confirmation the name server actually
 * resolves it. So, like `name-lookup`, this scenario brackets the write with two real reads:
 *  - **before**: look the (lowercased) name up first — `NotFound` means it's actually
 *    available (so a subsequent register failure is a real problem, not "of course it failed,
 *    it was already taken"); `Success` resolving to *this* account's own identity means someone
 *    already registered it for us (idempotent — nothing more to do); `Success` resolving to a
 *    *different* identity fails fast with a clear reason instead of attempting (and getting a
 *    generic `alreadyTaken`) anyway.
 *  - **after**: once `NameRegistrationEnded` reports `state=0`, look the name up again and
 *    require it resolve to this account's own identity — the actual proof the name server
 *    accepted the registration, not just that the daemon *said* it did.
 *
 * No `finally`/teardown — this must never wipe the account it just named.
 */
object RegisterNameOnAccountScenario : Scenario {
    override val id = "register-name-on-account"
    override val requiredRoles = 1

    private const val LOOKUP_SUCCESS = 0
    private const val LOOKUP_NOT_FOUND = 2

    override suspend fun run(ctx: ScenarioContext): Verdict {
        val username = ctx.runConfig.username
            ?: return Verdict(false, "missing -Pusername=<name>")
        val queryName = username.lowercase()

        ctx.send("A", GetAccounts)
        val ids = (ctx.await("A", 10_000) { it is AccountsSnapshot } as AccountsSnapshot).ids
        if (ids.isEmpty()) {
            return Verdict(false, "no account loaded on device[A] to register a name on — import/create one first")
        }
        if (ids.size > 1) {
            ctx.log("device[A] has ${ids.size} accounts loaded — registering on the first: ${ids.first()}")
        }
        val accountId = ids.first()

        // Best-effort: the account may already be REGISTERED from a prior run — this fresh
        // connection can miss that transition entirely, so don't hard-fail if it isn't observed.
        runCatching {
            ctx.await("A", 20_000) {
                it is RegistrationStateChanged && it.accountId == accountId && it.state == "REGISTERED"
            }
        }.onFailure {
            ctx.log("no fresh REGISTERED transition observed within 20s — assuming already registered, proceeding")
        }

        ctx.send("A", GetAccountUri(accountId))
        val uri = (ctx.await("A", 10_000) { it is AccountUri && it.accountId == accountId } as AccountUri).uri

        // Pre-check: is the name actually available, and can we tell an availability problem
        // apart from a genuine registration failure before even attempting the write?
        val pre = lookup(ctx, accountId, queryName)
        ctx.log("pre-check lookup '$queryName' → state=${pre.state} address=${pre.address}")
        when {
            pre.state == LOOKUP_SUCCESS && pre.address.equals(uri, ignoreCase = true) ->
                return Verdict(true, "'$queryName' already resolves to this account — nothing to register")
            pre.state == LOOKUP_SUCCESS ->
                return Verdict(false, "'$queryName' is already taken by a different identity (${pre.address})")
            pre.state == LOOKUP_NOT_FOUND ->
                ctx.log("'$queryName' is available — proceeding to register")
            else ->
                ctx.log("pre-check lookup inconclusive (state=${pre.state}) — attempting registration anyway")
        }

        ctx.log("registering name '$username' on account $accountId (identity $uri)")
        ctx.send("A", RegisterName(accountId, username))
        val result = ctx.await("A", 60_000) {
            it is NameRegistrationEnded && it.accountId == accountId
        } as NameRegistrationEnded
        ctx.log("name registration ended (self-reported): name='${result.name}' state=${result.state}")
        if (result.state != 0) {
            return Verdict(false, "name registration failed for '$username' (state=${result.state})")
        }

        // Post-check: independently confirm the name server actually resolves it now, rather
        // than trusting the self-reported state=0 alone. A couple of retries in case the write
        // hasn't propagated to the read path yet.
        var post = lookup(ctx, accountId, queryName)
        var attempt = 0
        while (post.state != LOOKUP_SUCCESS && attempt < 2) {
            ctx.log("post-check lookup '$queryName' → state=${post.state}, retrying in 2s")
            delay(2_000)
            post = lookup(ctx, accountId, queryName)
            attempt++
        }
        ctx.log("post-check lookup '$queryName' → state=${post.state} address=${post.address}")
        if (post.state != LOOKUP_SUCCESS || !post.address.equals(uri, ignoreCase = true)) {
            return Verdict(
                false,
                "daemon reported state=0 but post-check lookup did not confirm it: " +
                    "state=${post.state} address=${post.address}, expected $uri",
            )
        }

        // Keep the pool's bookkeeping in sync if this identity happens to be a known pool asset.
        // markRegistered stores whatever we pass — use the query name (already lowercased) since
        // that's what the post-check just independently confirmed actually resolves.
        val asset = ctx.memory.all().firstOrNull { it.fingerprint.equals(uri, ignoreCase = true) }
        if (asset != null) {
            ctx.memory.markRegistered(asset.fingerprint, queryName)
            ctx.log("pool asset ${asset.fingerprint} flipped unnamed → named '$queryName'")
        } else {
            ctx.log("identity $uri is not a known pool asset — pool bookkeeping unchanged")
        }

        return Verdict(true, "registered and independently confirmed name '$queryName' on account $accountId")
        // No finally: this operates on an already-live account and must never wipe it.
    }

    /** Issue a lookup and await its [NameLookupResult] (matched by account + query). */
    private suspend fun lookup(ctx: ScenarioContext, accountId: String, name: String): NameLookupResult {
        ctx.send("A", LookupName(accountId, name))
        return ctx.await("A", 30_000) {
            it is NameLookupResult && it.accountId == accountId && it.query == name
        } as NameLookupResult
    }
}
