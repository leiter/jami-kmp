package net.jami.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.ui.platform.captureRecentLogs

data class DebugLogsState(
    val logs: String = "",
    val isLoading: Boolean = false,
)

class DebugLogsViewModel(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _state = MutableStateFlow(DebugLogsState())
    val state: StateFlow<DebugLogsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _state.value = DebugLogsState(isLoading = true)
            val logs = captureRecentLogs(maxLines = 500)
            _state.value = DebugLogsState(logs = logs, isLoading = false)
        }
    }

    fun onCleared() {
        scope.cancel()
    }
}
