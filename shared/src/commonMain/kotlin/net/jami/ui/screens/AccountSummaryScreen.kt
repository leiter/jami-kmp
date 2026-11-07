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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import net.jami.ui.components.actions.JamiButton
import net.jami.ui.components.notification.JamiAlertDialog
import net.jami.ui.platform.AppPermission
import net.jami.ui.platform.PermissionRequesterEffect
import net.jami.ui.platform.rememberBatteryOptimizationState
import net.jami.ui.theme.JamiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSummaryScreen(
    onContinue: () -> Unit,
) {
    // Request notifications then contacts sequentially on first entry.
    // Notifications are critical — without them incoming calls are missed.
    var requestNotif by remember { mutableStateOf(false) }
    var requestContacts by remember { mutableStateOf(false) }
    var showNotifDeniedDialog by remember { mutableStateOf(false) }
    var batteryDialogPending by remember { mutableStateOf(false) }

    // isExempt is true wherever the platform has nothing of the kind to grant, so the dialog
    // below is Android-only without a platform check here.
    val battery = rememberBatteryOptimizationState()

    LaunchedEffect(Unit) { requestNotif = true }

    PermissionRequesterEffect(AppPermission.Notifications, requestNotif) { granted ->
        requestNotif = false
        if (!granted) showNotifDeniedDialog = true
        requestContacts = true
    }

    PermissionRequesterEffect(AppPermission.Contacts, requestContacts) {
        requestContacts = false
        // Last of the three, and the one with the most teeth on Android: notifications alone do
        // not keep the daemon alive, Doze kills the process a few minutes after the screen goes
        // off. Explained in our own dialog first — the system one gives no reason, and a refusal
        // there is remembered.
        if (!battery.isExempt) batteryDialogPending = true
    }

    if (showNotifDeniedDialog) {
        JamiAlertDialog(
            title = stringResource(Res.string.permission_dialog_post_notifications_title),
            body = stringResource(Res.string.permission_dialog_post_notifications_blocked_message),
            confirmText = "OK",
            dismissText = null,
            onConfirm = { showNotifDeniedDialog = false },
            onDismiss = { showNotifDeniedDialog = false },
        )
    }

    // Queued behind the notifications-denied dialog: the contacts result can arrive while that
    // one is still up, and two stacked dialogs would hide one another.
    if (batteryDialogPending && !showNotifDeniedDialog) {
        JamiAlertDialog(
            title = stringResource(Res.string.permission_dialog_battery_title),
            body = stringResource(Res.string.permission_dialog_battery_message),
            confirmText = stringResource(Res.string.permission_dialog_battery_confirm),
            dismissText = stringResource(Res.string.permission_dialog_battery_dismiss),
            onConfirm = {
                batteryDialogPending = false
                battery.request()
            },
            onDismiss = { batteryDialogPending = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.screen_title_account_created),
                        style = JamiTheme.typography.titleMedium,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                    titleContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = JamiTheme.spacing.l),
        ) {
            Spacer(Modifier.height(JamiTheme.spacing.xl))

            Text(
                text = stringResource(Res.string.account_ready_title),
                style = JamiTheme.typography.headlineSmall,
                color = JamiTheme.colors.onSurface,
            )

            Spacer(Modifier.height(JamiTheme.spacing.l))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = JamiTheme.colors.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(JamiTheme.spacing.l),
                ) {
                    Text(
                        text = stringResource(Res.string.account_info_title),
                        style = JamiTheme.typography.titleSmall,
                        color = JamiTheme.colors.onSurface,
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.m))

                    Text(
                        text = stringResource(Res.string.account_ready_description),
                        style = JamiTheme.typography.bodyMedium,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(JamiTheme.spacing.m))

                    Text(
                        text = stringResource(Res.string.account_backup_reminder),
                        style = JamiTheme.typography.bodySmall,
                        color = JamiTheme.colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(JamiTheme.spacing.xl))

            JamiButton(
                text = stringResource(Res.string.action_continue_to_jami),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
