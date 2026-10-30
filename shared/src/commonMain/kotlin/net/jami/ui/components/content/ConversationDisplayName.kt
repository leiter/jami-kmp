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
package net.jami.ui.components.content

import androidx.compose.runtime.Composable
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.conversation_title_syncing
import net.jami.ui.viewmodel.ConversationItem
import org.jetbrains.compose.resources.stringResource

/**
 * What to show as a conversation's name.
 *
 * A swarm that has synced no members yet has nothing to be named by, so
 * [net.jami.model.Conversation.title] leaves it empty rather than inventing an English literal
 * the way the reference client does; the localized placeholder belongs here, in the UI. Every
 * screen that renders a [ConversationItem] goes through this so the lists and the chat header
 * agree.
 */
@Composable
fun conversationDisplayName(item: ConversationItem): String =
    conversationDisplayName(item.displayName, item.isSyncing)

@Composable
fun conversationDisplayName(displayName: String, isSyncing: Boolean): String =
    if (displayName.isBlank() && isSyncing) stringResource(Res.string.conversation_title_syncing)
    else displayName
