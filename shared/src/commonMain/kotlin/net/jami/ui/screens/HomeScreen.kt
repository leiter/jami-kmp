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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiBadge
import net.jami.ui.components.content.PresenceStatus
import net.jami.ui.theme.JamiTheme
import net.jami.ui.components.actions.JamiFilterChip
import net.jami.ui.viewmodel.AccountItem
import net.jami.ui.viewmodel.ConversationFilter
import net.jami.ui.viewmodel.ConversationItem
import net.jami.ui.viewmodel.ConversationsViewModel

/**
 * Main home screen displaying the conversation list.
 *
 * Layout:
 * - Top: Search bar row with current user avatar, search placeholder, overflow menu
 * - Content: LazyColumn of conversation items
 * - FAB: "New conversation" button
 *
 * Uses [ConversationsViewModel] for conversation list state.
 *
 * @param onConversationClick Called when a conversation item is tapped.
 * @param onSearchClick Called when the search area is tapped.
 * @param onSettingsClick Called when "Account Settings" is selected from the menu.
 * @param onAppSettingsClick Called when "App Settings" is selected from the menu.
 * @param onAboutClick Called when "About" is selected from the menu.
 * @param onNewConversation Called when the FAB is tapped.
 * @param onRequestsClick Called when the pending requests banner is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onConversationClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onNewConversation: () -> Unit,
    onRequestsClick: () -> Unit = {},
    onAddAccount: () -> Unit = {},
) {
    val viewModel = getViewModel<ConversationsViewModel>()
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewConversation,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = JamiTheme.colors.onAccent,
                    )
                },
                text = {
                    Text(
                        text = stringResource(Res.string.action_new_conversation),
                        color = JamiTheme.colors.onAccent,
                    )
                },
                containerColor = JamiTheme.colors.accent,
                contentColor = JamiTheme.colors.onAccent,
            )
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.s,
                    ),
                shape = RoundedCornerShape(JamiTheme.radius.full),
                color = JamiTheme.colors.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSearchClick() }
                        .padding(
                            start = JamiTheme.spacing.m,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Current user avatar — tapping opens account picker
                    Box(
                        modifier = Modifier.clickable(
                            onClick = { showAccountPicker = true },
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        )
                    ) {
                        JamiAvatar(
                            displayName = "Me",
                            avatarBytes = state.currentAccountAvatarBytes,
                            size = AvatarSize.Small,
                            showPresence = true,
                            presenceStatus = if (state.isAccountOnline) PresenceStatus.Online
                            else PresenceStatus.Offline,
                        )
                    }

                    Spacer(Modifier.width(JamiTheme.spacing.m))

                    // Search placeholder
                    Text(
                        text = stringResource(Res.string.placeholder_search),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )

                    // Overflow menu
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(Res.string.content_desc_menu),
                                tint = JamiTheme.colors.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.screen_title_account_settings)) },
                                onClick = {
                                    menuExpanded = false
                                    onSettingsClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.menu_app_settings)) },
                                onClick = {
                                    menuExpanded = false
                                    onAppSettingsClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.screen_title_about)) },
                                onClick = {
                                    menuExpanded = false
                                    onAboutClick()
                                },
                            )
                        }
                    }
                }
            }

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.s),
            ) {
                JamiFilterChip(
                    text = stringResource(Res.string.filter_all),
                    selected = state.activeFilter == ConversationFilter.ALL,
                    onClick = { viewModel.setFilter(ConversationFilter.ALL) },
                )
                JamiFilterChip(
                    text = stringResource(Res.string.filter_unread),
                    selected = state.activeFilter == ConversationFilter.UNREAD,
                    onClick = { viewModel.setFilter(ConversationFilter.UNREAD) },
                )
                JamiFilterChip(
                    text = stringResource(Res.string.filter_groups),
                    selected = state.activeFilter == ConversationFilter.GROUPS,
                    onClick = { viewModel.setFilter(ConversationFilter.GROUPS) },
                )
            }

            // Pending requests banner
            if (state.pendingRequests > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestsClick() }
                        .padding(
                            horizontal = JamiTheme.spacing.l,
                            vertical = JamiTheme.spacing.xs,
                        ),
                    color = JamiTheme.colors.accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(JamiTheme.radius.m),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = JamiTheme.spacing.m,
                                vertical = JamiTheme.spacing.m,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${state.pendingRequests} pending invitation${if (state.pendingRequests > 1) "s" else ""}",
                            style = JamiTheme.typography.titleSmall,
                            color = JamiTheme.colors.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = JamiTheme.colors.primary,
                        )
                    }
                }
            }

            // Conversation list
            if (state.conversations.isEmpty() && !state.isLoading) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.home_no_conversation_title),
                            style = JamiTheme.typography.titleMedium,
                            color = JamiTheme.colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(JamiTheme.spacing.s))
                        Text(
                            text = stringResource(Res.string.home_no_conversation_hint),
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
                        items = state.conversations,
                        key = { it.id },
                    ) { conversation ->
                        val dismissState = rememberSwipeToDismissBoxState()

                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                               // viewModel.removeConversation(conversation.id)
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart -> JamiTheme.colors.error
                                        else -> JamiTheme.colors.surface
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = JamiTheme.spacing.l),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(Res.string.ic_delete_menu),
                                            tint = JamiTheme.colors.onError,
                                        )
                                    }
                                }
                            },
                            enableDismissFromStartToEnd = false,
                        ) {
                            Surface(color = JamiTheme.colors.surface) {
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
    }

    // Account picker bottom sheet
    if (showAccountPicker) {
        ModalBottomSheet(
            onDismissRequest = { showAccountPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = JamiTheme.colors.surface,
        ) {
            AccountPickerSheet(
                accounts = state.accounts,
                onAccountClick = { accountId ->
                    viewModel.switchAccount(accountId)
                    showAccountPicker = false
                },
                onAddAccount = {
                    showAccountPicker = false
                    onAddAccount()
                },
            )
        }
    }
}

@Composable
private fun AccountPickerSheet(
    accounts: List<AccountItem>,
    onAccountClick: (String) -> Unit,
    onAddAccount: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = JamiTheme.spacing.xl),
    ) {
        Text(
            text = stringResource(Res.string.account_selection),
            style = JamiTheme.typography.titleMedium,
            color = JamiTheme.colors.onSurface,
            modifier = Modifier.padding(
                horizontal = JamiTheme.spacing.l,
                vertical = JamiTheme.spacing.m,
            ),
        )

        HorizontalDivider(color = JamiTheme.colors.outline.copy(alpha = 0.3f))

        accounts.forEach { account ->
            AccountPickerRow(
                account = account,
                onClick = { onAccountClick(account.accountId) },
            )
            HorizontalDivider(color = JamiTheme.colors.outline.copy(alpha = 0.15f))
        }

        // Add account row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddAccount)
                .padding(
                    horizontal = JamiTheme.spacing.l,
                    vertical = JamiTheme.spacing.m,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = JamiTheme.colors.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = JamiTheme.colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(JamiTheme.spacing.m))
            Text(
                text = stringResource(Res.string.account_create_title),
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.primary,
            )
        }
    }
}

@Composable
private fun AccountPickerRow(
    account: AccountItem,
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
            displayName = account.displayName,
            avatarBytes = account.avatarBytes,
            size = AvatarSize.Small,
            showPresence = true,
            presenceStatus = if (account.isOnline) PresenceStatus.Online else PresenceStatus.Offline,
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (account.subtitle.isNotEmpty()) {
                Text(
                    text = account.subtitle,
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (account.isCurrentAccount) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = JamiTheme.colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Single conversation item in the list.
 *
 * Displays avatar with presence indicator, display name, timestamp,
 * last message preview, and unread badge.
 */
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
        // Avatar with presence dot
        JamiAvatar(
            displayName = conversation.displayName,
            avatarBytes = conversation.avatarBytes,
            size = AvatarSize.Small,
            showPresence = true,
            presenceStatus = if (conversation.isOnline) PresenceStatus.Online
            else PresenceStatus.Offline,
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        // Name on row 1; timestamp + message on row 2 — mirrors the official Android layout.
        // Bold is driven by isRead (false = unread = bold), matching SmartListViewHolder behaviour.
        val isUnread = !conversation.isRead
        val subtextColor = if (isUnread) JamiTheme.colors.onSurface
                           else JamiTheme.colors.onSurfaceVariant
        val subtextWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal

        Column(
            modifier = Modifier.weight(1f),
        ) {
            // Row 1: contact name only
            Text(
                text = conversation.displayName,
                style = JamiTheme.typography.titleSmall,
                fontWeight = if (isUnread) FontWeight.Bold else JamiTheme.typography.titleSmall.fontWeight,
                color = JamiTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(JamiTheme.spacing.xxs))

            // Row 2: [timestamp]  [message preview]  [badge]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val timestamp = formatTimestamp(conversation.timestamp)
                if (timestamp.isNotEmpty()) {
                    Text(
                        text = timestamp,
                        style = JamiTheme.typography.bodyMedium,
                        fontWeight = subtextWeight,
                        color = subtextColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(JamiTheme.spacing.xs))
                }
                Text(
                    text = conversation.lastMessage,
                    style = JamiTheme.typography.bodyMedium,
                    fontWeight = subtextWeight,
                    color = subtextColor,
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

/**
 * Format a timestamp (epoch millis) for display in the conversation list.
 */
private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    // Simple relative time formatting
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
