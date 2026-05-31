package net.jami.ui.components.inputs

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JamiFormattedTextFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val TAG = "jami_formatted_field"

    @Test
    fun infrastructure_basicTextRenders() {
        rule.setContent { Text("hello") }
        rule.waitForIdle()
        rule.onNode(hasText("hello")).assertIsDisplayed()
    }

    @Test
    fun emptyField_composesWithoutCrash() {
        rule.setContent {
            var value by remember { mutableStateOf(TextFieldValue("")) }
            JamiFormattedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.testTag(TAG),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag(TAG).assertIsDisplayed()
    }

    @Test
    fun typedText_propagatesToState() {
        var captured = TextFieldValue("")
        rule.setContent {
            var value by remember { mutableStateOf(TextFieldValue("")) }
            JamiFormattedTextField(
                value = value,
                onValueChange = {
                    value = it
                    captured = it
                },
                modifier = Modifier.testTag(TAG),
            )
        }
        rule.onNodeWithTag(TAG).performTextInput("hello")
        rule.runOnIdle { assertEquals("hello", captured.text) }
    }

    @Test
    fun plainText_noUrl_rendersCorrectly() {
        rule.setContent {
            var value by remember { mutableStateOf(TextFieldValue("plain text only")) }
            JamiFormattedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.testTag(TAG),
            )
        }
        rule.onNodeWithTag(TAG).assertTextEquals("plain text only")
    }

    @Test
    fun textWithUrl_rendersWithoutCrash() {
        rule.setContent {
            var value by remember {
                mutableStateOf(TextFieldValue("Check https://jami.net for info"))
            }
            JamiFormattedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.testTag(TAG),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag(TAG).assertIsDisplayed()
    }

    @Test
    fun typeUrl_thenBreakIt_fieldStaysStable() {
        var current = TextFieldValue("")
        rule.setContent {
            var value by remember { mutableStateOf(TextFieldValue("")) }
            JamiFormattedTextField(
                value = value,
                onValueChange = {
                    value = it
                    current = it
                },
                modifier = Modifier.testTag(TAG),
            )
        }
        rule.onNodeWithTag(TAG).performTextInput("https://example.com")
        rule.runOnIdle { assertEquals("https://example.com", current.text) }

        rule.onNodeWithTag(TAG).performTextInput(" extra")
        rule.runOnIdle { assertEquals("https://example.com extra", current.text) }
    }
}
