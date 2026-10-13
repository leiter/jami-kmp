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
package net.jami.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.jami.model.Certificate
import net.jami.model.CertificateStatus
import net.jami.services.AccountService
import net.jami.services.CertificateEvent
import net.jami.services.CertificateService

data class CertificatesState(
    val certificates: List<Certificate> = emptyList(),
    val isLoading: Boolean = false,
    /** The certificate expanded into its detail view, by id. */
    val expandedId: String? = null,
    /** Set when an action failed, so the screen can say so rather than appear to have worked. */
    val errorMessage: String? = null,
    /**
     * Certificates the daemon has just reported as expired. Kept separately from [certificates]
     * because the signal can arrive while the screen is open, for a certificate already listed.
     */
    val expiredIds: Set<String> = emptySet(),
)

/**
 * Pinned certificates for the active account: what is pinned, whether the daemon's own checks
 * pass, and the trust decision on each.
 *
 * The daemon owns the list, so every mutation is followed by a re-read rather than a local patch —
 * pinning a path can add several certificates at once, and unpinning one by id can remove more
 * than the row that was tapped if they share a path.
 */
class CertificatesViewModel(
    private val certificateService: CertificateService,
    private val accountService: AccountService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {

    private val _state = MutableStateFlow(CertificatesState())
    val state: StateFlow<CertificatesState> = _state.asStateFlow()

    init {
        refresh()
        observeDaemonEvents()
    }

    private val accountId: String?
        get() = accountService.currentAccount.value?.accountId

    fun refresh() {
        val accountId = accountId ?: run {
            _state.update { it.copy(certificates = emptyList(), isLoading = false) }
            return
        }
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            // Reading the pinned set is one daemon call per certificate, hence off the main thread.
            val certificates = certificateService.listCertificates(accountId)
            _state.update { current ->
                current.copy(
                    certificates = certificates,
                    isLoading = false,
                    // Drop an expansion whose certificate is no longer pinned.
                    expandedId = current.expandedId?.takeIf { id -> certificates.any { it.id == id } },
                    // The daemon's checks are authoritative over a signal we saw earlier.
                    expiredIds = current.expiredIds.intersect(certificates.map { it.id }.toSet()),
                )
            }
        }
    }

    /**
     * The daemon's certificate signals. `Expired` is the one that matters while the screen is
     * closed too, but here it only needs to refresh what is on display.
     */
    private fun observeDaemonEvents() {
        scope.launch {
            certificateService.events.collect { event ->
                when (event) {
                    is CertificateEvent.Expired ->
                        _state.update { it.copy(expiredIds = it.expiredIds + event.certId) }
                    is CertificateEvent.Pinned,
                    is CertificateEvent.PathPinned,
                    is CertificateEvent.StatusChanged -> refresh()
                }
            }
        }
    }

    fun toggleExpanded(certId: String) {
        _state.update { it.copy(expandedId = if (it.expandedId == certId) null else certId) }
    }

    fun pinCertificatePath(path: String) {
        val accountId = accountId ?: return
        scope.launch {
            // The daemon reports what it pinned through certificatePathPinned, which refreshes the
            // list; a path holding nothing usable raises no error, so say so rather than sit idle.
            val accepted = certificateService.pinCertificatePath(accountId, path)
            if (!accepted) {
                _state.update { it.copy(errorMessage = PIN_FAILED) }
            } else {
                refresh()
            }
        }
    }

    fun unpinCertificate(certId: String) {
        val accountId = accountId ?: return
        scope.launch {
            if (!certificateService.unpinCertificate(accountId, certId)) {
                _state.update { it.copy(errorMessage = UNPIN_FAILED) }
            }
            refresh()
        }
    }

    /** Set a certificate's trust status: UNDEFINED, ALLOWED or BANNED. */
    fun setStatus(certId: String, status: String) {
        val accountId = accountId ?: return
        scope.launch {
            if (!certificateService.setCertificateStatus(accountId, certId, status)) {
                _state.update { it.copy(errorMessage = STATUS_FAILED) }
            }
            refresh()
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    public override fun onCleared() {
        scope.cancel()
    }

    companion object {
        /** Statuses offered in the UI, in the order they are shown. */
        val statuses = listOf(
            CertificateStatus.UNDEFINED,
            CertificateStatus.ALLOWED,
            CertificateStatus.BANNED,
        )

        internal const val PIN_FAILED = "pin_failed"
        internal const val UNPIN_FAILED = "unpin_failed"
        internal const val STATUS_FAILED = "status_failed"
    }
}
