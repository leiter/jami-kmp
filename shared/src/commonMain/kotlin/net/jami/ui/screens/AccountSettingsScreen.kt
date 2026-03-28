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

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AccountSettingsViewModel
import net.jami.ui.viewmodel.DeviceItem

/**
 * Account settings screen displaying profile info, linked devices,
 * and navigation to sub-settings including account export.
 *
 * @param onBack Called when the user navigates back.
 * @param onBlockedContacts Called when "Blocked Contacts" is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onBlockedContacts: () -> Unit,
) {
    val viewModel = getViewModel<AccountSettingsViewModel>()
    val state by viewModel.state.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val exportSuccessMsg = stringResource(Res.string.snackbar_export_success)
    val exportErrorMsg = stringResource(Res.string.snackbar_export_error)

    LaunchedEffect(Unit) {
        viewModel.loadAccount()
    }

    // Export dialog
    if (showExportDialog) {
        ExportAccountDialog(
            onDismiss = { showExportDialog = false },
            onExport = { password ->
                showExportDialog = false
                val success = viewModel.exportAccount(password)
                coroutineScope.launch {
                    if (success) {
                        snackbarHostState.showSnackbar(exportSuccessMsg)
                    } else {
                        snackbarHostState.showSnackbar(exportErrorMsg)
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_account_settings),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Profile section
            JamiSectionTitle(title = "Profile")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(JamiTheme.spacing.m))

                // Large avatar
                JamiAvatar(
                    displayName = state.displayName.ifEmpty { "User" },
                    size = AvatarSize.XLarge,
                )

                Spacer(Modifier.height(JamiTheme.spacing.m))

                // Display name (editable)
                Text(
                    text = state.displayName.ifEmpty { "No display name" },
                    style = JamiTheme.typography.titleLarge,
                    color = JamiTheme.colors.onSurface,
                )

                // Username
                if (state.username.isNotEmpty()) {
                    Spacer(Modifier.height(JamiTheme.spacing.xs))
                    Text(
                        text = state.username,
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                // Identity hash
                if (state.identityHash.isNotEmpty()) {
                    Spacer(Modifier.height(JamiTheme.spacing.xs))
                    Text(
                        text = state.identityHash,
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(JamiTheme.spacing.l))
            }

            HorizontalDivider()

            // Devices section
            JamiSectionTitle(title = stringResource(Res.string.section_linked_devices))

            if (state.devices.isEmpty()) {
                Text(
                    text = stringResource(Res.string.empty_linked_devices),
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.s,
                    ),
                )
            } else {
                state.devices.forEach { device ->
                    DeviceListItem(device = device)
                }
            }

            HorizontalDivider()

            // Settings links
            JamiSectionTitle(title = "Settings")

            SettingsLinkRow(
                label = stringResource(Res.string.dialog_export_title),
                onClick = { showExportDialog = true },
            )

            SettingsLinkRow(
                label = stringResource(Res.string.screen_title_blocked_contacts),
                onClick = onBlockedContacts,
            )

            SettingsLinkRow(
                label = stringResource(Res.string.pref_category_account),
                onClick = { /* Navigate to account sub-settings */ },
            )

            SettingsLinkRow(
                label = stringResource(Res.string.pref_category_media),
                onClick = { /* Navigate to media settings */ },
            )

            SettingsLinkRow(
                label = stringResource(Res.string.pref_category_messages),
                onClick = { /* Navigate to message settings */ },
            )

            SettingsLinkRow(
                label = stringResource(Res.string.pref_category_advanced),
                onClick = { /* Navigate to advanced settings */ },
            )

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}

/**
 * Dialog for exporting the account with a password.
 */
@Composable
private fun ExportAccountDialog(
    onDismiss: () -> Unit,
    onExport: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_export_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.dialog_export_message),
                    style = JamiTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(JamiTheme.spacing.m))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.prompt_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(password) }) {
                Text(stringResource(Res.string.action_export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

/**
 * Row displaying a linked device.
 */
@Composable
private fun DeviceListItem(device: DeviceItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = JamiTheme.spacing.l,
                vertical = JamiTheme.spacing.m,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Devices,
            contentDescription = null,
            tint = JamiTheme.colors.onSurfaceVariant,
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.deviceName,
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurface,
            )
            Text(
                text = if (device.isCurrent) stringResource(Res.string.account_this_device) else device.deviceId,
                style = JamiTheme.typography.bodySmall,
                color = JamiTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Clickable settings row with label and chevron.
 */
@Composable
private fun SettingsLinkRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = JamiTheme.spacing.l,
                vertical = JamiTheme.spacing.m,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = JamiTheme.typography.bodyLarge,
            color = JamiTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = JamiTheme.colors.onSurfaceVariant,
        )
    }
}
