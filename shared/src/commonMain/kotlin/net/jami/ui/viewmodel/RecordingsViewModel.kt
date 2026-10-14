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
import kotlinx.coroutines.launch
import net.jami.model.CallRecording
import net.jami.services.CallRecordingsService

data class RecordingsState(
    val recordings: List<CallRecording> = emptyList(),
    val isLoading: Boolean = false,
    /** The recording currently expanded into the player, by path. */
    val playingPath: String? = null,
    /** The directory the daemon records into, shown so the user knows where the files live. */
    val directory: String = "",
)

/**
 * Lists the call recordings on disk and lets the user play or delete them.
 *
 * Reading the directory is a blocking filesystem call, so it runs off the main thread; the list
 * is re-read after a delete rather than patched, since the daemon may also have written a new
 * recording in the meantime.
 */
class RecordingsViewModel(
    private val recordingsService: CallRecordingsService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {
    private val scope = scope

    private val _state = MutableStateFlow(RecordingsState())
    val state: StateFlow<RecordingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val recordings = runCatching { recordingsService.listRecordings() }.getOrDefault(emptyList())
            val directory = runCatching { recordingsService.recordingsDirectory() }.getOrDefault("")
            _state.value = _state.value.copy(
                recordings = recordings,
                directory = directory,
                isLoading = false,
                // A recording that disappeared from disk must not stay expanded in the player.
                playingPath = _state.value.playingPath?.takeIf { path ->
                    recordings.any { it.path == path }
                },
            )
        }
    }

    /** Expand a recording into the player, or collapse it if it is already playing. */
    fun togglePlayback(path: String) {
        _state.value = _state.value.copy(
            playingPath = if (_state.value.playingPath == path) null else path
        )
    }

    fun deleteRecording(path: String) {
        scope.launch {
            recordingsService.deleteRecording(path)
            refresh()
        }
    }

    public override fun onCleared() {
        scope.cancel()
    }
}
