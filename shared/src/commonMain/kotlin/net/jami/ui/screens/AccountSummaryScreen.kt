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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import net.jami.di.getViewModel
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSummaryScreen(
    onContinue: () -> Unit,
) {
    val appViewModel = getViewModel<AppViewModel>()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Account Created",
                        style = JamiTheme.typography.titleMedium,
                    )
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
                .padding(horizontal = JamiTheme.spacing.l),
        ) {
            Spacer(Modifier.height(JamiTheme.spacing.xl))

            Text(
                text = "Your Jami account is ready!",
                style = JamiTheme.typography.headlineSmall,
                color = JamiTheme.colors.onSurface,
            )

            Spacer(Modifier.height(JamiTheme.spacing.l))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = JamiTheme.colors.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(JamiTheme.spacing.l),
                ) {
                    Text(
                        text = "Account Information",
                        style = JamiTheme.typography.titleSmall,
                        color = JamiTheme.colors.onSurface,
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.m))

                    Text(
                        text = "Your account has been created and is ready to use. " +
                            "You can share your Jami ID with others so they can contact you.",
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.m))

                    Text(
                        text = "Consider backing up your account to avoid losing access to it.",
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            JamiButton(
                text = "Continue to Jami",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
