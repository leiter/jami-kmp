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
import net.jami.e2e.protocol.AccountRemoved
import net.jami.e2e.protocol.CreateBareAccount
import net.jami.e2e.protocol.NameRegistrationEnded
import net.jami.e2e.protocol.RegisterName
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.RemoveAccount

/**
 * Name registration edge case: **the requested username is already taken** on the name server.
 * The one genuinely reachable "already exists" case — unlike re-importing an already-loaded
 * identity, which no real user flow can produce.
 *
 * Non-consuming: it deliberately collides with a name we have **already burned**
 * ([net.jami.e2e.memory.MemoryStore.burnedNames]) using a **different** unnamed account, so the
 * registration must fail — nothing new is burned and the claimed asset stays unnamed. (Names on
 * the Jami name server are permanent, so the collision holds even though the owning identity is
 * not currently loaded on the device.)
 *
 * The app treats any non-zero [NameRegistrationEnded.state] as failure without distinguishing
 * the code, so the asserted contract is simply `state != 0` (rejected); the exact code is logged.
 *
 * Requires a previously-burned name (run `account-creation-username` first) and an unnamed
 * account to attempt with (claimed from the pool, else a freshly created bare account).
 */
object RegisterNameTakenScenario : Scenario {
    override val id = "register-name-taken"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        val burned = ctx.memory.burnedNames().firstOrNull()
            ?: return Verdict(false, "no burned name to collide with — run account-creation-username first")
        val taken = burned.name

        // Registration installs/creates an account — start from a proven no-account state.
        if (!ensureNoAccounts(ctx, "A").clean) {
            return Verdict(false, "could not reach a no-account state before registration")
        }

        // Attempt with a DIFFERENT identity than the name's owner. The owner is already 'named'
        // in the registry, so an unnamed claim can never be it.
        val claimed = ctx.memory.claim(named = false)
        val accountId: String = if (claimed != null) {
            ctx.log("attempting to grab taken name '$taken' with unnamed asset ${claimed.fingerprint}")
            ctx.installAsset("A", claimed) ?: return Verdict(false, "failed to install claimed asset")
        } else {
            ctx.log("no unnamed asset — creating a bare account to attempt taken name '$taken'")
            ctx.send("A", CreateBareAccount(displayName = "harness_taken_${System.currentTimeMillis() / 1000}"))
            (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
        }

        try {
            // Name server is only reachable once the account is announced on the DHT.
            ctx.await("A", 60_000) {
                it is RegistrationStateChanged && it.accountId == accountId && it.state == "REGISTERED"
            }
            ctx.log("account REGISTERED — requesting already-taken name '$taken'")

            ctx.send("A", RegisterName(accountId, taken))
            val result = ctx.await("A", 60_000) {
                it is NameRegistrationEnded && it.accountId == accountId
            } as NameRegistrationEnded
            ctx.log("name registration ended: name='${result.name}' state=${result.state}")

            return if (result.state != 0) {
                Verdict(true, "already-taken name '$taken' correctly rejected (state=${result.state})")
            } else {
                // Should be impossible — the name is owned by ${burned.fingerprint}. Record the
                // burn defensively so the registry stays honest, then fail loudly.
                claimed?.let { ctx.memory.markRegistered(it.fingerprint, taken) }
                Verdict(false, "name server WRONGLY granted already-taken name '$taken' (state=0)")
            }
        } finally {
            // Skippable via -PkeepAccounts=true to leave the account on-device for inspection.
            if (!ctx.runConfig.keepAccounts) {
                runCatching {
                    ctx.send("A", RemoveAccount(accountId))
                    ctx.await("A", 10_000) { it is AccountRemoved && it.accountId == accountId }
                }
                // Hardened sweep — guarantee the device is left with no account loaded.
                runCatching { ensureNoAccounts(ctx, "A") }
            } else {
                ctx.log("keepAccounts=true — skipping teardown, leaving account on-device")
            }
        }
    }
}
