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

import kotlinx.coroutines.TimeoutCancellationException
import net.jami.e2e.Scenario
import net.jami.e2e.ScenarioContext
import net.jami.e2e.Verdict
import net.jami.e2e.protocol.AccountAdded
import net.jami.e2e.protocol.AccountExported
import net.jami.e2e.protocol.AccountRemoved
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.ChangePassword
import net.jami.e2e.protocol.CreateBareAccount
import net.jami.e2e.protocol.ExportAccount
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.ImportAccount
import net.jami.e2e.protocol.PasswordChanged
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.RemoveAccount

/**
 * Archive **password change** — add / change / remove, all in one run.
 *
 * `changeAccountPassword` is a purely local re-encryption of the account archive (no DHT, no
 * name server): decrypt with the old password, re-encrypt with the new one. It is only
 * reachable through a real `accountId`, so we reuse an existing **password-protected** pool
 * asset (realistic "user changes an existing password" case), drive the transitions on the
 * phone copy, and remove it — the host blob is never rewritten, so the fixture is untouched
 * (**non-consuming**).
 *
 * Two layers of proof:
 *  - the daemon's boolean result, anchored by a **negative control** (a wrong old password
 *    must return `false`) so the boolean can't be a rubber stamp;
 *  - an authoritative **export → re-import round-trip**: after changing to a new password the
 *    re-encrypted archive imports under the *new* password (identity preserved) and is
 *    rejected under the *old* one — the same failed-decrypt teardown as `import-wrong-password`.
 *
 * The run changes the password back to the asset's original at the end, closing the round-trip.
 * Requires a `pw` fixture in the pool (run `seed-pool` first); falls back to bootstrapping one.
 */
object ChangePasswordScenario : Scenario {
    override val id = "change-password"
    override val requiredRoles = 1

    private const val ARCHIVE = "changepw.gz"
    private const val P1 = "harness_pw_alpha"

