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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Checkbox
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
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.components.content.JamiToggle
import net.jami.ui.platform.FilePickerEffect
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AccountSubSettingsViewModel
import net.jami.ui.viewmodel.CodecItem
import org.jetbrains.compose.resources.stringResource

/**
 * Account media settings screen.
 *
 * Sections:
 * - Video: enable/disable video calls + video codec list
 * - Ringtone: picker row opening an audio file picker
 * - Audio: audio codec list
 *
 * @param onBack Called when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMediaSettingsScreen(
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<AccountSubSettingsViewModel>()
    val state by viewModel.state.collectAsState()
    var showRingtonePicker by remember { mutableStateOf(false) }

    FilePickerEffect(show = showRingtonePicker, mimeTypes = listOf("audio/*")) { path ->
        showRingtonePicker = false
        if (path != null) viewModel.setRingtonePath(path)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.account_preferences_media_tab),
                        color = JamiTheme.colors.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.export_side_step2_cancel),
                            tint = JamiTheme.colors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                ),
            )
        },
        containerColor = JamiTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── Ringtone ─────────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_ringtone_label))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRingtonePicker = true }
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = JamiTheme.colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 0.dp),
                )
                Spacer(Modifier.size(JamiTheme.spacing.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.account_ringtone_title),
                        style = JamiTheme.typography.bodyLarge,
                        color = JamiTheme.colors.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.account_ringtone_summary),
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── Audio Codecs ─────────────────────────────────────────────────
            if (state.audioCodecs.isNotEmpty()) {
                JamiSectionTitle(title = stringResource(Res.string.account_audio_label))

                state.audioCodecs.forEachIndexed { index, codec ->
                    if (index > 0) HorizontalDivider(color = JamiTheme.colors.outline)
                    CodecRow(codec = codec, onToggle = { viewModel.setCodecEnabled(codec.id, it) })
                }

                Spacer(Modifier.height(JamiTheme.spacing.m))
            }

            // ── Video ────────────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_video_label))

            JamiToggle(
                label = stringResource(Res.string.account_video_enable),
                checked = state.videoEnabled,
                onCheckedChange = { viewModel.setVideoEnabled(it) },
            )

            if (state.videoCodecs.isNotEmpty()) {
                state.videoCodecs.forEachIndexed { index, codec ->
                    if (index > 0) HorizontalDivider(color = JamiTheme.colors.outline)
                    CodecRow(codec = codec, onToggle = { viewModel.setCodecEnabled(codec.id, it) })
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}

@Composable
private fun CodecRow(
    codec: CodecItem,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = codec.name,
            style = JamiTheme.typography.bodyLarge,
            color = JamiTheme.colors.onSurface,
        )
        if (codec.sampleRate.isNotEmpty()) {
            Spacer(Modifier.size(JamiTheme.spacing.m))
            Text(
                text = codec.sampleRate,
                style = JamiTheme.typography.bodyMedium,
                color = JamiTheme.colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Checkbox(
            checked = codec.isEnabled,
            onCheckedChange = onToggle,
        )
    }
}
