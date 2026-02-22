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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import net.jami.ui.components.container.JamiScaffold
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.components.content.JamiSectionTitle
import net.jami.ui.components.navigation.JamiTopBar
import net.jami.ui.components.navigation.JamiTopBarStyle
import net.jami.ui.contracts.AccountSettingsContract
import net.jami.ui.contracts.DeviceItem
import net.jami.ui.theme.JamiTheme

/**
 * Account settings screen displaying profile info, linked devices,
 * and navigation to sub-settings.
 *
 * @param profileState The profile state (Tier 2 split).
 * @param devicesState The devices state (Tier 2 split).
 * @param onAction Dispatches settings actions.
 * @param onBack Called when the user navigates back.
 * @param onBlockedContacts Called when "Blocked Contacts" is tapped.
 */
@Composable
fun AccountSettingsScreen(
    profileState: AccountSettingsContract.ProfileState,
    devicesState: AccountSettingsContract.DevicesState,
    onAction: (AccountSettingsContract.Action) -> Unit,
    onBack: () -> Unit,
    onBlockedContacts: () -> Unit,
) {
    JamiScaffold(
        topBar = {
            JamiTopBar(
                style = JamiTopBarStyle.Settings,
                title = "Account Settings",
                onNavigateBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            JamiSectionTitle(title = "Profile")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = JamiTheme.spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(JamiTheme.spacing.m))

                JamiAvatar(
                    displayName = profileState.displayName.ifEmpty { "User" },
                    size = AvatarSize.XLarge,
                )

                Spacer(Modifier.height(JamiTheme.spacing.m))

                Text(
                    text = profileState.displayName.ifEmpty { "No display name" },
                    style = JamiTheme.typography.titleLarge,
                    color = JamiTheme.colors.onSurface,
                )

                if (profileState.username.isNotEmpty()) {
                    Spacer(Modifier.height(JamiTheme.spacing.xs))
                    Text(
                        text = profileState.username,
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }

                if (profileState.identityHash.isNotEmpty()) {
                    Spacer(Modifier.height(JamiTheme.spacing.xs))
                    Text(
                        text = profileState.identityHash,
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(JamiTheme.spacing.l))
            }

            HorizontalDivider()

            JamiSectionTitle(title = "Linked Devices")

            if (devicesState.devices.isEmpty()) {
                Text(
                    text = "No linked devices",
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = JamiTheme.spacing.l,
                        vertical = JamiTheme.spacing.s,
                    ),
                )
            } else {
                devicesState.devices.forEach { device ->
                    DeviceListItem(device = device)
                }
            }

            HorizontalDivider()

            JamiSectionTitle(title = "Settings")

            SettingsLinkRow(label = "Blocked Contacts", onClick = onBlockedContacts)
            SettingsLinkRow(label = "Account", onClick = { /* Navigate to account sub-settings */ })
            SettingsLinkRow(label = "Media", onClick = { /* Navigate to media settings */ })
            SettingsLinkRow(label = "Messages", onClick = { /* Navigate to message settings */ })
            SettingsLinkRow(label = "Advanced", onClick = { /* Navigate to advanced settings */ })

            Spacer(Modifier.height(JamiTheme.spacing.xxl))
        }
    }
}

@Composable
private fun DeviceListItem(device: DeviceItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = JamiTheme.spacing.l,
                vertical = JamiTheme.spacing.m,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Devices,
            contentDescription = null,
            tint = JamiTheme.colors.onSurfaceVariant,
        )

        Spacer(Modifier.width(JamiTheme.spacing.m))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.deviceName,
                style = JamiTheme.typography.bodyLarge,
                color = JamiTheme.colors.onSurface,
            )
            Text(
                text = if (device.isCurrent) "This device" else device.deviceId,
                style = JamiTheme.typography.bodySmall,
                color = JamiTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsLinkRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = JamiTheme.spacing.l,
                vertical = JamiTheme.spacing.m,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = JamiTheme.typography.bodyLarge,
            color = JamiTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = JamiTheme.colors.onSurfaceVariant,
        )
    }
}