    override suspend fun run(ctx: ScenarioContext): Verdict {
        if (!ensureNoAccounts(ctx, "A").clean) {
            return Verdict(false, "could not reach a no-account state before change-password")
        }

        // Reuse an existing password-protected asset; p0 is its known (current) password.
        val asset = ctx.memory.claim(hasPassword = true)
        val p0: String
        val identity: String
        var accountId: String
        if (asset != null) {
            p0 = asset.password
            identity = asset.fingerprint
            ctx.log("reusing pw asset $identity from pool (non-consuming)")
            accountId = ctx.installAsset("A", asset)
                ?: return Verdict(false, "failed to install claimed pw asset")
            // changeAccountPassword re-encrypts the on-disk archive — it fails if the account
            // is still INITIALIZING, so wait until it is fully loaded before touching it.
            awaitReady(ctx, accountId)
        } else {
            // Bootstrap: no pw fixture in the pool — create a bare account and add a password.
            ctx.log("no pw asset in pool — creating a bare account and adding a password")
            ctx.send("A", CreateBareAccount(displayName = "harness_pw_${System.currentTimeMillis() / 1000}"))
            accountId = (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
            awaitReady(ctx, accountId)
            identity = uriOf(ctx, accountId)
            if (identity.isBlank()) return Verdict(false, "empty account URI after bootstrap creation")
            p0 = "harness_pw_seed"
            if (!changePassword(ctx, accountId, "", p0)) {
                return Verdict(false, "bootstrap: adding an initial password failed")
            }
        }

        try {
            // 1. CHANGE the password (p0 → P1).
            if (!changePassword(ctx, accountId, p0, P1)) {
                return Verdict(false, "CHANGE failed: changeAccountPassword(p0 → P1) returned false")
            }
            ctx.log("CHANGE: p0 → P1 succeeded")

            // 2. Negative control — a wrong current password must be rejected, proving the
            //    boolean result actually validates the old password (not a rubber stamp).
            if (changePassword(ctx, accountId, "totally-wrong-$P1", "irrelevant")) {
                return Verdict(false, "old-password validation not enforced: change with a wrong current password returned success")
            }
            ctx.log("negative control: wrong current password correctly rejected")

            // 3. Authoritative round-trip — the archive is really re-encrypted under P1.
            ctx.send("A", ExportAccount(accountId, ARCHIVE, password = P1))
            val exported = ctx.await("A", 20_000) {
                it is AccountExported && it.fileName == ARCHIVE
            } as AccountExported
            if (!exported.success) return Verdict(false, "export under the new password P1 failed")
            ctx.pullArtifact("A", ARCHIVE) ?: return Verdict(false, "failed to pull archive")

            ctx.send("A", RemoveAccount(accountId))
            ctx.await("A", 10_000) { it is AccountRemoved && it.accountId == accountId }
            if (!ensureNoAccounts(ctx, "A").clean) {
                return Verdict(false, "could not reach a no-account state before re-import")
            }

            // 3a. Importing with the OLD password p0 must be rejected (change took effect).
            if (!ctx.pushArtifact("A", ARCHIVE)) return Verdict(false, "failed to push archive")
            if (!importIsRejected(ctx, ARCHIVE, p0, identity)) {
                return Verdict(false, "archive still opens with the OLD password — change did not re-encrypt it")
            }
            ctx.log("old password correctly rejected by the re-encrypted archive")

            // 3b. Importing with the NEW password P1 must succeed and preserve identity.
            if (!ctx.pushArtifact("A", ARCHIVE)) return Verdict(false, "failed to re-push archive")
            ctx.send("A", ImportAccount(ARCHIVE, password = P1))
            accountId = (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
            awaitReady(ctx, accountId)
            val reUri = uriOf(ctx, accountId)
            if (reUri != identity) {
                return Verdict(false, "identity mismatch after new-password import: expected $identity got '$reUri'")
            }
            ctx.log("new password P1 restored identity $identity (zip/unzip round-trip proven)")

            // 4. REMOVE the password (P1 → "").
            if (!changePassword(ctx, accountId, P1, "")) {
                return Verdict(false, "REMOVE failed: changeAccountPassword(P1 → \"\") returned false")
            }
            ctx.log("REMOVE: P1 → empty succeeded")

            // 5. ADD it back to the asset's original password (closes the round-trip).
            if (!changePassword(ctx, accountId, "", p0)) {
                return Verdict(false, "ADD failed: changeAccountPassword(\"\" → p0) returned false")
            }
            ctx.log("ADD: empty → original password succeeded; round-trip closed")

            return Verdict(true, "change / remove / add all verified; identity $identity preserved end-to-end (non-consuming)")
        } finally {
            // Non-consuming: remove the phone copy; the host pool blob is never rewritten.
            runCatching { ensureNoAccounts(ctx, "A") }
        }
    }

    /** Issue a [ChangePassword] and return the daemon's boolean result. */
    private suspend fun changePassword(
        ctx: ScenarioContext,
        accountId: String,
        oldPassword: String,
        newPassword: String,
    ): Boolean {
        ctx.send("A", ChangePassword(accountId, oldPassword, newPassword))
        return (ctx.await("A", 15_000) {
            it is PasswordChanged && it.accountId == accountId
        } as PasswordChanged).success
    }

    /**
     * Wait until an imported/created account is fully loaded (`REGISTERED`) before running a
     * password operation on it — `changeAccountPassword` re-encrypts the on-disk archive and
     * returns false if the account is still `INITIALIZING`.
     */
    private suspend fun awaitReady(ctx: ScenarioContext, accountId: String) {
        ctx.await("A", 60_000) {
            it is RegistrationStateChanged && it.accountId == accountId && it.state == "REGISTERED"
        }
    }

    /** Resolve an account's own Jami fingerprint (the daemon derives it shortly after load). */
    private suspend fun uriOf(ctx: ScenarioContext, accountId: String): String {
        ctx.send("A", GetAccountUri(accountId))
        return (ctx.await("A", 15_000) {
            it is AccountUri && it.accountId == accountId
        } as AccountUri).uri
    }

    /**
     * Import [deviceFile] with [badPassword] and assert the daemon does **not** restore a
     * usable account — mirrors [assertImportRejected]: `addAccount` is optimistic (emits
     * [AccountAdded] before decrypting), so the decisive signal is the resolved identity —
     * torn down / never resolves ⇒ rejected; resolves to [expectedFingerprint] ⇒ wrongly
     * accepted. Cleans up any leftover row. Returns true when the import was rejected.
     */
    private suspend fun importIsRejected(
        ctx: ScenarioContext,
        deviceFile: String,
        badPassword: String,
        expectedFingerprint: String,
    ): Boolean {
        ctx.send("A", ImportAccount(deviceFile, password = badPassword))
        val accountId = try {
            (ctx.await("A", 15_000) { it is AccountAdded } as AccountAdded).accountId
        } catch (_: TimeoutCancellationException) {
            return true // never added → rejected outright
        }
        ctx.send("A", GetAccountUri(accountId))
        val settled = try {
            ctx.await("A", 15_000) {
                (it is AccountUri && it.accountId == accountId) ||
                    (it is AccountRemoved && it.accountId == accountId)
            }
        } catch (_: TimeoutCancellationException) {
            null
        }
        val rejected = when {
            settled is AccountRemoved -> true
            settled is AccountUri && settled.uri == expectedFingerprint -> false // wrongly accepted
            else -> true
        }
        if (settled !is AccountRemoved) {
            runCatching {
                ctx.send("A", RemoveAccount(accountId))
                ctx.await("A", 10_000) { it is AccountRemoved && it.accountId == accountId }
            }
        }
        return rejected
    }
}
