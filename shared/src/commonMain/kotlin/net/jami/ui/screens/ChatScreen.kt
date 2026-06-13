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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.jami.model.ContactEvent
import net.jami.model.Interaction
import net.jami.ui.viewmodel.DeliveryStatus
import net.jami.ui.viewmodel.ReactionGroup
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallMissedOutgoing
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.jami.ui.utils.extractVideoThumbnail
import net.jami.ui.utils.toImageBitmap
import net.jami.utils.FileUtils
import net.jami.utils.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.PresenceStatus
import net.jami.ui.platform.AppPermission
import net.jami.ui.platform.FilePickerEffect
import net.jami.ui.platform.ImageCaptureEffect
import net.jami.ui.platform.PermissionRequesterEffect
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
    onShareLocation: () -> Unit = {},
    onImageClick: (filePath: String) -> Unit = {},
    onVideoClick: (filePath: String, fileName: String) -> Unit = { _, _ -> },
) {
    val viewModel = getViewModel<ChatViewModel>()
    val state by viewModel.state.collectAsState()
    val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()
    val listState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Link preview setting
    val settingsRepository: net.jami.repository.SettingsRepository = org.koin.compose.koinInject()
    val privacySettings by settingsRepository.privacySettings.collectAsState()
    val showLinkPreviews = privacySettings.showLinkPreviews

    // Edit mode state — set when user taps "Edit" on a message
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessageText by remember { mutableStateOf("") }

    // Camera permission and capture state
    var requestCameraPermission by remember { mutableStateOf(false) }
    var showImageCapture by remember { mutableStateOf(false) }

    // File picker state
    var showFilePicker by remember { mutableStateOf(false) }
    var filePickerMimeTypes by remember { mutableStateOf(listOf("*/*")) }

    // Location permission state
    var requestLocationPermission by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(false) }

    // Load conversation on first composition
    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
        // Pre-fill shared text immediately (does not need conversation to be fully loaded)
        net.jami.ui.navigation.ShareState.consumeText()?.let { text ->
            viewModel.updateInput(text)
        }
    }

    // Send pending shared files once the conversation is ready (isLoading transitions to false)
    val isLoading = state.isLoading
    var sharePending by remember { mutableStateOf(net.jami.ui.navigation.ShareState.pendingFilePaths.isNotEmpty()) }
    LaunchedEffect(isLoading) {
        if (!isLoading && sharePending) {
            sharePending = false
            val files = net.jami.ui.navigation.ShareState.consumeFiles()
            for (path in files) {
                viewModel.sendFile(path)
            }
        }
    }

    // Mark conversation as no longer visible when leaving the screen
    DisposableEffect(conversationId) {
        onDispose { viewModel.onLeave() }
    }

    // Scroll to bottom only when a new message is appended (newest ID changes),
    // not when older history is prepended by loadMore().
    val newestMessageId = state.messages.lastOrNull { it.type != MessageType.DateSeparator }?.id
    LaunchedEffect(newestMessageId) {
        if (newestMessageId != null && !state.isSearchActive) {
            listState.animateScrollToItem(0)
        }
    }

    // Auto-focus search field when search mode opens
    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    // Scroll to and briefly highlight a message after closing search
    LaunchedEffect(state.highlightedMessageId) {
        val targetId = state.highlightedMessageId ?: return@LaunchedEffect
        val idx = state.messages.reversed().indexOfFirst { it.id == targetId }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    // Pagination: load more when near the top (end of reversed list) and not currently loading.
    // Use snapshotFlow to reliably observe scroll position and trigger history loading.
    LaunchedEffect(listState, state.hasMoreHistory, state.isLoadingMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            (totalItems > 0 && lastVisibleItem >= totalItems - 5)
        }.collect { nearTop ->
            if (nearTop && !state.isLoadingMore && state.hasMoreHistory) {
                Log.d("ChatScreen", "Pagination triggered: loading more history")
                viewModel.loadMore()
            }
        }
    }

    // Handle camera permission request
    PermissionRequesterEffect(
        permission = AppPermission.Camera,
        request = requestCameraPermission,
        onResult = { granted ->
            requestCameraPermission = false
            if (granted) {
                showImageCapture = true
            }
        }
    )

    // Handle image capture
    ImageCaptureEffect(
        show = showImageCapture,
        onImageCaptured = { path ->
            showImageCapture = false
            if (path != null) {
                viewModel.sendImage(path)
            }
        }
    )

    // Handle file picker
    FilePickerEffect(
        show = showFilePicker,
        mimeTypes = filePickerMimeTypes,
        onFilePicked = { path ->
            showFilePicker = false
            if (path != null) {
                viewModel.sendFile(path)
            }
        }
    )

    // Handle location permission request
    PermissionRequesterEffect(
        permission = AppPermission.Location,
        request = requestLocationPermission,
        onResult = { granted ->
            requestLocationPermission = false
            locationPermissionGranted = granted
            if (granted) {
                // Navigate to location sharing screen
                onShareLocation()
            }
        }
    )

    var overflowMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSearchActive) {
                        // Search input replaces the normal title
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.searchConversation(it) },
                            placeholder = { Text(stringResource(Res.string.conversation_search_hint)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .combinedClickable(onClick = onDetailsClick)
                                .padding(vertical = JamiTheme.spacing.xxs),
                        ) {
                            JamiAvatar(
                                displayName = state.conversationTitle,
                                avatarBytes = state.contactAvatarBytes,
                                size = AvatarSize.Small,
                                showPresence = true,
                                presenceStatus = if (state.isContactOnline) PresenceStatus.Online else PresenceStatus.Offline,
                            )
                            Spacer(Modifier.width(JamiTheme.spacing.m))
                            Text(
                                text = state.conversationTitle,
                                style = JamiTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (state.isSearchActive) ({ viewModel.closeSearch() }) else onBack) {
                        Icon(
                            imageVector = if (state.isSearchActive) Icons.AutoMirrored.Filled.ArrowBack
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_desc_back),
                        )
                    }
                },
                actions = {
                    if (state.isSearchActive) {
                        // Only a close button in search mode
                        IconButton(onClick = { viewModel.closeSearch() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.content_desc_back),
                                tint = JamiTheme.colors.onSurface,
                            )
                        }
                    } else {
                        // Normal mode: call buttons + overflow
                        IconButton(onClick = { onCallClick(state.peerUri.ifEmpty { conversationId }, false) }) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = stringResource(Res.string.content_desc_audio_call),
                                tint = JamiTheme.colors.onSurface,
                            )
                        }
                        IconButton(onClick = { onCallClick(state.peerUri.ifEmpty { conversationId }, true) }) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = stringResource(Res.string.content_desc_video_call),
                                tint = JamiTheme.colors.onSurface,
                            )
                        }
                        Box {
                            IconButton(onClick = { overflowMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(Res.string.menu_overflow),
                                    tint = JamiTheme.colors.onSurface,
                                )
                            }
                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.conversation_details)) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        onDetailsClick()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.conversation_search_hint)) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        viewModel.openSearch()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.conversation_action_history_clear)) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        viewModel.clearHistory()
                                    },
                                )
                            }
                        }
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
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            if (state.isSearchActive) {
                // ── Search mode: show results instead of message list ──
                SearchResultsPanel(
                    results = state.searchResults,
                    query = state.searchQuery,
                    modifier = Modifier.weight(1f),
                    onResultClick = { viewModel.scrollToMessage(it) },
                )
            } else {
                // ── Location sharing indicator ──
                state.contactSharingLocation?.let { locationInfo ->
                    LocationSharingBanner(
                        contactName = locationInfo.displayName,
                        onClick = onShareLocation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Normal mode: scrollable message list ──
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
                            MessageType.DateSeparator -> DateSeparatorItem(message.text)
                            MessageType.System        -> SystemMessage(message)
                            MessageType.Call          -> CallMessage(message)
                            MessageType.Transfer      -> FileTransferMessage(
                                message = message,
                                onAccept = { viewModel.acceptTransfer(message.id, message.fileId ?: "") },
                                onCancel = { viewModel.cancelTransfer(message.id, message.fileId ?: "") },
                                onImageClick = onImageClick,
                                onVideoClick = onVideoClick,
                            )
                            else -> ChatBubble(
                                message = message,
                                isHighlighted = message.id == state.highlightedMessageId,
                                showLinkPreviews = showLinkPreviews,
                                onDelete = { viewModel.deleteMessage(message.id) },
                                onEdit = { originalText ->
                                    editingMessageId = message.id
                                    editingMessageText = originalText
                                    viewModel.updateInput(originalText)
                                },
                                onReact = { emoji -> viewModel.sendReaction(message.id, emoji) },
                            )
                        }
                    }

                    // Loading indicator at top (end of reversed list)
                    if (state.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(JamiTheme.spacing.m),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                // Typing indicator
                if (state.isContactTyping) {
                    Text(
                        text = stringResource(Res.string.conversation_typing),
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = JamiTheme.spacing.l,
                            vertical = JamiTheme.spacing.xxs,
                        ),
                    )
                }
            }

            // Audio recording indicator — shown while recording
            if (isRecordingAudio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.m, vertical = JamiTheme.spacing.s),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Recording audio…",
                        style = JamiTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f).padding(horizontal = JamiTheme.spacing.s),
                    )
                    TextButton(onClick = { viewModel.cancelAudioRecording() }) {
                        Text("Cancel")
                    }
                    Button(onClick = { viewModel.stopAudioRecording() }) {
                        Text("Send")
                    }
                }
            }

            // Editing banner — shown while the user is editing an existing message
            if (editingMessageId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = JamiTheme.colors.primary,
                    )
                    Spacer(Modifier.width(JamiTheme.spacing.s))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.menu_message_edit),
                            style = JamiTheme.typography.labelSmall,
                            color = JamiTheme.colors.primary,
                        )
                        Text(
                            text = editingMessageText,
                            style = JamiTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            editingMessageId = null
                            editingMessageText = ""
                            viewModel.updateInput("")
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Message input bar (hidden during search; replaced by call-only notice for SIP)
            if (!state.isSearchActive && state.isLegacy) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(JamiTheme.spacing.m),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.chat_sip_call_only),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
            }
            if (!state.isSearchActive && !state.isLegacy) {
                MessageInputBar(
                    value = state.inputText,
                    onValueChange = { viewModel.updateInput(it) },
                    onSend = {
                        val editId = editingMessageId
                        if (editId != null) {
                            viewModel.editMessage(editId, state.inputText)
                            editingMessageId = null
                            editingMessageText = ""
                        } else {
                            viewModel.sendMessage()
                        }
                    },
                    onSendEmoji = { viewModel.sendEmoji() },
                    onTakePicture = {
                        // Check if we have camera permission
                        if (viewModel.hasCameraPermission()) {
                            // Permission granted, show camera
                            showImageCapture = true
                        } else {
                            // Request permission
                            requestCameraPermission = true
                        }
                    },
                    onSelectMedia = {
                        // Open file picker for images and videos
                        filePickerMimeTypes = listOf("image/*", "video/*")
                        showFilePicker = true
                    },
                    onSendFile = {
                        // Open file picker for any file type
                        filePickerMimeTypes = listOf("*/*")
                        showFilePicker = true
                    },
                    onShareLocation = {
                        // Check if we have location permission
                        if (viewModel.hasLocationPermission()) {
                            // Permission granted - navigate to location sharing
                            onShareLocation()
                        } else {
                            // Request permission
                            requestLocationPermission = true
                        }
                    },
                    onStartAudioRecording = { viewModel.startAudioRecording() },
                    onVideoRecordingUnsupported = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Video recording coming soon")
                        }
                    },
                    onChatExtensionsUnsupported = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Plugins not yet supported")
                        }
                    },
                )
            } // end !isSearchActive && !isLegacy
        }
    }
}

