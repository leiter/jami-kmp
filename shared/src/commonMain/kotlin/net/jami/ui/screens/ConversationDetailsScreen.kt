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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import net.jami.ui.components.actions.JamiIconButton
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.components.notification.JamiAlertDialog
import net.jami.ui.contracts.ConversationDetailsContract
import net.jami.ui.theme.JamiTheme

/**
 * Conversation details screen showing contact info and actions.
 *
 * @param state The conversation details state.
 * @param onAction Dispatches detail actions.
 * @param onBack Called when the user navigates back.
 */
@Composable
fun ConversationDetailsScreen(
    state: ConversationDetailsContract.State,
    onAction: (ConversationDetailsContract.Action) -> Unit,
    onBack: () -> Unit,
) {
    var showBlockDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showBlockDialog) {
        JamiAlertDialog(
            title = if (state.isBlocked) "Unblock Contact" else "Block Contact",
            body = if (state.isBlocked) "This contact will be able to send you messages and calls again."
            else "This contact will no longer be able to send you messages or calls.",
            onConfirm = {
                showBlockDialog = false
                onAction(ConversationDetailsContract.Action.BlockContact)
            },
            onDismiss = { showBlockDialog = false },
            confirmText = if (state.isBlocked) "Unblock" else "Block",
            isDestructive = !state.isBlocked,
        )
    }

    if (showDeleteDialog) {
        JamiAlertDialog(
            title = "Remove Contact",
            body = "This will remove the contact and delete the conversation history.",
            onConfirm = {
                showDeleteDialog = false
                onAction(ConversationDetailsContract.Action.RemoveContact)
            },
            onDismiss = { showDeleteDialog = false },
            confirmText = "Remove",
            isDestructive = true,
        )
    }

    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Detail,
                title = "Details",
                onNavigateBack = onBack,
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
            Spacer(Modifier.height(JamiTheme.spacing.xl))

            JamiAvatar(
                displayName = state.displayName.ifEmpty { "?" },
                imageUri = state.avatarUri,
                size = AvatarSize.XLarge,
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            Text(
                text = state.displayName.ifEmpty { "Unknown" },
                style = JamiTheme.typography.titleLarge,
                color = JamiTheme.colors.onSurface,
            )

            if (state.username.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.xs))
                Text(
                    text = state.username,
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }

            if (state.identityHash.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.xs))
                Text(
                    text = state.identityHash,
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = JamiTheme.spacing.xl),
                )
            }

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.xl),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JamiIconButton(
                        icon = Icons.Default.Call,
                        onClick = { /* Place audio call */ },
                        contentDescription = "Audio call",
                        tint = JamiTheme.colors.primary,
                    )
                    Text(
                        text = "Audio",
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JamiIconButton(
                        icon = Icons.Default.Videocam,
                        onClick = { /* Place video call */ },
                        contentDescription = "Video call",
                        tint = JamiTheme.colors.primary,
                    )
                    Text(
                        text = "Video",
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JamiIconButton(
                        icon = Icons.Default.Block,
                        onClick = { showBlockDialog = true },
                        contentDescription = if (state.isBlocked) "Unblock" else "Block",
                        tint = JamiTheme.colors.warning,
                    )
                    Text(
                        text = if (state.isBlocked) "Unblock" else "Block",
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JamiIconButton(
                        icon = Icons.Default.Delete,
                        onClick = { showDeleteDialog = true },
                        contentDescription = "Delete",
                        tint = JamiTheme.colors.error,
                    )
                    Text(
                        text = "Delete",
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.xl))
            HorizontalDivider()

            JamiSectionTitle(title = "Members")

            if (state.displayName.isEmpty() && !state.isLoading) {
                Text(
                    text = "No members to display",
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.s,
                    ),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = JamiTheme.spacing.l,
                            vertical = JamiTheme.spacing.m,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JamiAvatar(
                        displayName = state.displayName,
                        size = AvatarSize.Small,
                    )
                    Spacer(Modifier.width(JamiTheme.spacing.m))
                    Text(
                        text = state.displayName,
                        style = JamiTheme.typography.bodyLarge,
                        color = JamiTheme.colors.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}
