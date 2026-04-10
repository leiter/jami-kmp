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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ProfileSetupViewModel
import net.jami.utils.clearFocusOnTap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onSkip: () -> Unit,
    onComplete: () -> Unit,
) {
    val viewModel = getViewModel<ProfileSetupViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_profile_setup),
                        style = JamiTheme.typography.titleMedium,
                    )
                },
                actions = {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(Res.string.action_continue), color = JamiTheme.colors.primary)
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
                .clearFocusOnTap()
                .imePadding()
                .padding(horizontal = JamiTheme.spacing.l)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(JamiTheme.spacing.xl))

            // Avatar placeholder
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = stringResource(Res.string.content_desc_profile_photo),
                modifier = Modifier.size(96.dp),
                tint = JamiTheme.colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            Text(
                text = stringResource(Res.string.prompt_add_profile_photo),
                style = JamiTheme.typography.bodyMedium,
                color = JamiTheme.colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            // Display name field
            OutlinedTextField(
                value = state.displayName,
                onValueChange = { viewModel.setDisplayName(it) },
                label = { Text(stringResource(Res.string.prompt_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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

            val canSave = !state.isLoading
            JamiButton(
                text = stringResource(Res.string.action_continue),
                onClick = { viewModel.saveProfile() },
                modifier = Modifier.fillMaxWidth(),
                loading = state.isLoading,
                enabled = canSave,
            )

            Spacer(Modifier.height(JamiTheme.spacing.xl))
        }
    }
}
