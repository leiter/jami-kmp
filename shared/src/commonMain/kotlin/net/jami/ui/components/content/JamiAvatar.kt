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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.jami.ui.theme.JamiTheme
import kotlin.math.abs

/**
 * Avatar size presets.
 */
enum class AvatarSize {
    Small, Medium, Large, XLarge
}

/**
 * Presence status for the avatar indicator dot.
 */
enum class PresenceStatus {
    Online, Away, Offline
}

/**
 * User avatar displaying an image or colored initials fallback, with an optional
 * presence indicator dot.
 *
 * @param displayName The user's display name used for initials and color generation.
 * @param modifier Modifier applied to the root container.
 * @param imageUri Optional URI of the user's profile image. When null, initials are shown.
 * @param size The avatar size preset.
 * @param showPresence Whether to show the presence indicator dot.
 * @param presenceStatus The current presence status.
 */
@Composable
fun JamiAvatar(
    displayName: String,
    modifier: Modifier = Modifier,
    imageUri: String? = null,
    size: AvatarSize = AvatarSize.Medium,
    showPresence: Boolean = false,
    presenceStatus: PresenceStatus = PresenceStatus.Offline,
) {
    val avatarDp = when (size) {
        AvatarSize.Small -> JamiTheme.sizes.avatarSmall
        AvatarSize.Medium -> JamiTheme.sizes.avatarMedium
        AvatarSize.Large -> JamiTheme.sizes.avatarLarge
        AvatarSize.XLarge -> JamiTheme.sizes.avatarXLarge
    }

    val initialsStyle = when (size) {
        AvatarSize.Small -> TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        AvatarSize.Medium -> TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        AvatarSize.Large -> TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        AvatarSize.XLarge -> TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
    }

    val presenceDotSize = when (size) {
        AvatarSize.Small -> 8.dp
        AvatarSize.Medium -> 12.dp
        AvatarSize.Large -> 14.dp
        AvatarSize.XLarge -> 18.dp
    }

    Box(modifier = modifier.size(avatarDp)) {
        // Avatar circle
        if (imageUri != null) {
            // When an image URI is provided, show a placeholder circle.
            // Actual image loading should be handled by the platform (e.g. Coil, Kamel).
            // The component exposes the URI so the caller can integrate their image loader.
            InitialsCircle(
                displayName = displayName,
                avatarDp = avatarDp,
                textStyle = initialsStyle,
            )
        } else {
            InitialsCircle(
                displayName = displayName,
                avatarDp = avatarDp,
                textStyle = initialsStyle,
            )
        }

        // Presence indicator dot
        if (showPresence && presenceStatus != PresenceStatus.Offline) {
            val dotColor = when (presenceStatus) {
                PresenceStatus.Online -> JamiTheme.colors.positive
                PresenceStatus.Away -> JamiTheme.colors.warning
                PresenceStatus.Offline -> JamiTheme.colors.onDisabled
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-1).dp, y = (-1).dp)
                    .size(presenceDotSize)
                    .clip(CircleShape)
                    .background(JamiTheme.colors.surface)
                    .border(width = 2.dp, color = JamiTheme.colors.surface, shape = CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(presenceDotSize - 4.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
    }
}

@Composable
private fun InitialsCircle(
    displayName: String,
    avatarDp: Dp,
    textStyle: TextStyle,
) {
    val initials = extractInitials(displayName)
    val backgroundColor = colorFromName(displayName)

    Box(
        modifier = Modifier
            .size(avatarDp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = textStyle,
            color = Color.White,
        )
    }
}

/**
 * Extracts up to two initials from the given display name.
 */
private fun extractInitials(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val parts = trimmed.split(" ").filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
        else -> parts.first().first().uppercaseChar().toString()
    }
}

/**
 * Generates a deterministic color from a name hash using a palette of pleasant colors.
 */
private fun colorFromName(name: String): Color {
    val palette = listOf(
        Color(0xFF1976D2), // Blue
        Color(0xFF388E3C), // Green
        Color(0xFFD32F2F), // Red
        Color(0xFF7B1FA2), // Purple
        Color(0xFFF57C00), // Orange
        Color(0xFF00897B), // Teal
        Color(0xFF5D4037), // Brown
        Color(0xFF455A64), // Blue Grey
        Color(0xFFC2185B), // Pink
        Color(0xFF00ACC1), // Cyan
    )
    val hash = abs(name.hashCode())
    return palette[hash % palette.size]
}
