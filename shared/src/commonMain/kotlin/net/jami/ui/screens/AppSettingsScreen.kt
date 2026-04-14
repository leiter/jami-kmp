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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.di.getViewModel
import net.jami.model.settings.ConversationSort
import net.jami.ui.components.actions.JamiFilterChip
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.components.content.JamiToggle
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AppSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Application settings screen.
 *
 * Sections:
 * - Appearance: Dark mode, Compact mode, Conversation sort
 * - Privacy: Read receipts, Typing indicators, Block unknown, Block screenshots, Link previews
 * - Notifications: Push, Call, Message, Sound, Vibration, Quiet hours
 * - Calls: Video, Hardware acceleration, Noise suppression, Echo cancellation, Auto-answer
 * - File Transfers: Max auto-accept size (slider), Auto-download WiFi/mobile
 * - System: Start on boot, Run in background
 *
 * @param onBack Called when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<AppSettingsViewModel>()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_app_settings),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                    titleContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ==================== Appearance ====================
            JamiSectionTitle(title = stringResource(Res.string.pref_category_appearance))

            JamiToggle(
                label = stringResource(Res.string.pref_dark_mode),
                description = stringResource(Res.string.pref_dark_mode_description),
                checked = state.isDarkTheme,
                onCheckedChange = { viewModel.toggleDarkTheme() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_compact_mode),
                description = stringResource(Res.string.pref_compact_mode_description),
                checked = state.isCompactMode,
                onCheckedChange = { viewModel.toggleCompactMode() },
            )

            SettingLabelRow(label = stringResource(Res.string.pref_conversation_sort))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.xs,
                    ),
                horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.s),
            ) {
                JamiFilterChip(
                    text = stringResource(Res.string.pref_sort_last_activity),
                    selected = state.conversationSort == ConversationSort.LAST_ACTIVITY,
                    onClick = { viewModel.setConversationSort(ConversationSort.LAST_ACTIVITY) },
                )
                JamiFilterChip(
                    text = stringResource(Res.string.pref_sort_alphabetical),
                    selected = state.conversationSort == ConversationSort.ALPHABETICAL,
                    onClick = { viewModel.setConversationSort(ConversationSort.ALPHABETICAL) },
                )
                JamiFilterChip(
                    text = stringResource(Res.string.pref_sort_unread_first),
                    selected = state.conversationSort == ConversationSort.UNREAD_FIRST,
                    onClick = { viewModel.setConversationSort(ConversationSort.UNREAD_FIRST) },
                )
            }

            HorizontalDivider()

            // ==================== Privacy ====================
            JamiSectionTitle(title = stringResource(Res.string.pref_category_privacy))

            JamiToggle(
                label = stringResource(Res.string.pref_read_receipts),
                description = stringResource(Res.string.pref_read_receipts_description),
                checked = state.isReadReceipts,
                onCheckedChange = { viewModel.toggleReadReceipts() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_typing_indicators),
                description = stringResource(Res.string.pref_typing_indicators_description),
                checked = state.isTypingIndicators,
                onCheckedChange = { viewModel.toggleTypingIndicators() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_block_unknown),
                description = stringResource(Res.string.pref_block_unknown_description),
                checked = state.isBlockUnknown,
                onCheckedChange = { viewModel.toggleBlockUnknown() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_screenshot_blocking),
                description = stringResource(Res.string.pref_screenshot_blocking_description),
                checked = state.isScreenshotBlocking,
                onCheckedChange = { viewModel.toggleScreenshotBlocking() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_link_preview),
                description = stringResource(Res.string.pref_link_preview_description),
                checked = state.isLinkPreview,
                onCheckedChange = { viewModel.toggleLinkPreview() },
            )

            HorizontalDivider()

            // ==================== Notifications ====================
            JamiSectionTitle(title = stringResource(Res.string.pref_category_notifications))

            JamiToggle(
                label = stringResource(Res.string.pref_push_notifications),
                description = stringResource(Res.string.pref_push_notifications_description),
                checked = state.isPushNotifications,
                onCheckedChange = { viewModel.togglePushNotifications() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_call_notifications),
                description = stringResource(Res.string.pref_call_notifications_description),
                checked = state.isCallNotifications,
                onCheckedChange = { viewModel.toggleCallNotifications() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_message_notifications),
                description = stringResource(Res.string.pref_message_notifications_description),
                checked = state.isMessageNotifications,
                onCheckedChange = { viewModel.toggleMessageNotifications() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_notification_sound),
                description = stringResource(Res.string.pref_notification_sound_description),
                checked = state.isNotificationSound,
                onCheckedChange = { viewModel.toggleNotificationSound() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_vibration),
                description = stringResource(Res.string.pref_vibration_description),
                checked = state.isVibration,
                onCheckedChange = { viewModel.toggleVibration() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_quiet_hours),
                description = stringResource(Res.string.pref_quiet_hours_description),
                checked = state.isQuietHours,
                onCheckedChange = { viewModel.toggleQuietHours() },
            )

            HorizontalDivider()

            // ==================== Calls ====================
            JamiSectionTitle(title = stringResource(Res.string.pref_category_calls))

            JamiToggle(
                label = stringResource(Res.string.pref_video_enabled),
                description = stringResource(Res.string.pref_video_enabled_description),
                checked = state.isVideoEnabled,
                onCheckedChange = { viewModel.toggleVideoEnabled() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_hardware_acceleration),
                description = stringResource(Res.string.pref_hardware_acceleration_description),
                checked = state.isHardwareAcceleration,
                onCheckedChange = { viewModel.toggleHardwareAcceleration() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_noise_suppression),
                description = stringResource(Res.string.pref_noise_suppression_description),
                checked = state.isNoiseSuppression,
                onCheckedChange = { viewModel.toggleNoiseSuppression() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_echo_cancellation),
                description = stringResource(Res.string.pref_echo_cancellation_description),
                checked = state.isEchoCancellation,
                onCheckedChange = { viewModel.toggleEchoCancellation() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_auto_answer),
                description = stringResource(Res.string.pref_auto_answer_description),
                checked = state.isAutoAnswer,
                onCheckedChange = { viewModel.toggleAutoAnswer() },
            )

            HorizontalDivider()

            // ==================== File Transfers ====================
            JamiSectionTitle(title = stringResource(Res.string.pref_category_file_transfers))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.pref_max_file_size),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.pref_max_file_size_value, state.maxAutoAcceptMb),
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
                Slider(
                    value = state.maxAutoAcceptMb.toFloat(),
                    onValueChange = { viewModel.setMaxAutoAcceptMb(it.roundToInt()) },
                    valueRange = 0f..100f,
                    steps = 19, // 5 MB increments: 0, 5, 10, … 100
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            JamiToggle(
                label = stringResource(Res.string.pref_auto_download_wifi),
                description = stringResource(Res.string.pref_auto_download_wifi_description),
                checked = state.isAutoDownloadWifi,
                onCheckedChange = { viewModel.toggleAutoDownloadWifi() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_auto_download_mobile),
                description = stringResource(Res.string.pref_auto_download_mobile_description),
                checked = state.isAutoDownloadMobile,
                onCheckedChange = { viewModel.toggleAutoDownloadMobile() },
            )

            HorizontalDivider()

            // ==================== System ====================
            JamiSectionTitle(title = stringResource(Res.string.pref_category_system))

            JamiToggle(
                label = stringResource(Res.string.pref_start_on_boot),
                description = stringResource(Res.string.pref_start_on_boot_description),
                checked = state.isStartOnBoot,
                onCheckedChange = { viewModel.toggleStartOnBoot() },
            )
            JamiToggle(
                label = stringResource(Res.string.pref_run_in_background),
                description = stringResource(Res.string.pref_run_in_background_description),
                checked = state.isRunInBackground,
                onCheckedChange = { viewModel.toggleRunInBackground() },
            )

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}

/**
 * A simple label row used above chip groups or sliders that don't fit into [JamiToggle].
 */
@Composable
private fun SettingLabelRow(label: String) {
    Text(
        text = label,
        style = JamiTheme.typography.bodyMedium,
        color = JamiTheme.colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = JamiTheme.spacing.l,
                vertical = JamiTheme.spacing.xs,
            ),
    )
}
