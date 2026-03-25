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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import net.jami.di.getViewModel
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.platform.FilePickerEffect
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ImportAccountViewModel

/**
 * Import account screen for restoring an account from a backup archive.
 *
 * Provides a file picker button, archive path display, and password field,
 * then uses [ImportAccountViewModel] to restore the account through the daemon.
 *
 * @param onBack Called when the user navigates back.
 * @param onImported Called after the account is successfully imported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportAccountScreen(
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val viewModel = getViewModel<ImportAccountViewModel>()
    val state by viewModel.state.collectAsState()
    var showFilePicker by remember { mutableStateOf(false) }

    // Navigate when import completes
    LaunchedEffect(state.isImported) {
        if (state.isImported) {
            onImported()
        }
    }

    // File picker effect
    FilePickerEffect(
        show = showFilePicker,
        mimeTypes = listOf("application/gzip", "application/x-gzip", "application/octet-stream"),
        onFilePicked = { path ->
            showFilePicker = false
            if (path != null) {
                viewModel.setArchivePath(path)
            }
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Import Account",
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
                .padding(horizontal = JamiTheme.spacing.l)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(JamiTheme.spacing.l))

            // Archive path field with Browse button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.archivePath,
                    onValueChange = { viewModel.setArchivePath(it) },
                    label = { Text("Archive file path") },
                    placeholder = { Text("/path/to/backup.gz") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.width(JamiTheme.spacing.s))
                OutlinedButton(
                    onClick = { showFilePicker = true },
                ) {
                    Text("Browse")
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // Password field
            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.setPassword(it) },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )

            // Error message
            if (state.error != null) {
                Spacer(Modifier.height(JamiTheme.spacing.s))
                Text(
                    text = state.error ?: "",
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.error,
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            // Import button
            JamiButton(
                text = "Import Account",
                onClick = { viewModel.importAccount() },
                modifier = Modifier.fillMaxWidth(),
                loading = state.isLoading,
                enabled = !state.isLoading && state.archivePath.isNotEmpty(),
            )

            Spacer(Modifier.height(JamiTheme.spacing.xl))
        }
    }
}
