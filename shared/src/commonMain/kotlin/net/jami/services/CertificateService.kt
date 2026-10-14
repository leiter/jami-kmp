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
     * Every certificate pinned on this account, with its trust status and the daemon's own checks.
     *
     * The daemon has no "list with details" call, so this is one call per certificate; the pinned
     * set is a handful of entries in practice. A certificate whose details cannot be read is still
     * listed — the id alone is enough to unpin it, which is what someone would want to do with it.
     */
    fun listCertificates(accountId: String): List<Certificate> =
        try {
            val statuses = statusesByCertId(accountId)
            daemonBridge.getPinnedCertificates(accountId).map { certId ->
                Certificate(
                    id = certId,
                    status = statuses[certId] ?: CertificateStatus.UNDEFINED,
                    details = runCatching { daemonBridge.getCertificateDetails(accountId, certId) }
                        .getOrDefault(emptyMap()),
                    checks = runCatching { daemonBridge.validateCertificate(accountId, certId) }
                        .getOrDefault(emptyMap()),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "listCertificates failed: ${e.message}")
            emptyList()
        }

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
     * Pin a certificate, or a directory of them, from a file on this device.
     *
     * The daemon reports what it actually pinned through `certificatePathPinned`, so the result
     * here says only whether the call was accepted: a path holding nothing usable pins nothing and
     * raises no error.
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

    fun unpinCertificate(accountId: String, certId: String): Boolean =
        try {
            daemonBridge.unpinCertificate(accountId, certId)
        } catch (e: Exception) {
            Log.e(TAG, "unpinCertificate failed: ${e.message}")
            false
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
    fun setCertificateStatus(accountId: String, certId: String, status: String): Boolean {
        if (status !in CertificateStatus.all) {
            Log.w(TAG, "refusing unknown certificate status '$status'")
            return false
        }
        return try {
            daemonBridge.setCertificateStatus(accountId, certId, status)
        } catch (e: Exception) {
            Log.e(TAG, "setCertificateStatus failed: ${e.message}")
            false
        }
    }

    // ==================== Daemon signals ====================

    internal fun onCertificatePinned(certId: String) {
        Log.d(TAG, "certificate pinned: $certId")
        _events.tryEmit(CertificateEvent.Pinned(certId))
    }

    internal fun onCertificatePathPinned(path: String, certIds: List<String>) {
        Log.d(TAG, "certificate path pinned: $path (${certIds.size} certificates)")
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
