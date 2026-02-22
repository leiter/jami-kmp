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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.jami.ui.components.actions.JamiIconButton
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.inputs.JamiMessageInput
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.contracts.ChatContract
import net.jami.ui.contracts.MessageItem
import net.jami.ui.contracts.MessageType
import net.jami.ui.theme.JamiTheme

/**
 * Chat screen for viewing and sending messages in a conversation.
 *
 * @param topBarState The top bar state (Tier 1 split).
 * @param messagesState The messages state (Tier 1 split).
 * @param inputState The input state (Tier 1 split).
 * @param onAction Dispatches chat actions.
 * @param onBack Called when the user navigates back.
 * @param onCallClick Called when a call button is tapped with (contactId, isVideo).
 * @param onDetailsClick Called when the conversation title/avatar is tapped.
 */
@Composable
fun ChatScreen(
    topBarState: ChatContract.TopBarState,
    messagesState: ChatContract.MessagesState,
    inputState: ChatContract.InputState,
    onAction: (ChatContract.Action) -> Unit,
    onBack: () -> Unit,
    onCallClick: (String, Boolean) -> Unit,
    onDetailsClick: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messagesState.messages.size) {
        if (messagesState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Detail,
                onNavigateBack = onBack,
                title = topBarState.conversationTitle,
                actions = {
                    JamiIconButton(
                        icon = Icons.Default.Call,
                        onClick = { onCallClick(topBarState.conversationTitle, false) },
                        contentDescription = "Audio call",
                        tint = JamiTheme.colors.onSurface,
                    )
                    JamiIconButton(
                        icon = Icons.Default.Videocam,
                        onClick = { onCallClick(topBarState.conversationTitle, true) },
                        contentDescription = "Video call",
                        tint = JamiTheme.colors.onSurface,
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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l),
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(JamiTheme.spacing.xs),
            ) {
                items(
                    items = messagesState.messages.reversed(),
                    key = { it.id },
                ) { message ->
                    when (message.type) {
                        MessageType.System -> SystemMessage(message)
                        else -> ChatBubble(message)
                    }
                }
            }

            JamiMessageInput(
                message = inputState.text,
                onMessageChange = { onAction(ChatContract.Action.UpdateInput(it)) },
                onSend = { onAction(ChatContract.Action.SendMessage) },
            )
        }
    }
}

@Composable
private fun ChatBubble(message: MessageItem) {
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isOutgoing) JamiTheme.colors.messageSent
    else JamiTheme.colors.messageReceived
    val textColor = if (isOutgoing) JamiTheme.colors.onMessageSent
    else JamiTheme.colors.onMessageReceived
    val bubbleShape = RoundedCornerShape(
        topStart = JamiTheme.radius.m,
        topEnd = JamiTheme.radius.m,
        bottomStart = if (isOutgoing) JamiTheme.radius.m else JamiTheme.radius.xs,
        bottomEnd = if (isOutgoing) JamiTheme.radius.xs else JamiTheme.radius.m,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.xxs),
        contentAlignment = alignment,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = bubbleShape,
            color = bubbleColor,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = JamiTheme.spacing.m,
                    vertical = JamiTheme.spacing.s,
                ),
            ) {
                Text(
                    text = message.text,
                    style = JamiTheme.typography.bodyMedium,
                    color = textColor,
                )
                Spacer(Modifier.height(JamiTheme.spacing.xxs))
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = JamiTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun SystemMessage(message: MessageItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message.text,
            style = JamiTheme.typography.bodySmall,
            color = JamiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val totalSeconds = timestamp / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}
