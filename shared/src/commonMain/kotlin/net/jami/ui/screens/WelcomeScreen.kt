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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.components.actions.JamiButtonStyle
import net.jami.ui.theme.JamiTheme

/**
 * Welcome screen shown on first launch when no account is configured.
 *
 * Displays the Jami branding and provides options to create a new
 * account or import an existing one from a backup archive.
 *
 * @param onCreateAccount Called when the user taps "Create Account".
 * @param onImportAccount Called when the user taps "Import Account".
 */
@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onImportAccount: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = JamiTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Jami logo text
        Text(
            text = "Jami",
            style = JamiTheme.typography.headlineLarge,
            color = JamiTheme.colors.primary,
        )

        Spacer(Modifier.height(JamiTheme.spacing.l))

        // Description text
        Text(
            text = "Free and universal communication platform\n" +
                "that respects the freedom and privacy of its users.",
            style = JamiTheme.typography.bodyLarge,
            color = JamiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(48.dp))

        // Create Account button
        JamiButton(
            text = "Create Account",
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
            style = JamiButtonStyle.Primary,
        )

        Spacer(Modifier.height(JamiTheme.spacing.m))

        // Import Account button
        JamiButton(
            text = "Import Account",
            onClick = onImportAccount,
            modifier = Modifier.fillMaxWidth(),
            style = JamiButtonStyle.Secondary,
        )
    }
}
