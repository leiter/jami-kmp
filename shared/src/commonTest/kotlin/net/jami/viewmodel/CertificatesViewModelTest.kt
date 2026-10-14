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
package net.jami.viewmodel

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.CertificateDetail
import net.jami.model.CertificateStatus
import net.jami.services.CertificateService
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.CertificatesViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CertificatesViewModelTest {

    private fun stubWithCertificates(): StubDaemonBridge = StubDaemonBridge().apply {
        certificates[TEST_ACCOUNT_ID] = mutableMapOf(
            "cert-ca" to mapOf(CertificateDetail.COMMON_NAME to "Example CA"),
            "cert-peer" to mapOf(CertificateDetail.COMMON_NAME to "peer.example.net"),
        )
    }

    private suspend fun TestScope.viewModel(
        stub: StubDaemonBridge,
        certificateService: CertificateService = CertificateService(stub),
    ): CertificatesViewModel {
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        advanceUntilIdle()
        return CertificatesViewModel(certificateService, accountService, viewModelScope())
    }

    private fun CertificatesViewModel.certificate(id: String) =
        state.value.certificates.first { id in it.ids }

    @Test
    fun pinnedCertificatesAreLoadedForTheActiveAccount() = runTest {
        val vm = viewModel(stubWithCertificates())
        advanceUntilIdle()

        assertEquals(
            setOf("cert-ca", "cert-peer"),
            vm.state.value.certificates.map { it.id }.toSet(),
        )
        assertTrue(vm.state.value.isLoading.not())
    }

    @Test
    fun settingATrustStatusIsReflectedAfterTheReread() = runTest {
        val stub = stubWithCertificates()
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.setStatus(vm.certificate("cert-peer"), CertificateStatus.BANNED)
        advanceUntilIdle()

        val peer = vm.state.value.certificates.first { it.id == "cert-peer" }
        assertEquals(CertificateStatus.BANNED, peer.status)
    }

    @Test
    fun unpinningRemovesTheRow() = runTest {
        val vm = viewModel(stubWithCertificates())
        advanceUntilIdle()

        vm.unpinCertificate(vm.certificate("cert-peer"))
        advanceUntilIdle()

        assertEquals(listOf("cert-ca"), vm.state.value.certificates.map { it.id })
        assertNull(vm.state.value.errorMessage)
    }

    /** A failed action must say so rather than silently appear to have worked. */
    @Test
    fun aFailedUnpinIsSurfacedAsAnError() = runTest {
        val vm = viewModel(stubWithCertificates())
        advanceUntilIdle()

        vm.unpinCertificate(net.jami.model.Certificate(id = "cert-gone"))
        advanceUntilIdle()

        assertEquals(CertificatesViewModel.UNPIN_FAILED, vm.state.value.errorMessage)
        vm.clearError()
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun anUnknownStatusIsReportedAsAFailedChange() = runTest {
        val vm = viewModel(stubWithCertificates())
        advanceUntilIdle()

        vm.setStatus(vm.certificate("cert-peer"), "SOMEWHAT_TRUSTED")
        advanceUntilIdle()

        assertEquals(CertificatesViewModel.STATUS_FAILED, vm.state.value.errorMessage)
    }

    /** An expansion whose certificate is no longer pinned must not survive the refresh. */
    @Test
    fun anExpansionIsDroppedWhenItsCertificateGoesAway() = runTest {
        val vm = viewModel(stubWithCertificates())
        advanceUntilIdle()

        vm.toggleExpanded("cert-peer")
        assertEquals("cert-peer", vm.state.value.expandedId)

        vm.unpinCertificate(vm.certificate("cert-peer"))
        advanceUntilIdle()

        assertNull(vm.state.value.expandedId)
    }

    @Test
    fun tappingAnExpandedRowCollapsesIt() = runTest {
        val vm = viewModel(stubWithCertificates())
        advanceUntilIdle()

        vm.toggleExpanded("cert-ca")
        vm.toggleExpanded("cert-ca")

        assertNull(vm.state.value.expandedId)
    }

    /**
     * The daemon's expiry signal can arrive for a certificate that is already listed, after its
     * checks were read — so it is tracked separately rather than waiting for the next refresh.
     */
    @Test
    fun theExpirySignalFlagsAListedCertificate() = runTest {
        val stub = stubWithCertificates()
        val certificateService = CertificateService(stub)
        val vm = viewModel(stub, certificateService)
        advanceUntilIdle()

        certificateService.onCertificateExpired("cert-ca")
        advanceUntilIdle()

        assertTrue("cert-ca" in vm.state.value.expiredIds)
    }

    /** An expiry flag for a certificate that is no longer pinned must not linger. */
    @Test
    fun anExpiryFlagIsClearedWhenTheCertificateIsUnpinned() = runTest {
        val stub = stubWithCertificates()
        val certificateService = CertificateService(stub)
        val vm = viewModel(stub, certificateService)
        advanceUntilIdle()

        certificateService.onCertificateExpired("cert-peer")
        advanceUntilIdle()
        vm.unpinCertificate(vm.certificate("cert-peer"))
        advanceUntilIdle()

        assertTrue(vm.state.value.expiredIds.isEmpty())
    }

    /** A pin signal from the daemon refreshes the list, including certificates we did not add. */
    @Test
    fun aPinSignalRefreshesTheList() = runTest {
        val stub = StubDaemonBridge()
        val certificateService = CertificateService(stub)
        val vm = viewModel(stub, certificateService)
        advanceUntilIdle()
        assertTrue(vm.state.value.certificates.isEmpty())

        stub.certificates[TEST_ACCOUNT_ID] = mutableMapOf("cert-new" to emptyMap())
        certificateService.onCertificatePinned("cert-new")
        advanceUntilIdle()

        assertEquals(listOf("cert-new"), vm.state.value.certificates.map { it.id })
    }

    /**
     * Pinning reads the file and pins by content, so a path that cannot be read is an error the
     * screen reports rather than a silent no-op — and it must not fall back to the path call,
     * which the daemon does not persist.
     */
    @Test
    fun pinningAnUnreadableFileIsReportedAsAnError() = runTest {
        val stub = StubDaemonBridge()
        val vm = viewModel(stub)
        advanceUntilIdle()

        vm.pinCertificateFile("/no/such/ca.pem")
        advanceUntilIdle()

        assertEquals(CertificatesViewModel.PIN_FAILED, vm.state.value.errorMessage)
        assertTrue(stub.pinnedCertificatePaths.isEmpty())
    }

    /** With no account there is nothing to read, and no crash either. */
    @Test
    fun noAccountYieldsAnEmptyList() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = CertificatesViewModel(CertificateService(stub), accountService, viewModelScope())
        advanceUntilIdle()

        assertTrue(vm.state.value.certificates.isEmpty())
        assertNotNull(vm.state)
    }
}
