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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import net.jami.ui.theme.JamiTheme

/**
 * Search bar used in the HomeScreen top bar area.
 *
 * @param query The current search query text.
 * @param onQueryChange Callback invoked when the query text changes.
 * @param modifier Modifier applied to the search bar container.
 * @param placeholder Placeholder text shown when the query is empty.
 * @param leadingContent Optional composable displayed at the start (e.g. user avatar).
 * @param trailingContent Optional composable displayed at the end (e.g. QR code button).
 */
@Composable
fun JamiSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(JamiTheme.sizes.minTouchTarget)
            .clip(RoundedCornerShape(JamiTheme.radius.full))
            .background(JamiTheme.colors.surfaceVariant)
            .padding(horizontal = JamiTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(JamiTheme.spacing.s))
        }

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = JamiTheme.typography.bodyLarge.copy(
                color = JamiTheme.colors.onSurface,
            ),
            cursorBrush = SolidColor(JamiTheme.colors.primary),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = JamiTheme.typography.bodyLarge,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )

        if (trailingContent != null) {
            Spacer(Modifier.width(JamiTheme.spacing.s))
            trailingContent()
        }
    }
}
