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
 */
package net.jami.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries "open this conversation" from the platform layer into the navigation graph.
 *
 * Mirrors [DeepLinkState], and exists because that one deliberately does something else: an
 * externally-supplied `jami:` link is not trusted enough to act on, so it only pre-fills the
 * search box. A launcher shortcut is not external — the user picked a conversation they are
 * already in — so it opens the chat.
 */
object OpenConversationState {
    private val _conversationId = MutableStateFlow<String?>(null)

    /** Non-null while a conversation is waiting to be opened. */
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    /** Called by the platform layer with a conversation id from a shortcut or notification. */
    fun request(conversationId: String) {
        if (conversationId.isNotBlank()) _conversationId.value = conversationId
    }

    /** Consumed by JamiNavigation once it has navigated. */
    fun consume() {
        _conversationId.value = null
    }
}
