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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PermissionRequesterEffect(
    permission: AppPermission,
    request: Boolean,
    onResult: (granted: Boolean) -> Unit,
) {
    LaunchedEffect(request) {
        if (!request) return@LaunchedEffect
        val granted = when (permission) {
            AppPermission.Camera -> requestAVPermission(AVMediaTypeVideo)
            AppPermission.Microphone -> requestAVPermission(AVMediaTypeAudio)
            AppPermission.Contacts -> requestContactsPermission()
            AppPermission.Notifications -> true // macOS notification permission handled at app level
            AppPermission.Location -> requestLocationPermission()
        }
        onResult(granted)
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun requestAVPermission(mediaType: String): Boolean =
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
private suspend fun requestLocationPermission(): Boolean =
    suspendCancellableCoroutine { cont ->
        val locationManager = CLLocationManager()
        val currentStatus = CLLocationManager.authorizationStatus()

        when (currentStatus) {
            kCLAuthorizationStatusAuthorizedAlways -> {
                cont.resume(true)
            }
            kCLAuthorizationStatusNotDetermined -> {
                // Create a delegate to handle the authorization response
                val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                        val status = CLLocationManager.authorizationStatus()
                        val granted = status == kCLAuthorizationStatusAuthorizedAlways
                        cont.resume(granted)
                    }
                }
                locationManager.delegate = delegate
                locationManager.requestAlwaysAuthorization()
            }
            else -> {
                // Denied or restricted
                cont.resume(false)
            }
        }
    }
