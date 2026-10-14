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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.jami.model.Certificate
import net.jami.model.CertificateStatus
import net.jami.utils.FileUtils
import net.jami.utils.Log

/** Something that happened to a certificate, as reported by the daemon. */
sealed class CertificateEvent {
    data class Pinned(val certId: String) : CertificateEvent()
    data class PathPinned(val path: String, val certIds: List<String>) : CertificateEvent()
    data class Expired(val certId: String) : CertificateEvent()
    data class StatusChanged(val accountId: String, val certId: String, val status: String) :
        CertificateEvent()
}

/**
 * Certificate pinning and trust for an account.
 *
 * The daemon owns the pinned set; this service only reads it and relays changes, so there is no
 * cached list to go stale. [events] carries the four daemon signals that were previously dropped
 * on the floor — in particular `certificateExpired`, which is the only warning that a pinned CA
 * has lapsed and connections relying on it are about to start failing verification.
 */
class CertificateService(
    private val daemonBridge: DaemonBridgeApi,
) {
    private val _events = MutableSharedFlow<CertificateEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CertificateEvent> = _events.asSharedFlow()

    /**
     * path -> the ids the daemon reported pinning from it, from `certificatePathPinned`.
     *
     * Needed because `unpinCertificate` cannot remove a path-pinned certificate at all — only
     * `unpinCertificatePath` can — and nothing in the daemon's API maps an id back to its path.
     * This is in-memory, which matches the daemon: its own `paths_` map is too.
     */
    private val pinnedPaths = mutableMapOf<String, List<String>>()

    /**
     * Every certificate pinned on this account, one entry per *certificate* — not per id.
     *
     * `getPinnedCertificates` returns several ids for the same certificate: the store emplaces
     * both the short and the long id and repeats that up the issuer chain
     * (`certstore.cpp:299-302`), so a self-signed certificate yields two ids and one with a CA
     * yields six. Listing them raw showed each certificate two or more times, with the rows
     * disagreeing about trust status because status is stored per id.
     *
     * They are collapsed on [Certificate.identity] (the fingerprint), keeping every id for the
     * actions that need them. The daemon has no "list with details" call, so this is two calls per
     * id; the pinned set is a handful in practice. A certificate whose details cannot be read is
     * still listed under its own id — it cannot be grouped, but it can still be unpinned, which is
     * what someone would want to do with it.
     */
    fun listCertificates(accountId: String): List<Certificate> =
        try {
            val statuses = statusesByCertId(accountId)
            val perId = daemonBridge.getPinnedCertificates(accountId).map { certId ->
                Certificate(
                    id = certId,
                    status = statuses[certId] ?: CertificateStatus.UNDEFINED,
                    details = runCatching { daemonBridge.getCertificateDetails(accountId, certId) }
                        .getOrDefault(emptyMap()),
                    checks = runCatching { daemonBridge.validateCertificate(accountId, certId) }
                        .getOrDefault(emptyMap()),
                )
            }
            perId.groupBy { it.identity }.map { (_, group) -> merge(group) }
        } catch (e: Exception) {
            Log.e(TAG, "listCertificates failed: ${e.message}")
            emptyList()
        }

    /**
     * Collapse the store's several ids for one certificate into a single row.
     *
     * The longest id wins as the one to act on: the file the store writes is named after the long
     * id (`certstore.cpp:384`), so `unpinCertificate` only succeeds with it. A status set on any
     * one id counts for the certificate — the ids are the same certificate, and the alternative
     * (reporting UNDEFINED because the *other* id was never set) is what made two rows disagree.
     */
    private fun merge(group: List<Certificate>): Certificate {
        val primary = group.maxBy { it.id.length }
        val ids = group.map { it.id }.distinct()
        val status = group.map { it.status }.firstOrNull { it != CertificateStatus.UNDEFINED }
            ?: CertificateStatus.UNDEFINED
        return primary.copy(
            ids = ids,
            status = status,
            pinnedFromPath = pathForIds(ids),
        )
    }

    /** The file a certificate was pinned from, if this session saw it pinned. */
    private fun pathForIds(ids: List<String>): String? =
        pinnedPaths.entries.firstOrNull { (_, pathIds) -> pathIds.any { it in ids } }?.key

    /**
     * Trust status per certificate. The daemon only answers "which certificates have status X", so
     * the answer is assembled from the non-default statuses; anything absent is UNDEFINED.
     */
    private fun statusesByCertId(accountId: String): Map<String, String> =
        buildMap {
            for (status in listOf(CertificateStatus.ALLOWED, CertificateStatus.BANNED)) {
                runCatching { daemonBridge.getCertificatesByStatus(accountId, status) }
                    .getOrDefault(emptyList())
                    .forEach { put(it, status) }
            }
        }

    fun certificateDetails(accountId: String, certId: String): Certificate = Certificate(
        id = certId,
        status = statusesByCertId(accountId)[certId] ?: CertificateStatus.UNDEFINED,
        details = runCatching { daemonBridge.getCertificateDetails(accountId, certId) }
            .getOrDefault(emptyMap()),
        checks = runCatching { daemonBridge.validateCertificate(accountId, certId) }
            .getOrDefault(emptyMap()),
    )

    /**
     * Pin a certificate from a file on this device.
     *
     * Deliberately **not** `pinCertificatePath`. That call keeps the certificate in the daemon's
     * in-memory `paths_` map and writes nothing to disk (`certstore.cpp:284-316`), so the pin is
     * gone on the next daemon start — which is what made pins look like they did not survive a
     * relaunch. Reading the bytes and pinning by content takes the `local = true` path, which
     * does `saveFile(certPath_ / ...)` and therefore persists.
     *
     * Returns the ids the daemon stored, empty if the file could not be read or held nothing
     * usable.
     */
    fun pinCertificateFile(accountId: String, path: String): List<String> {
        val bytes = FileUtils.readBytes(path)
        if (bytes == null || bytes.isEmpty()) {
            Log.w(TAG, "pinCertificateFile: nothing to read at $path")
            return emptyList()
        }
        val ids = pinCertificate(accountId, bytes, local = true)
        if (ids.isNotEmpty()) pinnedPaths[path] = ids
        return ids
    }

    /**
     * Pin a certificate, or a whole directory of them, by path.
     *
     * Kept because it is the only call that takes a directory, but note it does not persist — see
     * [pinCertificateFile]. The daemon reports what it actually pinned through
     * `certificatePathPinned`, so the result here says only whether the call was accepted.
     */
    fun pinCertificatePath(accountId: String, path: String): Boolean =
        try {
            daemonBridge.pinCertificatePath(accountId, path)
            true
        } catch (e: Exception) {
            Log.e(TAG, "pinCertificatePath failed: ${e.message}")
            false
        }

    /** Pin a certificate from its raw bytes. Returns the ids the daemon stored. */
    fun pinCertificate(accountId: String, certificate: ByteArray, local: Boolean = true): List<String> =
        try {
            daemonBridge.pinCertificate(accountId, certificate, local)
        } catch (e: Exception) {
            Log.e(TAG, "pinCertificate failed: ${e.message}")
            emptyList()
        }

    /**
     * Unpin a certificate, whichever way it was pinned.
     *
     * `unpinCertificate(id)` erases only the id it is given and returns the result of deleting
     * `certPath_/<id>` (`certstore.cpp:396-402`) — so it reports failure unless called with the
     * id the file is named after, and reports failure always for a path-pinned certificate, which
     * has no file there. Hence: the path first when known, then every id, and success if any of
     * them took.
     */
    fun unpinCertificate(accountId: String, certificate: Certificate): Boolean {
        certificate.pinnedFromPath?.let { path ->
            if (unpinCertificatePath(accountId, path) > 0) {
                pinnedPaths.remove(path)
                return true
            }
        }
        // Longest id first: that is the one the stored file is named after.
        val removed = certificate.ids.sortedByDescending { it.length }.map { id ->
            try {
                daemonBridge.unpinCertificate(accountId, id)
            } catch (e: Exception) {
                Log.e(TAG, "unpinCertificate($id) failed: ${e.message}")
                false
            }
        }
        return removed.any { it }
    }

    /** Unpin everything that came from [path]; returns how many certificates were removed. */
    fun unpinCertificatePath(accountId: String, path: String): Int =
        try {
            daemonBridge.unpinCertificatePath(accountId, path)
        } catch (e: Exception) {
            Log.e(TAG, "unpinCertificatePath failed: ${e.message}")
            0
        }

    /**
     * Set a certificate's trust status. Only the daemon's three statuses are accepted — passing
     * anything else through would be stored and then silently ignored by the daemon.
     */
    /**
     * Set a certificate's trust status on **every** id the store holds it under.
     *
     * Status is stored per id, so setting it on one left the certificate's other id reporting the
     * old value — visible as two rows disagreeing before the ids were collapsed. Some ids are
     * legitimately refused (`ContactList::setCertificateStatus` declines an id that is an
     * existing contact, `contact_list.cpp:71`), so this succeeds if any id took it and logs the
     * rest rather than reporting a failure the user cannot act on.
     */
    fun setCertificateStatus(accountId: String, certificate: Certificate, status: String): Boolean {
        if (status !in CertificateStatus.all) {
            Log.w(TAG, "refusing unknown certificate status '$status'")
            return false
        }
        val applied = certificate.ids.map { id -> setStatusForId(accountId, id, status) }
        return applied.any { it }
    }

    private fun setStatusForId(accountId: String, certId: String, status: String): Boolean =
        try {
            daemonBridge.setCertificateStatus(accountId, certId, status).also { ok ->
                if (!ok) Log.d(TAG, "daemon declined status $status for id $certId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setCertificateStatus($certId) failed: ${e.message}")
            false
        }

    // ==================== Daemon signals ====================

    internal fun onCertificatePinned(certId: String) {
        Log.d(TAG, "certificate pinned: $certId")
        _events.tryEmit(CertificateEvent.Pinned(certId))
    }

    internal fun onCertificatePathPinned(path: String, certIds: List<String>) {
        Log.d(TAG, "certificate path pinned: $path (${certIds.size} ids)")
        // Remembered so the certificate can be unpinned later: unpinCertificate cannot remove a
        // path pin, and no daemon call maps an id back to its path.
        if (certIds.isNotEmpty()) pinnedPaths[path] = certIds
        _events.tryEmit(CertificateEvent.PathPinned(path, certIds))
    }

    internal fun onCertificateExpired(certId: String) {
        Log.w(TAG, "pinned certificate has expired: $certId")
        _events.tryEmit(CertificateEvent.Expired(certId))
    }

    internal fun onCertificateStateChanged(accountId: String, certId: String, state: String) {
        Log.d(TAG, "certificate $certId state -> $state")
        _events.tryEmit(CertificateEvent.StatusChanged(accountId, certId, state))
    }

    private companion object {
        const val TAG = "CertificateService"
    }
}
