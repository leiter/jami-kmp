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
import net.jami.e2e.memory.AccountAsset
import net.jami.e2e.protocol.AccountAdded
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.CreateBareAccount
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.MessageReceived
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.SendMessage

/**
 * F4 regression test — send a real message into a 1:1 swarm **the instant the contact is
 * confirmed**, before the peer's join has propagated (`ConversationMemberEvent(action=1)`), and
 * assert it is still delivered.
 *
 * `send-message` and `default-one-on-one-conversation` both gate their first send on
 * [awaitMemberJoined], which is exactly what papers over F4 — so neither exercises it. This
 * scenario is `send-message` with that gate deliberately removed: A (the request *accepter*,
 * the reliable sending direction — see [ChatConversationGitRewindResyncScenario]'s class doc)
 * fires a message in the ~2-3 s window after `ContactAdded(confirmed=true)` when the swarm is
 * still bootstrapping (`Bootstrap with 0 device(s)` in the daemon log).
 *
 * Pre-fix behaviour (`doc/stabilization-findings-2026-09-04.md`, F4): the message commits
 * locally into a conversation with zero connected peer devices and is **silently dropped** —
 * B never receives it. Expected post-fix behaviour (reference parity — `libjamiclient`
 * `ConversationFacade` sends unconditionally, incl. `Mode.Syncing`, and relies on the daemon to
 * flush once the swarm bootstraps): the send is held by the daemon and delivered within a few
 * seconds of the swarm coming up.
 *
 * Non-consuming, reuse-first, same as `send-message`: distinct pool identities per role, both
 * devices swept back to no-account on teardown.
 */
object SendBeforeMemberJoinScenario : Scenario {
    override val id = "send-before-member-join"
    override val requiredRoles = 2

    // Generous: the daemon may need several seconds to bootstrap the fresh swarm before it can
    // flush the held message. A drop shows up as this whole window elapsing with no MessageReceived.
    private const val DELIVERY_TIMEOUT_MS = 90_000L

    override suspend fun run(ctx: ScenarioContext): Verdict {
        for (role in listOf("A", "B")) {
            if (!ensureNoAccounts(ctx, role).clean) {
                return Verdict(false, "could not reach a no-account state on device[$role]")
            }
        }

        val assets = ctx.memory.claimDistinct(2)
        val aAsset = assets.getOrNull(0)
        val bAsset = assets.getOrNull(1)
        if (aAsset == null || bAsset == null) {
            ctx.log("pool holds ${assets.size} distinct asset(s), need 2 — creating bare account(s) for the rest")
        }
        val aId = provision(ctx, "A", aAsset)
            ?: return Verdict(false, "failed to provision an account on device[A]")
        val bId = provision(ctx, "B", bAsset)
            ?: return Verdict(false, "failed to provision an account on device[B]")
        ctx.log("accounts ready: A=$aId B=$bId")

        try {
            ctx.await("A", 60_000) {
                it is RegistrationStateChanged && it.accountId == aId && it.state == "REGISTERED"
            }
            ctx.await("B", 60_000) {
                it is RegistrationStateChanged && it.accountId == bId && it.state == "REGISTERED"
            }
            ctx.log("both accounts REGISTERED on the DHT")

            ctx.send("A", GetAccountUri(aId))
            val aUri = (ctx.await("A", 5_000) {
                it is AccountUri && it.accountId == aId
            } as AccountUri).uri
            ctx.send("B", GetAccountUri(bId))
            val bUri = (ctx.await("B", 5_000) {
                it is AccountUri && it.accountId == bId
            } as AccountUri).uri
            ctx.log("relayed identities: A=$aUri B=$bUri")
            if (aUri.isBlank() || bUri.isBlank()) {
                return Verdict(false, "empty account URI (A='$aUri' B='$bUri')")
            }

            val conversationId = establishContact(ctx, "B", bId, bUri, "A", aId, aUri).conversationId
            val confirmedAt = System.currentTimeMillis()
            ctx.log("contact confirmed on both devices — swarm conversation '$conversationId' established")

            // NO awaitMemberJoined here — that is the whole point. Send straight into the
            // still-bootstrapping swarm.
            val stamp = confirmedAt
            val aToB = "harness-early-send-$stamp"
            ctx.send("A", SendMessage(aId, bUri, aToB))
            val elapsed = System.currentTimeMillis() - confirmedAt
            ctx.log("A sent into the un-bootstrapped swarm ${elapsed}ms after confirmation — awaiting delivery to B")

            val received = runCatching {
                ctx.await("B", DELIVERY_TIMEOUT_MS) {
                    it is MessageReceived && it.accountId == bId &&
                        it.authorUri.equals(aUri, ignoreCase = true) && it.text == aToB
                } as MessageReceived
            }.getOrNull()

            return if (received != null) {
                Verdict(
                    true,
                    "early send delivered — message sent ${elapsed}ms after confirmation (before member-join) " +
                        "reached B over the swarm; daemon held and flushed it once bootstrapped",
                )
            } else {
                Verdict(
                    false,
                    "F4 REPRODUCED — message sent ${elapsed}ms after confirmation was committed locally but " +
                        "never reached B within ${DELIVERY_TIMEOUT_MS}ms (silent drop into a 0-device swarm)",
                )
            }
        } finally {
            if (!ctx.runConfig.keepAccounts) {
                for (role in listOf("A", "B")) {
                    runCatching { ensureNoAccounts(ctx, role) }
                }
            } else {
                ctx.log("keepAccounts=true — skipping teardown, leaving accounts/conversation on-device")
            }
        }
    }

    private suspend fun provision(ctx: ScenarioContext, role: String, asset: AccountAsset?): String? {
        if (asset != null) {
            ctx.log("device[$role]: reusing asset ${asset.fingerprint} from pool (non-consuming)")
            return ctx.installAsset(role, asset)
        }
        ctx.log("device[$role]: no pool asset left — creating a bare account")
        ctx.send(role, CreateBareAccount(displayName = "harness_${role.lowercase()}_${System.currentTimeMillis() / 1000}"))
        return (ctx.await(role, 20_000) { it is AccountAdded } as AccountAdded).accountId
    }
}
