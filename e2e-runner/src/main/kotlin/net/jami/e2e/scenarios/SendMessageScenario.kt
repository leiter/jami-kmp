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
 * M4 (messaging half) — two devices exchange a real text message over an established 1:1
 * swarm conversation.
 *
 * This scenario proves the thing `two-device-contact` deliberately stops short of: not just
 * that a contact relationship can be established over the DHT, but that the resulting swarm
 * conversation actually carries real message traffic in both directions. Setup mirrors
 * `two-device-contact` exactly (same pool-fixture reuse, same contact-request/accept
 * handshake) — messaging is the payload this scenario adds on top.
 *
 * **Reuse-first and non-consuming**, like `two-device-contact`: each role gets a *distinct*
 * identity claimed from the fixture pool ("the already-available accounts") rather than
 * creating fresh ones, and both devices are swept back to a no-account state on teardown. No
 * name is ever burned.
 */
object SendMessageScenario : Scenario {
    override val id = "send-message"
    override val requiredRoles = 2

    override suspend fun run(ctx: ScenarioContext): Verdict {
        for (role in listOf("A", "B")) {
            if (!ensureNoAccounts(ctx, role).clean) {
                return Verdict(false, "could not reach a no-account state on device[$role]")
            }
        }

        // 1. Distinct identities per role, reused from the pool — never fresh unless the pool
        // is empty (see provision()).
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
            // 2. Wait until both accounts are announced on the DHT.
            ctx.await("A", 60_000) {
                it is RegistrationStateChanged && it.accountId == aId && it.state == "REGISTERED"
            }
            ctx.await("B", 60_000) {
                it is RegistrationStateChanged && it.accountId == bId && it.state == "REGISTERED"
            }
            ctx.log("both accounts REGISTERED on the DHT")

            // 3. Out-of-band identity relay over the coordination channel.
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

            // 4. Establish the contact relationship (same handshake as two-device-contact).
            val conversationId = establishContact(ctx, "B", bId, bUri, "A", aId, aUri).conversationId
            ctx.log("contact confirmed on both devices — swarm conversation established")

            // Gate B's send on B's own confirmation that it has actually processed A's join
            // commit — sending immediately after ContactAdded(confirmed=true) raced B's own
            // swarm channel to A not being fully live yet (2026-08-17: B's message got its own
            // echo but never reached A, twice, consistently — see doc/end2endTesting.md's A-join
            // precondition writeup).
            awaitMemberJoined(ctx, "B", bId, conversationId, aUri, timeoutMillis = 30_000)
            ctx.log("B confirmed it has processed A's join — safe to send")

            // 5. B → A: the core proof. A observes a message whose author is B, not its own echo.
            val stamp = System.currentTimeMillis()
            val bToA = "harness-msg-b-to-a-$stamp"
            ctx.send("B", SendMessage(bId, aUri, bToA))
            val received1 = ctx.await("A", 60_000) {
                it is MessageReceived && it.accountId == aId && it.authorUri.equals(bUri, ignoreCase = true) && it.text == bToA
            } as MessageReceived
            ctx.log("A received B's message over the real swarm: '${received1.text}'")

            // 6. A → B: the reverse leg, confirming the conversation is a live two-way channel,
            // not just a one-shot delivery.
            val aToB = "harness-msg-a-to-b-$stamp"
            ctx.send("A", SendMessage(aId, bUri, aToB))
            val received2 = ctx.await("B", 60_000) {
                it is MessageReceived && it.accountId == bId && it.authorUri.equals(aUri, ignoreCase = true) && it.text == aToB
            } as MessageReceived
            ctx.log("B received A's reply over the real swarm: '${received2.text}'")

            return Verdict(true, "bidirectional messaging confirmed over the real swarm (B→A and A→B)")
        } finally {
            // Non-consuming: sweep both devices back to a proven no-account state. The contact
            // and messages exchanged here live only in the on-device copies, which are removed;
            // the host archives keep the original contact-free identities.
            // Skippable via -PkeepAccounts=true to leave the conversation on both devices.
            if (!ctx.runConfig.keepAccounts) {
                for (role in listOf("A", "B")) {
                    runCatching { ensureNoAccounts(ctx, role) }
                }
            } else {
                ctx.log("keepAccounts=true — skipping teardown, leaving accounts/conversation on-device")
            }
        }
    }

    /**
     * Put an account on [role]'s device: install [asset] if the pool supplied one, otherwise
     * create a **bare** account (no username → the name server is never contacted). Returns the
     * on-device accountId, or null on failure.
     */
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
