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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.components.actions.JamiButtonStyle
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.contracts.BlockedContactsContract
import net.jami.ui.contracts.ContactItem
import net.jami.ui.theme.JamiTheme

/**
 * Blocked contacts screen displaying a list of blocked contacts
 * with an option to unblock each one.
 *
 * @param state The blocked contacts state.
 * @param onAction Dispatches blocked contacts actions.
 * @param onBack Called when the user navigates back.
 */
@Composable
fun BlockedContactsScreen(
    state: BlockedContactsContract.State,
    onAction: (BlockedContactsContract.Action) -> Unit,
    onBack: () -> Unit,
) {
    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Detail,
                title = "Blocked Contacts",
                onNavigateBack = onBack,
            )
        },
    ) { padding ->
        if (state.contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No blocked contacts",
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
                    items = state.contacts,
                    key = { it.uri },
                ) { contact ->
                    BlockedContactItem(
                        contact = contact,
                        onUnblock = {
                            onAction(BlockedContactsContract.Action.UnblockContact(contact.uri))
                        },
                    )
                }
            }
        }
    }
}

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
            text = "Unblock",
            onClick = onUnblock,
            style = JamiButtonStyle.Secondary,
        )
    }
}
