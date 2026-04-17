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
package net.jami.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.di.getViewModel
import net.jami.ui.platform.ContactMarker
import net.jami.ui.platform.OsmMapView
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.LocationSharingViewModel
import net.jami.ui.viewmodel.SharingDuration
import org.jetbrains.compose.resources.stringResource

/**
 * Location sharing screen with OSM map.
 *
 * Allows users to share their location with a contact for a specified duration.
 *
 * @param conversationId The conversation to share location with
 * @param onBack Called when the user navigates back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSharingScreen(
    conversationId: String,
    onBack: () -> Unit,
) {
    val viewModel = getViewModel<LocationSharingViewModel>()
    val state by viewModel.state.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }

    // Load conversation info
    androidx.compose.runtime.LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.conversation_share_location),
                        style = JamiTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_desc_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface.copy(alpha = 0.9f),
                    titleContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Convert contact locations to markers
            val contactMarkers = state.contactLocations.values.map { info ->
                ContactMarker(
                    contactUri = info.contactUri,
                    displayName = info.displayName,
                    latitude = info.location.latitude,
                    longitude = info.location.longitude,
                )
            }

            // Map View
            OsmMapView(
                modifier = Modifier.fillMaxSize(),
                myLocation = state.myLocation,
                contactMarkers = contactMarkers,
                centerOnMyLocation = state.centerOnMyLocation,
                onLocationUpdate = { location ->
                    viewModel.updateLocation(location)
                },
            )

            // Controls overlay at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(JamiTheme.spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Center on me button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.centerOnMyLocation() },
                        modifier = Modifier.size(48.dp),
                        containerColor = JamiTheme.colors.surface,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Center on my location",
                            tint = JamiTheme.colors.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(JamiTheme.spacing.m))

                // Duration selection chips (only shown when not sharing)
                if (!state.isSharing) {
                    Surface(
                        shape = RoundedCornerShape(JamiTheme.radius.l),
                        color = JamiTheme.colors.surface.copy(alpha = 0.9f),
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = JamiTheme.spacing.m,
                                vertical = JamiTheme.spacing.s,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(JamiTheme.spacing.s),
                        ) {
                            FilterChip(
                                selected = state.selectedDuration == SharingDuration.TEN_MINUTES,
                                onClick = { viewModel.selectDuration(SharingDuration.TEN_MINUTES) },
                                label = { Text("10 min") },
                            )
                            FilterChip(
                                selected = state.selectedDuration == SharingDuration.ONE_HOUR,
                                onClick = { viewModel.selectDuration(SharingDuration.ONE_HOUR) },
                                label = { Text("1 hour") },
                            )
                        }
                    }
                } else {
                    // Show remaining time when sharing
                    Surface(
                        shape = RoundedCornerShape(JamiTheme.radius.l),
                        color = JamiTheme.colors.surface.copy(alpha = 0.9f),
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            text = formatRemainingTime(state.remainingSeconds),
                            modifier = Modifier.padding(
                                horizontal = JamiTheme.spacing.l,
                                vertical = JamiTheme.spacing.s,
                            ),
                            style = JamiTheme.typography.bodyMedium,
                            color = JamiTheme.colors.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(JamiTheme.spacing.m))

                // Start/Stop sharing button
                ExtendedFloatingActionButton(
                    onClick = {
                        if (state.isSharing) {
                            viewModel.stopSharing()
                        } else {
                            viewModel.startSharing()
                        }
                    },
                    containerColor = if (state.isSharing) {
                        MaterialTheme.colorScheme.error
                    } else {
                        JamiTheme.colors.primary
                    },
                    contentColor = if (state.isSharing) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        JamiTheme.colors.onPrimary
                    },
                ) {
                    Icon(
                        imageVector = if (state.isSharing) Icons.Default.Stop else Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (state.isSharing) {
                            stringResource(Res.string.location_share_action_stop)
                        } else {
                            stringResource(Res.string.location_share_action_start)
                        },
                    )
                }
            }

            // Info button at bottom right
            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(JamiTheme.spacing.s),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(Res.string.location_share_about_title),
                    tint = JamiTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }

    // OSM Attribution dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(stringResource(Res.string.location_share_about_title))
            },
            text = {
                Text(stringResource(Res.string.location_share_about_message))
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK")
                }
            },
        )
    }
}

/**
 * Format remaining seconds as "X min" or "X h Y min".
 */
private fun formatRemainingTime(seconds: Long): String {
    if (seconds <= 0) return "0 min"
    val minutes = (seconds + 59) / 60 // Round up
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins > 0) "$hours h $mins min" else "$hours h"
    } else {
        "$minutes min"
    }
}
