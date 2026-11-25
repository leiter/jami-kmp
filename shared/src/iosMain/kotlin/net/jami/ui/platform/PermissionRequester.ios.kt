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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.Contacts.CNAuthorizationStatusDenied
import platform.Contacts.CNAuthorizationStatusRestricted
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Asks for [permission] when [request] turns true.
 *
 * iOS shows its prompt once: after a refusal it answers "no" without asking, and the app looked
 * as if the button did nothing. So when the permission is already refused, the user is told and
 * offered the app's settings page instead (PermissionBlockedDialog) and the result is "denied".
 * There is no rationale step on iOS: the system prompt carries the Info.plist purpose string.
 * Notifications keep the plain prompt: onboarding explains them itself (AccountSummaryScreen).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PermissionRequesterEffect(
    permission: AppPermission,
    request: Boolean,
    onResult: (granted: Boolean) -> Unit,
) {
    val currentOnResult by rememberUpdatedState(onResult)
    var showBlocked by remember { mutableStateOf(false) }
    if (showBlocked) {
        PermissionBlockedDialog(permission) {
            showBlocked = false
            currentOnResult(false)
        }
    }
    LaunchedEffect(request) {
        if (!request) return@LaunchedEffect
        if (hasPermissionDialogs(permission) && isRefused(permission)) {
            showBlocked = true
            return@LaunchedEffect
        }
        val granted = when (permission) {
            AppPermission.Camera -> requestAVPermission(AVMediaTypeVideo)
            AppPermission.Microphone -> requestAVPermission(AVMediaTypeAudio)
            AppPermission.Contacts -> requestContactsPermission()
            AppPermission.Notifications -> requestNotificationsPermission()
            AppPermission.Location -> requestLocationPermission()
        }
        currentOnResult(granted)
    }
}

/** Refused or restricted already: the system would answer "no" without showing a prompt. */
@OptIn(ExperimentalForeignApi::class)
private fun isRefused(permission: AppPermission): Boolean = when (permission) {
    AppPermission.Camera -> avRefused(AVMediaTypeVideo)
    AppPermission.Microphone -> avRefused(AVMediaTypeAudio)
    AppPermission.Contacts -> CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts).let {
        it == CNAuthorizationStatusDenied ||
            it == CNAuthorizationStatusRestricted
    }
    AppPermission.Location -> CLLocationManager.authorizationStatus().let {
        it == kCLAuthorizationStatusDenied || it == kCLAuthorizationStatusRestricted
    }
    AppPermission.Notifications -> false
}

@OptIn(ExperimentalForeignApi::class)
private fun avRefused(mediaType: String?): Boolean =
    AVCaptureDevice.authorizationStatusForMediaType(mediaType).let {
        it == AVAuthorizationStatusDenied || it == AVAuthorizationStatusRestricted
    }

@OptIn(ExperimentalForeignApi::class)
private suspend fun requestAVPermission(mediaType: String?): Boolean =
    suspendCancellableCoroutine { cont ->
        AVCaptureDevice.requestAccessForMediaType(mediaType) { granted ->
            cont.resume(granted)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private suspend fun requestContactsPermission(): Boolean =
    suspendCancellableCoroutine { cont ->
        CNContactStore().requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { granted, _ ->
            cont.resume(granted)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private suspend fun requestNotificationsPermission(): Boolean =
    suspendCancellableCoroutine { cont ->
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(options) { granted, _ ->
                cont.resume(granted)
            }
    }

@OptIn(ExperimentalForeignApi::class)
private suspend fun requestLocationPermission(): Boolean =
    suspendCancellableCoroutine { cont ->
        val locationManager = CLLocationManager()
        val currentStatus = CLLocationManager.authorizationStatus()

        when (currentStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways -> {
                cont.resume(true)
            }
            kCLAuthorizationStatusNotDetermined -> {
                // Create a delegate to handle the authorization response
                val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                        val status = CLLocationManager.authorizationStatus()
                        val granted = status == kCLAuthorizationStatusAuthorizedWhenInUse ||
                                status == kCLAuthorizationStatusAuthorizedAlways
                        cont.resume(granted)
                    }
                }
                locationManager.delegate = delegate
                locationManager.requestWhenInUseAuthorization()
            }
            else -> {
                // Denied or restricted
                cont.resume(false)
            }
        }
    }
