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
                        text = "Settings",
                        style = JamiTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            JamiSectionTitle(title = "Appearance")

            JamiToggle(
                label = "Dark mode",
                description = "Use dark color theme throughout the app",
                checked = state.isDarkTheme,
                onCheckedChange = { viewModel.toggleDarkTheme() },
            )

            HorizontalDivider()

            // Privacy section
            JamiSectionTitle(title = "Privacy")

            JamiToggle(
                label = "Typing indicators",
                description = "Let others see when you are typing",
                checked = state.isTypingIndicators,
                onCheckedChange = { viewModel.toggleTypingIndicators() },
            )

            JamiToggle(
                label = "Link preview",
                description = "Show previews for links in messages",
                checked = state.isLinkPreview,
                onCheckedChange = { viewModel.toggleLinkPreview() },
            )

            HorizontalDivider()

            // System section
            JamiSectionTitle(title = "System")

            JamiToggle(
                label = "Push notifications",
                description = "Receive notifications for new messages and calls",
                checked = state.isPushNotifications,
                onCheckedChange = { viewModel.togglePushNotifications() },
            )

            JamiToggle(
                label = "Start on boot",
                description = "Launch Jami when the device starts",
                checked = state.isStartOnBoot,
                onCheckedChange = { viewModel.toggleStartOnBoot() },
            )

            JamiToggle(
                label = "Run in background",
                description = "Keep Jami running to receive calls and messages",
                checked = state.isRunInBackground,
                onCheckedChange = { viewModel.toggleRunInBackground() },
            )

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}
