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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.di.getViewModel
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.components.content.JamiToggle
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AccountSubSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Account messages settings screen.
 *
 * Sections:
 * - Chat settings: read receipts
 * - Call settings: allow unknown calls, auto-answer, rendezvous mode
 * - Conversation settings: max file size for auto-download
 *
 * @param onBack Called when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMessagesSettingsScreen(
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<AccountSubSettingsViewModel>()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.pref_category_messages),
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

            // ── Chat settings ─────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_chat_settings_title))

            JamiToggle(
                label = stringResource(Res.string.account_read_receipts_label),
                description = stringResource(Res.string.account_read_receipts_summary),
                checked = state.readReceiptsEnabled,
                onCheckedChange = { viewModel.setReadReceipts(it) },
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── Call settings ─────────────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_call_settings_title))

            JamiToggle(
                label = stringResource(Res.string.account_dht_public_in_calls_label),
                checked = state.dhtPublicInCalls,
                onCheckedChange = { viewModel.setDhtPublicInCalls(it) },
            )

            HorizontalDivider(color = JamiTheme.colors.outline)

            JamiToggle(
                label = stringResource(Res.string.account_autoanswer_label),
                checked = state.autoAnswer,
                onCheckedChange = { viewModel.setAutoAnswer(it) },
            )

            HorizontalDivider(color = JamiTheme.colors.outline)

            JamiToggle(
                label = stringResource(Res.string.account_rendezvous_label),
                description = stringResource(Res.string.account_rendezvous_summary),
                checked = state.isRendezvous,
                onCheckedChange = { viewModel.setRendezvous(it) },
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // ── Conversation settings ─────────────────────────────────────────
            JamiSectionTitle(title = stringResource(Res.string.account_conversation_settings_title))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
            ) {
                Text(
                    text = stringResource(Res.string.pref_max_file_size),
                    style = JamiTheme.typography.bodyLarge,
                    color = JamiTheme.colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.pref_max_file_size_value, state.maxAutoAcceptMb),
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
                Slider(
                    value = state.maxAutoAcceptMb.toFloat(),
                    onValueChange = { viewModel.setMaxAutoAcceptMb(it.roundToInt()) },
                    valueRange = 1f..256f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}
