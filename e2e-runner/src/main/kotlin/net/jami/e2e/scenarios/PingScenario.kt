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
import net.jami.e2e.protocol.Ping
import net.jami.e2e.protocol.Pong

/**
 * M0 — proves the whole transport end to end: install → adb reverse → app + agent boot →
 * role assignment → bidirectional WebSocket → ledger. No daemon interaction.
 */
object PingScenario : Scenario {
    override val id = "ping"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        ctx.send("A", Ping)
        ctx.await("A", timeoutMillis = 5_000) { it is Pong }
        return Verdict(true, "received Pong")
    }
}
