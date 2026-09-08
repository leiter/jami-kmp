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
 * A persisted **pair** fixture: two identities that already know each other, each named +
 * avatar-set, sharing a real swarm [conversationId] with a seeded message transcript. Captured
 * as a full per-role app-data tar (identity + profile + local history DB — see
 * [net.jami.e2e.DeviceController.snapshotAppData]), so restoring it puts a device back into the
 * exact fixture state without touching the daemon. Non-consuming to restore; a fresh capture
 * only happens when the registry has none matching, or a scenario explicitly asks for one.
 */
@Serializable
data class ConversationPairAsset(
    val label: String,
    val fingerprintA: String,
    val fingerprintB: String,
    val nameA: String,
    val nameB: String,
    val avatarSet: Boolean,
    val messageCount: Int,
    val conversationId: String,
    val archiveA: String,
    val archiveB: String,
    val createdUtc: String,
)

@Serializable
private data class ConversationPairIndex(
    val pairs: List<ConversationPairAsset> = emptyList(),
)

/**
 * A persisted **single-conversation** fixture: just one conversation's raw on-disk swarm git
 * repo, captured independently of the whole-app-tar [ConversationPairAsset]. For scenarios that
 * want to repeatedly start from one pristine, already-synced repo (e.g. rewind/resync
 * experiments) without re-running a full account/contact/message setup each time. [accountId]/
 * [conversationId] record where it was captured *from*; restoring it onto a different live
 * account/conversation is a legitimate use — see [ScenarioContext.installConversationRepoAsset].
 */
@Serializable
data class ConversationRepositoryAsset(
    val label: String,
    val accountId: String,
    val conversationId: String,
    val fingerprint: String,
    val archive: String,
    val createdUtc: String,
)

