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
import net.jami.e2e.protocol.AccountsSnapshot
import net.jami.e2e.protocol.GetAccounts

/**
 * Registers whatever account is **already loaded** on device[A] into the persistent fixture
 * pool ([ScenarioContext.captureAsset]) — export + pull, no removal, no wipe. Companion to
 * `-PkeepAccounts=true`: a scenario that left a live account on-device (instead of tearing it
 * down) can be followed by this one to turn that live state into a reusable, named pool asset
 * instead of it only existing as a one-off on-device session that a later run's
 * `ensureNoAccounts` precondition would just remove.
 *
 * Purely additive — never touches device state (no `ensureNoAccounts`, no `finally`), so it's
 * always safe to run regardless of `-PkeepAccounts`.
 *
 * Assumes an unprotected archive (exports with an empty password); a password-protected account
 * would need that threaded through — not needed yet.
 */
object CaptureAccountScenario : Scenario {
    override val id = "capture-account"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        ctx.send("A", GetAccounts)
        val ids = (ctx.await("A", 10_000) { it is AccountsSnapshot } as AccountsSnapshot).ids
        if (ids.isEmpty()) return Verdict(false, "no account loaded on device[A] to capture")
        if (ids.size > 1) ctx.log("device[A] has ${ids.size} accounts loaded — capturing all of them")

        val captured = ids.mapNotNull { id ->
            ctx.captureAsset("A", id, registeredName = null, password = "")
        }
        return if (captured.size == ids.size) {
            Verdict(true, "captured ${captured.size} account(s) into the pool: ${captured.map { it.fingerprint }}")
        } else {
            Verdict(false, "captured ${captured.size}/${ids.size} account(s) — see log for which failed")
        }
    }
}
