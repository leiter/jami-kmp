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
import net.jami.e2e.protocol.AcceptContactRequest
import net.jami.e2e.protocol.AccountAdded
import net.jami.e2e.protocol.AccountRemoved
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.ContactAdded
import net.jami.e2e.protocol.CreateJamiAccount
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.IncomingContactRequest
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.RemoveAccount
import net.jami.e2e.protocol.SendContactRequest

/**
 * M3 — two devices, with the **out-of-band identity relay**.
 *
 * Both devices create real Jami accounts and announce on the DHT. Each device's own Jami
 * address is fetched over the localhost coordination channel (the relay — standing in for
 * an out-of-band QR / share-link exchange; no Jami payload crosses it). Device **B** then
 * initiates a real contact request to **A** using only that relayed identity: the request
 * travels peer-to-peer over the DHT. A observing it arrive is the core M3 proof — it means
 * B's daemon discovered and reached A's daemon with nothing but the relayed fingerprint.
 *
 * A then accepts; both sides confirming the contact strengthens the result to a verified
 * bidirectional swarm. The confirm step is best-effort (a second DHT round-trip): if it is
 * slow, the scenario still passes on the proven inbound-request leg and reports the gap.
 *
 * Roles are assigned by connect order; which physical device is A vs B is irrelevant.
 */
object TwoDeviceContactScenario : Scenario {
    override val id = "two-device-contact"
    override val requiredRoles = 2

    override suspend fun run(ctx: ScenarioContext): Verdict {
        val stamp = System.currentTimeMillis() / 1000

        // 1. Both devices create accounts.
        ctx.send("A", CreateJamiAccount("harness_a_$stamp", password = ""))
        ctx.send("B", CreateJamiAccount("harness_b_$stamp", password = ""))
        val aId = (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
        val bId = (ctx.await("B", 20_000) { it is AccountAdded } as AccountAdded).accountId
        ctx.log("accounts created: A=$aId B=$bId")

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

            // 4. B initiates a real contact request to A over the DHT.
            ctx.send("B", SendContactRequest(bId, aUri))

            // 5. CORE PROOF: A observes the request arriving over the DHT.
            val incoming = ctx.await("A", 120_000) {
                it is IncomingContactRequest && it.accountId == aId && it.fromUri == bUri
            } as IncomingContactRequest
            ctx.log("A received B's contact request over the DHT: from=${incoming.fromUri}")

            // 6. A accepts → confirm the bidirectional swarm (best-effort second round-trip).
            ctx.send("A", AcceptContactRequest(aId, bUri))
            val confirmed = try {
                ctx.await("A", 60_000) {
                    it is ContactAdded && it.accountId == aId && it.confirmed
                }
                ctx.await("B", 60_000) {
                    it is ContactAdded && it.accountId == bId && it.confirmed
                }
                ctx.log("contact confirmed on both devices")
                true
            } catch (e: Exception) {
                ctx.log("bidirectional confirm incomplete: ${e.message}")
                false
            }

            return Verdict(
                true,
                if (confirmed)
                    "B reached A over the DHT; contact confirmed on both devices"
                else
                    "B reached A over the DHT (incoming request received); confirm incomplete",
            )
        } finally {
            // Cleanup — ephemeral accounts, isolated per run. Never flips the verdict.
            runCatching {
                ctx.send("A", RemoveAccount(aId))
                ctx.send("B", RemoveAccount(bId))
                ctx.await("A", 10_000) { it is AccountRemoved && it.accountId == aId }
                ctx.await("B", 10_000) { it is AccountRemoved && it.accountId == bId }
            }
        }
    }
}
