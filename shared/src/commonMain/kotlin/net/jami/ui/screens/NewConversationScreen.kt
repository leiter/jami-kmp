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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiToggle
import net.jami.ui.components.inputs.JamiSearchField
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.contracts.ContactItem
import net.jami.ui.contracts.NewConversationContract
import net.jami.ui.theme.JamiTheme

/**
 * New conversation screen for selecting contacts and creating a conversation.
 *
 * @param searchState The search state (Tier 2 split).
 * @param selectionState The selection state (Tier 2 split).
 * @param onAction Dispatches new conversation actions.
 * @param onBack Called when the user navigates back.
 */
@Composable
fun NewConversationScreen(
    searchState: NewConversationContract.SearchState,
    selectionState: NewConversationContract.SelectionState,
    onAction: (NewConversationContract.Action) -> Unit,
    onBack: () -> Unit,
) {
    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Detail,
                title = "New Conversation",
                onNavigateBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            JamiSearchField(
                query = searchState.query,
                onQueryChange = { onAction(NewConversationContract.Action.Search(it)) },
                placeholder = "Search contacts or Jami ID...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.s,
                    ),
            )

            JamiToggle(
                label = "Create group conversation",
                checked = selectionState.isGroup,
                onCheckedChange = { onAction(NewConversationContract.Action.SetIsGroup(it)) },
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(
                    items = searchState.results,
                    key = { it.uri },
                ) { contact ->
                    val isSelected = selectionState.selectedContacts.any { it.uri == contact.uri }
                    SelectableContactItem(
                        contact = contact,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onAction(NewConversationContract.Action.RemoveContact(contact))
                            } else {
                                onAction(NewConversationContract.Action.SelectContact(contact))
                            }
                        },
                    )
                }
            }

            JamiButton(
                text = "Create",
                onClick = { onAction(NewConversationContract.Action.CreateConversation) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(JamiTheme.spacing.l),
                loading = searchState.isLoading,
                enabled = selectionState.selectedContacts.isNotEmpty() && !searchState.isLoading,
            )
        }
    }
}

@Composable
private fun SelectableContactItem(
    contact: ContactItem,
    isSelected: Boolean,
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

        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle
            else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint = if (isSelected) JamiTheme.colors.primary
            else JamiTheme.colors.onSurfaceVariant,
        )
    }
}
