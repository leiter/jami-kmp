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
import net.jami.e2e.protocol.CreateJamiAccount
import net.jami.e2e.protocol.RemoveAccount

/**
 * M1 — drives the real `AccountCreationViewModel.createAccount()` path on one device and
 * verifies the account is added (the VM navigates without waiting for REGISTERED, keeping
 * this deterministic). Cleans up by removing the created account.
 *
 * Name-registration / REGISTERED assertion is deferred to M2.
 */
object AccountCreationScenario : Scenario {
    override val id = "account-creation"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        val username = "harness_${System.currentTimeMillis() / 1000}"
        ctx.send("A", CreateJamiAccount(username, password = ""))

        val added = ctx.await("A", timeoutMillis = 20_000) { it is AccountAdded } as AccountAdded
        ctx.log("account added: ${added.accountId}")

        // Cleanup — ephemeral account isolation for this run.
        ctx.send("A", RemoveAccount(added.accountId))
        ctx.await("A", timeoutMillis = 10_000) {
            it is AccountRemoved && it.accountId == added.accountId
        }

        return Verdict(true, "created and removed account ${added.accountId}")
    }
}
