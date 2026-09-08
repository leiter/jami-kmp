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
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.AccountsSnapshot
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.GetAccounts

/**
 * Rescue capture: registers whatever pair of accounts is **already live** on both devices —
 * confirmed contact, established conversation — into the fixture pool, without establishing
 * anything itself. The companion to `capture-account` (which does this for a single account) for
 * the two-device pair case: a run of `default-one-on-one-conversation` that fails *after* the
 * handshake but *before* its own capture step (e.g. a message never arrived) still leaves real,
 * usable state on both devices — this scenario is how that state gets named and reused instead
 * of being silently lost to the next run's `ensureNoAccounts` precondition.
 *
 * Requires `-PaccountState=<label>` (what to save it as) and `-PconversationId=<id>` (which
 * conversation to capture — there is no daemon query to discover it, so the caller supplies the
 * id it already knows from the failed run's own timeline). Captures the same three fixture kinds
 * `default-one-on-one-conversation` does: the whole-app-tar pair, a portable account archive per
 * role, and a standalone conversation-repo per role. Message count is recorded as whatever the
 * caller believes was actually confirmed-received (not sent) — this scenario has no way to
 * verify it independently, so callers should pass `0` unless they know otherwise.
 *
 * Purely additive: no `ensureNoAccounts`, no teardown `finally` — it must never remove the
 * live state it exists to preserve.
 */
object CaptureLivePairScenario : Scenario {
    override val id = "capture-live-pair"
    override val requiredRoles = 2

    override suspend fun run(ctx: ScenarioContext): Verdict {
        val label = ctx.runConfig.accountState
            ?: return Verdict(false, "missing -PaccountState=<label> — what to save this pair as")
        val conversationId = ctx.runConfig.conversationId
            ?: return Verdict(false, "missing -PconversationId=<id> — which conversation to capture")

        ctx.send("A", GetAccounts)
        val aIds = (ctx.await("A", 10_000) { it is AccountsSnapshot } as AccountsSnapshot).ids
        val aId = aIds.firstOrNull()
            ?: return Verdict(false, "no live account on device[A] to capture")
        ctx.send("B", GetAccounts)
        val bIds = (ctx.await("B", 10_000) { it is AccountsSnapshot } as AccountsSnapshot).ids
        val bId = bIds.firstOrNull()
            ?: return Verdict(false, "no live account on device[B] to capture")
        ctx.log("found live accounts: A=$aId B=$bId")

        ctx.send("A", GetAccountUri(aId))
        val aUri = (ctx.await("A", 10_000) { it is AccountUri && it.accountId == aId } as AccountUri).uri
        ctx.send("B", GetAccountUri(bId))
        val bUri = (ctx.await("B", 10_000) { it is AccountUri && it.accountId == bId } as AccountUri).uri

        val saved = ctx.captureConversationPairAsset(
            roleA = "A",
            roleB = "B",
            label = label,
            fingerprintA = aUri,
            fingerprintB = bUri,
            nameA = "",
            nameB = "",
            avatarSet = false,
            conversationId = conversationId,
            messageCount = 0,
        )
        if (saved == null) return Verdict(false, "failed to capture whole-app-tar pair under '$label'")

        if (ctx.captureAsset("A", aId, registeredName = null, password = "") == null) {
            ctx.log("WARNING: failed to capture account archive for A under '$label'")
        }
        if (ctx.captureAsset("B", bId, registeredName = null, password = "") == null) {
            ctx.log("WARNING: failed to capture account archive for B under '$label'")
        }
        if (ctx.captureConversationRepoAsset("A", aId, conversationId, "$label-A") == null) {
            ctx.log("WARNING: failed to capture conversation-repo fixture for A under '$label-A'")
        }
        if (ctx.captureConversationRepoAsset("B", bId, conversationId, "$label-B") == null) {
            ctx.log("WARNING: failed to capture conversation-repo fixture for B under '$label-B'")
        }

        return Verdict(
            true,
            "captured live pair '$label': A=$aId ($aUri) B=$bId ($bUri) conversationId=$conversationId, " +
                "0 messages exchanged (contact confirmed, conversation established, no text yet)",
        )
        // No finally: purely additive, must never touch the live state it exists to preserve.
    }
}
