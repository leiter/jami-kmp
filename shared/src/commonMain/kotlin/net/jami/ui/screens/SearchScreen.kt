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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import net.jami.ui.components.actions.JamiFilterChip
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.PresenceStatus
import net.jami.ui.components.inputs.JamiSearchField
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.contracts.ConversationItem
import net.jami.ui.contracts.SearchContract
import net.jami.ui.theme.JamiTheme

/**
 * Search screen for finding conversations and contacts.
 *
 * @param state The search state.
 * @param onAction Dispatches search actions.
 * @param onBack Called when the user navigates back.
 * @param onConversationClick Called when a conversation result is tapped.
 */
@Composable
fun SearchScreen(
    state: SearchContract.State,
    onAction: (SearchContract.Action) -> Unit,
    onBack: () -> Unit,
    onConversationClick: (String) -> Unit,
) {
    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Main,
                onNavigateBack = onBack,
                searchContent = {
                    JamiSearchField(
                        query = state.searchQuery,
                        onQueryChange = { onAction(SearchContract.Action.Search(it)) },
                        placeholder = "Search...",
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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
                        text = "QR Code",
                        onClick = { /* QR scanner */ },
                        leadingIcon = Icons.Default.QrCodeScanner,
                    )
                }
                item {
                    JamiFilterChip(
                        text = "New Group",
                        onClick = { /* New group */ },
                        leadingIcon = Icons.Default.Groups,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = state.conversations,
                    key = { it.id },
                ) { conversation ->
                    SearchResultItem(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
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
            imageUri = conversation.avatarUri,
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
