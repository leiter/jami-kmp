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
package net.jami.e2e.memory

import kotlinx.serialization.Serializable
import net.jami.e2e.protocol.HarnessJson
import java.io.File
import java.time.Instant

/**
 * A persisted account asset (a Jami identity captured as an exportable archive).
 *
 * Assets are reused across runs to keep account *creation* — the slow, name-burning,
 * DHT-bootstrapping operation — to an absolute minimum. Scenarios claim assets by **state**
 * (see [MemoryStore.claim]); creation only happens when no matching asset exists.
 *
 * - [fingerprint] is the immutable Jami identity; it keys the archive blob.
 * - [registeredName] is `null` until a name is registered on the name server. The
 *   unnamed→named transition is **one-way and consuming** (a name is burned globally).
 * - [password] is the archive password (`""` = unprotected); fixed per archive, drives the
 *   wrong-password / no-password / correct-password import scenarios.
 */
@Serializable
data class AccountAsset(
    val fingerprint: String,
    val registeredName: String? = null,
    val password: String = "",
    val archive: String,
    val createdUtc: String,
    val lastUsedUtc: String? = null,
) {
    val named: Boolean get() = registeredName != null
    val hasPassword: Boolean get() = password.isNotEmpty()
}

/** Audit record of a username permanently claimed on the Jami name server. */
@Serializable
data class BurnedName(val name: String, val fingerprint: String, val utc: String)

@Serializable
private data class RegistryIndex(
    val accounts: List<AccountAsset> = emptyList(),
    val burnedNames: List<BurnedName> = emptyList(),
)

/**
 * Host-side persistent registry of reusable test assets, under `harness-memory/fixtures/`
 * (git-ignored — archives contain private keys). `index.json` is the source of truth; the
 * per-account descriptor filenames merely **hint** the state for at-a-glance scanning and
 * are regenerated on every change (never parsed for logic).
 */
class MemoryStore(root: File = File("harness-memory")) {
    private val fixturesDir = File(root, "fixtures")
    private val accountsDir = File(fixturesDir, "accounts")
    private val blobsDir = File(fixturesDir, "blobs")
    private val indexFile = File(fixturesDir, "index.json")

    private var index: RegistryIndex = load()

    /** Absolute path of an asset's archive blob (identity-keyed, stable). */
    fun blobFile(asset: AccountAsset): File = File(blobsDir, asset.archive)

    fun all(): List<AccountAsset> = index.accounts
    fun burnedNames(): List<BurnedName> = index.burnedNames

    /**
     * First asset matching the requested **state** — `null` for a dimension means "don't
     * care". Returns `null` when the pool has nothing matching (the caller's cue to create).
     */
    fun claim(named: Boolean? = null, hasPassword: Boolean? = null): AccountAsset? =
        index.accounts.firstOrNull { a ->
            (named == null || a.named == named) &&
                (hasPassword == null || a.hasPassword == hasPassword)
        }

    /**
     * Add a freshly-captured asset: copy [archiveSource] into the blob store keyed by
     * [fingerprint], record the descriptor, and persist. Replaces any existing entry for
     * the same fingerprint.
     */
    fun addAsset(
        fingerprint: String,
        registeredName: String?,
        password: String,
        archiveSource: File,
    ): AccountAsset {
        blobsDir.mkdirs()
        val blobName = "$fingerprint.gz"
        archiveSource.copyTo(File(blobsDir, blobName), overwrite = true)
        val asset = AccountAsset(
            fingerprint = fingerprint,
            registeredName = registeredName,
            password = password,
            archive = blobName,
            createdUtc = Instant.now().toString(),
        )
        index = index.copy(accounts = index.accounts.filter { it.fingerprint != fingerprint } + asset)
        persist()
        return asset
    }

    /** Flip an asset unnamed→named after a successful registration. Also logs the burn. */
    fun markRegistered(fingerprint: String, name: String): AccountAsset? {
        val updated = index.accounts.find { it.fingerprint == fingerprint }
            ?.copy(registeredName = name, lastUsedUtc = Instant.now().toString())
            ?: return null
        index = index.copy(
            accounts = index.accounts.map { if (it.fingerprint == fingerprint) updated else it },
            burnedNames = index.burnedNames + BurnedName(name, fingerprint, Instant.now().toString()),
        )
        persist()
        return updated
    }

    /** Log a name burned on the name server (for an account registered at creation time). */
    fun recordBurnedName(name: String, fingerprint: String) {
        index = index.copy(
            burnedNames = index.burnedNames + BurnedName(name, fingerprint, Instant.now().toString()),
        )
        persist()
    }

    /** Mark an asset as just used (touch). */
    fun touch(fingerprint: String) {
        index = index.copy(
            accounts = index.accounts.map {
                if (it.fingerprint == fingerprint) it.copy(lastUsedUtc = Instant.now().toString()) else it
            },
        )
        persist()
    }

    private fun load(): RegistryIndex =
        if (indexFile.exists()) HarnessJson.decodeFromString(indexFile.readText())
        else RegistryIndex()

    private fun persist() {
        accountsDir.mkdirs()
        blobsDir.mkdirs()
        indexFile.writeText(HarnessJson.encodeToString(index))
        // Rewrite descriptor hints from scratch so renamed/removed states never linger.
        accountsDir.listFiles()?.forEach { it.delete() }
        index.accounts.forEach { asset ->
            File(accountsDir, hintName(asset)).writeText(HarnessJson.encodeToString(asset))
        }
    }

    private fun hintName(a: AccountAsset): String {
        val nameTag = if (a.named) "named.${a.registeredName}" else "unnamed"
        val pwTag = if (a.hasPassword) "pw" else "nopw"
        return "acct__${nameTag}__${pwTag}__${a.fingerprint.take(8)}.json"
    }
}
