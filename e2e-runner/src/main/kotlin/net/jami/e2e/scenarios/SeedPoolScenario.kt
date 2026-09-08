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
 * Producer scenario — populate the asset pool with **unnamed accounts only**, so it burns
 * **no** names on the name server. Named assets are never seeded here: they emerge naturally
 * when [AccountCreationUsernameScenario] claims an unnamed asset and flips it to named.
 *
 * It first reports the current pool composition and then creates **only what is missing** to
 * reach the per-state targets (idempotent — safe to re-run). We start with
 * unnamed / no-password; the password tier is built up later (password fixtures feed the
 * import edge-case scenarios and also burn nothing).
 */
object SeedPoolScenario : Scenario {
    override val id = "seed-pool"
    override val requiredRoles = 1

    private const val SEED_PW = "harness_pw"

    /** Per-state targets. Bump [TARGET_UNNAMED_PW] when building up the password tier. */
    private const val TARGET_UNNAMED_NOPW = 2
    private const val TARGET_UNNAMED_PW = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        // 1. Check pool status — only create what is necessary.
        val pool = ctx.memory.all()
        val haveNoPw = pool.count { !it.named && !it.hasPassword }
        val havePw = pool.count { !it.named && it.hasPassword }
        ctx.log(
            "pool status: ${pool.size} assets " +
                "(${pool.count { it.named }} named, unnamed-nopw=$haveNoPw, unnamed-pw=$havePw)",
        )

        val toCreate = buildList {
            repeat((TARGET_UNNAMED_NOPW - haveNoPw).coerceAtLeast(0)) { add("") }
            repeat((TARGET_UNNAMED_PW - havePw).coerceAtLeast(0)) { add(SEED_PW) }
        }
        if (toCreate.isEmpty()) {
            return Verdict(true, "pool already satisfies targets (${pool.size} assets); nothing created")
        }
        ctx.log("creating ${toCreate.size} unnamed account(s) to top up the pool")

        // 2. Create only the missing unnamed accounts; capture each, then clean the device.
        val stamp = System.currentTimeMillis() / 1000
        var created = 0
        toCreate.forEachIndexed { i, password ->
            // The archive password must be set at creation — exportToFile uses the account's
            // *current* archive password to unlock the key.
            ctx.send("A", CreateBareAccount(displayName = "harness_seed_${stamp}_$i", password = password))
            val accountId = (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId

            ctx.captureAsset("A", accountId, registeredName = null, password = password)
                ?: return Verdict(false, "failed to capture seed asset (pw=${password.isNotEmpty()})")
            created++

            runCatching {
                ctx.send("A", RemoveAccount(accountId))
                ctx.await("A", 10_000) { it is AccountRemoved && it.accountId == accountId }
            }
        }

        val now = ctx.memory.all()
        ctx.log("pool now holds ${now.size} assets (unnamed-nopw=${now.count { !it.named && !it.hasPassword }})")
        return Verdict(true, "seeded $created unnamed asset(s); pool size ${now.size}")
    }
}
