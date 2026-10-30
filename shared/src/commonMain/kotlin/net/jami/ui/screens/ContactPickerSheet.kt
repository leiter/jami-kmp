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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.content_desc_remove_from_selection
import jami_kmp.shared.generated.resources.no_contact_to_add
import jami_kmp.shared.generated.resources.picker_btn
import jami_kmp.shared.generated.resources.picker_title
import kotlinx.coroutines.launch
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ContactItem
import net.jami.ui.viewmodel.NewConversationViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Pick the people for a new group, resampled from the reference client's
 * `ContactPickerFragment` / `frag_contact_picker.xml`:
 *
 * - a bottom sheet, not a screen;
 * - **no search** — it lists the contacts you already have, in a grid;
 * - the ones you pick become chips with their avatar and an X, above the list;
 * - an empty state when there is nobody to add;
 * - one button, disabled until somebody is picked.
 *
 * It asks for no group name, exactly as the reference does: the name is set afterwards from the
 * conversation details screen. One picked contact still makes a swarm rather than a 1:1, because
 * the reference's `onContactPicked` hands whatever was picked to `startConversation` unchanged.
 *
 * @param onCreated called with the new conversation id, to navigate into it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactPickerSheet(
    viewModel: NewConversationViewModel,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Opened fresh each time, like a new fragment instance — but without clearing the search
    // screen underneath, which shares this ViewModel.
    LaunchedEffect(Unit) { viewModel.startGroupPicking() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JamiTheme.colors.surface,
    ) {
        Column(modifier = Modifier.padding(bottom = JamiTheme.spacing.l)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.s),
            ) {
                Icon(
                    imageVector = Icons.Default.GroupAdd,
                    contentDescription = null,
                    tint = JamiTheme.colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.picker_title),
                    style = JamiTheme.typography.titleMedium,
                    color = JamiTheme.colors.onSurface,
                )
            }

            if (state.selectedContacts.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.xs),
                ) {
                    state.selectedContacts.forEach { contact ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.removeContact(contact) },
                            avatar = {
                                JamiAvatar(
                                    displayName = contact.displayName,
                                    size = net.jami.ui.components.content.AvatarSize.Small,
                                )
                            },
                            label = { Text(contact.displayName) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(
                                        Res.string.content_desc_remove_from_selection,
                                        contact.displayName,
                                    ),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }

            if (state.knownContacts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(JamiTheme.spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(JamiTheme.spacing.m),
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = JamiTheme.colors.onSurfaceVariant,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        text = stringResource(Res.string.no_contact_to_add),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 420.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = JamiTheme.spacing.m,
                        vertical = JamiTheme.spacing.s,
                    ),
                ) {
                    items(state.knownContacts, key = { it.uri }) { contact ->
                        val isSelected = state.selectedContacts.any { it.uri == contact.uri }
                        PickableContact(
                            contact = contact,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelected) viewModel.removeContact(contact)
                                else viewModel.selectContact(contact)
                            },
                        )
                    }
                }
            }

            JamiButton(
                text = stringResource(Res.string.picker_btn),
                onClick = {
                    scope.launch {
                        val conversationId = viewModel.createGroup()
                        if (conversationId != null) onCreated(conversationId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.s),
                loading = state.isLoading,
                enabled = state.selectedContacts.isNotEmpty() && !state.isLoading,
            )
        }
    }
}

/** One cell of the picker grid: avatar over name, ticked while selected (reference item_contact). */
@Composable
private fun PickableContact(
    contact: ContactItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(JamiTheme.spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(JamiTheme.spacing.xs),
    ) {
        Box {
            JamiAvatar(
                displayName = contact.displayName,
                size = net.jami.ui.components.content.AvatarSize.Large,
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = JamiTheme.colors.accent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp),
                )
            }
        }
        Text(
            text = contact.displayName,
            style = JamiTheme.typography.bodySmall,
            color = JamiTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
