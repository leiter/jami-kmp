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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.title_share_with
import net.jami.di.getViewModel
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.PresenceStatus
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ConversationItem
import net.jami.ui.viewmodel.ConversationsViewModel
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow

/**
 * Conversation picker shown when the user shares content into Jami from another app.
 *
 * Displays the full conversation list. Tapping a conversation navigates to
 * ChatScreen with [ShareState] content pre-loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePickerScreen(
    onConversationClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<ConversationsViewModel>()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(Res.string.title_share_with))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(state.conversations, key = { it.id }) { conversation ->
                ShareConversationItem(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ShareConversationItem(
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
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        JamiAvatar(
            displayName = conversation.displayName,
            avatarBytes = conversation.avatarBytes,
            size = AvatarSize.Small,
            showPresence = true,
            presenceStatus = if (conversation.isOnline) PresenceStatus.Online
            else PresenceStatus.Offline,
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.displayName,
                style = JamiTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = JamiTheme.colors.onSurface,
            )
            if (conversation.lastMessage.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.xxs))
                Text(
                    text = conversation.lastMessage,
                    style = JamiTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
