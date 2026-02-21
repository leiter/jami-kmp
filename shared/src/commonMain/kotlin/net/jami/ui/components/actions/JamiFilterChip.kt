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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.jami.ui.theme.JamiTheme

/**
 * Filter chip used on search and filter screens (e.g. "QR-Code", "New Group").
 *
 * @param text The chip label.
 * @param onClick Callback invoked when the chip is clicked.
 * @param modifier Modifier applied to the chip.
 * @param selected Whether the chip is in a selected state.
 * @param leadingIcon Optional leading [ImageVector] displayed before the text.
 */
@Composable
fun JamiFilterChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = JamiTheme.typography.labelMedium,
            )
        },
        modifier = modifier,
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else null,
        shape = RoundedCornerShape(JamiTheme.radius.full),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = JamiTheme.colors.outline,
            selectedBorderColor = JamiTheme.colors.primary,
            enabled = true,
            selected = selected,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = JamiTheme.colors.surface,
            labelColor = JamiTheme.colors.onSurface,
            iconColor = JamiTheme.colors.onSurfaceVariant,
            selectedContainerColor = JamiTheme.colors.primary.copy(alpha = 0.12f),
            selectedLabelColor = JamiTheme.colors.primary,
            selectedLeadingIconColor = JamiTheme.colors.primary,
        ),
    )
}
