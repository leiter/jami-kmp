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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.content_desc_attach_file
import jami_kmp.shared.generated.resources.content_desc_send_message
import jami_kmp.shared.generated.resources.content_desc_send_thumbs_up
import jami_kmp.shared.generated.resources.content_desc_take_photo
import net.jami.ui.components.actions.JamiIconButton
import net.jami.ui.theme.JamiTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Chat message input bar displayed at the bottom of the chat screen.
 *
 * Includes attachment and camera icon buttons, a text field, and a send button.
 * When the message is empty, the send button is replaced with a thumbs-up quick reaction.
 *
 * @param message The current message text.
 * @param onMessageChange Callback invoked when the message text changes.
 * @param onSend Callback invoked when the send button is tapped.
 * @param modifier Modifier applied to the root container.
 * @param onAttachment Callback invoked when the attachment button is tapped.
 * @param onCamera Callback invoked when the camera button is tapped.
 * @param placeholder Placeholder text shown when the message is empty.
 */
@Composable
fun JamiMessageInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onAttachment: (() -> Unit)? = null,
    onCamera: (() -> Unit)? = null,
    placeholder: String = "Message",
) {
    val hasText = message.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(JamiTheme.colors.surface)
            .padding(
                horizontal = JamiTheme.spacing.s,
                vertical = JamiTheme.spacing.xs,
            ),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Attachment button
        if (onAttachment != null) {
            JamiIconButton(
                icon = Icons.Default.AttachFile,
                onClick = onAttachment,
                contentDescription = stringResource(Res.string.content_desc_attach_file),
                tint = JamiTheme.colors.onSurfaceVariant,
            )
        }

        // Camera button
        if (onCamera != null) {
            JamiIconButton(
                icon = Icons.Default.CameraAlt,
                onClick = onCamera,
                contentDescription = stringResource(Res.string.content_desc_take_photo),
                tint = JamiTheme.colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(JamiTheme.spacing.xs))

        // Text field
        BasicTextField(
            value = message,
            onValueChange = onMessageChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = JamiTheme.sizes.messageInputMinHeight, max = JamiTheme.sizes.messageInputMaxHeight)
                .clip(RoundedCornerShape(JamiTheme.radius.xl))
                .background(JamiTheme.colors.surfaceVariant)
                .padding(
                    horizontal = JamiTheme.spacing.m,
                    vertical = JamiTheme.spacing.s,
                ),
            textStyle = JamiTheme.typography.bodyLarge.copy(
                color = JamiTheme.colors.onSurface,
            ),
            cursorBrush = SolidColor(JamiTheme.colors.primary),
            maxLines = 5,
            decorationBox = { innerTextField ->
                if (message.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = JamiTheme.typography.bodyLarge,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )

        Spacer(Modifier.width(JamiTheme.spacing.xs))

        // Send / thumbs-up button
        if (hasText) {
            JamiIconButton(
                icon = Icons.AutoMirrored.Filled.Send,
                onClick = onSend,
                contentDescription = stringResource(Res.string.content_desc_send_message),
                tint = JamiTheme.colors.primary,
            )
        } else {
            JamiIconButton(
                icon = Icons.Default.ThumbUp,
                onClick = onSend,
                contentDescription = "Send thumbs up",
                tint = JamiTheme.colors.primary,
            )
        }
    }
}
