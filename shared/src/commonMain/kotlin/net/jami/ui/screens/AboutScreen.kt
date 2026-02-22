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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.contracts.AboutContract
import net.jami.ui.theme.JamiTheme

/**
 * About screen displaying application information.
 *
 * @param state The about screen state.
 * @param onBack Called when the user navigates back.
 */
@Composable
fun AboutScreen(
    state: AboutContract.State,
    onBack: () -> Unit,
) {
    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Settings,
                title = "About",
                onNavigateBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = JamiTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Jami",
                style = JamiTheme.typography.headlineLarge,
                color = JamiTheme.colors.primary,
            )

            Spacer(Modifier.height(JamiTheme.spacing.s))

            Text(
                text = "Version ${state.version}",
                style = JamiTheme.typography.bodyMedium,
                color = JamiTheme.colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            Text(
                text = state.description,
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            Text(
                text = state.copyright,
                style = JamiTheme.typography.bodySmall,
                color = JamiTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            Text(
                text = "Licensed under the GNU General Public License v3.0",
                style = JamiTheme.typography.bodySmall,
                color = JamiTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
