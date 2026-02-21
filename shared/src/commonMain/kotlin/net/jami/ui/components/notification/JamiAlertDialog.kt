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
package net.jami.ui.components.notification

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.jami.ui.theme.JamiTheme

/**
 * Alert dialog with title, body, and confirm/dismiss buttons.
 *
 * @param title The dialog title.
 * @param body The dialog body/message text.
 * @param onConfirm Callback invoked when the confirm button is tapped.
 * @param onDismiss Callback invoked when the dismiss button is tapped or the dialog is dismissed.
 * @param modifier Modifier applied to the dialog.
 * @param confirmText Label for the confirm button.
 * @param dismissText Label for the dismiss button. When null, no dismiss button is shown.
 * @param isDestructive When true, the confirm button uses the error color.
 */
@Composable
fun JamiAlertDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "OK",
    dismissText: String? = "Cancel",
    isDestructive: Boolean = false,
) {
    val confirmColor = if (isDestructive) JamiTheme.colors.error
    else JamiTheme.colors.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = JamiTheme.typography.titleMedium,
                color = JamiTheme.colors.onSurface,
            )
        },
        text = {
            Text(
                text = body,
                style = JamiTheme.typography.bodyMedium,
                color = JamiTheme.colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    style = JamiTheme.typography.labelLarge,
                    color = confirmColor,
                )
            }
        },
        dismissButton = if (dismissText != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = dismissText,
                        style = JamiTheme.typography.labelLarge,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
            }
        } else null,
        shape = RoundedCornerShape(JamiTheme.radius.l),
        containerColor = JamiTheme.colors.surface,
        titleContentColor = JamiTheme.colors.onSurface,
        textContentColor = JamiTheme.colors.onSurfaceVariant,
    )
}
