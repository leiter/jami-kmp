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
package net.jami.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.di.getViewModel
import net.jami.model.CallRecording
import net.jami.ui.components.notification.JamiAlertDialog
import net.jami.ui.components.video.VideoPlayerView
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.RecordingsViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Browser for the call recordings the daemon has written.
 *
 * Tapping a row expands the platform player beneath it — recordings are audio or audio+video
 * depending on the call, and the media3/AVPlayer-backed [VideoPlayerView] handles both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<RecordingsViewModel>()
    val state by viewModel.state.collectAsState()
    var pendingDelete by remember { mutableStateOf<CallRecording?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_recordings),
                        style = JamiTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_desc_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.action_refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                    titleContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.recordings.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.recordings.isEmpty() -> EmptyRecordings(
                    directory = state.directory,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(JamiTheme.spacing.l),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.recordings, key = { it.path }) { recording ->
                        RecordingRow(
                            recording = recording,
                            isPlaying = state.playingPath == recording.path,
                            onToggle = { viewModel.togglePlayback(recording.path) },
                            onDelete = { pendingDelete = recording },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // Deleting a recording cannot be undone, so it is confirmed first.
    pendingDelete?.let { recording ->
        JamiAlertDialog(
            title = stringResource(Res.string.recordings_delete_title),
            body = stringResource(Res.string.recordings_delete_message, recording.name),
            confirmText = stringResource(Res.string.recordings_delete_confirm),
            dismissText = stringResource(Res.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteRecording(recording.path)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun RecordingRow(
    recording: CallRecording,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = JamiTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.size(JamiTheme.spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.name,
                    style = JamiTheme.typography.bodyLarge,
                    color = JamiTheme.colors.onSurface,
                )
                Text(
                    text = formatRecordingSize(recording.sizeBytes),
                    style = JamiTheme.typography.labelSmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.recordings_delete_confirm),
                    tint = JamiTheme.colors.onSurfaceVariant,
                )
            }
        }

        if (isPlaying) {
            VideoPlayerView(
                filePath = recording.path,
                fileName = recording.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = JamiTheme.spacing.l),
            )
            Spacer(Modifier.height(JamiTheme.spacing.m))
        }
    }
}

@Composable
private fun EmptyRecordings(directory: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.recordings_empty),
            style = JamiTheme.typography.bodyMedium,
            color = JamiTheme.colors.onSurfaceVariant,
        )
        if (directory.isNotEmpty()) {
            Spacer(Modifier.height(JamiTheme.spacing.s))
            Text(
                text = directory,
                style = JamiTheme.typography.labelSmall,
                color = JamiTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** Human-readable size without String.format, which is not available in commonMain. */
private fun formatRecordingSize(bytes: Long): String = when {
    bytes >= 1_000_000L -> "${bytes / 1_000_000L}.${(bytes % 1_000_000L) / 100_000L} MB"
    bytes >= 1_000L -> "${bytes / 1_000L} KB"
    else -> "$bytes B"
}
