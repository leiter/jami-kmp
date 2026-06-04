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
package net.jami.services

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import net.jami.ui.utils.scaleImageBytes
import net.jami.utils.FileUtils
import net.jami.utils.VCardUtils

/**
 * Provides scaled, disk-cached avatar bytes for local and peer vCards.
 *
 * Mirrors jami-client-android VCardServiceImpl.loadProfileWithCache:
 *   - Source vcf: {dataPath}/{accountId}/profile.vcf  (local)
 *                 {dataPath}/{accountId}/profiles/{base64url(peerUri)}.vcf  (peer)
 *   - Cache file: {cachePath}/{accountId}/profile_pic  (local)
 *                 {cachePath}/{accountId}/profiles/{base64url(peerUri)}  (peer)
 *   - Fast path: cache exists and is at least as new as the vcf → return cached bytes.
 *   - Slow path: parse vcf, scale to ≤512 px JPEG q88, write cache, return bytes.
 *   - In-memory map keyed "{accountId}:{uri}" prevents repeated disk reads per session.
 *
 * The in-memory cache must be invalidated via [invalidatePeer] / [invalidateLocal]
 * when the daemon fires onProfileReceived and writes a new vcf to disk.
 */
class VCardService(
    private val deviceRuntimeService: DeviceRuntimeService,
) {
    // In-memory cache: stores ByteArray? (null means "no photo in vcf")
    private val memoryCache = mutableMapOf<String, ByteArray?>()

    /**
     * Load the local account avatar, scaled and cached.
     * @param accountId Account ID
     * @return Scaled JPEG bytes, or null if no photo is present in the vcf.
     */
    fun loadLocalAvatar(accountId: String): ByteArray? {
        val key = "$accountId:__local__"
        if (memoryCache.containsKey(key)) return memoryCache[key]
        val dataPath = deviceRuntimeService.getDataPath()
        val cachePath = deviceRuntimeService.getCachePath()
        val vcfPath = "$dataPath/$accountId/profile.vcf"
        val cacheFilePath = "$cachePath/$accountId/profile_pic"
        val result = loadWithCache(vcfPath, cacheFilePath)
        memoryCache[key] = result
        return result
    }

    /**
     * Load a peer contact avatar, scaled and cached.
     * @param accountId Account ID (owner of the local daemon instance)
     * @param peerUri   Peer ring-ID / URI string (e.g. rawRingId)
     * @return Scaled JPEG bytes, or null if no photo is present in the vcf.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun loadPeerAvatar(accountId: String, peerUri: String): ByteArray? {
        val key = "$accountId:$peerUri"
        if (memoryCache.containsKey(key)) return memoryCache[key]
        val dataPath = deviceRuntimeService.getDataPath()
        val cachePath = deviceRuntimeService.getCachePath()
        val encodedUri = Base64.UrlSafe.encode(peerUri.toByteArray())
        val vcfPath = "$dataPath/$accountId/profiles/$encodedUri.vcf"
        val cacheFilePath = "$cachePath/$accountId/profiles/$encodedUri"
        val result = loadWithCache(vcfPath, cacheFilePath)
        memoryCache[key] = result
        return result
    }

    /**
     * Invalidate the in-memory cache entry for a peer's avatar so the next
     * [loadPeerAvatar] call re-reads and re-scales from disk.
     * Call this from [ContactService] when a new vCard arrives via daemon callback.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun invalidatePeer(accountId: String, peerUri: String) {
        memoryCache.remove("$accountId:$peerUri")
        // Also delete the stale disk cache so loadWithCache rescales from the new vcf.
        val cachePath = deviceRuntimeService.getCachePath()
        val encodedUri = Base64.UrlSafe.encode(peerUri.toByteArray())
        val cacheFilePath = "$cachePath/$accountId/profiles/$encodedUri"
        if (FileUtils.exists(cacheFilePath)) {
            FileUtils.deleteFile(cacheFilePath)
        }
    }

    /**
     * Invalidate the in-memory cache entry for the local account avatar.
     * Call this when the account profile has been updated.
     */
    fun invalidateLocal(accountId: String) {
        memoryCache.remove("$accountId:__local__")
        val cachePath = deviceRuntimeService.getCachePath()
        val cacheFilePath = "$cachePath/$accountId/profile_pic"
        if (FileUtils.exists(cacheFilePath)) {
            FileUtils.deleteFile(cacheFilePath)
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun loadWithCache(vcfPath: String, cacheFilePath: String): ByteArray? {
        // Fast path: cache file is at least as new as the source vcf.
        if (FileUtils.exists(cacheFilePath) &&
            FileUtils.getLastModified(cacheFilePath) >= FileUtils.getLastModified(vcfPath)
        ) {
            return FileUtils.readBytes(cacheFilePath)
        }

        // Slow path: parse vcf → extract photo → scale → persist to cache.
        val content = FileUtils.readText(vcfPath) ?: return null
        val vcard = VCardUtils.parseVCard(content) ?: return null
        val photo = vcard.photo ?: return null

        val scaled = scaleImageBytes(photo, 512)

        // Write to disk cache (best-effort; failure only skips the cache).
        val cacheDir = cacheFilePath.substringBeforeLast("/")
        FileUtils.mkdirs(cacheDir)
        FileUtils.writeBytes(cacheFilePath, scaled)

        return scaled
    }
}
