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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.di.getViewModel
import net.jami.services.ConversationSyncInfo
import net.jami.services.SyncState
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.DebugLogsViewModel
import net.jami.utils.shareText
import org.jetbrains.compose.resources.stringResource

/**
 * In-app debug log viewer.
 *
 * Captures recent logcat output (Android) or platform-equivalent logs and
 * displays them in a scrollable monospace view. The share button triggers the
 * native share sheet so logs can be sent to the development team.
 *
 * @param onBack Called when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsScreen(
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<DebugLogsViewModel>()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_debug_logs),
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
                    IconButton(
                        onClick = { shareText("Jami debug logs", state.logs) },
                        enabled = state.logs.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(Res.string.action_share),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                    titleContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.refresh() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(JamiTheme.spacing.m),
                ) {
                    // Per-conversation swarm-sync panel (plan phase 5): makes the
                    // "Bootstrap with 0 device(s)" / not-yet-live state visible in-app.
                    val mono = JamiTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    Text(
                        text = "sync: " + syncStateLabel(state.syncState),
                        style = mono,
                        color = JamiTheme.colors.onSurface,
                    )
                    if (state.conversations.isEmpty()) {
                        Text(
                            text = "  (no conversations)",
                            style = mono,
                            color = JamiTheme.colors.onSurfaceVariant,
                        )
                    } else {
                        state.conversations.forEach { info ->
                            Text(
                                text = conversationSyncLine(info),
                                style = mono,
                                color = if (info.bootstrapping) JamiTheme.colors.error
                                        else JamiTheme.colors.onSurface,
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = JamiTheme.spacing.s),
                    )
                    Text(
                        text = state.logs.ifEmpty { stringResource(Res.string.debug_logs_empty) },
                        style = mono,
                        color = JamiTheme.colors.onSurface,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

private fun syncStateLabel(s: SyncState): String = when (s) {
    is SyncState.Idle -> "idle"
    is SyncState.Syncing -> "syncing (loading…)"
    is SyncState.Bootstrapping ->
        "bootstrapping (${s.bootstrappingCount}/${s.conversationCount} not yet live)"
    is SyncState.Complete -> "complete (${s.conversationCount} conversations)"
    is SyncState.Error -> "error: ${s.error}"
}

private fun conversationSyncLine(i: ConversationSyncInfo): String = buildString {
    append(i.conversationId.take(12))
    append(" · ")
    append(i.mode.name)
    i.requestMode?.let { append(" (→${it.name})") }
    append(" · members ").append(i.memberCount)
    append(" · peers ").append(i.activePeerCount)
    append(" · msgs ").append(i.messageCount)
    i.lastCommitId?.let { append(" · last ").append(it.take(8)) }
    if (i.bootstrapping) append(" · BOOTSTRAPPING")
}
