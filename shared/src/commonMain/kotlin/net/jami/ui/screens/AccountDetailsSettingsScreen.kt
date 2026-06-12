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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import kotlinx.coroutines.launch
import net.jami.di.getViewModel
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AccountSettingsViewModel
import org.jetbrains.compose.resources.stringResource

private const val PASSWORD_MIN_LENGTH = 6

/**
 * Account details settings sub-screen, mirroring the "Account" tab of the
 * jami-android-client AccountFragment.
 *
 * Shows a flat list (no section headers, no card containers):
 *   1. Konto sichern       — hidden for managed accounts
 *   2. Passwort ändern     — hidden for managed accounts
 *   3. Biometrische Auth   — shown only if hasPassword && !hasManager
 *   4. Blockierte Kontakte — always visible
 *   5. Konto löschen       — always visible
 *
 * @param onBack Called when the user navigates back.
 * @param onBlockedContacts Called when the "Blockierte Kontakte" row is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailsSettingsScreen(
    onBack: () -> Unit,
    onBlockedContacts: () -> Unit = {},
) {
    val viewModel = getViewModel<AccountSettingsViewModel>()
    val state by viewModel.state.collectAsState()

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBiometricSetupDialog by remember { mutableStateOf(false) }
    var showDisableBiometricDialog by remember { mutableStateOf(false) }
    var biometricPassword by remember { mutableStateOf("") }
    var biometricError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val passwordChangedMsg = stringResource(Res.string.account_password_changed)
    val passwordErrorMsg = stringResource(Res.string.account_device_revocation_wrong_password)
    val exportSuccessMsg = stringResource(Res.string.snackbar_export_success)
    val exportErrorMsg = stringResource(Res.string.snackbar_export_error)

    LaunchedEffect(Unit) { viewModel.loadAccount() }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            hasPassword = state.hasPassword,
            onDismiss = { showChangePasswordDialog = false },
            onChangePassword = { oldPassword, newPassword ->
                showChangePasswordDialog = false
                val success = viewModel.changePassword(oldPassword, newPassword)
                coroutineScope.launch {
                    if (success) snackbarHostState.showSnackbar(passwordChangedMsg)
                    else snackbarHostState.showSnackbar(passwordErrorMsg)
                }
            },
        )
    }

    if (showExportDialog) {
        ExportAccountDialog(
            onDismiss = { showExportDialog = false },
            onExport = { password ->
                showExportDialog = false
                val success = viewModel.exportAccount(password)
                coroutineScope.launch {
                    if (success) snackbarHostState.showSnackbar(exportSuccessMsg)
                    else snackbarHostState.showSnackbar(exportErrorMsg)
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.account_delete_dialog_title)) },
            text = { Text(stringResource(Res.string.account_delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.removeAccount() }) {
                    Text(
                        stringResource(Res.string.menu_delete),
                        color = JamiTheme.colors.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.export_side_step2_cancel))
                }
            },
        )
    }

    // Biometric Setup Dialog (password confirmation)
    if (showBiometricSetupDialog) {
        AlertDialog(
            onDismissRequest = {
                showBiometricSetupDialog = false
                biometricPassword = ""
                biometricError = null
            },
            title = { Text(stringResource(Res.string.account_biometric_setup_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.account_biometric_setup_message))
                    Spacer(Modifier.height(JamiTheme.spacing.m))

                    OutlinedTextField(
                        value = biometricPassword,
                        onValueChange = {
                            biometricPassword = it
                            biometricError = null
                        },
                        label = { Text(stringResource(Res.string.account_enter_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = biometricError != null,
                        supportingText = biometricError?.let { err -> { Text(err) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val result = viewModel.enableBiometric(biometricPassword)
                            result.onSuccess {
                                showBiometricSetupDialog = false
                                biometricPassword = ""
                                biometricError = null
                            }.onFailure { e ->
                                biometricError = e.message
                            }
                        }
                    }
                ) {
                    Text(stringResource(Res.string.action_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBiometricSetupDialog = false
                    biometricPassword = ""
                    biometricError = null
                }) {
                    Text(stringResource(Res.string.export_side_step2_cancel))
                }
            },
        )
    }

    // Disable Biometric Dialog
    if (showDisableBiometricDialog) {
        AlertDialog(
            onDismissRequest = { showDisableBiometricDialog = false },
            title = { Text(stringResource(Res.string.account_biometric_disable_title)) },
            text = { Text(stringResource(Res.string.account_biometric_disable_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.disableBiometric()
                            showDisableBiometricDialog = false
                        }
                    }
                ) {
                    Text(stringResource(Res.string.action_disable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableBiometricDialog = false }) {
                    Text(stringResource(Res.string.export_side_step2_cancel))
                }
            },
        )
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.navigation_item_account),
                        color = JamiTheme.colors.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_desc_back),
                            tint = JamiTheme.colors.onSurface,
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
            Spacer(Modifier.height(JamiTheme.spacing.s))

            // 1. Konto sichern — hidden for managed accounts
            if (!state.hasManager) {
                AccountActionRow(
                    icon = Icons.Default.FileDownload,
                    label = stringResource(Res.string.account_export_file),
                    summary = stringResource(Res.string.account_export_file_summary),
                    onClick = { showExportDialog = true },
                )

                // 2. Passwort ändern / festlegen
                AccountActionRow(
                    icon = Icons.Default.Lock,
                    label = if (state.hasPassword)
                        stringResource(Res.string.account_password_change)
                    else
                        stringResource(Res.string.account_password_set),
                    summary = stringResource(Res.string.account_password_summary),
                    onClick = { showChangePasswordDialog = true },
                )

                // 3. Biometrische Authentifizierung — only if account has a password and hardware supports it
                if (state.hasPassword &&
                    state.biometricAvailability != net.jami.services.BiometricAvailability.NO_HARDWARE &&
                    state.biometricAvailability != net.jami.services.BiometricAvailability.UNKNOWN_ERROR) {
                    AccountActionRow(
                        icon = Icons.Default.Fingerprint,
                        label = if (state.hasBiometric)
                            stringResource(Res.string.account_biometric_disable)
                        else
                            stringResource(Res.string.account_biometric_set),
                        summary = stringResource(Res.string.account_biometric_summary),
                        onClick = {
                            if (state.hasBiometric) {
                                // Show disable confirmation dialog
                                showDisableBiometricDialog = true
                            } else {
                                // Show password confirmation for enrollment
                                showBiometricSetupDialog = true
                            }
                        },
                    )
                }
            }

            // 4. Blockierte Kontakte — always visible
            AccountActionRow(
                icon = Icons.Default.Block,
                label = stringResource(Res.string.pref_blackList_title),
                summary = stringResource(Res.string.pref_blackList_summary),
                onClick = onBlockedContacts,
            )

            // 5. Konto löschen — always visible
            AccountActionRow(
                icon = Icons.Default.Delete,
                label = stringResource(Res.string.account_delete_label),
                summary = stringResource(Res.string.account_delete_summary),
                onClick = { showDeleteDialog = true },
            )

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/** Icon + bold label + summary row for tappable account actions. */
@Composable
private fun AccountActionRow(
    icon: ImageVector,
    label: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = JamiTheme.spacing.m, vertical = JamiTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JamiTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(JamiTheme.spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = JamiTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = JamiTheme.colors.onSurface,
            )
            Text(
                text = summary,
                style = JamiTheme.typography.bodySmall,
                color = JamiTheme.colors.onSurfaceVariant,
            )
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

/**
 * Dialog for changing or setting the account archive password.
 *
 * When [hasPassword] is true the user must supply the current password first.
 * Both new-password fields left empty removes the password from the account.
 */
@Composable
private fun ChangePasswordDialog(
    hasPassword: Boolean,
    onDismiss: () -> Unit,
    onChangePassword: (oldPassword: String, newPassword: String) -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val errorMinLength = stringResource(Res.string.error_password_char_count)
    val errorMismatch = stringResource(Res.string.error_passwords_not_equals)

    fun validate(): Boolean {
        // Allow clearing password: old is set, both new fields are empty
        if (hasPassword && newPassword.isEmpty() && confirmPassword.isEmpty()) {
            newPasswordError = null
            confirmPasswordError = null
            return true
        }
        return if (newPassword.length < PASSWORD_MIN_LENGTH) {
            newPasswordError = errorMinLength
            confirmPasswordError = errorMinLength
            false
        } else if (newPassword != confirmPassword) {
            newPasswordError = errorMismatch
            confirmPasswordError = errorMismatch
            false
        } else {
            newPasswordError = null
            confirmPasswordError = null
            true
        }
    }

    val title = if (hasPassword)
        stringResource(Res.string.account_password_change)
    else
        stringResource(Res.string.account_password_set)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.help_password_choose),
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(JamiTheme.spacing.m))

                if (hasPassword) {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = { Text(stringResource(Res.string.prompt_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(JamiTheme.spacing.s))
                }

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        newPasswordError = null
                        confirmPasswordError = null
                    },
                    label = { Text(stringResource(Res.string.prompt_new_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = newPasswordError != null,
                    supportingText = newPasswordError?.let { err -> { Text(err) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(JamiTheme.spacing.s))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        newPasswordError = null
                        confirmPasswordError = null
                    },
                    label = { Text(stringResource(Res.string.prompt_new_password_repeat)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmPasswordError != null,
                    supportingText = confirmPasswordError?.let { err -> { Text(err) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (validate()) onChangePassword(oldPassword, newPassword)
                    }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (validate()) onChangePassword(oldPassword, newPassword)
            }) {
                Text(title)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.export_side_step2_cancel))
            }
        },
    )
}

/**
 * Dialog for entering the account password before exporting a backup archive.
 */
@Composable
private fun ExportAccountDialog(
    onDismiss: () -> Unit,
    onExport: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

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
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus(); onExport(password) },
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
                Text(stringResource(Res.string.export_side_step2_cancel))
            }
        },
    )
}
