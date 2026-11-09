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
package net.jami.services

import net.jami.model.Uri
import net.jami.ui.navigation.OpenConversationState
import net.jami.utils.Log
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationShortcutIcon
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.shortcutItems
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Recent conversations as home-screen quick actions (long-press the app icon), the iOS half of
 * Android's launcher shortcuts (ce17110). [ConversationShortcutPublisher] decides which four;
 * this only hands them to UIKit.
 *
 * Quick actions take a system symbol or a template image, not an arbitrary picture, so the
 * conversation's avatar cannot be shown the way Android's adaptive icon shows it; every item
 * carries the same message symbol.
 *
 * A tap arrives in the Swift scene delegate, which passes the item to [openConversationShortcut].
 */
class IOSConversationShortcuts : ConversationShortcutService {

    override fun publish(shortcuts: List<ConversationShortcut>) {
        val items = shortcuts.map { shortcut ->
            UIApplicationShortcutItem(
                type = SHORTCUT_TYPE,
                localizedTitle = shortcut.title,
                localizedSubtitle = null,
                icon = UIApplicationShortcutIcon.iconWithSystemImageName("message.fill"),
                userInfo = mapOf<Any?, Any?>(
                    KEY_CONVERSATION_ID to shortcut.conversationId,
                    KEY_ACCOUNT_ID to shortcut.accountId,
                ),
            )
        }
        setItems(items)
    }

    override fun clear() = setItems(emptyList())

    /** UIKit state; the publisher collects on a background dispatcher. */
    private fun setItems(items: List<UIApplicationShortcutItem>) {
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.shortcutItems = items
            Log.d(TAG, "Published ${items.size} home-screen quick actions")
        }
    }

    companion object {
        private const val TAG = "IOSConversationShortcuts"

        /** The quick-action type; the scene delegate passes it back with the tap. */
        const val SHORTCUT_TYPE = "net.jami.openConversation"
        internal const val KEY_CONVERSATION_ID = "conversationId"
        internal const val KEY_ACCOUNT_ID = "accountId"
    }
}

/**
 * Opens the conversation a tapped quick action names. Called from the Swift scene delegate, on
 * a warm tap (`windowScene(_:performActionFor:)`) and at a cold launch
 * (`connectionOptions.shortcutItem`). Returns whether the item was one of ours.
 *
 * A quick action is not external input — the user picked a conversation they are already in —
 * so it opens the chat, through the same [OpenConversationState] Android's shortcut uses.
 */
fun openConversationShortcut(type: String, conversationId: String?): Boolean {
    if (type != IOSConversationShortcuts.SHORTCUT_TYPE) return false
    val id = conversationId?.takeIf { it.isNotBlank() } ?: return false
    Log.d("IOSConversationShortcuts", "Quick action opened conversation $id")
    OpenConversationState.request(Uri.fromString(id).rawRingId)
    return true
}
