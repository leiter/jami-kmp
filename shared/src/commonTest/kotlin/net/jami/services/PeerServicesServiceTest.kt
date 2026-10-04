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

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeerServicesServiceTest {

    private val servicesJson = """
        [
          {"id":"svc1","name":"Files","description":"File browser","scheme":"http","device":"dev1"},
          {"id":"svc2","name":"Shell","description":"","scheme":"ssh","device":"dev1"}
        ]
    """.trimIndent()

    @Test
    fun exposedServicesRoundTripThroughTheDaemon() {
        val stub = StubDaemonBridge()
        val service = PeerServicesService(stub)

        val id = service.addExposedService("acc1", mapOf("name" to "Files", "scheme" to "http"))

        assertEquals("service1", id)
        assertEquals(1, service.getExposedServices("acc1").size)
        assertTrue(service.removeExposedService("acc1", id!!))
        assertTrue(service.getExposedServices("acc1").isEmpty())
    }

    @Test
    fun queryPeerServicesResolvesWithTheParsedServiceList() = runTest {
        val stub = StubDaemonBridge()
        val service = PeerServicesService(stub)

        val deferred = async { service.queryPeerServices("acc1", "peer1") }
        advanceUntilIdle()
        service.onPeerServicesReceived(
            requestId = stub.lastPeerServicesRequestId,
            accountId = "acc1",
            peerId = "peer1",
            status = 0,
            servicesJson = servicesJson,
        )
        val result = deferred.await()

        assertEquals(PeerServicesStatus.OK, result.status)
        assertEquals(listOf("svc1", "svc2"), result.services.map { it.id })
        assertEquals("Files", result.services[0].name)
        assertEquals("ssh", result.services[1].scheme)
    }

    /** A reply for a different request must not satisfy our query. */
    @Test
    fun queryPeerServicesIgnoresAnotherRequestsReply() = runTest {
        val stub = StubDaemonBridge()
        val service = PeerServicesService(stub)

        val deferred = async { service.queryPeerServices("acc1", "peer1") }
        advanceUntilIdle()
        service.onPeerServicesReceived(999L, "acc1", "peer1", 0, servicesJson)
        advanceUntilIdle()
        assertFalse(deferred.isCompleted)

        service.onPeerServicesReceived(stub.lastPeerServicesRequestId, "acc1", "peer1", 0, servicesJson)
        assertEquals(2, deferred.await().services.size)
    }

    /** The daemon's status code decides the outcome; an unknown code is an internal error. */
    @Test
    fun peerServicesStatusMapsDaemonCodes() {
        assertEquals(PeerServicesStatus.OK, PeerServicesStatus.fromCode(0))
        assertEquals(PeerServicesStatus.NO_DEVICES, PeerServicesStatus.fromCode(1))
        assertEquals(PeerServicesStatus.UNREACHABLE, PeerServicesStatus.fromCode(2))
        assertEquals(PeerServicesStatus.TIMEOUT, PeerServicesStatus.fromCode(3))
        assertEquals(PeerServicesStatus.INTERNAL_ERROR, PeerServicesStatus.fromCode(42))
    }

    /** A malformed payload must not throw on the daemon callback thread. */
    @Test
    fun malformedServiceListYieldsNoServices() = runTest {
        val stub = StubDaemonBridge()
        val service = PeerServicesService(stub)

        val deferred = async { service.queryPeerServices("acc1", "peer1") }
        advanceUntilIdle()
        service.onPeerServicesReceived(stub.lastPeerServicesRequestId, "acc1", "peer1", 0, "not json")

        assertTrue(deferred.await().services.isEmpty())
    }

    @Test
    fun openingATunnelMarksItPendingThenActive() {
        val stub = StubDaemonBridge()
        stub.openTunnelResult = "tunnel-a"
        val service = PeerServicesService(stub)
        val svc = PeerServiceInfo("svc1", "Files", "", "http", "dev1")

        service.openTunnel("acc1", "peer1", svc)
        assertTrue(service.tunnelState("acc1", "peer1").pendingServices.contains("svc1"))

        service.onTunnelOpened("acc1", "tunnel-a", 8080)

        val state = service.tunnelState("acc1", "peer1")
        assertTrue(state.pendingServices.isEmpty())
        assertEquals(8080, state.activeTunnels["svc1"]?.localPort)
        assertTrue(service.anyActiveTunnel.value)
    }

    /** A refused tunnel clears the pending marker instead of leaving the UI spinning. */
    @Test
    fun aRefusedTunnelClearsThePendingMarker() {
        val stub = StubDaemonBridge()
        stub.openTunnelResult = ""
        val service = PeerServicesService(stub)

        service.openTunnel("acc1", "peer1", PeerServiceInfo("svc1", "Files", "", "http", "dev1"))

        assertTrue(service.tunnelState("acc1", "peer1").pendingServices.isEmpty())
        assertFalse(service.anyActiveTunnel.value)
    }

    @Test
    fun closingATunnelRemovesItFromTheState() {
        val stub = StubDaemonBridge()
        stub.openTunnelResult = "tunnel-a"
        val service = PeerServicesService(stub)
        service.openTunnel("acc1", "peer1", PeerServiceInfo("svc1", "Files", "", "http", "dev1"))
        service.onTunnelOpened("acc1", "tunnel-a", 8080)

        service.closeTunnel("acc1", "peer1", "svc1")
        service.onTunnelClosed("acc1", "tunnel-a", "peer closed")

        assertEquals(listOf("tunnel-a"), stub.closedTunnels)
        assertTrue(service.tunnelState("acc1", "peer1").activeTunnels.isEmpty())
        assertFalse(service.anyActiveTunnel.value)
    }

    @Test
    fun activeTunnelsAreReadBackFromTheDaemon() {
        val stub = StubDaemonBridge()
        stub.activeTunnels = listOf(
            mapOf(
                "id" to "tunnel-a", "peerUri" to "peer1", "serviceId" to "svc1",
                "serviceName" to "Files", "scheme" to "http", "localPort" to "9000",
            ),
            // No id: the daemon reported a partial row, which must be skipped rather than
            // producing a tunnel the app cannot close.
            mapOf("peerUri" to "peer1", "serviceId" to "svc2"),
        )
        val service = PeerServicesService(stub)

        val tunnels = service.getAllActiveTunnels(listOf("acc1", ""))

        assertEquals(1, tunnels.size)
        assertEquals(9000, tunnels[0].localPort)
        assertEquals("Files", tunnels[0].serviceName)
    }

    @Test
    fun closeAllTunnelsClosesEveryTunnelTheDaemonHolds() {
        val stub = StubDaemonBridge()
        stub.activeTunnels = listOf(
            mapOf("id" to "tunnel-a", "peerUri" to "peer1", "serviceId" to "svc1"),
            mapOf("id" to "tunnel-b", "peerUri" to "peer2", "serviceId" to "svc2"),
        )
        val service = PeerServicesService(stub)

        service.closeAllTunnels(listOf("acc1"))

        assertEquals(listOf("tunnel-a", "tunnel-b"), stub.closedTunnels)
    }
}
