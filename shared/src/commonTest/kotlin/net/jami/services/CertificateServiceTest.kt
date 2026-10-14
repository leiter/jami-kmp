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

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.jami.model.CertificateCheck
import net.jami.model.CertificateDetail
import net.jami.model.CertificateStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CertificateServiceTest {

    private val accountId = "acc1"

    private fun stubWithCertificates(): StubDaemonBridge = StubDaemonBridge().apply {
        certificates[accountId] = mutableMapOf(
            "cert-ca" to mapOf(
                CertificateDetail.COMMON_NAME to "Example CA",
                CertificateDetail.ISSUER_COMMON_NAME to "Example Root",
                CertificateDetail.IS_CA to "true",
            ),
            "cert-peer" to mapOf(CertificateDetail.COMMON_NAME to "peer.example.net"),
        )
    }

    @Test
    fun pinnedCertificatesAreListedWithTheirDetails() {
        val service = CertificateService(stubWithCertificates())

        val certificates = service.listCertificates(accountId)

        assertEquals(setOf("cert-ca", "cert-peer"), certificates.map { it.id }.toSet())
        val ca = certificates.first { it.id == "cert-ca" }
        assertEquals("Example CA", ca.commonName)
        assertEquals("Example Root", ca.issuer)
        assertTrue(ca.isCertificateAuthority)
    }

    /**
     * The daemon only answers "which certificates have status X", so the per-certificate status
     * has to be assembled from those answers; anything it does not name is UNDEFINED.
     */
    @Test
    fun trustStatusIsResolvedPerCertificate() {
        val stub = stubWithCertificates()
        val service = CertificateService(stub)
        service.setCertificateStatus(accountId, "cert-peer", CertificateStatus.BANNED)

        val certificates = service.listCertificates(accountId).associateBy { it.id }

        assertEquals(CertificateStatus.BANNED, certificates["cert-peer"]!!.status)
        assertEquals(CertificateStatus.UNDEFINED, certificates["cert-ca"]!!.status)
    }

    /** A certificate whose details cannot be read is still listed: the id is enough to unpin it. */
    @Test
    fun aCertificateWithNoReadableDetailsIsStillListed() {
        val stub = StubDaemonBridge().apply {
            certificates[accountId] = mutableMapOf("cert-broken" to emptyMap())
        }
        val service = CertificateService(stub)

        val certificate = service.listCertificates(accountId).single()

        // With no CN to show, the row falls back to the id rather than rendering blank.
        assertEquals("cert-broken", certificate.commonName)
    }

    @Test
    fun theDaemonsChecksAreReportedAgainstTheCertificate() {
        val stub = stubWithCertificates()
        stub.certificateChecks["cert-peer"] = mapOf(
            CertificateCheck.EXPIRED to CertificateCheck.PASSED,
            CertificateCheck.KNOWN_AUTHORITY to CertificateCheck.FAILED,
            CertificateCheck.HAS_PRIVATE_KEY to CertificateCheck.UNSUPPORTED,
        )
        val service = CertificateService(stub)

        val certificate = service.listCertificates(accountId).first { it.id == "cert-peer" }

        // The daemon reports expiry as a check that *passed* when the certificate is expired.
        assertTrue(certificate.isExpired)
        assertTrue(certificate.hasFailedChecks)
        // A check the daemon could not run is not a failure.
        assertEquals(listOf(CertificateCheck.KNOWN_AUTHORITY), certificate.failedChecks)
    }

    @Test
    fun aCertificateWithPassingChecksHasNoFailures() {
        val stub = stubWithCertificates()
        stub.certificateChecks["cert-ca"] = mapOf(
            CertificateCheck.EXPIRED to CertificateCheck.FAILED,
            CertificateCheck.KNOWN_AUTHORITY to CertificateCheck.PASSED,
        )
        val service = CertificateService(stub)

        val certificate = service.listCertificates(accountId).first { it.id == "cert-ca" }

        assertFalse(certificate.isExpired)
        assertTrue(certificate.failedChecks.contains(CertificateCheck.EXPIRED))
    }

    @Test
    fun unpinningRemovesTheCertificate() {
        val stub = stubWithCertificates()
        val service = CertificateService(stub)

        assertTrue(service.unpinCertificate(accountId, "cert-peer"))

        assertEquals(listOf("cert-ca"), service.listCertificates(accountId).map { it.id })
    }

    @Test
    fun unpinningSomethingThatIsNotPinnedIsReportedAsAFailure() {
        val service = CertificateService(stubWithCertificates())

        assertFalse(service.unpinCertificate(accountId, "cert-unknown"))
    }

    /**
     * An unknown status would be accepted by the bridge and then ignored by the daemon, which
     * would look like the setting had been applied.
     */
    @Test
    fun anUnknownTrustStatusIsRefusedWithoutReachingTheDaemon() {
        val stub = stubWithCertificates()
        val service = CertificateService(stub)

        assertFalse(service.setCertificateStatus(accountId, "cert-peer", "SOMEWHAT_TRUSTED"))

        assertEquals(
            CertificateStatus.UNDEFINED,
            service.listCertificates(accountId).first { it.id == "cert-peer" }.status,
        )
    }

    @Test
    fun pinningAPathIsPassedToTheDaemon() {
        val stub = stubWithCertificates()
        val service = CertificateService(stub)

        assertTrue(service.pinCertificatePath(accountId, "/sdcard/ca.pem"))

        assertEquals(listOf(accountId to "/sdcard/ca.pem"), stub.pinnedCertificatePaths)
    }

    /** An account with nothing pinned is an empty list, not an error. */
    @Test
    fun anAccountWithNoPinnedCertificatesListsNothing() {
        val service = CertificateService(StubDaemonBridge())

        assertTrue(service.listCertificates(accountId).isEmpty())
    }

    // ==================== Daemon signals (notifications plan F6) ====================

    /** Starts collecting before anything is emitted; the flow has no replay. */
    private fun TestScope.collectEvents(service: CertificateService): List<CertificateEvent> {
        val events = mutableListOf<CertificateEvent>()
        backgroundScope.launch { service.events.collect { events += it } }
        runCurrent()
        return events
    }

    @Test
    fun theExpirySignalIsRelayed() = runTest {
        val service = CertificateService(stubWithCertificates())
        val events = collectEvents(service)

        service.onCertificateExpired("cert-ca")
        runCurrent()

        assertEquals(listOf(CertificateEvent.Expired("cert-ca")), events)
    }

    @Test
    fun theStatusChangeSignalCarriesTheAccountAndStatus() = runTest {
        val service = CertificateService(stubWithCertificates())
        val events = collectEvents(service)

        service.onCertificateStateChanged(accountId, "cert-peer", CertificateStatus.BANNED)
        runCurrent()

        assertEquals(
            listOf(CertificateEvent.StatusChanged(accountId, "cert-peer", CertificateStatus.BANNED)),
            events,
        )
    }

    @Test
    fun thePathPinnedSignalCarriesEveryCertificateFound() = runTest {
        val service = CertificateService(stubWithCertificates())
        val events = collectEvents(service)

        service.onCertificatePathPinned("/sdcard/cas", listOf("cert-a", "cert-b"))
        runCurrent()

        assertEquals(
            listOf(CertificateEvent.PathPinned("/sdcard/cas", listOf("cert-a", "cert-b"))),
            events,
        )
    }
}
