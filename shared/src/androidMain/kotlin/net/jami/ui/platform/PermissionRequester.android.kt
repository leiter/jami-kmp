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

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Asks for [permission] when [request] turns true, the way jami-client-android HomeActivity asks
 * for notifications and local network:
 *  - already granted → granted, no prompt;
 *  - the system says a rationale is due (refused once) → explain first, then ask; "Later" → denied;
 *  - asked before and no rationale due → "don't ask again" is in force and the system would
 *    refuse without showing anything, so say so and offer the app's settings page → denied;
 *  - otherwise → the system prompt.
 * Notifications keep the plain prompt: onboarding explains them itself (AccountSummaryScreen).
 */
@Composable
actual fun PermissionRequesterEffect(
    permission: AppPermission,
    request: Boolean,
    onResult: (granted: Boolean) -> Unit,
) {
    val manifestPermission: String? = when (permission) {
        AppPermission.Camera -> Manifest.permission.CAMERA
        AppPermission.Microphone -> Manifest.permission.RECORD_AUDIO
        AppPermission.Contacts -> Manifest.permission.READ_CONTACTS
        AppPermission.Notifications ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS
            else null // Pre-Android 13: notifications are always allowed
        AppPermission.Location -> Manifest.permission.ACCESS_FINE_LOCATION
    }
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var showRationale by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { currentOnResult(it) },
    )

    fun launch(perm: String) {
        LocalPrefs.setBoolean(askedKey(perm), true)
        launcher.launch(perm)
    }

    LaunchedEffect(request) {
        if (!request) return@LaunchedEffect
        val perm = manifestPermission
        when {
            // Permission not needed on this API level — grant immediately
            perm == null -> currentOnResult(true)
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED ->
                currentOnResult(true)
            !hasPermissionDialogs(permission) -> launch(perm)
            context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, perm) } == true ->
                showRationale = true
            LocalPrefs.getBoolean(askedKey(perm), false) -> showBlocked = true
            else -> launch(perm)
        }
    }

    if (showRationale) {
        PermissionRationaleDialog(
            permission = permission,
            onContinue = {
                showRationale = false
                manifestPermission?.let { launch(it) }
            },
            onLater = {
                showRationale = false
                currentOnResult(false)
            },
        )
    }
    if (showBlocked) {
        PermissionBlockedDialog(permission) {
            showBlocked = false
            currentOnResult(false)
        }
    }
}

/** Remembers that the system prompt was shown once, which is what tells "never asked" apart
 *  from "don't ask again": the system reports no rationale due for both. */
private fun askedKey(manifestPermission: String) = "permission_asked_$manifestPermission"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