/**
 * Chat message bubble. Outgoing messages are right-aligned with primary
 * color, incoming messages are left-aligned with surface variant color.
 *
 * The timestamp is placed inline with the message text (same line for short
 * messages, next to the last line for multi-line messages) using the
 * transparent-spacer technique: an invisible copy of the timestamp is appended
 * to the message text so that the text wraps appropriately, and the visible
 * timestamp is overlaid at Alignment.BottomEnd — matching the official
 * MessageBubble.kt behaviour.
 *
 * Long-press shows a context menu with Copy, Edit (own messages), and Delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: MessageItem,
    isHighlighted: Boolean = false,
    showLinkPreviews: Boolean = false,
    onDelete: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onReact: (String) -> Unit = {},
) {
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isOutgoing) JamiTheme.colors.messageSent
    else JamiTheme.colors.messageReceived
    val textColor = if (isOutgoing) JamiTheme.colors.onMessageSent
    else JamiTheme.colors.onMessageReceived
    val timeColor = textColor.copy(alpha = 0.7f)
    val bubbleShape = RoundedCornerShape(
        topStart = JamiTheme.radius.m,
        topEnd = JamiTheme.radius.m,
        bottomStart = if (isOutgoing) JamiTheme.radius.m else JamiTheme.radius.xs,
        bottomEnd = if (isOutgoing) JamiTheme.radius.xs else JamiTheme.radius.m,
    )

    var showMenu by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val timeText = formatMessageTime(message.timestamp)
    val timeFontSize = JamiTheme.typography.labelSmall.fontSize

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.xxs)
            .then(
                // Add horizontal padding to create more visual separation
                if (isOutgoing) {
                    // Outgoing: pad left side more to push message to the right
                    Modifier.padding(start = 56.dp, end = 8.dp)
                } else {
                    // Incoming: pad right side more to keep message on the left
                    Modifier.padding(start = 8.dp, end = 56.dp)
                }
            ),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
    ) {
        // Show sender name for incoming messages
        if (!isOutgoing && message.author.isNotEmpty()) {
            Text(
                text = message.author,
                style = JamiTheme.typography.labelSmall,
                color = JamiTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = JamiTheme.spacing.m,
                    bottom = JamiTheme.spacing.xxs,
                ),
            )
        }

        Box {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .then(
                        if (isHighlighted) Modifier.border(2.dp, JamiTheme.colors.accent, bubbleShape)
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true },
                    ),
                shape = bubbleShape,
                color = bubbleColor,
            ) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = JamiTheme.spacing.m,
                        vertical = JamiTheme.spacing.s,
                    ),
                ) {
                    // Message text with an invisible trailing spacer whose width
                    // matches the timestamp (+ checkmark for outgoing) so the last
                    // text line always has room for the overlay next to it.
                    Text(
                        text = buildAnnotatedString {
                            append(message.text)
                            withStyle(SpanStyle(color = Color.Transparent, fontSize = timeFontSize)) {
                                if (isOutgoing) append("  $timeText  ") else append("  $timeText")
                            }
                        },
                        style = JamiTheme.typography.bodyMedium,
                        color = textColor,
                    )
                    // Timestamp (+ checkmark for outgoing) overlaid at bottom-right.
                    if (isOutgoing) {
                        val (checkIcon, checkTint) = when (message.deliveryStatus) {
                            DeliveryStatus.READ      -> Icons.Default.DoneAll to JamiTheme.colors.primary
                            DeliveryStatus.DELIVERED -> Icons.Default.DoneAll to timeColor
                            DeliveryStatus.SENDING   -> Icons.Default.Done    to timeColor
                        }
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = timeText,
                                style = JamiTheme.typography.labelSmall,
                                color = timeColor,
                            )
                            Icon(
                                imageVector = checkIcon,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = checkTint,
                            )
                        }
                    } else {
                        Text(
                            text = timeText,
                            style = JamiTheme.typography.labelSmall,
                            color = timeColor,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }

            // "(edited)" label — shown beneath the bubble when this message has been edited
            if (message.isEdited) {
                Text(
                    text = stringResource(Res.string.edited_message_label),
                    style = JamiTheme.typography.labelSmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                    textAlign = if (isOutgoing) TextAlign.End else TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
            }

            // Link preview card — shown below the bubble when the message contains a URL
            // and the user has link previews enabled
            if (showLinkPreviews && message.type == net.jami.ui.viewmodel.MessageType.Text
                && message.text.isNotEmpty()) {
                val urls = remember(message.text) {
                    net.jami.ui.utils.extractUrls(message.text)
                }
                val firstUrl = urls.firstOrNull()
                if (firstUrl != null) {
                    var linkPreview by remember(firstUrl) { mutableStateOf<net.jami.ui.utils.LinkPreview?>(null) }
                    var previewLoading by remember(firstUrl) { mutableStateOf(true) }

                    LaunchedEffect(firstUrl) {
                        linkPreview = net.jami.ui.utils.fetchLinkPreview(firstUrl)
                        previewLoading = false
                    }

                    if (!previewLoading && linkPreview != null) {
                        LinkPreviewCard(
                            preview = linkPreview!!,
                            bubbleColor = bubbleColor,
                            textColor = textColor,
                            modifier = Modifier
                                .padding(top = JamiTheme.spacing.xxs)
                                .then(if (isOutgoing) Modifier else Modifier),
                        )
                    }
                }
            }

            // Context menu (long-press)
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("React") },
                    onClick = {
                        showMenu = false
                        showEmojiPicker = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.menu_item_copy)) },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.text))
                        showMenu = false
                    },
                )
                if (isOutgoing) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.menu_item_edit)) },
                        onClick = {
                            showMenu = false
                            onEdit(message.text)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.ic_delete_menu)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }

            // Emoji quick-reaction picker
            DropdownMenu(
                expanded = showEmojiPicker,
                onDismissRequest = { showEmojiPicker = false },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf("👍", "❤️", "😂", "😮", "😢", "👏").forEach { emoji ->
                        Text(
                            text = emoji,
                            modifier = Modifier
                                .clickable {
                                    onReact(emoji)
                                    showEmojiPicker = false
                                }
                                .padding(6.dp),
                            fontSize = 22.sp,
                        )
                    }
                }
            }
        }

        // Reaction pills below the bubble
        if (message.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                message.reactions.forEach { reaction ->
                    ReactionPill(
                        reaction = reaction,
                        onClick = { onReact(reaction.emoji) },
                    )
                }
            }
        }
    }
}

/**
 * System message displayed centered with subdued styling.
 * For contact events (member joined/left/etc.), the localized text is resolved
 * here from [MessageItem.contactEventType] so the ViewModel stays string-free.
 */
