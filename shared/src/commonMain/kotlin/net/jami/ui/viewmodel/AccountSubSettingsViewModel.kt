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
import net.jami.model.ConfigKey
import net.jami.repository.SettingsRepository
import net.jami.services.AccountService

/**
 * A codec item as seen by the UI.
 *
 * @param sampleRate Non-empty when the codec has a meaningful sample rate (e.g. "32000" for Speex).
 */
data class CodecItem(
    val id: Long,
    val name: String,
    val type: String,
    val sampleRate: String,
    val isEnabled: Boolean,
)

/**
 * State for account sub-settings screens (Media, Messages, Advanced).
 */
data class AccountSubSettingsState(
    // ── Media ────────────────────────────────────────────────────────────────
    val videoEnabled: Boolean = false,
    val ringtonePath: String = "",
    val audioCodecs: List<CodecItem> = emptyList(),
    val videoCodecs: List<CodecItem> = emptyList(),

    // ── Messages ─────────────────────────────────────────────────────────────
    val readReceiptsEnabled: Boolean = false,
    // Call settings
    val dhtPublicInCalls: Boolean = false,
    val autoAnswer: Boolean = false,
    val isRendezvous: Boolean = false,
    // Conversation settings
    val maxAutoAcceptMb: Int = 32,

    // ── Advanced ─────────────────────────────────────────────────────────────
    // Name server
    val nameServer: String = "",
    // OpenDHT
    val bootstrapNode: String = "",
    val proxyEnabled: Boolean = false,
    val proxyServer: String = "",
    val proxyListEnabled: Boolean = false,
    val proxyListUrl: String = "",
    val peerDiscoveryEnabled: Boolean = false,
    // P2P
    val upnpEnabled: Boolean = false,
    val turnEnabled: Boolean = false,
    val turnServer: String = "",
    val turnUsername: String = "",
    val turnPassword: String = "",
    // STUN
    val stunEnabled: Boolean = false,
    val stunServer: String = "",
    // Audio-RTP port range
    val audioPortMin: String = "",
    val audioPortMax: String = "",
    // Video-RTP port range
    val videoPortMin: String = "",
    val videoPortMax: String = "",

    // ── Security ─────────────────────────────────────────────────────────────
    // SRTP
    val srtpKeyExchange: Boolean = false,    // true = "sdes", false = ""
    // TLS
    val tlsEnabled: Boolean = false,
    val tlsPort: String = "",
    val tlsCaListFile: String = "",
    val tlsCertFile: String = "",
    val tlsPrivateKeyFile: String = "",
    val tlsPassword: String = "",
    val tlsMethod: String = "",
    val tlsCiphers: String = "",
    val tlsServerName: String = "",
    val tlsVerifyServer: Boolean = false,
    val tlsVerifyClient: Boolean = false,
    val tlsRequireClientCert: Boolean = false,
    val tlsNegotiationTimeout: String = "",

    val isLoading: Boolean = false,
)

/**
 * ViewModel shared by AccountMediaSettingsScreen, AccountMessagesSettingsScreen,
 * and AccountAdvancedSettingsScreen.
 *
 * Reads account details and codec lists from [AccountService] on init, and
 * persists each change immediately via [AccountService.setAccountDetails] /
 * [AccountService.setActiveCodecList]. Max file size is persisted via [SettingsRepository].
 */
