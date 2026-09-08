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
import net.jami.e2e.protocol.RemoveAccount

/**
 * Single-device account creation **without username registration** — a bare account that
 * never contacts the name server. Verifies the account is added, then cleans up.
 *
 * The complement of [AccountCreationUsernameScenario]; together they cover both creation
 * paths (with / without a registered username).
 */
object AccountCreationBareScenario : Scenario {
    override val id = "account-creation-bare"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        ctx.send("A", CreateBareAccount(displayName = "harness_bare_${System.currentTimeMillis() / 1000}"))

        val added = ctx.await("A", timeoutMillis = 20_000) { it is AccountAdded } as AccountAdded
        ctx.log("bare account added (no username): ${added.accountId}")

        // Cleanup — ephemeral account isolation for this run.
        ctx.send("A", RemoveAccount(added.accountId))
        ctx.await("A", timeoutMillis = 10_000) {
            it is AccountRemoved && it.accountId == added.accountId
        }

        return Verdict(true, "created and removed bare account ${added.accountId} (no name registration)")
    }
}
