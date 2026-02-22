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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.inputs.JamiInputText
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.contracts.ImportAccountContract
import net.jami.ui.theme.JamiTheme

/**
 * Import account screen for restoring an account from a backup archive.
 *
 * @param state The import account state.
 * @param onAction Dispatches import actions.
 * @param onBack Called when the user navigates back.
 */
@Composable
fun ImportAccountScreen(
    state: ImportAccountContract.State,
    onAction: (ImportAccountContract.Action) -> Unit,
    onBack: () -> Unit,
) {
    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Detail,
                title = "Import Account",
                onNavigateBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = JamiTheme.spacing.l)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(JamiTheme.spacing.l))

            JamiInputText(
                value = state.archivePath,
                onValueChange = { onAction(ImportAccountContract.Action.SetArchivePath(it)) },
                label = "Archive file path",
                placeholder = "/path/to/backup.gz",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            JamiInputText(
                value = state.password,
                onValueChange = { onAction(ImportAccountContract.Action.SetPassword(it)) },
                label = "Password",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )

            if (state.error != null) {
                Spacer(Modifier.height(JamiTheme.spacing.s))
                Text(
                    text = state.error ?: "",
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.error,
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            JamiButton(
                text = "Import Account",
                onClick = { onAction(ImportAccountContract.Action.Import) },
                modifier = Modifier.fillMaxWidth(),
                loading = state.isLoading,
                enabled = !state.isLoading,
            )

            Spacer(Modifier.height(JamiTheme.spacing.xl))
        }
    }
}
