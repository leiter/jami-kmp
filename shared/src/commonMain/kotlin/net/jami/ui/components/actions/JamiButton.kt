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
package net.jami.ui.components.actions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import net.jami.ui.theme.JamiTheme

/**
 * Style variants for [JamiButton].
 */
enum class JamiButtonStyle {
    /** Filled button with primary color background. */
    Primary,
    /** Outlined button with primary color border and transparent background. */
    Secondary,
    /** Filled button with error color background for destructive actions. */
    Destructive,
}

/**
 * Reusable button component that supports primary, secondary, and destructive styles,
 * an optional leading icon, and a loading state.
 *
 * @param text The button label.
 * @param onClick Callback invoked when the button is clicked.
 * @param modifier Modifier applied to the button.
 * @param style The visual style variant.
 * @param enabled Whether the button is enabled.
 * @param loading When true, a [CircularProgressIndicator] replaces the content.
 * @param icon Optional leading [ImageVector] displayed before the text.
 */
@Composable
fun JamiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: JamiButtonStyle = JamiButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(JamiTheme.radius.s)
    val effectiveEnabled = enabled && !loading

    when (style) {
        JamiButtonStyle.Primary -> {
            Button(
                onClick = onClick,
                modifier = modifier.height(JamiTheme.sizes.minTouchTarget),
                enabled = effectiveEnabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JamiTheme.colors.primary,
                    contentColor = JamiTheme.colors.onPrimary,
                    disabledContainerColor = JamiTheme.colors.disabled,
                    disabledContentColor = JamiTheme.colors.onDisabled,
                ),
                contentPadding = PaddingValues(
                    horizontal = JamiTheme.spacing.l,
                    vertical = JamiTheme.spacing.s,
                ),
            ) {
                ButtonContent(
                    text = text,
                    loading = loading,
                    icon = icon,
                    contentColor = JamiTheme.colors.onPrimary,
                    loadingColor = JamiTheme.colors.onPrimary,
                )
            }
        }

        JamiButtonStyle.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.height(JamiTheme.sizes.minTouchTarget),
                enabled = effectiveEnabled,
                shape = shape,
                border = BorderStroke(
                    width = JamiTheme.sizes.buttonBorderWidth,
                    color = if (effectiveEnabled) JamiTheme.colors.primary
                    else JamiTheme.colors.disabled,
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = JamiTheme.colors.primary,
                    disabledContentColor = JamiTheme.colors.onDisabled,
                ),
                contentPadding = PaddingValues(
                    horizontal = JamiTheme.spacing.l,
                    vertical = JamiTheme.spacing.s,
                ),
            ) {
                ButtonContent(
                    text = text,
                    loading = loading,
                    icon = icon,
                    contentColor = JamiTheme.colors.primary,
                    loadingColor = JamiTheme.colors.primary,
                )
            }
        }

        JamiButtonStyle.Destructive -> {
            Button(
                onClick = onClick,
                modifier = modifier.height(JamiTheme.sizes.minTouchTarget),
                enabled = effectiveEnabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JamiTheme.colors.error,
                    contentColor = JamiTheme.colors.onError,
                    disabledContainerColor = JamiTheme.colors.disabled,
                    disabledContentColor = JamiTheme.colors.onDisabled,
                ),
                contentPadding = PaddingValues(
                    horizontal = JamiTheme.spacing.l,
                    vertical = JamiTheme.spacing.s,
                ),
            ) {
                ButtonContent(
                    text = text,
                    loading = loading,
                    icon = icon,
                    contentColor = JamiTheme.colors.onError,
                    loadingColor = JamiTheme.colors.onError,
                )
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
    icon: ImageVector?,
    contentColor: Color,
    loadingColor: Color,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(JamiTheme.sizes.progressIndicator),
            color = loadingColor,
            strokeWidth = JamiTheme.sizes.progressStrokeWidth,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(JamiTheme.sizes.iconButton),
                    tint = contentColor,
                )
                Spacer(Modifier.width(JamiTheme.spacing.s))
            }
            Text(
                text = text,
                style = JamiTheme.typography.labelLarge,
            )
        }
    }
}
