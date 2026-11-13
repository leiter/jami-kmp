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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.jami.ui.platform.clearFocusOnTapOutside
import kotlin.test.Test
import kotlin.test.assertEquals

/** The iOS "tap outside to hide the keyboard" behaviour (forced on here; it is iOS-only). */
@OptIn(ExperimentalTestApi::class)
class ClearFocusOnTapOutsideTest {

    private fun screen(enabled: Boolean = true, test: androidx.compose.ui.test.ComposeUiTest.(clicks: () -> Int) -> Unit) =
        runComposeUiTest {
            var clicks = 0
            setContent {
                var a by remember { mutableStateOf("") }
                var b by remember { mutableStateOf("") }
                Box(Modifier.fillMaxSize().clearFocusOnTapOutside(enabled = enabled)) {
                    Column {
                        TextField(a, { a = it }, Modifier.testTag("a"))
                        TextField(b, { b = it }, Modifier.testTag("b"))
                        // A tap handler that cannot take focus. A desktop Button would: a click
                        // there moves focus to it whatever this modifier does, while on iOS a
                        // touch never focuses a button.
                        Box(
                            Modifier.size(80.dp).testTag("button")
                                .pointerInput(Unit) { detectTapGestures { clicks++ } }
                        )
                        Box(Modifier.size(200.dp).testTag("empty"))
                    }
                }
            }
            test { clicks }
        }

    @Test
    fun aTapOnEmptySpaceClearsFocus() = screen {
        onNodeWithTag("a").performClick()
        onNodeWithTag("a").assertIsFocused()
        onNodeWithTag("empty").performClick()
        onNodeWithTag("a").assertIsNotFocused()
    }

    @Test
    fun aTapSomethingHandlesKeepsTheKeyboardAndStillArrives() = screen { clicks ->
        onNodeWithTag("a").performClick()
        onNodeWithTag("button").performTouchInput { click() }
        assertEquals(1, clicks())
        onNodeWithTag("a").assertIsFocused()
    }

    @Test
    fun aTapIntoAnotherFieldMovesFocusThere() = screen {
        onNodeWithTag("a").performClick()
        onNodeWithTag("b").performClick()
        onNodeWithTag("b").assertIsFocused()
    }
}
