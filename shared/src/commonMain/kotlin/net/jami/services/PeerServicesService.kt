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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.jami.utils.Log

/** Outcome of a peer-services query, as reported by the daemon's status code. */
enum class PeerServicesStatus(val code: Int) {
    OK(0),
    NO_DEVICES(1),
    UNREACHABLE(2),
    TIMEOUT(3),
    INTERNAL_ERROR(4);

    companion object {
        fun fromCode(code: Int): PeerServicesStatus =
            entries.firstOrNull { it.code == code } ?: INTERNAL_ERROR
    }
}

/** One service a peer announces. [device] identifies which of the peer's devices offers it. */
data class PeerServiceInfo(
    val id: String,
    val name: String,
    val description: String,
    val scheme: String,
    val device: String,
)

/** A tunnel forwarding one of a peer's services to a local port on this device. */
data class TunnelInfo(
    val tunnelId: String,
    val accountId: String,
    val peerUri: String,
    val serviceId: String,
    val serviceName: String,
    val scheme: String,
    val localPort: Int,
)

/** Tunnel state for one (account, peer) pair. */
data class PeerTunnelState(
    val activeTunnels: Map<String, TunnelInfo> = emptyMap(),
    val pendingServices: Set<String> = emptySet(),
)

/** Answer to a [PeerServicesService.queryPeerServices] request. */
data class PeerServicesResult(
    val requestId: Long,
    val accountId: String,
    val peerId: String,
    val status: PeerServicesStatus,
    val services: List<PeerServiceInfo>,
)

/**
 * Peer service discovery and tunnelling.
 *
 * A device can announce services to its contacts (`addExposedService`), ask a contact what it
 * offers (`queryPeerServices`), and open a tunnel that forwards one of those services to a local
 * port (`openServiceTunnel`). This lets the app know a peer supports something before trying it,
 * instead of attempting an operation and interpreting the failure.
 *
 * Ported from jami-client-android's `PeerServicesService`; RxJava subjects became Flows and the
 * Gson parse became kotlinx.serialization. The daemon's `NetworkServiceCallback` feeds it.
 *
 * Only Android reaches the daemon here: the Apple cinterop header set has no
 * `networkservice_interface.h`, so those platforms' bridges return empty values.
 */
