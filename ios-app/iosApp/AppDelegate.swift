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

import UIKit
import UserNotifications
import JamiShared

class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // 1. Initialize Koin dependency injection.
        // Intentionally NOT wrapped in do/catch: doInitKoin() is no longer @Throws, so any
        // failure propagates as an unhandled Kotlin exception and the crash report carries
        // the original throwing stack instead of being silently swallowed here.
        NSLog("JAMI_KOIN_APPDELEGATE didFinishLaunching: calling doInitKoin()")
        KoinInitKt.doInitKoin()
        NSLog("JAMI_KOIN_APPDELEGATE doInitKoin() returned OK")

        // 2. Start the Jami daemon (mirrors JamiApplication.onCreate on Android)
        NSLog("JAMI_KOIN_APPDELEGATE calling startJami()")
        IOSApplicationHelperKt.startJami()
        NSLog("JAMI_KOIN_APPDELEGATE startJami() returned")

        // 3. Register for remote notifications (token delivery handled by daemon when push is wired)
        application.registerForRemoteNotifications()

        // 4. Set notification delegate so foreground notifications are delivered
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared

        return true
    }

    func applicationWillTerminate(_ application: UIApplication) {
        IOSApplicationHelperKt.stopJami()
    }

    // MARK: - Remote notifications (placeholder — APNs not yet integrated)

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // TODO: deliver token to daemon via setPushNotificationToken once APNs integration is wired
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        print("[Jami] APNs device token: \(token)")
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("[Jami] Failed to register for remote notifications: \(error)")
    }
}
