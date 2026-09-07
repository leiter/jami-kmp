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
)

class DebugLogsViewModel(
    private val conversationFacade: ConversationFacade,
    private val accountService: AccountService,
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
            _state.value = DebugLogsState(
                logs = logs,
                isLoading = false,
                syncState = conversationFacade.syncState.value,
                conversations = conversations,
            )
        }
    }

    public override fun onCleared() {
        scope.cancel()
    }
}
