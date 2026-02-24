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
import net.jami.ui.contracts.CreateAccountContract
import net.jami.ui.theme.JamiTheme

/**
 * Account creation screen with username, password, and confirm password fields.
 *
 * @param state The account creation state.
 * @param onAction Dispatches creation actions.
 * @param onBack Called when the user navigates back.
 */
@Composable
fun CreateAccountScreen(
    state: CreateAccountContract.State,
    onAction: (CreateAccountContract.Action) -> Unit,
    onBack: () -> Unit,
) {
    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Detail,
                title = "Create Account",
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

            val hasUsernameError = state.usernameError != null

            JamiInputText(
                value = state.username,
                onValueChange = { onAction(CreateAccountContract.Action.SetUsername(it)) },
                label = "Username (optional)",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                isError = hasUsernameError,
                errorMessage = state.usernameError,
            )

            if (state.isCheckingUsername) {
                Spacer(Modifier.height(JamiTheme.spacing.xs))
                Text(
                    text = "Checking availability...",
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            } else if (state.usernameAvailable == true && state.username.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.xs))
                Text(
                    text = "Username available",
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.primary,
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.m))

            JamiInputText(
                value = state.password,
                onValueChange = { onAction(CreateAccountContract.Action.SetPassword(it)) },
                label = "Password (optional)",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            JamiInputText(
                value = state.confirmPassword,
                onValueChange = { onAction(CreateAccountContract.Action.SetConfirmPassword(it)) },
                label = "Confirm Password",
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

            val usernameBlocking = state.username.isNotEmpty() && (
                state.isCheckingUsername ||
                state.usernameError != null ||
                state.username.length < 3 ||
                state.usernameAvailable != true
            )

            JamiButton(
                text = "Create Account",
                onClick = { onAction(CreateAccountContract.Action.CreateAccount) },
                modifier = Modifier.fillMaxWidth(),
                loading = state.isLoading,
                enabled = !state.isLoading && !usernameBlocking,
            )

            Spacer(Modifier.height(JamiTheme.spacing.xl))
        }
    }
}
