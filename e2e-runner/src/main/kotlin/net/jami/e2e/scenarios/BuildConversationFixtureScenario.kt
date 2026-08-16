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
import net.jami.e2e.protocol.NameRegistrationEnded
import net.jami.e2e.protocol.ProfileUpdated
import net.jami.e2e.protocol.RegisterName
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.SeedConversationMessages
import net.jami.e2e.protocol.SeedMessage
import net.jami.e2e.protocol.SetProfile
import java.util.Base64

/**
 * Producer scenario: builds the reusable **conversation-pair fixture** — two accounts that
 * already know each other, each with a registered name and avatar, sharing a real swarm
 * conversation with a seeded message transcript — and captures it into the registry
 * ([net.jami.e2e.memory.MemoryStore.addConversationPair]) as a full per-role app-data snapshot.
 *
 * Setup reuses the proven building blocks: pool-fixture reuse + the `two-device-contact`
 * handshake for a *real*, DHT-verified mutual contact and swarm `conversationId`. Message
 * history is then seeded directly into each device's local SQLDelight DB
 * ([SeedConversationMessages]) — bypassing the daemon/swarm entirely, because the real
 * initiator-side send is currently broken (see doc/TODO.md → "Bug Findings (2026-08-14)"). This
 * keeps fixture-building unblocked by that bug rather than depending on a fix for it.
 *
 * Consuming (burns two names) but only needs to run **once** — later consumers restore the
 * captured pair via [ScenarioContext.installConversationPairAsset] instead of rebuilding it.
 */
object BuildConversationFixtureScenario : Scenario {
    override val id = "build-conversation-fixture"
    override val requiredRoles = 2

    private val SEED_TRANSCRIPT = listOf(
        "hey!" to "A",
        "hi, how's it going?" to "B",
        "good, just testing things out" to "A",
        "nice, this is a seeded fixture message" to "B",
    )

    override suspend fun run(ctx: ScenarioContext): Verdict {
        for (role in listOf("A", "B")) {
            if (!ensureNoAccounts(ctx, role).clean) {
                return Verdict(false, "could not reach a no-account state on device[$role]")
            }
        }

        // 1. Distinct identities, reuse-first. Password-free: RegisterName below doesn't thread
        // an archive password through, so a password-protected asset would fail registration.
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

            // 2. Register a name on each (consuming — this scenario is a one-time producer).
            val nameA = MemorableNames.next(ctx.memory)
            ctx.send("A", RegisterName(aId, nameA))
            val regA = ctx.await("A", 60_000) { it is NameRegistrationEnded && it.accountId == aId } as NameRegistrationEnded
            if (regA.state != 0) return Verdict(false, "name registration failed for A ('$nameA', state=${regA.state})")
            aAsset?.let { ctx.memory.markRegistered(it.fingerprint, nameA) }

            val nameB = MemorableNames.next(ctx.memory)
            ctx.send("B", RegisterName(bId, nameB))
            val regB = ctx.await("B", 60_000) { it is NameRegistrationEnded && it.accountId == bId } as NameRegistrationEnded
            if (regB.state != 0) return Verdict(false, "name registration failed for B ('$nameB', state=${regB.state})")
            bAsset?.let { ctx.memory.markRegistered(it.fingerprint, nameB) }
            ctx.log("names registered: A='$nameA' B='$nameB'")

            // 3. Set a real avatar on both via the daemon's own profile path. Fire-and-forget,
            // same as the real profile-edit UI (AccountSettingsViewModel never awaits a
            // callback for updateProfile either): best-effort wait for the ProfileUpdated echo,
            // but don't fail the fixture build if the daemon doesn't emit it for a self-update
            // (observed on hardware — worth a focused follow-up, not a blocker here).
            val avatarBase64 = loadFixtureAvatarBase64()
            ctx.send("A", SetProfile(aId, nameA, avatarBase64, "png"))
            runCatching { ctx.await("A", 5_000) { it is ProfileUpdated && it.accountId == aId } }
                .onFailure { ctx.log("no ProfileUpdated echo for A within 5s (fire-and-forget, continuing)") }
            ctx.send("B", SetProfile(bId, nameB, avatarBase64, "png"))
            runCatching { ctx.await("B", 5_000) { it is ProfileUpdated && it.accountId == bId } }
                .onFailure { ctx.log("no ProfileUpdated echo for B within 5s (fire-and-forget, continuing)") }
            ctx.log("avatar update sent on both accounts")

            // 4. Out-of-band identity relay + the proven contact-request/accept handshake.
            ctx.send("A", GetAccountUri(aId))
            val aUri = (ctx.await("A", 5_000) { it is AccountUri && it.accountId == aId } as AccountUri).uri
            ctx.send("B", GetAccountUri(bId))
            val bUri = (ctx.await("B", 5_000) { it is AccountUri && it.accountId == bId } as AccountUri).uri
            if (aUri.isBlank() || bUri.isBlank()) return Verdict(false, "empty account URI (A='$aUri' B='$bUri')")

            val conversationId = establishContact(ctx, "B", bId, bUri, "A", aId, aUri).conversationId
            ctx.log("contact confirmed on both devices — swarm conversation '$conversationId' established")

            // 5. Seed a fixed transcript directly into each device's local history DB, bypassing
            // the currently-broken real send path (see class doc).
            val base = System.currentTimeMillis() - SEED_TRANSCRIPT.size * 60_000L
            val messagesFromA = SEED_TRANSCRIPT.mapIndexed { i, (body, author) ->
                SeedMessage(if (author == "A") aUri else bUri, body, i * 60_000L)
            }
            ctx.send("A", SeedConversationMessages(aId, conversationId, bUri, base, messagesFromA))
            ctx.send("B", SeedConversationMessages(bId, conversationId, aUri, base, messagesFromA))
            ctx.log("seeded ${SEED_TRANSCRIPT.size} messages into both devices' local history")

            // 6. Capture the whole fixture pair.
            val label = "$nameA-$nameB"
            val pair = ctx.captureConversationPairAsset(
                roleA = "A",
                roleB = "B",
                label = label,
                fingerprintA = aUri,
                fingerprintB = bUri,
                nameA = nameA,
                nameB = nameB,
                avatarSet = true,
                conversationId = conversationId,
                messageCount = SEED_TRANSCRIPT.size,
            ) ?: return Verdict(false, "failed to capture conversation-pair fixture")

            return Verdict(true, "captured conversation-pair fixture '${pair.label}' (${pair.messageCount} messages)")
        } finally {
            // Skippable via -PkeepAccounts=true to leave the just-built fixture live on-device.
            if (!ctx.runConfig.keepAccounts) {
                for (role in listOf("A", "B")) {
                    runCatching { ensureNoAccounts(ctx, role) }
                }
            } else {
                ctx.log("keepAccounts=true — skipping teardown, leaving accounts/conversation on-device")
            }
        }
    }

    private fun loadFixtureAvatarBase64(): String {
        val bytes = requireNotNull(
            BuildConversationFixtureScenario::class.java.classLoader.getResourceAsStream("fixture-avatar.png"),
        ) { "fixture-avatar.png resource not found" }.readBytes()
        return Base64.getEncoder().encodeToString(bytes)
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