class PeerServicesService(
    private val daemonBridge: DaemonBridgeApi,
) {
    private val _peerServicesReceived = MutableSharedFlow<PeerServicesResult>(extraBufferCapacity = 16)
    val peerServicesReceived: SharedFlow<PeerServicesResult> = _peerServicesReceived.asSharedFlow()

    private val _tunnelStates = MutableStateFlow<Map<String, PeerTunnelState>>(emptyMap())

    private val _tunnelOpened = MutableSharedFlow<TunnelInfo>(extraBufferCapacity = 16)
    val tunnelOpened: SharedFlow<TunnelInfo> = _tunnelOpened.asSharedFlow()

    private val _anyActiveTunnel = MutableStateFlow(false)

    /** True while at least one tunnel is open, across every account and peer. */
    val anyActiveTunnel: StateFlow<Boolean> = _anyActiveTunnel.asStateFlow()

    private val activeTunnelIds = mutableSetOf<String>()

    /**
     * Tunnel opens are matched to their service through this queue rather than by id: the daemon
     * fires TunnelOpened synchronously from `openServiceTunnel`, before it has returned the
     * tunnel id to us. Mirrors the reference client's pendingOpens deque.
     */
    private data class PendingOpen(
        val accountId: String,
        val peerUri: String,
        val serviceId: String,
        val serviceName: String,
        val scheme: String,
    )

    private val pendingOpens = ArrayDeque<PendingOpen>()

    private val json = Json { ignoreUnknownKeys = true }

    private fun stateKey(accountId: String, peerUri: String) = "$accountId\u0000$peerUri"

    /** Tunnel state for one peer, loading the daemon's current tunnels on first access. */
    fun tunnelState(accountId: String, peerUri: String): PeerTunnelState {
        val key = stateKey(accountId, peerUri)
        _tunnelStates.value[key]?.let { return it }
        val loaded = loadTunnelState(accountId, peerUri)
        _tunnelStates.value = _tunnelStates.value + (key to loaded)
        return loaded
    }

    /** All tunnel states, keyed internally; observe this to react to tunnels opening/closing. */
    val tunnelStates: StateFlow<Map<String, PeerTunnelState>> = _tunnelStates.asStateFlow()

    private fun loadTunnelState(accountId: String, peerUri: String): PeerTunnelState {
        val active = daemonBridge.getActiveTunnels(accountId)
            .filter { it["peerUri"] == peerUri }
            .mapNotNull { map -> map.toTunnelInfo(accountId)?.let { it.serviceId to it } }
            .toMap()
        active.values.forEach { activeTunnelIds.add(it.tunnelId) }
        if (activeTunnelIds.isNotEmpty()) _anyActiveTunnel.value = true
        return PeerTunnelState(activeTunnels = active)
    }

    private fun Map<String, String>.toTunnelInfo(accountId: String): TunnelInfo? {
        val tunnelId = this["id"]?.takeIf { it.isNotEmpty() } ?: return null
        val serviceId = this["serviceId"]?.takeIf { it.isNotEmpty() } ?: return null
        return TunnelInfo(
            tunnelId = tunnelId,
            accountId = accountId,
            peerUri = this["peerUri"] ?: "",
            serviceId = serviceId,
            serviceName = this["serviceName"] ?: "",
            scheme = this["scheme"] ?: "",
            localPort = this["localPort"]?.toIntOrNull() ?: 0,
        )
    }

    // ==================== Exposed services (this device's own) ====================

    /** Announce a service this device offers; returns the daemon-assigned id, or null. */
    fun addExposedService(accountId: String, details: Map<String, String>): String? =
        daemonBridge.addExposedService(accountId, details).takeIf { it.isNotEmpty() }

    fun updateExposedService(accountId: String, details: Map<String, String>): Boolean =
        daemonBridge.updateExposedService(accountId, details)

    fun removeExposedService(accountId: String, serviceId: String): Boolean =
        daemonBridge.removeExposedService(accountId, serviceId)

    fun getExposedServices(accountId: String): List<Map<String, String>> =
        daemonBridge.getExposedServices(accountId)

    // ==================== Peer queries ====================

    /**
     * Ask a peer which services it offers and suspend until that peer answers.
     *
     * The daemon correlates the answer by request id, so a query for one peer is never satisfied
     * by another peer's reply.
     */
    suspend fun queryPeerServices(accountId: String, peerUri: String): PeerServicesResult {
        val requestId = daemonBridge.queryPeerServices(accountId, peerUri)
        return peerServicesReceived.first {
            it.requestId == requestId && it.accountId == accountId && it.peerId == peerUri
        }
    }

    // ==================== Tunnels ====================

    /**
     * Open a tunnel to one of a peer's services. The tunnel's local port arrives with
     * [tunnelOpened] / in [tunnelStates]; a failure clears the pending marker again.
     */
    fun openTunnel(accountId: String, peerUri: String, service: PeerServiceInfo) {
        val key = stateKey(accountId, peerUri)
        val current = tunnelState(accountId, peerUri)
        pendingOpens.addLast(
            PendingOpen(accountId, peerUri, service.id, service.name, service.scheme)
        )
        _tunnelStates.value = _tunnelStates.value +
            (key to current.copy(pendingServices = current.pendingServices + service.id))

        val tunnelId = daemonBridge.openServiceTunnel(
            accountId, peerUri, service.device, service.id, service.name, 0
        )
        if (tunnelId.isEmpty()) {
            Log.w(TAG, "openTunnel: daemon refused a tunnel for ${service.id}")
            pendingOpens.removeLastOrNull()
            val state = _tunnelStates.value[key] ?: return
            _tunnelStates.value = _tunnelStates.value +
                (key to state.copy(pendingServices = state.pendingServices - service.id))
        }
    }

    fun closeTunnel(accountId: String, peerUri: String, serviceId: String) {
        val tunnelId = _tunnelStates.value[stateKey(accountId, peerUri)]
            ?.activeTunnels?.get(serviceId)?.tunnelId ?: return
        daemonBridge.closeServiceTunnel(accountId, tunnelId)
    }

    /** Every tunnel the daemon currently holds for the given accounts. */
    fun getAllActiveTunnels(accountIds: List<String>): List<TunnelInfo> =
        accountIds.filter { it.isNotEmpty() }.flatMap { accountId ->
            runCatching {
                daemonBridge.getActiveTunnels(accountId).mapNotNull { it.toTunnelInfo(accountId) }
            }.onFailure { Log.e(TAG, "getAllActiveTunnels failed for $accountId: ${it.message}") }
                .getOrDefault(emptyList())
        }

    /** Close every tunnel on the given accounts, e.g. when leaving a conversation or signing out. */
    fun closeAllTunnels(accountIds: List<String>) {
        for (accountId in accountIds.filter { it.isNotEmpty() }) {
            runCatching {
                daemonBridge.getActiveTunnels(accountId).forEach { tunnel ->
                    tunnel["id"]?.takeIf { it.isNotEmpty() }
                        ?.let { daemonBridge.closeServiceTunnel(accountId, it) }
                }
            }.onFailure { Log.e(TAG, "closeAllTunnels failed for $accountId: ${it.message}") }
        }
    }

    // ==================== Daemon callbacks ====================

    internal fun onPeerServicesReceived(
        requestId: Long,
        accountId: String,
        peerId: String,
        status: Int,
        servicesJson: String,
    ) {
        val result = PeerServicesResult(
            requestId = requestId,
            accountId = accountId,
            peerId = peerId,
            status = PeerServicesStatus.fromCode(status),
            services = parseServices(servicesJson),
        )
        Log.d(TAG, "peerServicesReceived: $peerId status=${result.status} services=${result.services.size}")
        _peerServicesReceived.tryEmit(result)
    }

    internal fun onTunnelOpened(accountId: String, tunnelId: String, localPort: Int) {
        activeTunnelIds.add(tunnelId)
        _anyActiveTunnel.value = true
        val pending = pendingOpens.removeFirstOrNull() ?: return
        val key = stateKey(pending.accountId, pending.peerUri)
        val current = _tunnelStates.value[key] ?: return
        val info = TunnelInfo(
            tunnelId = tunnelId,
            accountId = pending.accountId,
            peerUri = pending.peerUri,
            serviceId = pending.serviceId,
            serviceName = pending.serviceName,
            scheme = pending.scheme,
            localPort = localPort,
        )
        _tunnelStates.value = _tunnelStates.value + (key to current.copy(
            activeTunnels = current.activeTunnels + (pending.serviceId to info),
            pendingServices = current.pendingServices - pending.serviceId,
        ))
        _tunnelOpened.tryEmit(info)
    }

    internal fun onTunnelClosed(accountId: String, tunnelId: String, reason: String) {
        Log.d(TAG, "tunnelClosed: $tunnelId ${reason.ifEmpty { "(no reason given)" }}")
        activeTunnelIds.remove(tunnelId)
        _anyActiveTunnel.value = activeTunnelIds.isNotEmpty()
        val entry = _tunnelStates.value.entries.firstOrNull { (_, state) ->
            state.activeTunnels.values.any { it.tunnelId == tunnelId }
        } ?: return
        val serviceId = entry.value.activeTunnels.entries
            .first { it.value.tunnelId == tunnelId }.key
        _tunnelStates.value = _tunnelStates.value + (entry.key to
            entry.value.copy(activeTunnels = entry.value.activeTunnels - serviceId))
    }

    /**
     * The daemon sends the service list as a JSON array of objects. A malformed payload yields an
     * empty list rather than throwing on the callback thread.
     */
    private fun parseServices(servicesJson: String): List<PeerServiceInfo> {
        if (servicesJson.isBlank()) return emptyList()
        return try {
            json.parseToJsonElement(servicesJson).jsonArray.map { element ->
                val obj = element.jsonObject
                fun field(name: String) = obj[name]?.jsonPrimitive?.content ?: ""
                PeerServiceInfo(
                    id = field("id"),
                    name = field("name"),
                    description = field("description"),
                    scheme = field("scheme"),
                    device = field("device"),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "peerServicesReceived: malformed service list: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "PeerServicesService"
    }
}
