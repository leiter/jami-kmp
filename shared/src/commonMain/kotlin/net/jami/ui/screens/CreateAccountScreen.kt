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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.action_create_account
import jami_kmp.shared.generated.resources.content_desc_available
import jami_kmp.shared.generated.resources.content_desc_back
import jami_kmp.shared.generated.resources.content_desc_taken
import jami_kmp.shared.generated.resources.error_password_min_chars
import jami_kmp.shared.generated.resources.error_passwords_mismatch
import jami_kmp.shared.generated.resources.error_username_taken
import jami_kmp.shared.generated.resources.prompt_choose_username
import jami_kmp.shared.generated.resources.prompt_confirm_password
import jami_kmp.shared.generated.resources.prompt_password_optional
import net.jami.di.getViewModel
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.AccountCreationViewModel
import net.jami.utils.clearFocusOnTap
import org.jetbrains.compose.resources.stringResource

/**
 * Account creation screen with username, password, and confirm password fields.
 *
 * Uses [AccountCreationViewModel] to handle account creation through the daemon.
 * Navigates to Home on success.
 *
 * @param onBack Called when the user navigates back.
 * @param onAccountCreated Called after the account is successfully created.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountScreen(
    onBack: () -> Unit,
    onAccountCreated: () -> Unit,
) {
    val viewModel = getViewModel<AccountCreationViewModel>()
    val state by viewModel.state.collectAsState()

    // Navigate when account is created
    LaunchedEffect(state.isCreated) {
        if (state.isCreated) {
            onAccountCreated()
        }
    }

    Scaffold(
        containerColor = JamiTheme.colors.background,
        contentColor = JamiTheme.colors.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.action_create_account),
                        style = JamiTheme.typography.titleMedium,
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
                    navigationIconContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
    ) { padding ->
        val passwordFocus = remember { FocusRequester() }
        val confirmPasswordFocus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = JamiTheme.colors.onSurface,
            unfocusedTextColor = JamiTheme.colors.onSurface,
            focusedLabelColor = JamiTheme.colors.primary,
            unfocusedLabelColor = JamiTheme.colors.onSurfaceVariant,
            focusedBorderColor = JamiTheme.colors.primary,
            unfocusedBorderColor = JamiTheme.colors.outline,
            cursorColor = JamiTheme.colors.primary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorTextColor = JamiTheme.colors.onSurface,
            errorBorderColor = JamiTheme.colors.error,
            errorLabelColor = JamiTheme.colors.error,
            errorCursorColor = JamiTheme.colors.error,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clearFocusOnTap()
                .imePadding()
                .padding(horizontal = JamiTheme.spacing.l)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(JamiTheme.spacing.l))

            // Username field with availability check (mandatory)
            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.setUsername(it) },
                label = { Text(stringResource(Res.string.prompt_choose_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                trailingIcon = {
                    if (state.username.isNotEmpty()) {
                        when {
                            state.usernameCheckInProgress -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = JamiTheme.colors.onSurfaceVariant,
                            )
                            state.usernameAvailable == true -> Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(Res.string.content_desc_available),
                                tint = JamiTheme.colors.primary,
                            )
                            state.usernameAvailable == false -> Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(Res.string.content_desc_taken),
                                tint = JamiTheme.colors.error,
                            )
                        }
                    }
                },
                supportingText = when {
                    state.usernameAvailable == false ->
                        ({ Text(stringResource(Res.string.error_username_taken), color = JamiTheme.colors.error) })
                    state.usernameCheckError != null ->
                        ({ Text(state.usernameCheckError ?: "", color = JamiTheme.colors.error) })
                    else -> null
                },
                isError = state.usernameAvailable == false,
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // Password field (optional, min 6 chars if set)
            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.setPassword(it) },
                label = { Text(stringResource(Res.string.prompt_password_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                colors = textFieldColors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { confirmPasswordFocus.requestFocus() },
                ),
                supportingText = if (state.password.isNotEmpty() && state.password.length < 6) {
                    { Text(stringResource(Res.string.error_password_min_chars), color = JamiTheme.colors.error) }
                } else null,
                isError = state.password.isNotEmpty() && state.password.length < 6,
            )

            // Confirm password field (only shown when password is entered)
            if (state.password.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.m))

                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = { viewModel.setConfirmPassword(it) },
                    label = { Text(stringResource(Res.string.prompt_confirm_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(confirmPasswordFocus),
                    colors = textFieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() },
                    ),
                    supportingText = if (state.confirmPassword.isNotEmpty() && state.password != state.confirmPassword) {
                        { Text(stringResource(Res.string.error_passwords_mismatch), color = JamiTheme.colors.error) }
                    } else null,
                    isError = state.confirmPassword.isNotEmpty() && state.password != state.confirmPassword,
                )
            }

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

            // Create button — requires: username set, not taken, not checking, password valid
            val canCreate = !state.isLoading
                && state.username.isNotEmpty()
                && !state.usernameCheckInProgress
                && state.usernameAvailable != false
                && (state.password.isEmpty() || state.password.length >= 6)
                && (state.password.isEmpty() || state.password == state.confirmPassword)
            JamiButton(
                text = stringResource(Res.string.action_create_account),
                onClick = { viewModel.createAccount() },
                modifier = Modifier.fillMaxWidth(),
                loading = state.isLoading,
                enabled = canCreate,
            )

            Spacer(Modifier.height(JamiTheme.spacing.xl))
        }
    }
}
