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
 * Orchestration, not a single-purpose test: establishes the **standard starting point** for
 * hand-driven chat investigation — two accounts, a real DHT-confirmed contact, a real swarm
 * conversation with a few exchanged messages — and then, unlike every other scenario, does
 * **not** tear any of it down. The live devices are the deliverable: run this once, then poke at
 * the result by hand (adb shell, daemon logs) or with a narrowly-scoped diagnostic scenario, e.g.
 * the A-join precondition investigation for `chat-conversation-git-rewind-resync`.
 *
 * `-PaccountState=<label>` turns this from an ephemeral one-off into a **named, reusable**
 * fixture:
 * - label given, found in the registry → fast path, just [ScenarioContext.installConversationPairAsset]
 *   restores that exact state on both devices — no fresh handshake needed.
 * - label given, not found → builds fresh (below), then [ScenarioContext.captureConversationPairAsset]
 *   saves it under that name so the *next* run with the same label takes the fast path.
 * - no label → builds fresh, logs the handoff line, does not persist. Purely ephemeral.
 *
 * No `finally` teardown block, deliberately: running this scenario again is itself idempotent
 * (`ensureNoAccounts` at the top of the fresh-build path), so there is no separate cleanup to
 * remember to call.
 */
object DefaultOneOnOneConversationScenario : Scenario {
    override val id = "default-one-on-one-conversation"
    override val requiredRoles = 2

    private const val MESSAGES_TO_SEND = 3

    override suspend fun run(ctx: ScenarioContext): Verdict {
        val label = ctx.runConfig.accountState

        if (label != null) {
            val existing = ctx.memory.claimConversationPair(label)
            if (existing != null) {
                if (!ctx.installConversationPairAsset(existing, "A", "B")) {
                    return Verdict(false, "found account state '$label' but failed to restore it")
                }
                ctx.log(
                    "handoff: restored account state '$label' — A=${existing.fingerprintA} " +
                        "B=${existing.fingerprintB} conversationId=${existing.conversationId} " +
                        "messages=${existing.messageCount}",
                )
                return Verdict(true, "restored named account state '$label' on both devices")
            }
            ctx.log("account state '$label' not found in registry — building fresh and saving under that name")
        }

        for (role in listOf("A", "B")) {
            if (!ensureNoAccounts(ctx, role).clean) {
                return Verdict(false, "could not reach a no-account state on device[$role]")
            }
        }

        // Identity/name/avatar are irrelevant here — this establishes sync-ready state, not a
        // profile fixture (see build-conversation-fixture for that).
        val assets = ctx.memory.claimDistinct(2, named = false, hasPassword = false)
        val aAsset = assets.getOrNull(0)
        val bAsset = assets.getOrNull(1)
        val aId = provision(ctx, "A", aAsset)
            ?: return Verdict(false, "failed to provision an account on device[A]")
        val bId = provision(ctx, "B", bAsset)
            ?: return Verdict(false, "failed to provision an account on device[B]")
        ctx.log("accounts ready: A=$aId B=$bId")

        ctx.await("A", 60_000) {
            it is RegistrationStateChanged && it.accountId == aId && it.state == "REGISTERED"
        }
        ctx.await("B", 60_000) {
            it is RegistrationStateChanged && it.accountId == bId && it.state == "REGISTERED"
        }
        ctx.log("both accounts REGISTERED on the DHT")

        ctx.send("A", GetAccountUri(aId))
        val aUri = (ctx.await("A", 5_000) { it is AccountUri && it.accountId == aId } as AccountUri).uri
        ctx.send("B", GetAccountUri(bId))
        val bUri = (ctx.await("B", 5_000) { it is AccountUri && it.accountId == bId } as AccountUri).uri
        if (aUri.isBlank() || bUri.isBlank()) return Verdict(false, "empty account URI (A='$aUri' B='$bUri')")

        val conversationId = establishContact(ctx, "B", bId, bUri, "A", aId, aUri).conversationId
        ctx.log("contact confirmed — swarm conversation '$conversationId' established")

        // A sends — the reliable direction (B, the contact-request initiator, hits the
        // known initiator-can't-resolve-its-own-conversation bug, doc/TODO.md 2026-08-14).
        val stamp = System.currentTimeMillis()
        repeat(MESSAGES_TO_SEND) { i ->
            val text = "default-conversation-msg-${i + 1}-$stamp"
            ctx.send("A", SendMessage(aId, bUri, text))
            ctx.await("B", 60_000) {
                it is MessageReceived && it.accountId == bId &&
                    it.authorUri.equals(aUri, ignoreCase = true) && it.text == text
            }
        }
        ctx.log("B confirmed all $MESSAGES_TO_SEND real messages from A")

        // The actual deliverable: everything needed to pick this session back up by hand.
        ctx.log(
            "handoff: A=$aId ($aUri) on role A, B=$bId ($bUri) on role B, " +
                "conversationId=$conversationId, $MESSAGES_TO_SEND messages sent A→B",
        )

        if (label != null) {
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
                messageCount = MESSAGES_TO_SEND,
            )
            if (saved == null) {
                ctx.log("WARNING: failed to persist account state '$label' — session is still live, just not saved")
            } else {
                ctx.log("saved this state as '$label' — future runs with -PaccountState=$label restore it directly")
            }
        }

        return Verdict(
            true,
            "established a live one-on-one conversation ($MESSAGES_TO_SEND messages)" +
                if (label != null) " and saved as '$label'" else " (ephemeral — no -PaccountState given)",
        )
        // No finally: teardown is intentionally skipped — see class doc.
    }

    private suspend fun provision(ctx: ScenarioContext, role: String, asset: AccountAsset?): String? {
        if (asset != null) {
            ctx.log("device[$role]: reusing unnamed asset ${asset.fingerprint} from pool")
            return ctx.installAsset(role, asset)
        }
        ctx.log("device[$role]: no unnamed pool asset left — creating a bare account")
        ctx.send(role, CreateBareAccount(displayName = "harness_${role.lowercase()}_${System.currentTimeMillis() / 1000}"))
        return (ctx.await(role, 20_000) { it is AccountAdded } as AccountAdded).accountId
    }
}
