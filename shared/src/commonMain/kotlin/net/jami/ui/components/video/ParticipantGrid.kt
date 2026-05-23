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
package net.jami.ui.components.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Represents a participant in a video conference.
 */
data class VideoParticipant(
    val id: String,
    val sinkId: String,
    val displayName: String,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isActiveSpeaker: Boolean = false,
    val isLocal: Boolean = false,
    val isModerator: Boolean = false
)

/**
 * Layout modes for the participant grid.
 */
enum class GridLayoutMode {
    AUTO,
    GRID,
    SPOTLIGHT,
    STRIP
}

/**
 * Grid layout for conference call participants.
 *
 * Automatically arranges participants in an optimal grid layout based on count.
 * Supports highlighting the active speaker and showing participant status.
 *
 * @param participants List of conference participants
 * @param modifier Modifier for the container
 * @param layoutMode Layout mode (auto, grid, spotlight, strip)
 * @param showNames Whether to show participant names
 * @param showStatusIcons Whether to show mute/video status icons
 */
@Composable
fun ParticipantGrid(
    participants: List<VideoParticipant>,
    modifier: Modifier = Modifier,
    layoutMode: GridLayoutMode = GridLayoutMode.AUTO,
    showNames: Boolean = true,
    showStatusIcons: Boolean = true
) {
    if (participants.isEmpty()) {
        EmptyParticipantState(modifier)
        return
    }

    when (layoutMode) {
        GridLayoutMode.SPOTLIGHT -> SpotlightLayout(participants, modifier, showNames, showStatusIcons)
        GridLayoutMode.STRIP -> StripLayout(participants, modifier, showNames, showStatusIcons)
        else -> AutoGridLayout(participants, modifier, showNames, showStatusIcons)
    }
}

@Composable
private fun AutoGridLayout(
    participants: List<VideoParticipant>,
    modifier: Modifier,
    showNames: Boolean,
    showStatusIcons: Boolean
) {
    val count = participants.size
    val columns = calculateOptimalColumns(count)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        when {
            count == 1 -> {
                SingleParticipantLayout(
                    participant = participants.first(),
                    modifier = Modifier.fillMaxSize(),
                    showName = showNames,
                    showStatusIcons = showStatusIcons
                )
            }
            count == 2 -> {
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        participants.forEach { participant ->
                            ParticipantTile(
                                participant = participant,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                showName = showNames,
                                showStatusIcons = showStatusIcons
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        participants.forEach { participant ->
                            ParticipantTile(
                                participant = participant,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                showName = showNames,
                                showStatusIcons = showStatusIcons
                            )
                        }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(participants) { participant ->
                        ParticipantTile(
                            participant = participant,
                            modifier = Modifier.aspectRatio(16f / 9f),
                            showName = showNames,
                            showStatusIcons = showStatusIcons
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightLayout(
    participants: List<VideoParticipant>,
    modifier: Modifier,
    showNames: Boolean,
    showStatusIcons: Boolean
) {
    val activeSpeaker = participants.find { it.isActiveSpeaker } ?: participants.first()
    val others = participants.filter { it.id != activeSpeaker.id }

    Column(modifier = modifier.fillMaxSize()) {
        ParticipantTile(
            participant = activeSpeaker,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            showName = showNames,
            showStatusIcons = showStatusIcons
        )

        if (others.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                others.take(4).forEach { participant ->
                    ParticipantTile(
                        participant = participant,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(16f / 9f),
                        showName = showNames,
                        showStatusIcons = showStatusIcons
                    )
                }
            }
        }
    }
}

@Composable
private fun StripLayout(
    participants: List<VideoParticipant>,
    modifier: Modifier,
    showNames: Boolean,
    showStatusIcons: Boolean
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        participants.forEach { participant ->
            ParticipantTile(
                participant = participant,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                showName = showNames,
                showStatusIcons = showStatusIcons
            )
        }
    }
}

@Composable
private fun SingleParticipantLayout(
    participant: VideoParticipant,
    modifier: Modifier,
    showName: Boolean,
    showStatusIcons: Boolean
) {
    ParticipantTile(
        participant = participant,
        modifier = modifier,
        showName = showName,
        showStatusIcons = showStatusIcons
    )
}

/**
 * Individual participant tile with video or avatar.
 */
@Composable
fun ParticipantTile(
    participant: VideoParticipant,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    showStatusIcons: Boolean = true
) {
    val borderColor = if (participant.isActiveSpeaker) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(
                width = if (participant.isActiveSpeaker) 3.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        if (participant.isVideoEnabled) {
            VideoSurface(
                sinkId = participant.sinkId,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ParticipantAvatar(
                displayName = participant.displayName,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showName || showStatusIcons) {
            ParticipantOverlay(
                participant = participant,
                showName = showName,
                showStatusIcons = showStatusIcons,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun ParticipantAvatar(
    displayName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (displayName.isNotEmpty()) {
                    Text(
                        text = displayName.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantOverlay(
    participant: VideoParticipant,
    showName: Boolean,
    showStatusIcons: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showName) {
            Text(
                text = participant.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (showStatusIcons) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (participant.isMuted) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Muted",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Red
                    )
                }
                if (!participant.isVideoEnabled) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "Video off",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyParticipantState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.Gray
            )
            Text(
                text = "Waiting for participants...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun calculateOptimalColumns(count: Int): Int {
    return when {
        count <= 1 -> 1
        count <= 2 -> 2
        count <= 4 -> 2
        count <= 6 -> 3
        count <= 9 -> 3
        else -> ceil(sqrt(count.toDouble())).toInt()
    }
}
