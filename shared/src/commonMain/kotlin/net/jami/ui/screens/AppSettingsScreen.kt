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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.components.content.JamiToggle
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AppSettingsViewModel

/**
 * Application settings screen with toggle rows organized by section.
 *
 * Sections:
 * - Appearance: Dark mode
 * - Privacy: Typing indicators, link preview
 * - System: Push notifications, start on boot, run in background
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
            // Appearance section
            JamiSectionTitle(title = stringResource(Res.string.pref_category_appearance))

            JamiToggle(
                label = stringResource(Res.string.pref_dark_mode),
                description = stringResource(Res.string.pref_dark_mode_description),
                checked = state.isDarkTheme,
                onCheckedChange = { viewModel.toggleDarkTheme() },
            )

            HorizontalDivider()

            // Privacy section
            JamiSectionTitle(title = stringResource(Res.string.pref_category_privacy))

            JamiToggle(
                label = stringResource(Res.string.pref_typing_indicators),
                description = stringResource(Res.string.pref_typing_indicators_description),
                checked = state.isTypingIndicators,
                onCheckedChange = { viewModel.toggleTypingIndicators() },
            )

            JamiToggle(
                label = stringResource(Res.string.pref_link_preview),
                description = stringResource(Res.string.pref_link_preview_description),
                checked = state.isLinkPreview,
                onCheckedChange = { viewModel.toggleLinkPreview() },
            )

            HorizontalDivider()

            // System section
            JamiSectionTitle(title = stringResource(Res.string.pref_category_system))

            JamiToggle(
                label = stringResource(Res.string.pref_push_notifications),
                description = stringResource(Res.string.pref_push_notifications_description),
                checked = state.isPushNotifications,
                onCheckedChange = { viewModel.togglePushNotifications() },
            )

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
