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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import net.jami.model.Contact
import net.jami.ui.components.actions.JamiFilterChip
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.PresenceStatus
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ContactItem
import net.jami.ui.viewmodel.NewConversationViewModel

/**
 * Search screen for finding conversations and contacts.
 *
 * Uses [NewConversationViewModel] to search both local contacts and
 * the nameserver for remote results.
 *
 * @param onBack Called when the user navigates back.
 * @param onConversationClick Called when a conversation result is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onConversationClick: (String) -> Unit,
) {
    val viewModel = getViewModel<NewConversationViewModel>()
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.search(it) },
                        placeholder = { Text(stringResource(Res.string.placeholder_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                        onClick = { /* QR scanner */ },
                        leadingIcon = Icons.Default.QrCodeScanner,
                    )
                }
                item {
                    JamiFilterChip(
                        text = stringResource(Res.string.action_new_group),
                        onClick = { /* New group */ },
                        leadingIcon = Icons.Default.Groups,
                    )
                }
            }

            // Search results list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = state.searchResults,
                    key = { it.uri },
                ) { contact ->
                    SearchResultItem(
                        contact = contact,
                        onClick = {
                            viewModel.selectContact(contact)
                            coroutineScope.launch {
                                val conversationId = viewModel.createConversation()
                                if (conversationId != null) {
                                    onConversationClick(conversationId)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Single search result item displaying avatar, name, and username.
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
