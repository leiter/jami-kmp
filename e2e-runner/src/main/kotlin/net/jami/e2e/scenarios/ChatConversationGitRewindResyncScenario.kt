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
 * Does the daemon's swarm sync **self-heal** when one peer's local copy of a conversation loses
 * recent commits? Establishes a real two-device swarm conversation, has A send several real
 * messages that B confirms receiving (real git commits on both sides), then deliberately
 * rewinds B's on-disk copy of that conversation by a few commits
 * ([ScenarioContext.rewindConversation] — pull the repo, `git reset --hard HEAD~n`, push it
 * back, relaunch), and observes whether B's daemon notices it's behind A and re-fetches the
 * "forgotten" messages on its own once both are back online.
 *
 * Messages are sent by **A**, not B, deliberately: B is the contact-request *initiator*
 * (`SendContactRequest`), and the still-open sender bug (`doc/TODO.md`, 2026-08-14) means the
 * initiator's device never resolves its own new swarm conversation locally and can't send into
 * it. A — the request *receiver*, via `AcceptContactRequest` — is the reliable sending
 * direction. B is rewound instead of A purely so B keeps a passive, receive-only role
 * throughout (fewer moving parts to attribute a failure to).
 *
 * Directly relevant to the still-open "malformed-commit sync wedge" bug (`doc/TODO.md`, found
 * 2026-08-13) — this gives a repeatable way to provoke and observe that class of behavior on
 * demand. The daemon source confirms this is a *supported* recovery path, not something we'd be
 * forcing it into by accident: commit validation is cryptographic/signature-based, not
 * positional (no check that a ref hasn't moved backward), and there's no persistent in-memory
 * conversation cache to invalidate — see `doc/end2endTesting.md`.
 *
 * The verdict is **diagnostic, not binary**: a nudge message (sent after the rewind) confirms
 * the sync channel itself still works; whether the *reverted* messages also come back separates
 * "full self-heal" from "daemon only syncs forward, doesn't backfill" — both are meaningful,
 * reportable outcomes.
 */
object ChatConversationGitRewindResyncScenario : Scenario {
    override val id = "chat-conversation-git-rewind-resync"
    override val requiredRoles = 2

    private const val MESSAGES_TO_SEND = 4
    private const val COMMITS_TO_REVERT = 2

    override suspend fun run(ctx: ScenarioContext): Verdict {
        for (role in listOf("A", "B")) {
            if (!ensureNoAccounts(ctx, role).clean) {
                return Verdict(false, "could not reach a no-account state on device[$role]")
            }
        }

        // Identity/name/avatar are irrelevant here — this tests sync mechanics, not fixtures.
        val assets = ctx.memory.claimDistinct(2, named = false, hasPassword = false)
        val aAsset = assets.getOrNull(0)
        val bAsset = assets.getOrNull(1)
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
            val aUri = (ctx.await("A", 5_000) { it is AccountUri && it.accountId == aId } as AccountUri).uri
            ctx.send("B", GetAccountUri(bId))
            val bUri = (ctx.await("B", 5_000) { it is AccountUri && it.accountId == bId } as AccountUri).uri
            if (aUri.isBlank() || bUri.isBlank()) return Verdict(false, "empty account URI (A='$aUri' B='$bUri')")

            // Real contact handshake — same proven path as two-device-contact.
            val conversationId = establishContact(ctx, "B", bId, bUri, "A", aId, aUri).conversationId
            ctx.log("contact confirmed — swarm conversation '$conversationId' established")

            // A sends MESSAGES_TO_SEND real messages (the reliable direction — see class doc);
            // B confirms each as a real commit before moving on, so we know exactly what exists
            // on both sides before touching anything.
            val stamp = System.currentTimeMillis()
            val sentTexts = (1..MESSAGES_TO_SEND).map { "rewind-test-msg-$it-$stamp" }
            for (text in sentTexts) {
                ctx.send("A", SendMessage(aId, bUri, text))
                ctx.await("B", 60_000) {
                    it is MessageReceived && it.accountId == bId &&
                        it.authorUri.equals(aUri, ignoreCase = true) && it.text == text
                }
            }
            ctx.log("B confirmed all $MESSAGES_TO_SEND real messages before rewind")

            // The rewind: pull B's copy of the conversation, reset --hard back COMMITS_TO_REVERT
            // commits, push it back, relaunch B under the same role.
            val reverted = ctx.rewindConversation("B", bId, conversationId, COMMITS_TO_REVERT)
            if (reverted == 0) {
                return Verdict(false, "rewind produced 0 reverted commits — setup/tooling issue, not a sync result")
            }
            ctx.log("rewound B's local conversation copy by $reverted commit(s); relaunched B")

            ctx.await("B", 60_000) {
                it is RegistrationStateChanged && it.accountId == bId && it.state == "REGISTERED"
            }
            ctx.log("B back on the DHT after rewind + relaunch")

            // Nudge: A sends one more real message. This must arrive regardless of whether
            // backfill works — it isolates "sync channel broken" from "no backfill".
            val nudgeText = "rewind-test-nudge-$stamp"
            ctx.send("A", SendMessage(aId, bUri, nudgeText))
            val nudgeArrived = runCatching {
                ctx.await("B", 60_000) {
                    it is MessageReceived && it.accountId == bId &&
                        it.authorUri.equals(aUri, ignoreCase = true) && it.text == nudgeText
                }
            }.isSuccess
            ctx.log(if (nudgeArrived) "nudge message arrived on B" else "nudge message did NOT arrive on B within 60s")

            val revertedTexts = sentTexts.takeLast(reverted)
            val recoveredCount = revertedTexts.count { text ->
                runCatching {
                    ctx.await("B", 15_000) {
                        it is MessageReceived && it.accountId == bId &&
                            it.authorUri.equals(aUri, ignoreCase = true) && it.text == text
                    }
                }.isSuccess
            }
            ctx.log("$recoveredCount of $reverted reverted message(s) resynced onto B")

            return when {
                !nudgeArrived ->
                    Verdict(false, "nudge never arrived — sync channel itself is broken, not just backfill")
                recoveredCount == reverted ->
                    Verdict(true, "full self-heal: nudge + all $reverted reverted message(s) resynced onto B")
                recoveredCount == 0 ->
                    Verdict(
                        true,
                        "partial finding: nudge arrived but daemon did NOT backfill the $reverted reverted " +
                            "message(s) on reconnect — sync only goes forward from new activity",
                    )
                else ->
                    Verdict(true, "partial finding: nudge arrived, $recoveredCount of $reverted reverted message(s) resynced")
            }
        } finally {
            // Skippable via -PkeepAccounts=true to leave both devices' post-rewind state on-device
            // for hand inspection (the actual point of the A-join precondition investigation).
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
            ctx.log("device[$role]: reusing unnamed asset ${asset.fingerprint} from pool")
            return ctx.installAsset(role, asset)
        }
        ctx.log("device[$role]: no unnamed pool asset left — creating a bare account")
        ctx.send(role, CreateBareAccount(displayName = "harness_${role.lowercase()}_${System.currentTimeMillis() / 1000}"))
        return (ctx.await(role, 20_000) { it is AccountAdded } as AccountAdded).accountId
    }
}
