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
package net.jami.ui.components.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.jami.ui.theme.JamiTheme

/**
 * Toggle row for settings screens, displaying a label, optional description, and a switch.
 *
 * @param label The primary label text.
 * @param checked The current toggle state.
 * @param onCheckedChange Callback invoked when the toggle state changes.
 * @param modifier Modifier applied to the row container.
 * @param description Optional secondary description text displayed below the label.
 * @param enabled Whether the toggle is interactive.
 */
@Composable
fun JamiToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(
                horizontal = JamiTheme.spacing.l,
                vertical = JamiTheme.spacing.m,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = JamiTheme.typography.bodyLarge,
                color = if (enabled) JamiTheme.colors.onSurface
                else JamiTheme.colors.onDisabled,
            )
            if (description != null) {
                Spacer(Modifier.height(JamiTheme.spacing.xxs))
                Text(
                    text = description,
                    style = JamiTheme.typography.bodySmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JamiTheme.colors.onPrimary,
                checkedTrackColor = JamiTheme.colors.primary,
                uncheckedThumbColor = JamiTheme.colors.onSurfaceVariant,
                uncheckedTrackColor = JamiTheme.colors.surfaceVariant,
                uncheckedBorderColor = JamiTheme.colors.outline,
            ),
        )
    }
}
