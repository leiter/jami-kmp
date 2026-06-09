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
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.components.actions.JamiButtonStyle
import net.jami.ui.theme.JamiTheme

/**
 * Welcome screen shown on first launch when no account is configured.
 *
 * Displays the Jami branding and provides options to create a new account,
 * import from a backup archive, or link from an existing device.
 *
 * @param onCreateAccount Called when the user taps "Create Account".
 * @param onImportAccount Called when the user taps "Import Account".
 * @param onLinkDevice    Called when the user taps "Link from another device".
 */
@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onImportAccount: () -> Unit,
    onLinkDevice: () -> Unit = {},
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
            text = stringResource(Res.string.app_name),
            style = JamiTheme.typography.headlineLarge,
            color = JamiTheme.colors.primary,
        )

        Spacer(Modifier.height(JamiTheme.spacing.l))

        // Description text
        Text(
            text = stringResource(Res.string.welcome_text),
            style = JamiTheme.typography.bodyLarge,
            color = JamiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(48.dp))

        // Create Account button
        JamiButton(
            text = stringResource(Res.string.action_create_account),
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
            style = JamiButtonStyle.Primary,
        )

        Spacer(Modifier.height(JamiTheme.spacing.m))

        // Import Account button
        JamiButton(
            text = stringResource(Res.string.action_import_account),
            onClick = onImportAccount,
            modifier = Modifier.fillMaxWidth(),
            style = JamiButtonStyle.Secondary,
        )

        Spacer(Modifier.height(JamiTheme.spacing.m))

        // Link from another device button
        JamiButton(
            text = stringResource(Res.string.account_link_device),
            onClick = onLinkDevice,
            modifier = Modifier.fillMaxWidth(),
            style = JamiButtonStyle.Secondary,
        )
    }
}
