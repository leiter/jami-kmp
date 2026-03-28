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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.components.actions.JamiButtonStyle
import net.jami.ui.components.actions.JamiIconButton
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ContactDetailsViewModel
import net.jami.ui.viewmodel.ContactDetailsState

/**
 * Conversation details screen showing participant info, actions, and members.
 *
 * Layout:
 * - Top bar: back arrow + "Details"
 * - Avatar and name
 * - Contact ID
 * - Action row: audio call, video call, block, delete conversation
 * - Members list (for group conversations)
 *
 * @param conversationId The conversation to display details for.
 * @param onBack Called when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailsScreen(
    conversationId: String,
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<ContactDetailsViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(conversationId) {
        viewModel.loadContact(conversationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_details),
                        style = JamiTheme.typography.titleMedium,
                    )
                },
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
                    titleContentColor = JamiTheme.colors.onSurface,
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
            Spacer(Modifier.height(JamiTheme.spacing.xl))

            // Avatar
            JamiAvatar(
                displayName = state.displayName.ifEmpty { "?" },
                imageUri = state.avatarUri,
                size = AvatarSize.XLarge,
            )

            Spacer(Modifier.height(JamiTheme.spacing.m))

            // Name
            Text(
                text = state.displayName.ifEmpty { "Unknown" },
                style = JamiTheme.typography.titleLarge,
                color = JamiTheme.colors.onSurface,
            )

            // Username
            if (state.username.isNotEmpty()) {
                Spacer(Modifier.height(JamiTheme.spacing.xs))
                Text(
                    text = state.username,
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }

            // Identity hash
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

            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.xl),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Audio call
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JamiIconButton(
                        icon = Icons.Default.Call,
                        onClick = { /* Place audio call */ },
                        contentDescription = stringResource(Res.string.content_desc_audio_call),
                        tint = JamiTheme.colors.primary,
                    )
                    Text(
                        text = stringResource(Res.string.action_audio),
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                // Video call
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JamiIconButton(
                        icon = Icons.Default.Videocam,
                        onClick = { /* Place video call */ },
                        contentDescription = stringResource(Res.string.content_desc_video_call),
                        tint = JamiTheme.colors.primary,
                    )
                    Text(
                        text = stringResource(Res.string.action_video),
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                // Block
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JamiIconButton(
                        icon = Icons.Default.Block,
                        onClick = { viewModel.blockContact() },
                        contentDescription = if (state.isBlocked) stringResource(Res.string.action_unblock) else stringResource(Res.string.action_block),
                        tint = JamiTheme.colors.warning,
                    )
                    Text(
                        text = if (state.isBlocked) stringResource(Res.string.action_unblock) else stringResource(Res.string.action_block),
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                // Delete conversation
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JamiIconButton(
                        icon = Icons.Default.Delete,
                        onClick = { viewModel.removeContact() },
                        contentDescription = stringResource(Res.string.ic_delete_menu),
                        tint = JamiTheme.colors.error,
                    )
                    Text(
                        text = stringResource(Res.string.ic_delete_menu),
                        style = JamiTheme.typography.labelSmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.xl))
            HorizontalDivider()

            // Members section (visible for group conversations)
            JamiSectionTitle(title = "Members")

            if (state.displayName.isEmpty() && !state.isLoading) {
                Text(
                    text = stringResource(Res.string.empty_members),
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.s,
                    ),
                )
            } else {
                // Display the contact as the only member for 1:1 conversations
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
