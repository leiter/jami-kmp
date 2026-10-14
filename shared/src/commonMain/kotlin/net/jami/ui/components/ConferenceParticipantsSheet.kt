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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.ConferenceLayout
import net.jami.ui.viewmodel.ParticipantUi
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom-sheet content listing the conference participants.
 *
 * Moderators get per-participant controls (mute/unmute, promote/demote, lower hand,
 * eject) through [ParticipantContextMenu], plus the mixer layout selector and a
 * "mute all" shortcut. Non-moderators only see the roster and its status badges.
 */
@Composable
fun ConferenceParticipantsSheet(
    participants: List<ParticipantUi>,
    isModerator: Boolean,
    conferenceLayout: Int,
    onSetParticipantMuted: (ParticipantUi, Boolean) -> Unit,
    onSetParticipantModerator: (ParticipantUi, Boolean) -> Unit,
    onLowerHand: (ParticipantUi) -> Unit,
    onEjectParticipant: (ParticipantUi) -> Unit,
    onSetActiveStream: (ParticipantUi, Boolean) -> Unit,
    onMuteAll: () -> Unit,
    onSetLayout: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.m),
    ) {
        Text(
            text = stringResource(Res.string.call_participants_title),
            style = JamiTheme.typography.titleMedium,
            color = JamiTheme.colors.onSurface,
        )

        if (isModerator) {
            Spacer(Modifier.height(JamiTheme.spacing.s))
            ConferenceLayoutSelector(
                conferenceLayout = conferenceLayout,
                onSetLayout = onSetLayout,
            )
        }

        Spacer(Modifier.height(JamiTheme.spacing.s))
        HorizontalDivider()

        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
            items(participants, key = { it.callId.ifEmpty { it.peerUri } }) { participant ->
                ParticipantRow(
                    participant = participant,
                    isModerator = isModerator,
                    onSetParticipantMuted = onSetParticipantMuted,
                    onSetParticipantModerator = onSetParticipantModerator,
                    onLowerHand = onLowerHand,
                    onEjectParticipant = onEjectParticipant,
                    onSetActiveStream = onSetActiveStream,
                )
            }
        }

        if (isModerator && participants.size > 1) {
            HorizontalDivider()
            TextButton(onClick = onMuteAll, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.MicOff, contentDescription = null)
                Spacer(Modifier.size(JamiTheme.spacing.s))
                Text(stringResource(Res.string.call_mute_all))
            }
        }
    }
}

@Composable
private fun ConferenceLayoutSelector(
    conferenceLayout: Int,
    onSetLayout: (Int) -> Unit,
) {
    val layoutDesc = stringResource(Res.string.content_desc_conference_layout)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = layoutDesc,
            style = JamiTheme.typography.labelMedium,
            color = JamiTheme.colors.onSurfaceVariant,
        )
        FilterChip(
            selected = conferenceLayout == ConferenceLayout.GRID,
            onClick = { onSetLayout(ConferenceLayout.GRID) },
            label = { Text(stringResource(Res.string.call_layout_grid)) },
            leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
        )
        FilterChip(
            selected = conferenceLayout == ConferenceLayout.ONE_BIG,
            onClick = { onSetLayout(ConferenceLayout.ONE_BIG) },
            label = { Text(stringResource(Res.string.call_layout_one_big)) },
            leadingIcon = { Icon(Icons.Default.ViewAgenda, contentDescription = null) },
        )
        FilterChip(
            selected = conferenceLayout == ConferenceLayout.ONE_BIG_WITH_SMALL,
            onClick = { onSetLayout(ConferenceLayout.ONE_BIG_WITH_SMALL) },
            label = { Text(stringResource(Res.string.call_layout_one_big_small)) },
            leadingIcon = { Icon(Icons.Default.ViewSidebar, contentDescription = null) },
        )
    }
}

@Composable
private fun ParticipantRow(
    participant: ParticipantUi,
    isModerator: Boolean,
    onSetParticipantMuted: (ParticipantUi, Boolean) -> Unit,
    onSetParticipantModerator: (ParticipantUi, Boolean) -> Unit,
    onLowerHand: (ParticipantUi) -> Unit,
    onEjectParticipant: (ParticipantUi) -> Unit,
    onSetActiveStream: (ParticipantUi, Boolean) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JamiTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = JamiTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.size(JamiTheme.spacing.m))
        Column(modifier = Modifier.fillMaxWidth(0.6f)) {
            Text(
                text = participant.displayName,
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurface,
            )
            val badges = buildList {
                if (participant.isModerator) add(stringResource(Res.string.call_moderator))
                if (participant.isAudioMuted) add(stringResource(Res.string.call_muted))
                if (participant.isHandRaised) add(stringResource(Res.string.call_handRaised))
            }
            if (badges.isNotEmpty()) {
                Text(
                    text = badges.joinToString(" · "),
                    style = JamiTheme.typography.labelSmall,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.fillMaxWidth(0.1f))

        if (participant.isHandRaised) {
            Icon(
                imageVector = Icons.Default.BackHand,
                contentDescription = stringResource(Res.string.call_handRaised),
                tint = JamiTheme.colors.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(JamiTheme.spacing.s))
        }
        if (participant.isModerator) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = stringResource(Res.string.call_moderator),
                tint = JamiTheme.colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        if (isModerator) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.content_desc_participant_options),
                    )
                }
                ParticipantContextMenu(
                    expanded = menuExpanded,
                    participant = participant,
                    isModerator = true,
                    onDismiss = { menuExpanded = false },
                    onMuteAudio = { onSetParticipantMuted(participant, true) },
                    onUnmuteAudio = { onSetParticipantMuted(participant, false) },
                    onRemove = { onEjectParticipant(participant) },
                    onSetModerator = onSetParticipantModerator,
                    onLowerHand = onLowerHand,
                    onSetActiveStream = onSetActiveStream,
                )
            }
        }
    }
}
