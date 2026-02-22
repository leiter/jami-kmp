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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.components.content.JamiToggle
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.contracts.AppSettingsContract
import net.jami.ui.theme.JamiTheme

/**
 * Application settings screen with toggle rows organized by section.
 *
 * @param state The settings state.
 * @param onAction Dispatches settings actions.
 * @param onBack Called when the user navigates back.
 */
@Composable
fun AppSettingsScreen(
    state: AppSettingsContract.State,
    onAction: (AppSettingsContract.Action) -> Unit,
    onBack: () -> Unit,
) {
    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Settings,
                title = "Settings",
                onNavigateBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            JamiSectionTitle(title = "Appearance")

            JamiToggle(
                label = "Dark mode",
                description = "Use dark color theme throughout the app",
                checked = state.isDarkTheme,
                onCheckedChange = { onAction(AppSettingsContract.Action.ToggleDarkTheme) },
            )

            HorizontalDivider()

            JamiSectionTitle(title = "Privacy")

            JamiToggle(
                label = "Typing indicators",
                description = "Let others see when you are typing",
                checked = state.isTypingIndicators,
                onCheckedChange = { onAction(AppSettingsContract.Action.ToggleTypingIndicators) },
            )

            JamiToggle(
                label = "Link preview",
                description = "Show previews for links in messages",
                checked = state.isLinkPreview,
                onCheckedChange = { onAction(AppSettingsContract.Action.ToggleLinkPreview) },
            )

            HorizontalDivider()

            JamiSectionTitle(title = "System")

            JamiToggle(
                label = "Push notifications",
                description = "Receive notifications for new messages and calls",
                checked = state.isPushNotifications,
                onCheckedChange = { onAction(AppSettingsContract.Action.TogglePushNotifications) },
            )

            JamiToggle(
                label = "Start on boot",
                description = "Launch Jami when the device starts",
                checked = state.isStartOnBoot,
                onCheckedChange = { onAction(AppSettingsContract.Action.ToggleStartOnBoot) },
            )

            JamiToggle(
                label = "Run in background",
                description = "Keep Jami running to receive calls and messages",
                checked = state.isRunInBackground,
                onCheckedChange = { onAction(AppSettingsContract.Action.ToggleRunInBackground) },
            )

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}