@Composable
private fun SystemMessage(message: MessageItem) {
    val displayText = when (message.contactEventType) {
        ContactEvent.Event.ADDED     -> stringResource(Res.string.conversation_contact_added, message.author)
        ContactEvent.Event.INVITED   -> stringResource(Res.string.conversation_contact_invited, message.author)
        ContactEvent.Event.REMOVED   -> stringResource(Res.string.conversation_contact_left, message.author)
        ContactEvent.Event.BLOCKED   -> stringResource(Res.string.conversation_contact_blocked, message.author)
        ContactEvent.Event.UNBLOCKED -> stringResource(Res.string.conversation_contact_unblocked, message.author)
        ContactEvent.Event.INCOMING_REQUEST -> stringResource(Res.string.hist_invitation_received)
        ContactEvent.Event.UNKNOWN, null -> message.text
    }
    if (displayText.isEmpty()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText,
            style = JamiTheme.typography.bodySmall,
            color = JamiTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Date separator badge shown between messages from different calendar days.
 */
@Composable
private fun DateSeparatorItem(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(JamiTheme.radius.m),
            color = JamiTheme.colors.surfaceVariant,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(
                    horizontal = JamiTheme.spacing.m,
                    vertical = JamiTheme.spacing.xxs,
                ),
                style = JamiTheme.typography.labelSmall,
                color = JamiTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Call history item: centered row with phone icon, direction/missed label, and duration.
 * Missed calls are shown in error (red) colour; answered calls use directional icons.
 */
@Composable
private fun CallMessage(message: MessageItem) {
    val isMissed = message.isMissed
    val isOutgoing = message.isOutgoing

    val icon = when {
        isMissed && !isOutgoing -> Icons.Default.CallMissed
        isMissed && isOutgoing  -> Icons.Default.CallMissedOutgoing
        !isMissed && isOutgoing -> Icons.Default.CallMade
        else                    -> Icons.Default.CallReceived
    }
    val iconColor = when {
        isMissed              -> JamiTheme.colors.error
        !isMissed && isOutgoing -> JamiTheme.colors.primary
        else                  -> JamiTheme.colors.onSurface
    }
    val textColor = if (isMissed) JamiTheme.colors.error else JamiTheme.colors.onSurfaceVariant

    val label = when {
        isMissed && !isOutgoing  -> stringResource(Res.string.notif_missed_incoming_call)
        isMissed && isOutgoing   -> stringResource(Res.string.notif_missed_outgoing_call)
        !isMissed && !isOutgoing -> stringResource(Res.string.notif_incoming_call)
        else                     -> stringResource(Res.string.notif_outgoing_call)
    }
    val durationText = if (message.callDuration > 0L) {
        val totalSecs = message.callDuration / 1000L
        val mins = (totalSecs / 60).toString().padStart(2, '0')
        val secs = (totalSecs % 60).toString().padStart(2, '0')
        " — " + stringResource(Res.string.call_duration, "$mins:$secs")
    } else ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "$label$durationText",
                style = JamiTheme.typography.bodySmall,
                color = textColor,
            )
            Text(
                text = formatMessageTime(message.timestamp),
                style = JamiTheme.typography.labelSmall,
                color = JamiTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Returns a human-readable file size string (KMP-safe, no String.format).
 */
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000L -> "${bytes / 1_000_000L}.${(bytes % 1_000_000L) / 100_000L} MB"
    bytes >= 1_000L     -> "${bytes / 1_000L} KB"
    else                -> "$bytes B"
}

/**
 * File transfer card. Mirrors item_conv_file_peer/me.xml from the Android client.
 *
 * Layout (inside a bubble aligned left/right like a text bubble):
 *   [icon]  filename (bold, single line)
 *           size / status text
 *   [download button]        (only for AWAITING_HOST / FILE_AVAILABLE)
 *   ──── progress bar ────   (only during TRANSFER_ONGOING)
 *   timestamp (bottom-end overlay)
 */
@Composable
private fun FileTransferMessage(
    message: MessageItem,
    onAccept: () -> Unit = {},
    onCancel: () -> Unit = {},
    onImageClick: (filePath: String) -> Unit = {},
    onVideoClick: (filePath: String, fileName: String) -> Unit = { _, _ -> },
) {
    // Asynchronously load image bytes for completed picture transfers
    var imageBitmap by remember(message.destinationPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(message.destinationPath, message.transferStatus) {
        if (message.isPicture &&
            message.transferStatus == Interaction.TransferStatus.TRANSFER_FINISHED &&
            message.destinationPath != null) {
            val bytes = withContext(Dispatchers.Default) {
                FileUtils.readBytes(message.destinationPath)
            }
            imageBitmap = bytes?.toImageBitmap()
        } else {
            imageBitmap = null
        }
    }

    // Asynchronously extract first-frame thumbnail for completed video transfers
    var videoThumbnail by remember(message.destinationPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(message.destinationPath, message.transferStatus) {
        if (message.isVideo &&
            message.transferStatus == Interaction.TransferStatus.TRANSFER_FINISHED &&
            message.destinationPath != null) {
            videoThumbnail = extractVideoThumbnail(message.destinationPath)
        } else {
            videoThumbnail = null
        }
    }
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isOutgoing) JamiTheme.colors.messageSent
                      else JamiTheme.colors.messageReceived
    val contentColor = if (isOutgoing) JamiTheme.colors.onMessageSent
                       else JamiTheme.colors.onMessageReceived
    val timeColor = contentColor.copy(alpha = 0.7f)
    val bubbleShape = RoundedCornerShape(
        topStart = JamiTheme.radius.m,
        topEnd = JamiTheme.radius.m,
        bottomStart = if (isOutgoing) JamiTheme.radius.m else JamiTheme.radius.xs,
        bottomEnd = if (isOutgoing) JamiTheme.radius.xs else JamiTheme.radius.m,
    )

    val status = message.transferStatus
    val isError = status.isError
    val isOngoing = status == Interaction.TransferStatus.TRANSFER_ONGOING
    val showDownload = !isOutgoing &&
        (status == Interaction.TransferStatus.TRANSFER_AWAITING_HOST ||
         status == Interaction.TransferStatus.FILE_AVAILABLE)

    val statusText = when (status) {
        Interaction.TransferStatus.TRANSFER_CREATED        -> "Initializing…"
        Interaction.TransferStatus.TRANSFER_AWAITING_PEER  -> "Waiting for peer…"
        Interaction.TransferStatus.TRANSFER_AWAITING_HOST,
        Interaction.TransferStatus.FILE_AVAILABLE          -> "Tap to download"
        Interaction.TransferStatus.TRANSFER_ONGOING        ->
            "${formatFileSize(message.bytesProgress)} / ${formatFileSize(message.totalSize)}"
        Interaction.TransferStatus.TRANSFER_FINISHED       -> formatFileSize(message.totalSize)
        Interaction.TransferStatus.TRANSFER_CANCELED       -> "Canceled"
        Interaction.TransferStatus.TRANSFER_ERROR,
        Interaction.TransferStatus.FAILURE                 -> "Transfer failed"
        Interaction.TransferStatus.TRANSFER_UNJOINABLE_PEER -> "Peer unreachable"
        Interaction.TransferStatus.TRANSFER_TIMEOUT_EXPIRED -> "Timed out"
        Interaction.TransferStatus.FILE_REMOVED            -> "File deleted"
        else                                               -> ""
    }

    val timeText = formatMessageTime(message.timestamp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.xxs),
        contentAlignment = alignment,
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 160.dp, max = 280.dp),
            color = bubbleColor,
            shape = bubbleShape,
        ) {
            Column(modifier = Modifier.padding(JamiTheme.spacing.s)) {
                // ── Main row: icon + info + optional download button ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.xs),
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Warning else Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = if (isError) JamiTheme.colors.error else contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.text,
                            style = JamiTheme.typography.bodyMedium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        if (statusText.isNotEmpty()) {
                            Text(
                                text = statusText,
                                style = JamiTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.8f),
                            )
                        }
                    }
                    if (showDownload) {
                        IconButton(
                            onClick = onAccept,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Download",
                                tint = contentColor,
                            )
                        }
                    }
                }

                // ── Image preview (completed pictures only) — tap to open full-screen viewer ──
                if (imageBitmap != null) {
                    Spacer(modifier = Modifier.height(JamiTheme.spacing.xs))
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = message.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(JamiTheme.radius.s))
                            .clickable {
                                message.destinationPath?.let { onImageClick(it) }
                            },
                        contentScale = ContentScale.Crop,
                    )
                }

                // ── Video preview (completed videos only) — tap to open player ──
                if (message.isVideo &&
                    message.transferStatus == Interaction.TransferStatus.TRANSFER_FINISHED &&
                    message.destinationPath != null
                ) {
                    Spacer(modifier = Modifier.height(JamiTheme.spacing.xs))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(JamiTheme.radius.s))
                            .background(Color.Black)
                            .clickable { onVideoClick(message.destinationPath, message.text) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (videoThumbnail != null) {
                            Image(
                                bitmap = videoThumbnail!!,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        // Play icon overlay
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play video",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }

                // ── Progress bar (TRANSFER_ONGOING only) ──
                if (isOngoing) {
                    Spacer(modifier = Modifier.height(JamiTheme.spacing.xs))
                    if (message.totalSize > 0L) {
                        LinearProgressIndicator(
                            progress = { (message.bytesProgress.toFloat() / message.totalSize.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = contentColor,
                            trackColor = contentColor.copy(alpha = 0.3f),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = contentColor,
                            trackColor = contentColor.copy(alpha = 0.3f),
                        )
                    }
                }

                // ── Timestamp (right-aligned) ──
                Spacer(modifier = Modifier.height(JamiTheme.spacing.xxs))
                Text(
                    text = timeText,
                    style = JamiTheme.typography.labelSmall,
                    color = timeColor,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

/**
 * Message input bar with text field and send button.
 * Matches jami-android-client appearance with card container and action buttons.
 */
@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendEmoji: () -> Unit = {},
    onTakePicture: () -> Unit = {},
    onSelectMedia: () -> Unit = {},
    onSendFile: () -> Unit = {},
    onShareLocation: () -> Unit = {},
    onStartAudioRecording: () -> Unit = {},
    onVideoRecordingUnsupported: () -> Unit = {},
    onChatExtensionsUnsupported: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

    // Outer container with background
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = JamiTheme.colors.surface,
    ) {
        // Card container matching Android's cvMessageInput
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = JamiTheme.colors.surface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Menu button (3 dots) with dropdown
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Message options",
                            tint = JamiTheme.colors.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        // Share location
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.conversation_share_location)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onShareLocation()
                            },
                        )

                        // Record audio
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.conversation_send_audio)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onStartAudioRecording()
                            },
                        )

                        // Record video
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.conversation_send_video)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onVideoRecordingUnsupported()
                            },
                        )

                        // Select media (gallery)
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.select_media)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onSelectMedia()
                            },
                        )

                        // Send file
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.send_file)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onSendFile()
                            },
                        )

                        // Chat extensions
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.chat_extensions)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onChatExtensionsUnsupported()
                            },
                        )
                    }
                }

                // Camera button
                IconButton(
                    onClick = onTakePicture,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Take photo",
                        tint = JamiTheme.colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Text input field (transparent background)
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = {
                        Text(
                            stringResource(Res.string.conversation_type_message),
                            color = JamiTheme.colors.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )

                // Send button or thumbs up emoji button
                if (value.isNotBlank()) {
                    // Send button (visible when text is present)
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(Res.string.content_desc_send),
                            tint = JamiTheme.colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    // Thumbs up emoji button (visible when text is empty)
                    IconButton(
                        onClick = onSendEmoji,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.conversation_default_emoji),
                            style = JamiTheme.typography.titleLarge,
                            color = JamiTheme.colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search results panel shown in place of the message list when search is active.
 *
 * - Empty query → hint to start typing
 * - Non-empty query, no results → "No results found"
 * - Results → scrollable list of tappable message items
 */
@Composable
private fun SearchResultsPanel(
    results: List<MessageItem>,
    query: String,
    modifier: Modifier = Modifier,
    onResultClick: (messageId: String) -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            query.isBlank() -> {
                Text(
                    text = stringResource(Res.string.search_hint_type_to_search),
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(JamiTheme.spacing.xl),
                )
            }
            results.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.search_no_results),
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(JamiTheme.spacing.xl),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = false,
                    verticalArrangement = Arrangement.spacedBy(JamiTheme.spacing.xxs),
                ) {
                    item {
                        Text(
                            text = stringResource(Res.string.search_results_count, results.size),
                            style = JamiTheme.typography.labelMedium,
                            color = JamiTheme.colors.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = JamiTheme.spacing.l,
                                vertical = JamiTheme.spacing.s,
                            ),
                        )
                    }
                    items(items = results, key = { "sr_${it.id}" }) { message ->
                        SearchResultItem(
                            message = message,
                            query = query,
                            onClick = { onResultClick(message.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single search result item. Shows the message bubble with the matching
 * text fragment highlighted and a tap-to-navigate handler.
 */
@Composable
private fun SearchResultItem(
    message: MessageItem,
    query: String,
    onClick: () -> Unit,
) {
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isOutgoing) JamiTheme.colors.messageSent else JamiTheme.colors.messageReceived
    val textColor = if (isOutgoing) JamiTheme.colors.onMessageSent else JamiTheme.colors.onMessageReceived
    val timeColor = textColor.copy(alpha = 0.7f)
    val bubbleShape = RoundedCornerShape(JamiTheme.radius.m)
    val timeText = formatMessageTime(message.timestamp)
    val timeFontSize = JamiTheme.typography.labelSmall.fontSize
    val highlightColor = JamiTheme.colors.accent.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.xxs),
        contentAlignment = alignment,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable(onClick = onClick),
            shape = bubbleShape,
            color = bubbleColor,
        ) {
            Box(
                modifier = Modifier.padding(
                    horizontal = JamiTheme.spacing.m,
                    vertical = JamiTheme.spacing.s,
                ),
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(highlightQueryInText(message.text, query, highlightColor))
                        withStyle(SpanStyle(color = Color.Transparent, fontSize = timeFontSize)) {
                            append("  $timeText")
                        }
                    },
                    style = JamiTheme.typography.bodyMedium,
                    color = textColor,
                )
                Text(
                    text = timeText,
                    style = JamiTheme.typography.labelSmall,
                    color = timeColor,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

/**
 * Build an [AnnotatedString] with all occurrences of [query] in [text]
 * wrapped in a highlight [SpanStyle].
 */
private fun highlightQueryInText(text: String, query: String, highlightColor: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        val lower = text.lowercase()
        val lowerQuery = query.lowercase()
        var cursor = 0
        while (cursor <= text.length) {
            val matchStart = lower.indexOf(lowerQuery, cursor)
            if (matchStart == -1) {
                append(text.substring(cursor))
                break
            }
            append(text.substring(cursor, matchStart))
            withStyle(SpanStyle(background = highlightColor)) {
                append(text.substring(matchStart, matchStart + query.length))
            }
            cursor = matchStart + query.length
        }
    }
}

/**
 * Format a message timestamp for display.
 */
private fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val dt = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

/**
 * Banner showing that a contact is sharing their location.
 * Tapping the banner opens the location sharing screen to view the contact's location.
 */
@Composable
private fun LocationSharingBanner(
    contactName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = JamiTheme.colors.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = JamiTheme.spacing.m, vertical = JamiTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = JamiTheme.colors.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(JamiTheme.spacing.s))
            Text(
                text = stringResource(Res.string.location_share_contact, contactName),
                style = JamiTheme.typography.bodyMedium,
                color = JamiTheme.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = JamiTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = 180f },
            )
        }
    }
}

