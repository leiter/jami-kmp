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
package net.jami.ui.platform

import androidx.compose.runtime.Composable
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.ui.components.notification.JamiAlertDialog
import net.jami.utils.openAppSettings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The two dialogs PermissionRequesterEffect shows around the system prompt, after
// jami-client-android HomeActivity (local network / notifications):
//  - rationale: the system says the user should be told why first (Android, after one refusal);
//    "OK" then asks, "Later" gives up for now;
//  - blocked: the system will not ask any more (Android "don't ask again", iOS after any refusal),
//    so the only way left is the app's page in the settings.
// Notifications are not covered: onboarding has its own dialogs for them (AccountSummaryScreen).

private class PermissionTexts(val title: StringResource, val rationale: StringResource, val blocked: StringResource)

private fun textsFor(permission: AppPermission): PermissionTexts? = when (permission) {
    AppPermission.Camera -> PermissionTexts(
        Res.string.permission_dialog_camera_title,
        Res.string.permission_dialog_camera_rationale,
        Res.string.permission_dialog_camera_message,
    )
    AppPermission.Microphone -> PermissionTexts(
        Res.string.permission_dialog_microphone_title,
        Res.string.permission_dialog_microphone_rationale,
        Res.string.permission_dialog_microphone_message,
    )
    AppPermission.Location -> PermissionTexts(
        Res.string.permission_dialog_location_title,
        Res.string.permission_dialog_location_rationale,
        Res.string.permission_dialog_location_message,
    )
    AppPermission.Contacts -> PermissionTexts(
        Res.string.permission_dialog_contacts_title,
        Res.string.permission_dialog_contacts_rationale,
        Res.string.permission_dialog_read_contacts_message,
    )
    AppPermission.Notifications -> null
}

/** Whether PermissionRequesterEffect explains and offers the settings for [permission]. */
internal fun hasPermissionDialogs(permission: AppPermission): Boolean = textsFor(permission) != null

/** Why the app needs [permission], before the system asks again. */
@Composable
internal fun PermissionRationaleDialog(permission: AppPermission, onContinue: () -> Unit, onLater: () -> Unit) {
    val texts = textsFor(permission) ?: return
    JamiAlertDialog(
        title = stringResource(texts.title),
        body = stringResource(texts.rationale),
        onConfirm = onContinue,
        onDismiss = onLater,
        dismissText = stringResource(Res.string.permission_dialog_later),
    )
}

/** [permission] is refused for good: say so, and offer the app's page in the settings. */
@Composable
internal fun PermissionBlockedDialog(permission: AppPermission, onDone: () -> Unit) {
    val texts = textsFor(permission) ?: return
    PermissionSettingsDialog(stringResource(texts.title), stringResource(texts.blocked), onDone)
}

/**
 * A permission is off and only the settings can turn it back on: [body] says which, and the
 * dialog offers to open them. Public for the screens that explain a permission of their own.
 */
@Composable
fun PermissionSettingsDialog(title: String, body: String, onDone: () -> Unit) {
    JamiAlertDialog(
        title = title,
        body = body,
        confirmText = stringResource(Res.string.permission_dialog_open_settings),
        dismissText = stringResource(Res.string.permission_dialog_later),
        onConfirm = {
            openAppSettings()
            onDone()
        },
        onDismiss = onDone,
    )
}
