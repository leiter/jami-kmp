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
 * Name registration **in isolation** — the consuming operation. Reuse-first: claim an
 * existing **unnamed** account from the registry and register a name on it, falling back to
 * creating a bare account only when the pool has none. On success the asset is flipped to
 * *named* in the registry ([net.jami.e2e.memory.MemoryStore.markRegistered]) and the burned
 * username is recorded — so we never register the same identity twice.
 *
 * The name registration is the genuine DHT-async proof: `REGISTERED` then
 * [NameRegistrationEnded] state == 0.
 */
object AccountCreationUsernameScenario : Scenario {
    override val id = "account-creation-username"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        // Registration installs/creates an account — start from a proven no-account state so a
        // stray from a prior run can't skew the DHT/registration observations.
        if (!ensureNoAccounts(ctx, "A").clean) {
            return Verdict(false, "could not reach a no-account state before registration")
        }

        val username = MemorableNames.next(ctx.memory)

        // Reuse an unnamed asset if one exists; otherwise create a bare account (last resort).
        val claimed = ctx.memory.claim(named = false, hasPassword = false)
        val fromPool = claimed != null
        val accountId: String = if (claimed != null) {
            ctx.log("reusing unnamed asset ${claimed.fingerprint} from pool")
            ctx.installAsset("A", claimed) ?: return Verdict(false, "failed to install claimed asset")
        } else {
            ctx.log("no unnamed asset available — creating a bare account")
            ctx.send("A", CreateBareAccount(displayName = "harness_reg_${System.currentTimeMillis() / 1000}"))
            (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
        }

        try {
            // Account must be announced on the DHT before the name server is reachable.
            ctx.await("A", 60_000) {
                it is RegistrationStateChanged && it.accountId == accountId && it.state == "REGISTERED"
            }
            ctx.log("account REGISTERED on the DHT — registering username '$username'")

            ctx.send("A", RegisterName(accountId, username))
            val result = ctx.await("A", 60_000) {
                it is NameRegistrationEnded && it.accountId == accountId
            } as NameRegistrationEnded
            ctx.log("name registration ended: name='${result.name}' state=${result.state}")

            if (result.state != 0) {
                return Verdict(false, "name registration failed for '$username' (state=${result.state})")
            }

            // The claimed identity now owns a name — flip it in the registry (one-way).
            claimed?.let {
                ctx.memory.markRegistered(it.fingerprint, username)
                ctx.log("asset ${it.fingerprint} flipped unnamed → named '$username'")
            }
            return Verdict(true, "registered username '$username' (${if (fromPool) "reused pool asset" else "freshly created"})")
        } finally {
            // Remove from the device; the (now named) identity persists in its archive.
            // Skippable via -PkeepAccounts=true to leave the registered account on-device.
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
