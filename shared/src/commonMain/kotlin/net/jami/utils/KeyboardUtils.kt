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
package net.jami.utils

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Dismisses the software keyboard and clears focus when the user taps on a
 * non-interactive area of the screen.
 *
 * ## iOS behaviour
 * On iOS, the keyboard does not auto-dismiss when tapping outside a text field —
 * there is no Back button equivalent. Switching *between* text fields keeps the
 * keyboard open (UIKit moves the first-responder without a dismiss/show cycle).
 * This modifier handles the "tap on empty space → dismiss" case by calling
 * [LocalFocusManager.clearFocus], which internally triggers `resignFirstResponder`.
 *
 * ## Usage
 * Apply to the **root container** of any screen that contains text input, typically
 * the outermost `Column`, `Box`, or `LazyColumn`:
 *
 * ```kotlin
 * Column(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .clearFocusOnTap()      // ← add this
 *         .verticalScroll(...)
 *         .imePadding()           // ← keep content above keyboard
 * ) { ... }
 * ```
 *
 * ## Where to apply
 * | Screen | Apply? | Notes |
 * |---|---|---|
 * | CreateAccountScreen | ✅ | Multiple form fields |
 * | AccountSettingsScreen | ✅ | Multiple form fields |
 * | ProfileSetupScreen | ✅ | Name / bio fields |
 * | ImportAccountScreen | ✅ | PIN / password field |
 * | SearchScreen | ✅ | Single search field |
 * | ChatScreen root | ⚠️ | Apply only above message input; tapping message list should NOT dismiss |
 * | HomeScreen | ❌ | No text input |
 * | ConversationDetailsScreen | ❌ | No text input |
 *
 * ## What NOT to do
 * - Do not use `SoftwareKeyboardController.hide()` instead: it hides the keyboard but
 *   leaves focus on the field, so the keyboard reappears the instant the user taps
 *   elsewhere. `clearFocus()` removes focus AND hides the keyboard in one step.
 * - Do not apply this modifier inside a `LazyColumn` item — it will capture gestures
 *   and break list scrolling. Apply it outside/around the list.
 */
@Composable
fun Modifier.clearFocusOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return this.pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}
