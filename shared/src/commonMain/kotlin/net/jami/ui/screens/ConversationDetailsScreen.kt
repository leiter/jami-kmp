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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import net.jami.di.getViewModel
import net.jami.ui.components.actions.JamiIconButton
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.theme.JamiTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import net.jami.ui.viewmodel.ContactDetailsViewModel
import net.jami.utils.QRCodeColors
import net.jami.utils.QRCodeUtils
import net.jami.utils.shareText
import org.jetbrains.compose.resources.stringResource

/**
 * Conversation details screen mirroring the official Jami Android client layout.
 *
 * Upper half (this iteration):
 * - Large avatar + display name + username
 * - Audio / Video call action buttons
 * - Tab row: Details | Files
 * - Details tab: identity card (registered username, Jami ID, QR/Share)
 *                + conversation card (Secure P2P, conversation type, Swarm ID)
 * - Files tab: placeholder
 *
 * @param conversationId The conversation ID (raw ring ID / swarm hex) to display.
 * @param onBack Called when the user navigates back.
 * @param onCallClick Called with `isVideo` flag when a call button is tapped.
 * @param onQrClick Called when the QR code button is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailsScreen(
    conversationId: String,
    onBack: () -> Unit,
    onCallClick: (isVideo: Boolean) -> Unit = {},
    onQrClick: () -> Unit = {},
) {
    val viewModel = getViewModel<ContactDetailsViewModel>()
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showQrSheet by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val shareSubject = stringResource(Res.string.share_contact_subject)
    val shareBody = stringResource(
        Res.string.share_contact_body,
        state.displayName.ifEmpty { state.identityHash },
        "https://jami.net",
    )
    val clipboardManager = LocalClipboardManager.current

    fun copyToClipboard(value: String) {
        clipboardManager.setText(AnnotatedString(value))
    }

    LaunchedEffect(conversationId) {
        viewModel.loadContact(conversationId)
    }

    // Generate QR code bitmap in background when sheet opens
    LaunchedEffect(showQrSheet, state.contactUri) {
        if (showQrSheet && state.contactUri.isNotEmpty() && qrBitmap == null) {
            val bitmap = withContext(Dispatchers.Default) {
                val qrData = QRCodeUtils.encodeStringAsQRCodeData(
                    state.contactUri,
                    QRCodeColors.BLACK,
                    QRCodeColors.WHITE,
                ) ?: return@withContext null

                val imageBitmap = ImageBitmap(qrData.width, qrData.height)
                val canvas = Canvas(imageBitmap)

                val bgPaint = Paint().apply { color = Color.White }
                canvas.drawRect(
                    androidx.compose.ui.geometry.Rect(
                        0f, 0f, qrData.width.toFloat(), qrData.height.toFloat()
                    ),
                    bgPaint,
                )

                val fgPoints = mutableListOf<Float>()
                for (i in qrData.data.indices) {
                    if (qrData.data[i] != QRCodeColors.WHITE) {
                        fgPoints.add((i % qrData.width) + 0.5f)
                        fgPoints.add((i / qrData.width) + 0.5f)
                    }
                }
                val fgPaint = Paint().apply {
                    color = Color.Black
                    strokeWidth = 1f
                    strokeCap = StrokeCap.Square
                }
                canvas.drawRawPoints(PointMode.Points, fgPoints.toFloatArray(), fgPaint)
                imageBitmap
            }
            qrBitmap = bitmap
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(JamiTheme.spacing.l))

            // Avatar
            JamiAvatar(
                displayName = state.displayName.ifEmpty { "?" },
                avatarBytes = state.avatarBytes,
                size = AvatarSize.XLarge,
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // Display name
            Text(
                text = state.displayName.ifEmpty { "Unknown" },
                style = JamiTheme.typography.titleLarge,
                color = JamiTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = JamiTheme.spacing.xl),
            )

            // Registered username (secondary line below name)
            if (state.username.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.xs))
                Text(
                    text = state.username,
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            // Action buttons: Audio call + Video call
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.xxl),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CallActionButton(
                    icon = Icons.Default.Call,
                    label = stringResource(Res.string.action_audio),
                    onClick = { onCallClick(false) },
                )
                CallActionButton(
                    icon = Icons.Default.Videocam,
                    label = stringResource(Res.string.action_video),
                    onClick = { onCallClick(true) },
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.l))

            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = JamiTheme.colors.surface,
                contentColor = JamiTheme.colors.primary,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(Res.string.details)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(Res.string.tab_files)) },
                )
            }

            when (selectedTab) {
                0 -> DetailsTabContent(
                    state = state,
                    onQrClick = { showQrSheet = true },
                    onShareClick = { shareText(shareSubject, shareBody) },
                    onBlockClick = { viewModel.blockContact() },
                    onDeleteClick = { viewModel.removeContact() },
                    onLeaveConversation = { viewModel.leaveConversation() },
                    onAddMember = { uri -> viewModel.addMember(uri) },
                    onRemoveMember = { uri -> viewModel.removeMember(uri) },
                    onCopyIdentifier = { copyToClipboard(state.identityHash) },
                    onCopySwarmId = { copyToClipboard(state.swarmId) },
                )
                1 -> FilesTabContent()
            }
        }

        // QR Code Bottom Sheet
        if (showQrSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQrSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = JamiTheme.colors.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.xl)
                        .padding(bottom = JamiTheme.spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Header: QR icon + title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = JamiTheme.colors.onSurface,
                        )
                        Spacer(Modifier.width(JamiTheme.spacing.s))
                        Text(
                            text = stringResource(Res.string.show_qr_code),
                            style = JamiTheme.typography.titleMedium,
                            color = JamiTheme.colors.onSurface,
                        )
                    }

                    Spacer(Modifier.height(JamiTheme.spacing.xl))

                    // QR Code image (white background box)
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White, RoundedCornerShape(JamiTheme.spacing.m)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap!!,
                                contentDescription = stringResource(Res.string.show_qr_code),
                                modifier = Modifier
                                    .size(216.dp)
                                    .padding(JamiTheme.spacing.s),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }

                    Spacer(Modifier.height(JamiTheme.spacing.m))

                    // Subtitle
                    Text(
                        text = stringResource(Res.string.qr_scan_instruction),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.xl))

                    // Share button — triggers native system share sheet
                    Button(
                        onClick = { shareText(shareSubject, shareBody) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(stringResource(Res.string.share_contact_information))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsTabContent(
    state: net.jami.ui.viewmodel.ContactDetailsState,
    onQrClick: () -> Unit,
    onShareClick: () -> Unit,
    onBlockClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCopyIdentifier: () -> Unit,
    onCopySwarmId: () -> Unit,
    onLeaveConversation: () -> Unit = {},
    onAddMember: (String) -> Unit = {},
    onRemoveMember: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.l),
    ) {
        Spacer(Modifier.height(JamiTheme.spacing.m))

        // Card 1 — Identity info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = JamiTheme.colors.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Registered username row (only shown if non-empty)
                if (state.username.isNotEmpty()) {
                    DetailRow(
                        label = stringResource(Res.string.registered_name),
                        value = state.username,
                    )
                    HorizontalDivider(color = JamiTheme.colors.outline.copy(alpha = 0.3f))
                }

                // Jami ID row
                DetailRow(
                    label = stringResource(Res.string.identifier),
                    value = state.identityHash,
                    onCopy = onCopyIdentifier,
                )

                HorizontalDivider(color = JamiTheme.colors.outline.copy(alpha = 0.3f))

                // QR Code and Share buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = onQrClick,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(JamiTheme.spacing.xs))
                        Text(stringResource(Res.string.show_qr_code))
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(JamiTheme.colors.outline.copy(alpha = 0.3f)),
                    )
                    TextButton(
                        onClick = onShareClick,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(JamiTheme.spacing.xs))
                        Text(stringResource(Res.string.share_label))
                    }
                }
            }
        }

        Spacer(Modifier.height(JamiTheme.spacing.m))

        // Card 2 — Conversation info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = JamiTheme.colors.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Secure P2P connection
                DetailRow(
                    label = stringResource(Res.string.secure_p2p_connection),
                    leadingIcon = Icons.Default.Lock,
                )

                if (state.conversationType.isNotEmpty()) {
                    HorizontalDivider(color = JamiTheme.colors.outline.copy(alpha = 0.3f))
                    DetailRow(
                        label = stringResource(Res.string.swarm_type),
                        value = state.conversationType,
                    )
                }

                if (state.swarmId.isNotEmpty()) {
                    HorizontalDivider(color = JamiTheme.colors.outline.copy(alpha = 0.3f))
                    DetailRow(
                        label = stringResource(Res.string.swarm_id),
                        value = state.swarmId,
                        onCopy = onCopySwarmId,
                    )
                }
            }
        }

        // Group member management (swarm conversations only)
        if (state.isSwarm) {
            Spacer(Modifier.height(JamiTheme.spacing.m))
            GroupMemberSection(
                memberUris = state.memberUris,
                isAdmin = state.isAdmin,
                onLeave = onLeaveConversation,
                onAdd = onAddMember,
                onRemove = onRemoveMember,
            )
        }

        Spacer(Modifier.height(JamiTheme.spacing.xl))

        // Block / Delete actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = JamiTheme.spacing.l),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                JamiIconButton(
                    icon = Icons.Default.Block,
                    onClick = onBlockClick,
                    contentDescription = if (state.isBlocked)
                        stringResource(Res.string.conversation_action_unblock_this)
                    else
                        stringResource(Res.string.conversation_action_block_this),
                    tint = JamiTheme.colors.warning,
                )
                Text(
                    text = if (state.isBlocked)
                        stringResource(Res.string.conversation_action_unblock_this)
                    else
                        stringResource(Res.string.conversation_action_block_this),
                    style = JamiTheme.typography.labelSmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                JamiIconButton(
                    icon = Icons.Default.Delete,
                    onClick = onDeleteClick,
                    contentDescription = stringResource(Res.string.delete_contact),
                    tint = JamiTheme.colors.error,
                )
                Text(
                    text = stringResource(Res.string.delete_contact),
                    style = JamiTheme.typography.labelSmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(JamiTheme.spacing.xxl))
    }
}

@Composable
private fun GroupMemberSection(
    memberUris: List<String>,
    isAdmin: Boolean,
    onLeave: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showRemoveUri by remember { mutableStateOf<String?>(null) }
    var newMemberInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = JamiTheme.colors.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.m, vertical = JamiTheme.spacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.group_members),
                    style = JamiTheme.typography.titleSmall,
                    color = JamiTheme.colors.onSurface,
                )
                if (isAdmin) {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text(stringResource(Res.string.group_add_member))
                    }
                }
            }
            HorizontalDivider(color = JamiTheme.colors.outline.copy(alpha = 0.3f))
            memberUris.forEach { uri ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.m, vertical = JamiTheme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uri,
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (isAdmin) {
                        TextButton(onClick = { showRemoveUri = uri }) {
                            Text(
                                text = stringResource(Res.string.group_remove_member),
                                color = JamiTheme.colors.error,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = JamiTheme.colors.outline.copy(alpha = 0.3f))
            TextButton(
                onClick = onLeave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.s),
            ) {
                Text(
                    text = stringResource(Res.string.swarm_group_action_leave),
                    color = JamiTheme.colors.error,
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; newMemberInput = "" },
            title = { Text(stringResource(Res.string.group_add_member)) },
            text = {
                OutlinedTextField(
                    value = newMemberInput,
                    onValueChange = { newMemberInput = it },
                    label = { Text(stringResource(Res.string.group_enter_jami_id)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newMemberInput.isNotBlank()) {
                            onAdd(newMemberInput.trim())
                            newMemberInput = ""
                            showAddDialog = false
                        }
                    }),
                )
            },
            confirmButton = {
                Button(
                    enabled = newMemberInput.isNotBlank(),
                    onClick = {
                        onAdd(newMemberInput.trim())
                        newMemberInput = ""
                        showAddDialog = false
                    },
                ) { Text(stringResource(Res.string.bottomSheet_add_participants_btn)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; newMemberInput = "" }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    showRemoveUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { showRemoveUri = null },
            title = { Text(stringResource(Res.string.group_remove_member_title)) },
            text = { Text(stringResource(Res.string.group_remove_member_message)) },
            confirmButton = {
                Button(onClick = { onRemove(uri); showRemoveUri = null }) {
                    Text(stringResource(Res.string.group_remove_member))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveUri = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun FilesTabContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.swarm_detail_no_document),
            style = JamiTheme.typography.bodyMedium,
            color = JamiTheme.colors.onSurfaceVariant,
        )
    }
}

/**
 * Circular tonal icon button with a text label below, used for call actions.
 */
@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Spacer(Modifier.height(JamiTheme.spacing.xs))
        Text(
            text = label,
            style = JamiTheme.typography.labelSmall,
            color = JamiTheme.colors.onSurfaceVariant,
        )
    }
}

/**
 * A single info row with an optional leading icon, label on the left, and value on the right.
 * When [onCopy] is provided, a copy icon button appears at the trailing edge.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String = "",
    leadingIcon: ImageVector? = null,
    onCopy: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = JamiTheme.spacing.l,
                end = if (onCopy != null) JamiTheme.spacing.xs else JamiTheme.spacing.l,
                top = JamiTheme.spacing.m,
                bottom = JamiTheme.spacing.m,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = JamiTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(JamiTheme.spacing.m))
        }
        Text(
            text = label,
            style = JamiTheme.typography.bodyMedium,
            color = JamiTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(JamiTheme.spacing.s))
            Text(
                text = value,
                style = JamiTheme.typography.bodyMedium,
                color = JamiTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(Res.string.jami_id_copy),
                    tint = JamiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