@Serializable
private data class ConversationRepositoryIndex(
    val repos: List<ConversationRepositoryAsset> = emptyList(),
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
    private val pairsDir = File(fixturesDir, "conversation-pairs")
    private val pairsIndexFile = File(pairsDir, "index.json")
    private val reposDir = File(fixturesDir, "conversation-repos")
    private val reposIndexFile = File(reposDir, "index.json")

    private var index: RegistryIndex = load()
    private var pairIndex: ConversationPairIndex = loadPairs()
    private var repoIndex: ConversationRepositoryIndex = loadRepos()

    /** Absolute path of an asset's archive blob (identity-keyed, stable). */
    fun blobFile(asset: AccountAsset): File = File(blobsDir, asset.archive)

    fun all(): List<AccountAsset> = index.accounts
    fun burnedNames(): List<BurnedName> = index.burnedNames

    /**
     * First asset matching the requested **state** — `null` for a dimension means "don't
     * care". Returns `null` when the pool has nothing matching (the caller's cue to create).
     * [exclude] holds fingerprints already taken by the caller, so a scenario needing several
     * assets at once never gets the same identity twice.
     */
    fun claim(
        named: Boolean? = null,
        hasPassword: Boolean? = null,
        exclude: Set<String> = emptySet(),
    ): AccountAsset? =
        index.accounts.firstOrNull { a ->
            a.fingerprint !in exclude &&
                (named == null || a.named == named) &&
                (hasPassword == null || a.hasPassword == hasPassword)
        }

    /**
     * Up to [count] assets with **distinct identities**, matching the requested state. Returns
     * fewer than [count] (possibly none) when the pool is too small — the caller creates the
     * remainder. Needed by multi-device scenarios, where each role must be a different peer.
     */
    fun claimDistinct(
        count: Int,
        named: Boolean? = null,
        hasPassword: Boolean? = null,
    ): List<AccountAsset> {
        val taken = mutableListOf<AccountAsset>()
        repeat(count) {
            val next = claim(named, hasPassword, taken.mapTo(mutableSetOf()) { a -> a.fingerprint })
                ?: return taken
            taken += next
        }
        return taken
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

    /** Blob file for one role of a conversation-pair fixture (`A` or `B`). */
    fun pairBlobFile(pair: ConversationPairAsset, role: String): File =
        File(File(pairsDir, pair.label), if (role == "A") pair.archiveA else pair.archiveB)

    fun allConversationPairs(): List<ConversationPairAsset> = pairIndex.pairs

    /** By [label] if given, else the first available pair; `null` is the cue to build one. */
    fun claimConversationPair(label: String? = null): ConversationPairAsset? =
        if (label != null) pairIndex.pairs.firstOrNull { it.label == label }
        else pairIndex.pairs.firstOrNull()

    /**
     * Record a freshly-captured conversation-pair fixture: copy each role's app-data tar
     * ([archiveASource]/[archiveBSource]) into `conversation-pairs/<label>/`, index it, and
     * persist. Replaces any existing entry with the same [label].
     */
    fun addConversationPair(
        label: String,
        fingerprintA: String,
        fingerprintB: String,
        nameA: String,
        nameB: String,
        avatarSet: Boolean,
        messageCount: Int,
        conversationId: String,
        archiveASource: File,
        archiveBSource: File,
    ): ConversationPairAsset {
        val dir = File(pairsDir, label).apply { mkdirs() }
        val archiveA = "A.tar"
        val archiveB = "B.tar"
        archiveASource.copyTo(File(dir, archiveA), overwrite = true)
        archiveBSource.copyTo(File(dir, archiveB), overwrite = true)
        val pair = ConversationPairAsset(
            label = label,
            fingerprintA = fingerprintA,
            fingerprintB = fingerprintB,
            nameA = nameA,
            nameB = nameB,
            avatarSet = avatarSet,
            messageCount = messageCount,
            conversationId = conversationId,
            archiveA = archiveA,
            archiveB = archiveB,
            createdUtc = Instant.now().toString(),
        )
        pairIndex = pairIndex.copy(pairs = pairIndex.pairs.filter { it.label != label } + pair)
        persistPairs()
        return pair
    }

    private fun loadPairs(): ConversationPairIndex =
        if (pairsIndexFile.exists()) HarnessJson.decodeFromString(pairsIndexFile.readText())
        else ConversationPairIndex()

    private fun persistPairs() {
        pairsDir.mkdirs()
        pairsIndexFile.writeText(HarnessJson.encodeToString(pairIndex))
    }

    /** Blob file for a single-conversation-repo fixture. */
    fun repoBlobFile(asset: ConversationRepositoryAsset): File = File(File(reposDir, asset.label), asset.archive)

    fun allConversationRepos(): List<ConversationRepositoryAsset> = repoIndex.repos

    /** By [label] if given, else the first available. `null` is the cue to build one. */
    fun claimConversationRepo(label: String? = null): ConversationRepositoryAsset? =
        if (label != null) repoIndex.repos.firstOrNull { it.label == label }
        else repoIndex.repos.firstOrNull()

    /**
     * Record a freshly-captured single-conversation-repo fixture: copy [archiveSource] into
     * `conversation-repos/<label>/`, index it, and persist. Replaces any existing entry with
     * the same [label].
     */
    fun addConversationRepo(
        label: String,
        accountId: String,
        conversationId: String,
        fingerprint: String,
        archiveSource: File,
    ): ConversationRepositoryAsset {
        val dir = File(reposDir, label).apply { mkdirs() }
        val archive = "repo.tar"
        archiveSource.copyTo(File(dir, archive), overwrite = true)
        val asset = ConversationRepositoryAsset(
            label = label,
            accountId = accountId,
            conversationId = conversationId,
            fingerprint = fingerprint,
            archive = archive,
            createdUtc = Instant.now().toString(),
        )
        repoIndex = repoIndex.copy(repos = repoIndex.repos.filter { it.label != label } + asset)
        persistRepos()
        return asset
    }

    private fun loadRepos(): ConversationRepositoryIndex =
        if (reposIndexFile.exists()) HarnessJson.decodeFromString(reposIndexFile.readText())
        else ConversationRepositoryIndex()

    private fun persistRepos() {
        reposDir.mkdirs()
        reposIndexFile.writeText(HarnessJson.encodeToString(repoIndex))
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
