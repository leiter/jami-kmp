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
import net.jami.e2e.ScenarioContext
import net.jami.e2e.Verdict
import net.jami.e2e.protocol.AccountAdded
import net.jami.e2e.protocol.AccountRemoved
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.ImportAccount
import net.jami.e2e.protocol.RemoveAccount

/** Device file the failure scenarios push their archive to (overwritten each run). */
private const val BAD_IMPORT_FILE = "import_reject.gz"

/**
 * Shared body for the wrong-password / no-password import scenarios: push a
 * password-protected fixture and try to import it with [badPassword], asserting the daemon
 * does **not** restore a usable account.
 *
 * Observed daemon behaviour (Pixel 7a, daemon build in use): `addAccount` with an archive is
 * **optimistic** — it creates the account row and emits [AccountAdded] → `INITIALIZING`
 * *before* it has decrypted the archive, even when the password is wrong. So [AccountAdded]
 * is **not** a success signal. The decisive, robust discriminator is the resolved **identity**:
 *  - a correctly-decrypted archive resolves to the asset's known fingerprint  → import succeeded;
 *  - a failed decrypt cannot derive the identity (blank / auto-removed)        → import rejected.
 *
 * So we let the account settle and compare the resolved URI to the fixture fingerprint:
 *  - fingerprint matches                         → wrongly accepted → FAIL
 *  - account auto-removed / URI never resolves   → rejected → PASS
 *  - account never added at all                  → rejected → PASS
 *
 * Non-consuming: the pooled archive is only read, never modified.
 */
suspend fun assertImportRejected(ctx: ScenarioContext, badPassword: String): Verdict {
    val asset = ctx.memory.claim(hasPassword = true)
        ?: return Verdict(false, "no password-protected asset in pool — run seed-pool first")

    // An import is an onboarding-state operation: prove the device has no account loaded first.
    val clean = ensureNoAccounts(ctx, "A")
    if (!clean.clean) return Verdict(false, "could not reach a no-account state before import")

    ctx.log("importing pw asset ${asset.fingerprint} with a bad password (len=${badPassword.length})")

    if (!ctx.pushAsset("A", asset, BAD_IMPORT_FILE)) {
        return Verdict(false, "failed to push archive to device")
    }
    ctx.send("A", ImportAccount(BAD_IMPORT_FILE, password = badPassword))

    // The account row appears optimistically even for a bad password — capture its id, but
    // don't treat its presence as success. If it never appears, the import was refused outright.
    val accountId = try {
        (ctx.await("A", 15_000) { it is AccountAdded } as AccountAdded).accountId
    } catch (_: TimeoutCancellationException) {
        return Verdict(true, "import rejected — account never added")
    }
    ctx.log("account $accountId added optimistically; probing whether the archive decrypted")

    // Let the identity settle: the daemon either resolves the fingerprint (decrypt OK) or
    // tears the half-loaded account down (decrypt failed).
    ctx.send("A", GetAccountUri(accountId))
    val settled = try {
        ctx.await("A", 15_000) {
            (it is AccountUri && it.accountId == accountId) ||
                (it is AccountRemoved && it.accountId == accountId)
        }
    } catch (_: TimeoutCancellationException) {
        null
    }

    val verdict = when {
        settled is AccountRemoved ->
            Verdict(true, "import rejected — account torn down after failed decrypt")

        settled is AccountUri && settled.uri == asset.fingerprint ->
            Verdict(false, "import WRONGLY succeeded — archive decrypted to ${asset.fingerprint}")

        settled is AccountUri ->
            Verdict(true, "import rejected — identity did not resolve (uri='${settled.uri}')")

        else ->
            Verdict(true, "import rejected — identity never resolved")
    }
    ctx.log("settle outcome → ${verdict.reason}")

    // Cleanup — remove the leftover row if the daemon didn't already (never flips the verdict).
    if (settled !is AccountRemoved) {
        runCatching {
            ctx.send("A", RemoveAccount(accountId))
            ctx.await("A", 10_000) { it is AccountRemoved && it.accountId == accountId }
        }
    }
    return verdict
}
