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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import net.jami.ui.theme.JamiTheme

/**
 * Badge display style.
 */
enum class JamiBadgeStyle {
    /** Displays the unread count number. */
    Count,
    /** Displays a simple colored dot without text. */
    Dot,
}

/**
 * Unread message count badge or status dot.
 *
 * @param count The number to display. For [JamiBadgeStyle.Dot], only visibility depends on count > 0.
 * @param modifier Modifier applied to the badge.
 * @param style The badge display style.
 */
@Composable
fun JamiBadge(
    count: Int,
    modifier: Modifier = Modifier,
    style: JamiBadgeStyle = JamiBadgeStyle.Count,
) {
    if (count <= 0) return

    when (style) {
        JamiBadgeStyle.Count -> {
            val displayText = if (count > 99) "99+" else count.toString()
            Box(
                modifier = modifier
                    .defaultMinSize(minWidth = JamiTheme.sizes.badgeMinSize, minHeight = JamiTheme.sizes.badgeMinSize)
                    .clip(CircleShape)
                    .background(JamiTheme.colors.error)
                    .padding(horizontal = JamiTheme.sizes.badgePaddingHorizontal, vertical = JamiTheme.spacing.xxs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayText,
                    color = JamiTheme.colors.onError,
                    style = JamiTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }

        JamiBadgeStyle.Dot -> {
            Box(
                modifier = modifier
                    .size(JamiTheme.sizes.badgeDot)
                    .clip(CircleShape)
                    .background(JamiTheme.colors.error),
            )
        }
    }
}
