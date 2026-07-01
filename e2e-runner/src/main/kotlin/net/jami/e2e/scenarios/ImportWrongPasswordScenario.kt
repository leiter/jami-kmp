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

/**
 * Import edge case: **password-protected archive imported with the WRONG password.**
 *
 * Reuse-first and non-consuming — claims a `pw` fixture and imports it with a password that
 * is not the archive's. The daemon must reject the import (no usable account restored). See
 * [assertImportRejected] for the exact pass/fail race.
 *
 * Requires a `pw` fixture in the pool (run `seed-pool` first).
 */
object ImportWrongPasswordScenario : Scenario {
    override val id = "import-wrong-password"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict =
        assertImportRejected(ctx, badPassword = "definitely_not_the_password")
}
