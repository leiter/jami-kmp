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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.components.actions.JamiIconButton
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ContactDetailsViewModel

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

    LaunchedEffect(conversationId) {
        viewModel.loadContact(conversationId)
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
                    onQrClick = onQrClick,
                    onBlockClick = { viewModel.blockContact() },
                    onDeleteClick = { viewModel.removeContact() },
                )
                1 -> FilesTabContent()
            }
        }
    }
}

@Composable
private fun DetailsTabContent(
    state: net.jami.ui.viewmodel.ContactDetailsState,
    onQrClick: () -> Unit,
    onBlockClick: () -> Unit,
    onDeleteClick: () -> Unit,
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
                        onClick = { /* Share via system share sheet — future iteration */ },
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
                    )
                }
            }
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
 */
@Composable
private fun DetailRow(
    label: String,
    value: String = "",
    leadingIcon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = JamiTheme.spacing.l,
                vertical = JamiTheme.spacing.m,
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
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}
