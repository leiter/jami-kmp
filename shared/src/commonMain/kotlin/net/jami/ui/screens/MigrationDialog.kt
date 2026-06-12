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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.account_enter_password
import jami_kmp.shared.generated.resources.account_migrate_button
import jami_kmp.shared.generated.resources.account_migration_message
import jami_kmp.shared.generated.resources.account_migration_message_error
import jami_kmp.shared.generated.resources.account_migration_title
import jami_kmp.shared.generated.resources.action_skip
import kotlinx.coroutines.flow.filterIsInstance
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.ui.theme.JamiTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun MigrationDialog(
    accountId: String,
    onMigrated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accountService: AccountService = koinInject()
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val errorText = stringResource(Res.string.account_migration_message_error)

    LaunchedEffect(accountId) {
        accountService.accountEvents
            .filterIsInstance<AccountEvent.MigrationEnded>()
            .collect { event ->
                if (event.accountId == accountId) {
                    isLoading = false
                    if (event.state == "SUCCESS") onMigrated()
                    else error = errorText
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.account_migration_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.account_migration_message),
                    style = JamiTheme.typography.bodyMedium,
                )
                if (error != null) {
                    Spacer(Modifier.height(JamiTheme.spacing.s))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = JamiTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(JamiTheme.spacing.m))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(stringResource(Res.string.account_enter_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (password.length >= 6 && !isLoading) {
                            isLoading = true
                            accountService.migrateAccount(accountId, password)
                        }
                    }),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = password.length >= 6 && !isLoading,
                onClick = {
                    isLoading = true
                    accountService.migrateAccount(accountId, password)
                },
            ) {
                Text(stringResource(Res.string.account_migrate_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_skip))
            }
        },
    )
}
