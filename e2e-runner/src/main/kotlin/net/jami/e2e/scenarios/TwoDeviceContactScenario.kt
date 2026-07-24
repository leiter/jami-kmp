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
import net.jami.e2e.protocol.AcceptContactRequest
import net.jami.e2e.protocol.AccountAdded
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.ContactAdded
import net.jami.e2e.protocol.CreateBareAccount
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.IncomingContactRequest
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.SendContactRequest

/**
 * M3 — two devices, with the **out-of-band identity relay**.
 *
 * Both devices bring up a real Jami account and announce on the DHT. Each device's own Jami
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
 *
 * **Reuse-first and non-consuming**, like the single-device scenarios: each role gets a
 * *distinct* identity claimed from the fixture pool, installed on its device; only the roles
 * the pool cannot cover fall back to creating a **bare** account (no name server contact, so a
 * run never burns a username). Both devices are driven to a proven no-account state first —
 * the harness reports account changes by diffing from its connect-time baseline, so an account
 * stranded by an earlier run would be invisible on the timeline while still steering the
 * daemon. The same sweep runs on teardown; the host archives are never rewritten.
 */
object TwoDeviceContactScenario : Scenario {
    override val id = "two-device-contact"
    override val requiredRoles = 2

    override suspend fun run(ctx: ScenarioContext): Verdict {
        for (role in listOf("A", "B")) {
            if (!ensureNoAccounts(ctx, role).clean) {
                return Verdict(false, "could not reach a no-account state on device[$role]")
            }
        }

        // 1. Distinct identities per role — installing one asset on both devices would make
        // them two devices of the *same* peer, which cannot exchange a contact request.
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
            // For pool-installed roles the identity is known up front, so the relay doubles as
            // an identity-preservation check on the import.
            mismatchedIdentity("A", aAsset, aUri)?.let { return Verdict(false, it) }
            mismatchedIdentity("B", bAsset, bUri)?.let { return Verdict(false, it) }

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
            // Non-consuming: sweep both devices back to a proven no-account state. The contact
            // added here lives only in the on-device copies, which are removed; the host
            // archives keep the original contact-free identities. Never flips the verdict.
            for (role in listOf("A", "B")) {
                runCatching { ensureNoAccounts(ctx, role) }
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

    /** Non-null failure reason when a pool-installed account did not come back as its own identity. */
    private fun mismatchedIdentity(role: String, asset: AccountAsset?, uri: String): String? =
        if (asset != null && !uri.equals(asset.fingerprint, ignoreCase = true))
            "device[$role] resolved URI '$uri' does not match installed asset ${asset.fingerprint}"
        else null
}
