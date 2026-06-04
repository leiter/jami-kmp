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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ContactItem
import net.jami.ui.viewmodel.ContactsViewModel

/**
 * Blocked contacts screen displaying a list of blocked contacts
 * with an option to unblock each one.
 *
 * @param onBack Called when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedContactsScreen(
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<ContactsViewModel>()
    val blockedContacts by viewModel.blockedContacts.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadContacts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_blocked_contacts),
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
        if (blockedContacts.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.no_blocked_contact),
                    style = JamiTheme.typography.bodyLarge,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(
                    items = blockedContacts,
                    key = { it.uri },
                ) { contact ->
                    BlockedContactItem(
                        contact = contact,
                        onUnblock = { viewModel.unblockContact(contact.uri) },
                    )
                }
            }
        }
    }
}

/**
 * Single blocked contact row with avatar, name, and unblock button.
 */
@Composable
private fun BlockedContactItem(
    contact: ContactItem,
    onUnblock: () -> Unit,
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
        JamiAvatar(
            displayName = contact.displayName,
            size = AvatarSize.Medium,
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contact.username.isNotEmpty()) {
                Text(
                    text = contact.username,
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        JamiButton(
            text = stringResource(Res.string.conversation_action_unblock_this),
            onClick = onUnblock,
            style = JamiButtonStyle.Secondary,
        )
    }
}
