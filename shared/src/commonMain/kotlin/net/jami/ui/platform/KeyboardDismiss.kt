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
package net.jami.ui.platform

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Whether a tap outside a text field hides the keyboard.
 *
 * iOS: yes. Native iOS screens behave this way, and Compose on iOS has no other way out of the
 * keyboard on screens without a "Done" key — it stayed up, covering the bottom of the screen,
 * until the user navigated away. Android: no, the system back gesture closes the keyboard there
 * and Android's own client does not do this.
 */
expect val dismissesKeyboardOnTapOutside: Boolean

/**
 * Clears focus — which hides the keyboard — when a tap lands on nothing that handles it.
 *
 * The root sees a gesture in the main pass after its children, and `waitForUpOrCancellation`
 * returns null once anything consumed it: so tapping a text field, a button or a list item, or
 * scrolling, leaves the keyboard alone, and only a tap on empty space clears focus.
 */
@Composable
fun Modifier.clearFocusOnTapOutside(enabled: Boolean = dismissesKeyboardOnTapOutside): Modifier {
    if (!enabled) return this
    val focusManager = LocalFocusManager.current
    return this.pointerInput(focusManager) { clearFocusOnUnhandledTap(focusManager) }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.clearFocusOnUnhandledTap(
    focusManager: FocusManager,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = true)
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        if (!up.isConsumed) focusManager.clearFocus()
    }
}
