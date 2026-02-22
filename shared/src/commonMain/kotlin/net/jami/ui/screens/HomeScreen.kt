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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiBadge
import net.jami.ui.components.content.PresenceStatus
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.contracts.ConversationItem
import net.jami.ui.contracts.HomeContract
import net.jami.ui.theme.JamiTheme

/**
 * Main home screen displaying the conversation list.
 *
 * @param conversationsState The conversations list state (Tier 1 split).
 * @param headerState The header state (Tier 1 split).
 * @param onAction Dispatches home actions.
 * @param onConversationClick Called when a conversation item is tapped.
 * @param onSearchClick Called when the search area is tapped.
 * @param onSettingsClick Called when "Account Settings" is selected from the menu.
 * @param onAppSettingsClick Called when "App Settings" is selected from the menu.
 * @param onAboutClick Called when "About" is selected from the menu.
 * @param onNewConversation Called when the FAB is tapped.
 */
@Composable
fun HomeScreen(
    conversationsState: HomeContract.ConversationsState,
    headerState: HomeContract.HeaderState,
    onAction: (HomeContract.Action) -> Unit,
    onConversationClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onNewConversation: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Main,
                searchContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSearchClick() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        JamiAvatar(
                            displayName = headerState.userDisplayName.ifEmpty { "Me" },
                            size = AvatarSize.Small,
                        )

                        Spacer(Modifier.width(JamiTheme.spacing.m))

                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = JamiTheme.colors.onSurfaceVariant,
                        )

                        Spacer(Modifier.width(JamiTheme.spacing.s))

                        Text(
                            text = "Search conversations...",
                            style = JamiTheme.typography.bodyMedium,
                            color = JamiTheme.colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = JamiTheme.colors.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Account Settings") },
                                onClick = {
                                    menuExpanded = false
                                    onSettingsClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("App Settings") },
                                onClick = {
                                    menuExpanded = false
                                    onAppSettingsClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    menuExpanded = false
                                    onAboutClick()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewConversation,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                text = { Text("New conversation") },
                containerColor = JamiTheme.colors.primary,
                contentColor = JamiTheme.colors.onPrimary,
            )
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (conversationsState.conversations.isEmpty() && !conversationsState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No conversations yet",
                            style = JamiTheme.typography.titleMedium,
                            color = JamiTheme.colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(JamiTheme.spacing.s))
                        Text(
                            text = "Start a new conversation to begin messaging",
                            style = JamiTheme.typography.bodyMedium,
                            color = JamiTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = conversationsState.conversations,
                        key = { it.id },
                    ) { conversation ->
                        ConversationListItem(
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
private fun ConversationListItem(
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

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conversation.displayName,
                    style = JamiTheme.typography.titleSmall,
                    color = JamiTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = formatTimestamp(conversation.timestamp),
                    style = JamiTheme.typography.labelSmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.xxs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conversation.lastMessage,
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(JamiTheme.spacing.s))
                    JamiBadge(count = conversation.unreadCount)
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = net.jami.utils.currentTimeMillis()
    val diffSeconds = (now - timestamp) / 1000
    return when {
        diffSeconds < 60 -> "Now"
        diffSeconds < 3600 -> "${diffSeconds / 60}m"
        diffSeconds < 86400 -> "${diffSeconds / 3600}h"
        diffSeconds < 604800 -> "${diffSeconds / 86400}d"
        else -> "${diffSeconds / 604800}w"
    }
}
