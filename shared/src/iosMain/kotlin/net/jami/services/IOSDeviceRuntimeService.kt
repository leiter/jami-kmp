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
package net.jami.services

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Foundation.*
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation of DeviceRuntimeService.
 *
 * Uses Foundation APIs for file paths.
 * Permission checking returns true by default - apps should use native iOS APIs
 * to request and check permissions at the UI level.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSDeviceRuntimeService : DeviceRuntimeService {

    private val fileManager: NSFileManager = NSFileManager.defaultManager

    // UNUserNotificationCenter has no synchronous status API; cache the result of an async probe at startup
    @kotlin.concurrent.Volatile
    private var notificationPermissionGranted: Boolean = true

    init {
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                notificationPermissionGranted =
                    settings?.authorizationStatus == UNAuthorizationStatusAuthorized
            }
    }

    private val documentsDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        (paths.firstOrNull() as? String) ?: ""
    }

    private val cachesDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        )
        (paths.firstOrNull() as? String) ?: ""
    }

    private val conversationsDir: String by lazy {
        val path = "$documentsDir/conversations"
        createDirectoryIfNeeded(path)
        path
    }

    private fun createDirectoryIfNeeded(path: String) {
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(
                path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
    }

    override fun getConversationPath(accountId: String, conversationId: String, name: String): String {
        val dir = "$conversationsDir/$accountId/$conversationId"
        createDirectoryIfNeeded(dir)
        return "$dir/$name"
    }

    override fun getNewConversationPath(accountId: String, conversationId: String, name: String): String {
        var prefix = 0
        var destPath: String
        do {
            val fileName = if (prefix == 0) name else "${prefix}_$name"
            destPath = getConversationPath(accountId, conversationId, fileName)
            prefix++
        } while (fileManager.fileExistsAtPath(destPath))
        return destPath
    }

    override fun getTempPath(): String =
        NSTemporaryDirectory()

    override fun getCachePath(): String =
        cachesDir

    /**
     * The daemon's data directory, where libjami keeps `<accountId>/profile.vcf` and
     * `<accountId>/profiles/`. Must match the path `DaemonBridge.init` hands the daemon
     * (Application Support/jami), as Android's `filesDir` does.
     */
    private val daemonDataDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true
        )
        val appSupport = (paths.firstOrNull() as? String) ?: return@lazy ""
        "$appSupport/jami"
    }

    /**
     * Every caller reads daemon files under this path (`<accountId>/profile.vcf`, the peer
     * vCards under `<accountId>/profiles/`). It used to return Documents/, where none of them
     * exist, so invitations went out without the sender's vCard (`payload=0 bytes`) and avatars
     * read from disk were always missing. See doc/TODO.md, 2026-11-17.
     */
    override fun getDataPath(): String =
        daemonDataDir

    override fun hasStoragePermission(): Boolean =
        true // iOS doesn't require explicit storage permissions for app sandbox

    override fun hasCameraPermission(): Boolean =
        AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized

    override fun hasMicrophonePermission(): Boolean =
        AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) == AVAuthorizationStatusAuthorized

    override fun hasContactsPermission(): Boolean =
        CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts) == CNAuthorizationStatusAuthorized

    override fun hasNotificationsPermission(): Boolean =
        notificationPermissionGranted

    override fun hasLocationPermission(): Boolean {
        val status = CLLocationManager.authorizationStatus()
        return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
               status == kCLAuthorizationStatusAuthorizedAlways
    }

    override fun fileExists(path: String): Boolean =
        fileManager.fileExistsAtPath(path)

    override fun deleteFile(path: String): Boolean =
        fileManager.removeItemAtPath(path, error = null)

    override fun getLocalDeviceName(): String =
        platform.UIKit.UIDevice.currentDevice.name
}