class AccountSubSettingsViewModel(
    private val accountService: AccountService,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {
    private val scope = scope

    private val _state = MutableStateFlow(AccountSubSettingsState())
    val state: StateFlow<AccountSubSettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            val account = accountService.currentAccount.value ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val accountId = account.accountId
            val details = account.details

            fun bool(key: ConfigKey) = details[key.key]?.equals("true", ignoreCase = true) == true
            fun str(key: ConfigKey) = details[key.key] ?: ""

            // Codec lists
            val allCodecIds = accountService.getCodecList()
            val activeCodecIds = accountService.getActiveCodecList(accountId).toSet()
            val audioCodecs = mutableListOf<CodecItem>()
            val videoCodecs = mutableListOf<CodecItem>()
            for (id in allCodecIds) {
                val d = accountService.getCodecDetails(accountId, id)
                val name = d["CodecInfo.name"] ?: d["name"] ?: id.toString()
                val type = d["CodecInfo.type"] ?: d["type"] ?: ""
                val sampleRate = d["CodecInfo.sampleRate"] ?: d["sampleRate"] ?: ""
                val item = CodecItem(id = id, name = name, type = type, sampleRate = sampleRate, isEnabled = id in activeCodecIds)
                if (type.equals("AUDIO", ignoreCase = true)) audioCodecs.add(item)
                else videoCodecs.add(item)
            }

            val maxBytes = settingsRepository.fileTransferSettings.value.maxAutoAcceptSize
            val maxMb = (maxBytes / (1024L * 1024L)).toInt().coerceAtLeast(1)

            _state.value = AccountSubSettingsState(
                videoEnabled = bool(ConfigKey.VIDEO_ENABLED),
                ringtonePath = str(ConfigKey.RINGTONE_PATH),
                audioCodecs = audioCodecs,
                videoCodecs = videoCodecs,
                readReceiptsEnabled = bool(ConfigKey.SEND_READ_RECEIPT),
                dhtPublicInCalls = bool(ConfigKey.DHT_PUBLIC_IN),
                autoAnswer = bool(ConfigKey.ACCOUNT_AUTOANSWER),
                isRendezvous = bool(ConfigKey.ACCOUNT_ISRENDEZVOUS),
                maxAutoAcceptMb = maxMb,
                nameServer = str(ConfigKey.RINGNS_HOST),
                bootstrapNode = str(ConfigKey.ACCOUNT_HOSTNAME),
                proxyEnabled = bool(ConfigKey.ACCOUNT_PROXY_ENABLED),
                proxyServer = str(ConfigKey.ACCOUNT_PROXY_SERVER),
                proxyListEnabled = bool(ConfigKey.PROXY_LIST_ENABLED),
                proxyListUrl = str(ConfigKey.PROXY_SERVER_LIST),
                peerDiscoveryEnabled = bool(ConfigKey.ACCOUNT_CONVERSATION_ENABLED),
                upnpEnabled = bool(ConfigKey.ACCOUNT_UPNP_ENABLE),
                turnEnabled = bool(ConfigKey.TURN_ENABLE),
                turnServer = str(ConfigKey.TURN_SERVER),
                turnUsername = str(ConfigKey.TURN_USERNAME),
                turnPassword = str(ConfigKey.TURN_PASSWORD),
                stunEnabled = bool(ConfigKey.STUN_ENABLE),
                stunServer = str(ConfigKey.STUN_SERVER),
                audioPortMin = str(ConfigKey.AUDIO_PORT_MIN),
                audioPortMax = str(ConfigKey.AUDIO_PORT_MAX),
                videoPortMin = str(ConfigKey.VIDEO_PORT_MIN),
                videoPortMax = str(ConfigKey.VIDEO_PORT_MAX),
                srtpKeyExchange = str(ConfigKey.SRTP_KEY_EXCHANGE) == "sdes",
                tlsEnabled = bool(ConfigKey.TLS_ENABLE),
                tlsPort = str(ConfigKey.TLS_LISTENER_PORT),
                tlsCaListFile = str(ConfigKey.TLS_CA_LIST_FILE),
                tlsCertFile = str(ConfigKey.TLS_CERTIFICATE_FILE),
                tlsPrivateKeyFile = str(ConfigKey.TLS_PRIVATE_KEY_FILE),
                tlsPassword = str(ConfigKey.TLS_PASSWORD),
                tlsMethod = str(ConfigKey.TLS_METHOD),
                tlsCiphers = str(ConfigKey.TLS_CIPHERS),
                tlsServerName = str(ConfigKey.TLS_SERVER_NAME),
                tlsVerifyServer = bool(ConfigKey.TLS_VERIFY_SERVER),
                tlsVerifyClient = bool(ConfigKey.TLS_VERIFY_CLIENT),
                tlsRequireClientCert = bool(ConfigKey.TLS_REQUIRE_CLIENT_CERTIFICATE),
                tlsNegotiationTimeout = str(ConfigKey.TLS_NEGOTIATION_TIMEOUT_SEC),
                isLoading = false,
            )
        }
    }

    // ── Media ─────────────────────────────────────────────────────────────────

    fun setVideoEnabled(enabled: Boolean) {
        _state.update { it.copy(videoEnabled = enabled) }
        updateDetail(ConfigKey.VIDEO_ENABLED, enabled.toString())
    }

    fun setRingtonePath(path: String) {
        _state.update { it.copy(ringtonePath = path) }
        updateDetail(ConfigKey.RINGTONE_PATH, path)
        if (path.isNotEmpty()) updateDetail(ConfigKey.RINGTONE_ENABLED, "true")
    }

    fun setCodecEnabled(id: Long, enabled: Boolean) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            val accountId = account.accountId

            _state.update { s ->
                val updatedAudio = s.audioCodecs.map { if (it.id == id) it.copy(isEnabled = enabled) else it }
                val updatedVideo = s.videoCodecs.map { if (it.id == id) it.copy(isEnabled = enabled) else it }
                s.copy(audioCodecs = updatedAudio, videoCodecs = updatedVideo)
            }

            pushActiveCodecList(accountId)
        }
    }

    /**
     * Move a codec up or down within its group (audio or video), changing its priority.
     * Visual order = daemon priority order.
     */
    fun moveCodec(id: Long, isAudio: Boolean, up: Boolean) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            val accountId = account.accountId

            _state.update { s ->
                val list = if (isAudio) s.audioCodecs.toMutableList() else s.videoCodecs.toMutableList()
                val idx = list.indexOfFirst { it.id == id }
                val targetIdx = if (up) idx - 1 else idx + 1
                if (idx < 0 || targetIdx < 0 || targetIdx >= list.size) return@update s
                val tmp = list[idx]; list[idx] = list[targetIdx]; list[targetIdx] = tmp
                if (isAudio) s.copy(audioCodecs = list) else s.copy(videoCodecs = list)
            }

            pushActiveCodecList(accountId)
        }
    }

    /** Send the current enabled codec ids in display order to the daemon. */
    private suspend fun pushActiveCodecList(accountId: String) {
        val enabledIds = (_state.value.audioCodecs + _state.value.videoCodecs)
            .filter { it.isEnabled }
            .map { it.id }
        accountService.setActiveCodecList(accountId, enabledIds)
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun setReadReceipts(enabled: Boolean) {
        _state.update { it.copy(readReceiptsEnabled = enabled) }
        updateDetail(ConfigKey.SEND_READ_RECEIPT, enabled.toString())
    }

    fun setDhtPublicInCalls(enabled: Boolean) {
        _state.update { it.copy(dhtPublicInCalls = enabled) }
        updateDetail(ConfigKey.DHT_PUBLIC_IN, enabled.toString())
    }

    fun setAutoAnswer(enabled: Boolean) {
        _state.update { it.copy(autoAnswer = enabled) }
        updateDetail(ConfigKey.ACCOUNT_AUTOANSWER, enabled.toString())
    }

    fun setRendezvous(enabled: Boolean) {
        _state.update { it.copy(isRendezvous = enabled) }
        updateDetail(ConfigKey.ACCOUNT_ISRENDEZVOUS, enabled.toString())
    }

    fun setMaxAutoAcceptMb(mb: Int) {
        _state.update { it.copy(maxAutoAcceptMb = mb) }
        settingsRepository.updateMaxAutoAcceptSize(mb.toLong() * 1024L * 1024L)
    }

    // ── Advanced ──────────────────────────────────────────────────────────────

    fun setNameServer(server: String) {
        _state.update { it.copy(nameServer = server) }
        updateDetail(ConfigKey.RINGNS_HOST, server)
    }

    fun setBootstrapNode(node: String) {
        _state.update { it.copy(bootstrapNode = node) }
        updateDetail(ConfigKey.ACCOUNT_HOSTNAME, node)
    }

    fun setProxyEnabled(enabled: Boolean) {
        _state.update { it.copy(proxyEnabled = enabled) }
        updateDetail(ConfigKey.ACCOUNT_PROXY_ENABLED, enabled.toString())
    }

    fun setProxyServer(server: String) {
        _state.update { it.copy(proxyServer = server) }
        updateDetail(ConfigKey.ACCOUNT_PROXY_SERVER, server)
    }

    fun setProxyListEnabled(enabled: Boolean) {
        _state.update { it.copy(proxyListEnabled = enabled) }
        updateDetail(ConfigKey.PROXY_LIST_ENABLED, enabled.toString())
    }

    fun setProxyListUrl(url: String) {
        _state.update { it.copy(proxyListUrl = url) }
        updateDetail(ConfigKey.PROXY_SERVER_LIST, url)
    }

    fun setPeerDiscovery(enabled: Boolean) {
        _state.update { it.copy(peerDiscoveryEnabled = enabled) }
        updateDetail(ConfigKey.ACCOUNT_CONVERSATION_ENABLED, enabled.toString())
    }

    fun setUpnpEnabled(enabled: Boolean) {
        _state.update { it.copy(upnpEnabled = enabled) }
        updateDetail(ConfigKey.ACCOUNT_UPNP_ENABLE, enabled.toString())
    }

    fun setTurnEnabled(enabled: Boolean) {
        _state.update { it.copy(turnEnabled = enabled) }
        updateDetail(ConfigKey.TURN_ENABLE, enabled.toString())
    }

    fun setTurnServer(server: String) {
        _state.update { it.copy(turnServer = server) }
        updateDetail(ConfigKey.TURN_SERVER, server)
    }

    fun setTurnUsername(username: String) {
        _state.update { it.copy(turnUsername = username) }
        updateDetail(ConfigKey.TURN_USERNAME, username)
    }

    fun setTurnPassword(password: String) {
        _state.update { it.copy(turnPassword = password) }
        updateDetail(ConfigKey.TURN_PASSWORD, password)
    }

    fun setAudioPortMin(port: String) {
        _state.update { it.copy(audioPortMin = port) }
        updateDetail(ConfigKey.AUDIO_PORT_MIN, port)
    }

    fun setAudioPortMax(port: String) {
        _state.update { it.copy(audioPortMax = port) }
        updateDetail(ConfigKey.AUDIO_PORT_MAX, port)
    }

    fun setStunEnabled(enabled: Boolean) {
        _state.update { it.copy(stunEnabled = enabled) }
        updateDetail(ConfigKey.STUN_ENABLE, enabled.toString())
    }

    fun setStunServer(server: String) {
        _state.update { it.copy(stunServer = server) }
        updateDetail(ConfigKey.STUN_SERVER, server)
    }

    fun setVideoPortMin(port: String) {
        _state.update { it.copy(videoPortMin = port) }
        updateDetail(ConfigKey.VIDEO_PORT_MIN, port)
    }

    fun setVideoPortMax(port: String) {
        _state.update { it.copy(videoPortMax = port) }
        updateDetail(ConfigKey.VIDEO_PORT_MAX, port)
    }

    // ── Security ──────────────────────────────────────────────────────────────

    fun setSrtpKeyExchange(enabled: Boolean) {
        _state.update { it.copy(srtpKeyExchange = enabled) }
        updateDetail(ConfigKey.SRTP_KEY_EXCHANGE, if (enabled) "sdes" else "")
    }

    fun setTlsEnabled(enabled: Boolean) {
        _state.update { it.copy(tlsEnabled = enabled) }
        updateDetail(ConfigKey.TLS_ENABLE, enabled.toString())
        // TLS and STUN are mutually exclusive in the daemon
        if (enabled && _state.value.stunEnabled) {
            _state.update { it.copy(stunEnabled = false) }
            updateDetail(ConfigKey.STUN_ENABLE, "false")
        }
    }

    fun setTlsPort(port: String) {
        _state.update { it.copy(tlsPort = port) }
        updateDetail(ConfigKey.TLS_LISTENER_PORT, port)
    }

    fun setTlsCaListFile(path: String) {
        _state.update { it.copy(tlsCaListFile = path) }
        updateDetail(ConfigKey.TLS_CA_LIST_FILE, path)
    }

    fun setTlsCertFile(path: String) {
        _state.update { it.copy(tlsCertFile = path) }
        updateDetail(ConfigKey.TLS_CERTIFICATE_FILE, path)
    }

    fun setTlsPrivateKeyFile(path: String) {
        _state.update { it.copy(tlsPrivateKeyFile = path) }
        updateDetail(ConfigKey.TLS_PRIVATE_KEY_FILE, path)
    }

    fun setTlsPassword(password: String) {
        _state.update { it.copy(tlsPassword = password) }
        updateDetail(ConfigKey.TLS_PASSWORD, password)
    }

    fun setTlsMethod(method: String) {
        _state.update { it.copy(tlsMethod = method) }
        updateDetail(ConfigKey.TLS_METHOD, method)
    }

    fun setTlsCiphers(ciphers: String) {
        _state.update { it.copy(tlsCiphers = ciphers) }
        updateDetail(ConfigKey.TLS_CIPHERS, ciphers)
    }

    fun setTlsServerName(name: String) {
        _state.update { it.copy(tlsServerName = name) }
        updateDetail(ConfigKey.TLS_SERVER_NAME, name)
    }

    fun setTlsVerifyServer(enabled: Boolean) {
        _state.update { it.copy(tlsVerifyServer = enabled) }
        updateDetail(ConfigKey.TLS_VERIFY_SERVER, enabled.toString())
    }

    fun setTlsVerifyClient(enabled: Boolean) {
        _state.update { it.copy(tlsVerifyClient = enabled) }
        updateDetail(ConfigKey.TLS_VERIFY_CLIENT, enabled.toString())
    }

    fun setTlsRequireClientCert(enabled: Boolean) {
        _state.update { it.copy(tlsRequireClientCert = enabled) }
        updateDetail(ConfigKey.TLS_REQUIRE_CLIENT_CERTIFICATE, enabled.toString())
    }

    fun setTlsNegotiationTimeout(secs: String) {
        _state.update { it.copy(tlsNegotiationTimeout = secs) }
        updateDetail(ConfigKey.TLS_NEGOTIATION_TIMEOUT_SEC, secs)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun updateDetail(key: ConfigKey, value: String) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            val updatedDetails = account.details.toMutableMap().apply { put(key.key, value) }
            accountService.setAccountDetails(account.accountId, updatedDetails)
        }
    }

    public override fun onCleared() {
        scope.cancel()
    }
}