/**
 * A small card rendered below a chat bubble showing the title and description
 * extracted from the first URL in the message text.
 */
@Composable
private fun LinkPreviewCard(
    preview: net.jami.ui.utils.LinkPreview,
    bubbleColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Surface(
        onClick = { runCatching { uriHandler.openUri(preview.url) } },
        modifier = modifier.widthIn(max = 280.dp),
        shape = RoundedCornerShape(JamiTheme.radius.s),
        color = bubbleColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.2f)),
    ) {
        Column(modifier = Modifier.padding(JamiTheme.spacing.s)) {
            Text(
                text = preview.url
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore("/"),
                style = JamiTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preview.title,
                style = JamiTheme.typography.bodySmall,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!preview.description.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preview.description,
                    style = JamiTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.75f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A compact emoji + count chip shown below a message bubble.
 * Highlighted with a primary-colour border when the current user has reacted.
 */
@Composable
private fun ReactionPill(reaction: ReactionGroup, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (reaction.isMine) JamiTheme.colors.primary.copy(alpha = 0.15f)
                else JamiTheme.colors.surfaceVariant,
        border = if (reaction.isMine) BorderStroke(1.dp, JamiTheme.colors.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = reaction.emoji, fontSize = 14.sp)
            if (reaction.count > 1) {
                Text(
                    text = reaction.count.toString(),
                    style = JamiTheme.typography.labelSmall,
                    color = if (reaction.isMine) JamiTheme.colors.primary else JamiTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
