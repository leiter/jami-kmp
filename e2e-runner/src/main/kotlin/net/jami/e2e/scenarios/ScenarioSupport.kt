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

import net.jami.e2e.ScenarioContext
import net.jami.e2e.protocol.AcceptContactRequest
import net.jami.e2e.protocol.AccountRemoved
import net.jami.e2e.protocol.AccountsSnapshot
import net.jami.e2e.protocol.ContactAdded
import net.jami.e2e.protocol.GetAccounts
import net.jami.e2e.protocol.IncomingContactRequest
import net.jami.e2e.protocol.RemoveAccount
import net.jami.e2e.protocol.SendContactRequest

/** Outcome of [ensureNoAccounts]. */
data class CleanResult(val startedWith: Int, val clean: Boolean)

/**
 * Force [role]'s device into a **no-account-loaded** state — the real precondition for an
 * import/onboarding flow — and prove it, rather than assuming it.
 *
 * This matters because the harness reports account add/remove by **diffing the accounts flow
 * from its value at connect time**: any account stranded by a previous run (a harness reinstall
 * is an *update* — it never wipes daemon data — and a mid-scenario failure can leave one behind)
 * is already in that baseline and would be **silently invisible** in the timeline while still
 * skewing the daemon's behaviour. So we explicitly snapshot, remove everything, and re-snapshot
 * to confirm empty.
 *
 * Returns how many accounts were present and whether the device is confirmed clean.
 */
suspend fun ensureNoAccounts(ctx: ScenarioContext, role: String = "A"): CleanResult {
    ctx.send(role, GetAccounts)
    val before = (ctx.await(role, 10_000) { it is AccountsSnapshot } as AccountsSnapshot).ids
    if (before.isEmpty()) {
        ctx.log("device[$role] confirmed clean — no account loaded")
        return CleanResult(0, clean = true)
    }

    ctx.log("device[$role] has ${before.size} pre-existing account(s) ${before} — removing for a clean import state")
    for (id in before) {
        ctx.send(role, RemoveAccount(id))
        runCatching { ctx.await(role, 10_000) { it is AccountRemoved && it.accountId == id } }
    }

    ctx.send(role, GetAccounts)
    val after = (ctx.await(role, 10_000) { it is AccountsSnapshot } as AccountsSnapshot).ids
    val clean = after.isEmpty()
    if (clean) ctx.log("device[$role] now clean — no account loaded")
    else ctx.log("device[$role] STILL has ${after.size} account(s) after reset: $after")
    return CleanResult(before.size, clean = clean)
}

/** The derived swarm conversation from a completed [establishContact]/[acceptAndConfirmContact]. */
data class ContactHandshakeResult(val conversationId: String)

/**
 * Step 1 of the handshake — the CORE PROOF half: [initiatorRole] sends a real contact request
 * to [accepterRole] over the DHT; [accepterRole] observes it arrive. Split out from
 * [acceptAndConfirmContact] (rather than folded into one [establishContact]) because
 * `two-device-contact` treats *this* half as a hard requirement (an uncaught timeout here is a
 * real scenario failure) while treating the accept/confirm half as best-effort — a distinction
 * that would be lost if both were bundled behind a single try/catch at the call site.
 */
suspend fun sendContactRequestAndAwaitIncoming(
    ctx: ScenarioContext,
    initiatorRole: String,
    initiatorId: String,
    accepterRole: String,
    accepterId: String,
    initiatorUri: String,
    accepterUri: String,
): IncomingContactRequest {
    ctx.send(initiatorRole, SendContactRequest(initiatorId, accepterUri))
    return ctx.await(accepterRole, 120_000) {
        it is IncomingContactRequest && it.accountId == accepterId && it.fromUri == initiatorUri
    } as IncomingContactRequest
}

/**
 * Step 2 of the handshake: [accepterRole] accepts [incoming] and both sides confirm
 * `ContactAdded(confirmed = true)`. Returns the derived swarm `conversationId`.
 *
 * Accepts via the request's own `conversationUri`, not the bare peer URI — accepting via the
 * peer URI silently takes the legacy accept path even on a modern swarm request, which confirms
 * the contact but never joins the swarm conversation (see [IncomingContactRequest]'s doc).
 */
suspend fun acceptAndConfirmContact(
    ctx: ScenarioContext,
    accepterRole: String,
    accepterId: String,
    initiatorRole: String,
    initiatorId: String,
    incoming: IncomingContactRequest,
): ContactHandshakeResult {
    ctx.send(accepterRole, AcceptContactRequest(accepterId, incoming.conversationUri))
    ctx.await(accepterRole, 60_000) { it is ContactAdded && it.accountId == accepterId && it.confirmed }
    ctx.await(initiatorRole, 60_000) { it is ContactAdded && it.accountId == initiatorId && it.confirmed }
    val conversationId = incoming.conversationUri.substringAfter(':', incoming.conversationUri)
    return ContactHandshakeResult(conversationId)
}

/**
 * The full real, DHT-verified contact handshake — [sendContactRequestAndAwaitIncoming] followed
 * by [acceptAndConfirmContact] — for callers that treat the whole thing as one all-or-nothing
 * step (`send-message`, `build-conversation-fixture`, `chat-conversation-git-rewind-resync`,
 * `default-one-on-one-conversation`). `two-device-contact` calls the two halves directly instead
 * (see [sendContactRequestAndAwaitIncoming]'s doc for why).
 */
suspend fun establishContact(
    ctx: ScenarioContext,
    initiatorRole: String,
    initiatorId: String,
    initiatorUri: String,
    accepterRole: String,
    accepterId: String,
    accepterUri: String,
): ContactHandshakeResult {
    val incoming = sendContactRequestAndAwaitIncoming(
        ctx, initiatorRole, initiatorId, accepterRole, accepterId, initiatorUri, accepterUri,
    )
    return acceptAndConfirmContact(ctx, accepterRole, accepterId, initiatorRole, initiatorId, incoming)
}
