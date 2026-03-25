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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.jami.di.getViewModel
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ChatViewModel
import net.jami.ui.viewmodel.MessageItem
import net.jami.ui.viewmodel.MessageType

/**
 * Chat screen for viewing and sending messages in a conversation.
 *
 * Layout:
 * - Top bar: back arrow, avatar + name, phone and video call buttons
 * - Content: reversed LazyColumn of messages with bubbles
 * - Bottom: message input bar with send button
 *
 * @param conversationId The conversation to display.
 * @param onBack Called when the user navigates back.
 * @param onCallClick Called when a call button is tapped with (contactId, isVideo).
 * @param onDetailsClick Called when the conversation title/avatar is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onCallClick: (String, Boolean) -> Unit,
    onDetailsClick: () -> Unit,
) {
    val viewModel = getViewModel<ChatViewModel>()
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Load conversation on first composition
    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JamiAvatar(
                            displayName = state.conversationTitle,
                            size = AvatarSize.Small,
                        )
                        Spacer(Modifier.width(JamiTheme.spacing.m))
                        Text(
                            text = state.conversationTitle,
                            style = JamiTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    // Audio call button
                    IconButton(
                        onClick = { onCallClick(conversationId, false) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Audio call",
                            tint = JamiTheme.colors.onSurface,
                        )
                    }
                    // Video call button
                    IconButton(
                        onClick = { onCallClick(conversationId, true) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video call",
                            tint = JamiTheme.colors.onSurface,
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
            // Messages list (reversed so newest at bottom)
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
                    items = state.messages.reversed(),
                    key = { it.id },
                ) { message ->
                    when (message.type) {
                        MessageType.System -> SystemMessage(message)
                        else -> ChatBubble(
                            message = message,
                            onDelete = { viewModel.deleteMessage(message.id) },
                            onEdit = { newText -> viewModel.editMessage(message.id, newText) },
                        )
                    }
                }
            }

            // Typing indicator
            if (state.isContactTyping) {
                Text(
                    text = "typing...",
                    style = JamiTheme.typography.labelSmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.xxs,
                    ),
                )
            }

            // Message input bar
            MessageInputBar(
                value = state.inputText,
                onValueChange = { viewModel.updateInput(it) },
                onSend = { viewModel.sendMessage() },
            )
        }
    }
}

/**
 * Chat message bubble. Outgoing messages are right-aligned with primary
 * color, incoming messages are left-aligned with surface variant color.
 * Long-press shows a context menu with Copy, Edit (own messages), and Delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: MessageItem,
    onDelete: () -> Unit = {},
    onEdit: (String) -> Unit = {},
) {
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

    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.xxs),
        contentAlignment = alignment,
    ) {
        Box {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true },
                    ),
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

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.text))
                        showMenu = false
                    },
                )
                if (isOutgoing) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            // For now, re-send the message text as an edit.
                            // A full implementation would open an edit dialog.
                            onEdit(message.text)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/**
 * System message displayed centered with subdued styling.
 */
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

/**
 * Message input bar with text field and send button.
 */
@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = JamiTheme.colors.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = JamiTheme.spacing.m,
                    vertical = JamiTheme.spacing.s,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(JamiTheme.radius.xl),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )

            Spacer(Modifier.width(JamiTheme.spacing.s))

            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (value.isNotBlank()) JamiTheme.colors.primary
                    else JamiTheme.colors.onDisabled,
                )
            }
        }
    }
}

/**
 * Format a message timestamp for display.
 */
private fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val totalSeconds = timestamp / 1000
    val hours = (totalSeconds / 3600) % 24
    val minutes = (totalSeconds / 60) % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}
