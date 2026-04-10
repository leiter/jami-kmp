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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiToggle
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ContactItem
import net.jami.ui.viewmodel.NewConversationViewModel

/**
 * New conversation screen for selecting contacts and creating a conversation.
 *
 * Provides search, contact selection (single or group), and a create button.
 *
 * @param onBack Called when the user navigates back.
 * @param onConversationCreated Called with the new conversation ID after creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onBack: () -> Unit,
    onConversationCreated: (String) -> Unit,
) {
    val viewModel = getViewModel<NewConversationViewModel>()
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.resetSearch() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_new_conversation),
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search field
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                placeholder = { Text(stringResource(Res.string.placeholder_search_contacts)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.s,
                    ),
            )

            // Group toggle
            JamiToggle(
                label = stringResource(Res.string.toggle_create_group),
                checked = state.isGroup,
                onCheckedChange = { viewModel.setGroupMode(it) },
            )

            // Group name field — shown only when group mode is on
            if (state.isGroup) {
                OutlinedTextField(
                    value = state.groupName,
                    onValueChange = { viewModel.setGroupName(it) },
                    placeholder = { Text(stringResource(Res.string.placeholder_group_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = JamiTheme.spacing.l,
                            vertical = JamiTheme.spacing.s,
                        ),
                )
            }

            // Contact list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(
                    items = state.publicDirectoryResults,
                    key = { it.uri },
                ) { contact ->
                    val isSelected = state.selectedContacts.any { it.uri == contact.uri }
                    SelectableContactItem(
                        contact = contact,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                viewModel.removeContact(contact)
                            } else {
                                viewModel.selectContact(contact)
                            }
                        },
                    )
                }
            }

            // Create button
            JamiButton(
                text = stringResource(Res.string.action_create),
                onClick = {
                    coroutineScope.launch {
                        val conversationId = viewModel.createConversation()
                        if (conversationId != null) {
                            onConversationCreated(conversationId)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(JamiTheme.spacing.l),
                loading = state.isLoading,
                enabled = state.selectedContacts.isNotEmpty() && !state.isLoading,
            )
        }
    }
}

/**
 * Contact item with a selection indicator (checkbox circle).
 */
@Composable
private fun SelectableContactItem(
    contact: ContactItem,
    isSelected: Boolean,
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
        JamiAvatar(
            displayName = contact.displayName,
            size = AvatarSize.Medium,
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contact.username.isNotEmpty()) {
                Text(
                    text = contact.username,
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle
            else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint = if (isSelected) JamiTheme.colors.primary
            else JamiTheme.colors.onSurfaceVariant,
        )
    }
}
