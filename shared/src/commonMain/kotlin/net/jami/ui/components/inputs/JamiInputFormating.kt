package net.jami.ui.components.inputs

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

private val URL_REGEX = Regex("""https?://\S+""")

internal fun findUrlRanges(text: String): List<IntRange> =
    URL_REGEX.findAll(text).map { it.range }.toList()

class LinkVisualTransformation : VisualTransformation {
    private var lastText = ""
    private var lastResult = TransformedText(AnnotatedString(""), OffsetMapping.Identity)

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text == lastText) return lastResult
        lastText = text.text
        lastResult = applyLinkStyles(text)
        return lastResult
    }

    private fun applyLinkStyles(text: AnnotatedString): TransformedText {
        val ranges = findUrlRanges(text.text)
        if (ranges.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                addStyle(
                    style = SpanStyle(
                        color = Color.Blue,
                        textDecoration = TextDecoration.Underline,
                    ),
                    start = range.first,
                    end = range.last + 1,
                )
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
fun JamiFormattedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
) {
    val transformation = remember { LinkVisualTransformation() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        visualTransformation = transformation,
    )
}

