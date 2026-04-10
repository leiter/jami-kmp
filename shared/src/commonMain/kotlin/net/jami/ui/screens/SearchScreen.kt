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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.model.Contact
import net.jami.ui.components.actions.JamiFilterChip
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.PresenceStatus
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ContactItem
import net.jami.ui.viewmodel.ConversationItem
import net.jami.ui.viewmodel.NewConversationViewModel

/**
 * Search screen for finding conversations and contacts.
 *
 * Shows two sections:
 * - "Public directory" — nameserver/DHT lookup results
 * - "Conversations" — existing conversations matching the query
 *
 * Uses [NewConversationViewModel] for search state and lookup routing.
 *
 * @param onBack Called when the user navigates back.
 * @param onConversationClick Called when a result is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onConversationClick: (String) -> Unit,
    onQrScanClick: () -> Unit = {},
    onNewGroupClick: () -> Unit = {},
) {
    val viewModel = getViewModel<NewConversationViewModel>()
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.resetSearch()
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.search(it) },
                        placeholder = { Text(stringResource(Res.string.placeholder_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
            // Filter chips row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.s,
                    ),
                horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.s),
            ) {
                item {
                    JamiFilterChip(
                        text = stringResource(Res.string.action_qr_code),
                        onClick = onQrScanClick,
                        leadingIcon = Icons.Default.QrCodeScanner,
                    )
                }
                item {
                    JamiFilterChip(
                        text = stringResource(Res.string.action_new_group),
                        onClick = onNewGroupClick,
                        leadingIcon = Icons.Default.Groups,
                    )
                }
            }

            // Sectioned results list
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // Loading spinner (nameserver lookup in progress)
                if (state.isLoading) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(JamiTheme.spacing.l),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = JamiTheme.colors.accent)
                        }
                    }
                }

                // No-results state
                if (state.searchQuery.isNotEmpty()
                    && state.publicDirectoryResults.isEmpty()
                    && state.conversationResults.isEmpty()
                    && !state.isLoading
                ) {
                    item(key = "no_results") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(JamiTheme.spacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.search_no_results),
                                style = JamiTheme.typography.bodyMedium,
                                color = JamiTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Public directory section
                if (state.publicDirectoryResults.isNotEmpty()) {
                    item(key = "header_public_directory") {
                        SectionHeader(stringResource(Res.string.search_results_public_directory))
                    }
                    items(
                        items = state.publicDirectoryResults,
                        key = { "pub_${it.uri}" },
                    ) { contact ->
                        SearchResultItem(
                            contact = contact,
                            onClick = {
                                viewModel.selectContact(contact)
                                coroutineScope.launch {
                                    val conversationId = viewModel.createConversation()
                                    if (conversationId != null) onConversationClick(conversationId)
                                }
                            },
                        )
                    }
                }

                // Conversations section — header only shown when public directory also has results
                if (state.conversationResults.isNotEmpty()) {
                    if (state.publicDirectoryResults.isNotEmpty()) {
                        item(key = "header_conversations") {
                            SectionHeader(stringResource(Res.string.navigation_item_conversation))
                        }
                    }
                    items(
                        items = state.conversationResults,
                        key = { "conv_${it.id}" },
                    ) { conversation ->
                        ConversationSearchItem(
                            conversation = conversation,
                            onClick = { onConversationClick(conversation.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = JamiTheme.typography.labelMedium,
        color = JamiTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = JamiTheme.spacing.l,
                vertical = JamiTheme.spacing.s,
            ),
    )
}

/**
 * Single search result item for a public directory contact.
 */
@Composable
private fun SearchResultItem(
    contact: ContactItem,
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
            imageUri = contact.avatarUri,
            size = AvatarSize.Medium,
            showPresence = true,
            presenceStatus = if (contact.presenceStatus != Contact.PresenceStatus.OFFLINE)
                PresenceStatus.Online else PresenceStatus.Offline,
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = JamiTheme.typography.titleSmall,
                color = JamiTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contact.username.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.xxs))
                Text(
                    text = contact.username,
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Single search result item for an existing conversation.
 */
@Composable
private fun ConversationSearchItem(
    conversation: ConversationItem,
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
            displayName = conversation.displayName,
            avatarBytes = conversation.avatarBytes,
            size = AvatarSize.Medium,
            showPresence = true,
            presenceStatus = if (conversation.isOnline) PresenceStatus.Online
            else PresenceStatus.Offline,
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.displayName,
                style = JamiTheme.typography.titleSmall,
                color = JamiTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (conversation.lastMessage.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.xxs))
                Text(
                    text = conversation.lastMessage,
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
