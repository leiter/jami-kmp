package net.jami.ui.components.inputs

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private val URL_REGEX = Regex("""https?://\S+""")

internal fun findUrlRanges(text: String): List<IntRange> =
    URL_REGEX.findAll(text).map { it.range }.toList()

class LinkVisualTransformation(private val linkColor: Color) : VisualTransformation {
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
                        color = linkColor,
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
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: String? = null,
) {
    val contentColor = LocalContentColor.current
    val linkColor = MaterialTheme.colorScheme.primary
    val transformation = remember(linkColor) { LinkVisualTransformation(linkColor) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val shape = RoundedCornerShape(4.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle.merge(TextStyle(color = contentColor)),
        visualTransformation = transformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .border(borderWidth, borderColor, shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (value.text.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = textStyle.merge(
                            TextStyle(color = contentColor.copy(alpha = 0.5f))
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}
