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
import net.jami.model.Certificate
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
        val peer = service.listCertificates(accountId).first { it.id == "cert-peer" }
        service.setCertificateStatus(accountId, peer, CertificateStatus.BANNED)

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

        // EXPIRED = PASSED means the notExpired check passed, i.e. the certificate is valid.
        assertFalse(certificate.isExpired)
        assertTrue(certificate.hasFailedChecks)
        // A check the daemon could not run is not a failure.
        assertEquals(listOf(CertificateCheck.KNOWN_AUTHORITY), certificate.failedChecks)
    }

    /**
     * The regression that shipped in 4000a79: EXPIRED=PASSED was read as expired, so every valid
     * certificate was labelled "Expired". The daemon's check is `notExpired`, so FAILED is what
     * means expired.
     */
    @Test
    fun onlyAFailedExpiryCheckMeansExpired() {
        val stub = stubWithCertificates()
        stub.certificateChecks["cert-ca"] = mapOf(
            CertificateCheck.EXPIRED to CertificateCheck.FAILED,
            CertificateCheck.KNOWN_AUTHORITY to CertificateCheck.PASSED,
        )
        stub.certificateChecks["cert-peer"] = mapOf(
            CertificateCheck.EXPIRED to CertificateCheck.PASSED,
        )
        val service = CertificateService(stub)
        val certificates = service.listCertificates(accountId).associateBy { it.id }

        assertTrue(certificates["cert-ca"]!!.isExpired)
        assertFalse(certificates["cert-peer"]!!.isExpired)
    }

    /** A certificate the daemon has not validated is not assumed expired either. */
    @Test
    fun anUncheckedCertificateIsNotExpired() {
        val service = CertificateService(stubWithCertificates())

        assertFalse(service.listCertificates(accountId).first { it.id == "cert-ca" }.isExpired)
    }

    @Test
    fun unpinningRemovesTheCertificate() {
        val stub = stubWithCertificates()
        val service = CertificateService(stub)
        val peer = service.listCertificates(accountId).first { it.id == "cert-peer" }

        assertTrue(service.unpinCertificate(accountId, peer))

        assertEquals(listOf("cert-ca"), service.listCertificates(accountId).map { it.id })
    }

    @Test
    fun unpinningSomethingThatIsNotPinnedIsReportedAsAFailure() {
        val service = CertificateService(stubWithCertificates())

        assertFalse(service.unpinCertificate(accountId, Certificate(id = "cert-unknown")))
    }

    /**
     * An unknown status would be accepted by the bridge and then ignored by the daemon, which
     * would look like the setting had been applied.
     */
    @Test
    fun anUnknownTrustStatusIsRefusedWithoutReachingTheDaemon() {
        val stub = stubWithCertificates()
        val service = CertificateService(stub)
        val peer = service.listCertificates(accountId).first { it.id == "cert-peer" }

        assertFalse(service.setCertificateStatus(accountId, peer, "SOMEWHAT_TRUSTED"))

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

    // ==================== One certificate, several store ids ====================

    /**
     * `CertificateStore` emplaces both the short and the long id for every certificate, and
     * repeats that up the issuer chain, so `getPinnedCertificates` returns two or more ids per
     * certificate. Listing them raw showed the same certificate as several rows.
     */
    private fun stubWithDoubleIds(): StubDaemonBridge = StubDaemonBridge().apply {
        val details = mapOf(
            CertificateDetail.COMMON_NAME to "Example CA",
            CertificateDetail.SHA1_FINGERPRINT to "AA:BB:CC",
        )
        certificates[accountId] = mutableMapOf(
            "short" to details,
            "the-much-longer-id" to details,
        )
    }

    @Test
    fun theStoresTwoIdsForOneCertificateCollapseIntoOneRow() {
        val service = CertificateService(stubWithDoubleIds())

        val certificates = service.listCertificates(accountId)

        assertEquals(1, certificates.size)
        assertEquals(setOf("short", "the-much-longer-id"), certificates.single().ids.toSet())
    }

    /** The long id is the one to act on: the stored file is named after it. */
    @Test
    fun theLongestIdIsTheOneActionsUse() {
        val service = CertificateService(stubWithDoubleIds())

        assertEquals("the-much-longer-id", service.listCertificates(accountId).single().id)
    }

    /** Two certificates that merely lack a fingerprint must not be merged into one. */
    @Test
    fun certificatesWithNoFingerprintAreNotGroupedTogether() {
        val stub = StubDaemonBridge().apply {
            certificates[accountId] = mutableMapOf("a" to emptyMap(), "b" to emptyMap())
        }
        val service = CertificateService(stub)

        assertEquals(2, service.listCertificates(accountId).size)
    }

    /** A status set on either id counts for the certificate, instead of the rows disagreeing. */
    @Test
    fun aStatusSetOnOneIdIsReportedForTheCertificate() {
        val stub = stubWithDoubleIds()
        val service = CertificateService(stub)
        stub.setCertificateStatus(accountId, "short", CertificateStatus.BANNED)

        assertEquals(CertificateStatus.BANNED, service.listCertificates(accountId).single().status)
    }

    @Test
    fun settingAStatusAppliesItToEveryIdOfTheCertificate() {
        val stub = stubWithDoubleIds()
        val service = CertificateService(stub)
        val certificate = service.listCertificates(accountId).single()

        assertTrue(service.setCertificateStatus(accountId, certificate, CertificateStatus.ALLOWED))

        assertEquals(
            setOf(CertificateStatus.ALLOWED),
            certificate.ids.map { stub.certificateStatuses[accountId]?.get(it) }.toSet(),
        )
    }

    /** Unpinning must clear every id, not just the one the row happened to carry. */
    @Test
    fun unpinningRemovesEveryIdOfTheCertificate() {
        val stub = stubWithDoubleIds()
        val service = CertificateService(stub)
        val certificate = service.listCertificates(accountId).single()

        assertTrue(service.unpinCertificate(accountId, certificate))

        assertTrue(service.listCertificates(accountId).isEmpty())
    }

    // ==================== Pinning from a file ====================

    /**
     * Pinning by *path* leaves the certificate in the daemon's in-memory map and writes nothing,
     * so it is gone after a restart. Pinning the file's content takes the persisting path.
     */
    /**
     * Pinning the content takes the daemon's `local = true` path, which writes the certificate to
     * its store and therefore survives a restart. Pinning by *path* only fills an in-memory map.
     */
    @Test
    fun pinningByContentReachesTheStoreAndNotThePathMap() {
        val stub = StubDaemonBridge()
        val service = CertificateService(stub)

        val ids = service.pinCertificate(accountId, "a certificate".encodeToByteArray(), local = true)

        assertTrue(ids.isNotEmpty())
        assertEquals(ids.toSet(), stub.certificates[accountId]!!.keys)
        assertTrue(stub.pinnedCertificatePaths.isEmpty())
    }

    /** A file that cannot be read pins nothing, and must not fall back to the path call. */
    @Test
    fun pinningAFileThatCannotBeReadPinsNothing() {
        val stub = stubWithCertificates()
        val service = CertificateService(stub)

        assertTrue(service.pinCertificateFile(accountId, "/no/such/certificate.pem").isEmpty())
        assertTrue(stub.pinnedCertificatePaths.isEmpty())
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
