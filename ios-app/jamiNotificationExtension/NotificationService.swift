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

import UserNotifications
import CoreFoundation
import JamiShared

/*
 * Notification Service Extension.
 *
 * On iOS a P2P client cannot hold a socket open while suspended, so an APNs push carries only
 * an opaque wake-up. This extension turns that wake-up into a real notification:
 *
 *   1. Parse the push payload.
 *   2. Ask the main app (via a Darwin notification) whether it is already running. If it is, it
 *      owns the live daemon — stash the payload for it and present nothing here.
 *   3. Otherwise drive a headless libjami instance, pointed at the shared App Group working
 *      directory, until the message / file / call info for this push has been decrypted, then
 *      present a local notification.
 *   4. Always call the OS completion handler before the ~30 s wall-clock budget expires;
 *      `serviceExtensionTimeWillExpire()` is the backstop.
 *
 * The heavy lifting in step 3 lives in Kotlin (`NotificationExtensionHandler`) so it stays
 * unit-testable and shared with any future macOS extension. This file owns only the iOS
 * extension lifecycle and the app-active handshake.
 *
 * Ported/adapted from jami-client-ios `jamiNotificationExtension/NotificationService.swift`,
 * trimmed to jami-kmp's needs.
 */
final class NotificationService: UNNotificationServiceExtension {

    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var pendingContent: UNMutableNotificationContent?

    /// How long to wait for the main app to answer the "are you active?" ping.
    private let appActiveProbeTimeout: TimeInterval = 0.3

    override func didReceive(_ request: UNNotificationRequest,
                             withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        self.contentHandler = contentHandler
        self.pendingContent = (request.content.mutableCopy() as? UNMutableNotificationContent)

        let payload = flatten(request.content.userInfo)
        NSLog("[JamiNSE] didReceive push, keys=\(payload.keys.sorted())")

        // Step 2: if the app is foregrounded/backgrounded-and-alive, let it handle sync.
        if mainAppIsActive() {
            NSLog("[JamiNSE] main app is active — handing off, suppressing extension notification")
            stashPayloadForApp(payload)
            suppress()
            return
        }

        // Step 3: headless daemon path (Kotlin). `result` is the bridged
        // NotificationExtensionHandler.Result — a flat value type (all String / [String:String]).
        NotificationExtensionHandlerKt.handlePush(
            payload: payload,
            dataPath: sharedDataPath() ?? "",
            timeoutMs: 25_000
        ) { [weak self] result in
            guard let self = self else { return }
            self.present(from: result)
        }
    }

    override func serviceExtensionTimeWillExpire() {
        NSLog("[JamiNSE] time will expire — presenting best-effort content")
        NotificationExtensionHandlerKt.cancel()
        if let content = pendingContent {
            // Leave the OS-provided fallback body rather than a blank notification.
            contentHandler?(content)
        }
        contentHandler = nil
    }

    // MARK: - Presentation

    private func present(from result: NotificationExtensionHandlerResult) {
        defer { contentHandler = nil }
        guard let handler = contentHandler else { return }

        if result.kind == "suppress" {
            suppress()
            return
        }

        let content = pendingContent ?? UNMutableNotificationContent()
        if !result.title.isEmpty { content.title = result.title }
        if !result.body.isEmpty { content.body = result.body }
        content.threadIdentifier = result.conversationId
        if !result.categoryIdentifier.isEmpty {
            content.categoryIdentifier = result.categoryIdentifier
        }
        if !result.userInfo.isEmpty {
            content.userInfo = result.userInfo
        }
        handler(content)
    }

    /// Present nothing. iOS still requires the completion handler to run; an empty content with
    /// no alert body and a zero-badge is the documented way to drop a notification silently.
    private func suppress() {
        defer { contentHandler = nil }
        let empty = UNMutableNotificationContent()
        empty.title = ""
        empty.body = ""
        contentHandler?(empty)
    }

    // MARK: - App-active handshake (Darwin notifications)

    private func mainAppIsActive() -> Bool {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        var didRespond = false

        let observer = Unmanaged.passUnretained(self).toOpaque()
        let responseName = NotificationExtensionHandlerKt.darwinAppActiveResponse() as CFString
        CFNotificationCenterAddObserver(center, observer, { _, obs, _, _, _ in
            guard let obs = obs else { return }
            let this = Unmanaged<NotificationService>.fromOpaque(obs).takeUnretainedValue()
            this.appActiveFlag = true
        }, responseName, nil, .deliverImmediately)

        appActiveFlag = false
        CFNotificationCenterPostNotification(
            center,
            CFNotificationName(NotificationExtensionHandlerKt.darwinQueryAppActive() as CFString),
            nil, nil, true
        )

        // Spin the run loop briefly waiting for a reply.
        let deadline = Date().addingTimeInterval(appActiveProbeTimeout)
        while Date() < deadline && !appActiveFlag {
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.02))
        }
        didRespond = appActiveFlag

        CFNotificationCenterRemoveObserver(center, observer, CFNotificationName(responseName), nil)
        return didRespond
    }

    private var appActiveFlag = false

    // MARK: - Shared container helpers

    private func stashPayloadForApp(_ payload: [String: String]) {
        guard let defaults = UserDefaults(suiteName: NotificationExtensionHandlerKt.appGroupIdentifier()) else { return }
        let key = NotificationExtensionHandlerKt.pendingNotificationsKey()
        var pending = defaults.array(forKey: key) as? [[String: String]] ?? []
        pending.append(payload)
        defaults.set(pending, forKey: key)
    }

    private func sharedDataPath() -> String? {
        NotificationExtensionHandlerKt.appGroupDataPath()
    }

    private func flatten(_ userInfo: [AnyHashable: Any]) -> [String: String] {
        var out: [String: String] = [:]
        for (k, v) in userInfo { out[String(describing: k)] = String(describing: v) }
        return out
    }
}
