package net.jami.ui.viewmodel

import androidx.lifecycle.ViewModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.services.AccountService
import net.jami.services.ConversationFacade
import net.jami.services.DaemonDiagnostics
import net.jami.services.PeerServicesService
import net.jami.services.TunnelInfo
import net.jami.services.ConversationSyncInfo
import net.jami.services.SyncState
import net.jami.ui.platform.captureRecentLogs

data class DebugLogsState(
    val logs: String = "",
    val isLoading: Boolean = false,
    /** Account-level sync state at the last refresh. */
    val syncState: SyncState = SyncState.Idle,
    /** Per-conversation swarm-sync health at the last refresh. */
    val conversations: List<ConversationSyncInfo> = emptyList(),
    /** Daemon-reported diagnostics (error alerts, STUN failures, codec state, daemon log). */
    val daemonDiagnostics: DaemonDiagnostics = DaemonDiagnostics(),
    /** Active peer connections for the current account at the last refresh. */
    val connections: List<Map<String, String>> = emptyList(),
    /** Call-history retention in days as the daemon reports it; 0 keeps everything. */
    val historyLimitDays: Int = 0,
    /** Outgoing-call ringing timeout in seconds. */
    val ringingTimeoutSeconds: Int = 0,
    /** Peer-service tunnels the daemon currently holds open. */
    val tunnels: List<TunnelInfo> = emptyList(),
)

class DebugLogsViewModel(
    private val conversationFacade: ConversationFacade,
    private val accountService: AccountService,
    private val peerServicesService: PeerServicesService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {
    private val scope = scope

    private val _state = MutableStateFlow(DebugLogsState())
    val state: StateFlow<DebugLogsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val logs = captureRecentLogs(maxLines = 500)
            val accountId = accountService.currentAccount.value?.accountId
            val conversations = if (accountId != null) {
                runCatching { conversationFacade.snapshotConversationSyncInfo(accountId) }
                    .getOrDefault(emptyList())
            } else emptyList()
            val connections = if (accountId != null) {
                runCatching { accountService.getConnectionList(accountId) }.getOrDefault(emptyList())
            } else emptyList()
            _state.value = DebugLogsState(
                logs = logs,
                isLoading = false,
                syncState = conversationFacade.syncState.value,
                conversations = conversations,
                daemonDiagnostics = accountService.daemonDiagnostics.value,
                connections = connections,
                historyLimitDays = runCatching { accountService.getHistoryLimit() }.getOrDefault(0),
                ringingTimeoutSeconds = runCatching { accountService.getRingingTimeout() }.getOrDefault(0),
                tunnels = if (accountId != null) {
                    runCatching { peerServicesService.getAllActiveTunnels(listOf(accountId)) }
                        .getOrDefault(emptyList())
                } else emptyList(),
            )
        }
    }

    /**
     * Ask the daemon to dump its internal connection state into its log, then re-read the log so
     * the dump shows up on screen.
     */
    fun dumpDaemonState() {
        scope.launch {
            accountService.monitorDaemon(continuous = false)
            refresh()
        }
    }

    public override fun onCleared() {
        scope.cancel()
    }
}
