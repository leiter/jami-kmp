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
import net.jami.e2e.protocol.MessageReceived
import net.jami.e2e.protocol.SendMessage

/**
 * Probes whether real send/receive works against an **already-settled** conversation, restored
 * from a named fixture (`-PaccountState=<label>`) rather than freshly handshaken. Deliberately
 * isolates messaging from the handshake/join-timing path: `send-message` and
 * `default-one-on-one-conversation` both send immediately after their own fresh handshake, which
 * conflates "did the handshake settle in time" with "does the send path work at all" — this
 * scenario removes the first variable entirely by starting from a conversation that has been
 * sitting confirmed for as long as the fixture has existed.
 *
 * Round trip, not one-way, mirroring `send-message`'s B→A / A→B pattern: proves the channel is
 * live in both directions, not just a one-shot delivery.
 *
 * Fails fast if `-PaccountState=<label>` is missing or doesn't match a fixture in the registry —
 * this scenario only tests against a known, already-established conversation, it never builds
 * one (see `default-one-on-one-conversation`/`capture-live-pair` for that).
 */
object SendReplyRoundtripScenario : Scenario {
    override val id = "send-reply-roundtrip"
    override val requiredRoles = 2

    override suspend fun run(ctx: ScenarioContext): Verdict {
        val label = ctx.runConfig.accountState
            ?: return Verdict(false, "missing -PaccountState=<label> — which fixture to test against")
        val pair = ctx.memory.claimConversationPair(label)
            ?: return Verdict(false, "no account state named '$label' in the registry — see e2eListAccountStates")

        if (!ctx.installConversationPairAsset(pair, "A", "B")) {
            return Verdict(false, "found account state '$label' but failed to restore it")
        }
        ctx.log(
            "restored '$label': A=${pair.fingerprintA} B=${pair.fingerprintB} " +
                "conversationId=${pair.conversationId} (had ${pair.messageCount} messages at capture time)",
        )

        try {
            ctx.send("A", GetAccounts)
            val aId = (ctx.await("A", 10_000) { it is AccountsSnapshot } as AccountsSnapshot).ids.firstOrNull()
                ?: return Verdict(false, "restored device[A] reports no account loaded")
            ctx.send("B", GetAccounts)
            val bId = (ctx.await("B", 10_000) { it is AccountsSnapshot } as AccountsSnapshot).ids.firstOrNull()
                ?: return Verdict(false, "restored device[B] reports no account loaded")
            ctx.log("restored accounts: A=$aId B=$bId")

            // A → B first: this is the direction that failed against a freshly-established
            // conversation on 2026-08-17 (see doc/TODO.md) — the direct thing this scenario
            // exists to re-test against a settled one instead.
            val stamp = System.currentTimeMillis()
            val aToB = "roundtrip-a-to-b-$stamp"
            ctx.send("A", SendMessage(aId, pair.fingerprintB, aToB))
            val received1 = ctx.await("B", 60_000) {
                it is MessageReceived && it.accountId == bId &&
                    it.authorUri.equals(pair.fingerprintA, ignoreCase = true) && it.text == aToB
            } as MessageReceived
            ctx.log("B received A's message over the real swarm: '${received1.text}'")

            val bToA = "roundtrip-b-to-a-$stamp"
            ctx.send("B", SendMessage(bId, pair.fingerprintA, bToA))
            val received2 = ctx.await("A", 60_000) {
                it is MessageReceived && it.accountId == aId &&
                    it.authorUri.equals(pair.fingerprintB, ignoreCase = true) && it.text == bToA
            } as MessageReceived
            ctx.log("A received B's reply over the real swarm: '${received2.text}'")

            return Verdict(
                true,
                "bidirectional messaging confirmed against settled fixture '$label' (A→B and B→A)",
            )
        } finally {
            // Non-consuming: the fixture's stored archives are never mutated by installing them.
            // Skippable via -PkeepAccounts=true to leave the (now-messaged) conversation live.
            if (!ctx.runConfig.keepAccounts) {
                for (role in listOf("A", "B")) {
                    runCatching { ensureNoAccounts(ctx, role) }
                }
            } else {
                ctx.log("keepAccounts=true — skipping teardown, leaving accounts/conversation on-device")
            }
        }
    }
}
