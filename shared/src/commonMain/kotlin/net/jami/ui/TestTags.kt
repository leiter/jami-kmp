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
package net.jami.ui

/**
 * Stable identifiers for UI nodes that a test or an automated accessibility sweep needs to find.
 *
 * Before 2026-11-07 the repository had none at all, which is why the accessibility audit of
 * 2026-09-30 (`doc/accessibility-audit-2026-09-30.md`) could not be expressed as a test and had
 * to be re-derived by reading source. A tag belongs here when a node carries semantics worth
 * protecting — merged descriptions, custom actions, live regions — not on every composable.
 *
 * Keep the values stable: they are matched literally by `onNodeWithTag`.
 */
object TestTags {
    /** A conversation row in the home list — merged semantics, state and four custom actions. */
    const val CONVERSATION_ROW = "conversation_row"

    /** The search affordance at the top of the home screen. */
    const val SEARCH_BAR = "search_bar"

    /** The banner offering the pending contact requests. */
    const val PENDING_REQUESTS_BANNER = "pending_requests_banner"

    /** The "contact is typing" bubble — a polite live region. */
    const val TYPING_INDICATOR = "typing_indicator"

    /** The chat composer's text field. */
    const val MESSAGE_INPUT = "message_input"

    /** The call-state line on the call screen — a polite live region. */
    const val CALL_STATUS = "call_status"
}
