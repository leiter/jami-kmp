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
package net.jami.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import net.jami.model.VideoQuality
import net.jami.ui.theme.JamiTheme

/**
 * Quality selector dropdown for video calls.
 *
 * Shows options: Low, Medium (default), High, Ultra
 */
@Composable
fun VideoQualityControl(
    currentQuality: VideoQuality,
    onQualitySelected: (VideoQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { expanded = !expanded }
            .padding(JamiTheme.spacing.m),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Quality: ${currentQuality.label}",
                style = JamiTheme.typography.bodyMedium,
                color = Color.White
            )

            Text(
                text = "⋮",
                color = Color.White,
                modifier = Modifier.padding(start = JamiTheme.spacing.m)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            VideoQuality.entries.forEach { quality ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(quality.label)
                            Text(
                                text = "${quality.width}x${quality.height} @ ${quality.frameRate}fps",
                                style = JamiTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    },
                    onClick = {
                        onQualitySelected(quality)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Quick quality preset buttons (Low/Medium/High/Ultra).
 */
@Composable
fun QuickQualityButtons(
    currentQuality: VideoQuality,
    onQualitySelected: (VideoQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(JamiTheme.spacing.m),
        horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoQuality.entries.forEach { quality ->
            val isSelected = quality == currentQuality
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected) JamiTheme.colors.primary else Color.Gray,
                        shape = JamiTheme.shapes.small
                    )
                    .clickable { onQualitySelected(quality) }
                    .padding(JamiTheme.spacing.s),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = quality.label,
                    style = JamiTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}
