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
package net.jami.ui.components.inputs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import net.jami.ui.theme.JamiTheme

/**
 * Text input field with label, placeholder, and error state support.
 *
 * @param value The current text value.
 * @param onValueChange Callback invoked when the text changes.
 * @param modifier Modifier applied to the outer column.
 * @param label Optional label displayed above the field.
 * @param placeholder Optional placeholder text shown when the field is empty.
 * @param isError Whether the field is in an error state.
 * @param errorMessage Error message displayed below the field when [isError] is true.
 * @param singleLine Whether the input is constrained to a single line.
 * @param maxLines Maximum number of visible lines.
 * @param keyboardOptions Software keyboard options for the field.
 * @param enabled Whether the field accepts input.
 */
@Composable
fun JamiInputText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = JamiTheme.typography.labelMedium,
                color = if (isError) JamiTheme.colors.error
                else JamiTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(JamiTheme.spacing.xs))
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            isError = isError,
            placeholder = if (placeholder != null) {
                {
                    Text(
                        text = placeholder,
                        style = JamiTheme.typography.bodyLarge,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
            } else null,
            textStyle = JamiTheme.typography.bodyLarge.copy(
                color = JamiTheme.colors.onSurface,
            ),
            shape = RoundedCornerShape(JamiTheme.radius.s),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JamiTheme.colors.primary,
                unfocusedBorderColor = JamiTheme.colors.outline,
                errorBorderColor = JamiTheme.colors.error,
                cursorColor = JamiTheme.colors.primary,
                disabledBorderColor = JamiTheme.colors.disabled,
                disabledTextColor = JamiTheme.colors.onDisabled,
            ),
        )

        if (isError && errorMessage != null) {
            Spacer(Modifier.height(JamiTheme.spacing.xs))
            Text(
                text = errorMessage,
                style = JamiTheme.typography.bodySmall,
                color = JamiTheme.colors.error,
                modifier = Modifier.padding(start = JamiTheme.spacing.xs),
            )
        }
    }
}
