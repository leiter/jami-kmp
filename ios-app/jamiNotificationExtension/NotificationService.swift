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

/// Turns a message push into something the user sees while Jami is suspended (I7).
///
/// A DHT proxy push carries only an opaque wake-up: the account (`to`) and the key of an
/// encrypted DHT value (`key`), never the message. jami-client-ios starts a second daemon
/// inside its extension to fetch and decrypt it. That is not possible here yet: a Notification
/// Service Extension has a memory limit of about 24 MB, far below the shared Kotlin framework
/// with libjami. So this extension stays small and does what can be done without the daemon:
///
/// - Jami is running (it answers a Darwin notification within 0.3 s): its own daemon receives
///   the message and posts the real notification, so the push shows nothing.
/// - A resubscribe push (`timeout`): the proxy renewing its subscription; nothing to show.
/// - Otherwise: a generic "New message" notification, grouped per account; opening it starts
///   Jami, whose daemon then syncs. Nothing about the sender or the text leaks.
///
/// Pushes only arrive once a push-capable DHT proxy holds APNs credentials (I12,
/// doc/push-notifications.md).
final class NotificationService: UNNotificationServiceExtension {

    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var bestAttempt: UNMutableNotificationContent?

    override func didReceive(_ request: UNNotificationRequest,
                             withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        self.contentHandler = contentHandler
        let content = (request.content.mutableCopy() as? UNMutableNotificationContent) ?? UNMutableNotificationContent()
        bestAttempt = content
        let payload = PushPayload(userInfo: request.content.userInfo)

        switch payload.disposition(appIsRunning: AppActivityProbe.appIsRunning()) {
        case .suppress:
            deliver(UNMutableNotificationContent())
        case .genericMessage:
            content.title = NSLocalizedString("notification_title", comment: "App name as title")
            content.body = NSLocalizedString("notification_new_message", comment: "Generic new message")
            content.sound = .default
            content.threadIdentifier = payload.accountId ?? "jami"
            content.userInfo = ["accountId": payload.accountId ?? ""]
            deliver(content)
        }
    }

    override func serviceExtensionTimeWillExpire() {
        if let bestAttempt { deliver(bestAttempt) }
    }

    private func deliver(_ content: UNNotificationContent) {
        contentHandler?(content)
        contentHandler = nil
    }
}

/// What a push carries, as the DHT proxy sends it (jami-client-ios NotificationField).
struct PushPayload {
    enum Disposition { case suppress, genericMessage }

    let accountId: String?
    let isResubscribe: Bool

    init(userInfo: [AnyHashable: Any]) {
        func value(_ key: String) -> String? {
            guard let raw = userInfo[key] else { return nil }
            let text = String(describing: raw)
            return text.isEmpty || text == "<null>" ? nil : text
        }
        accountId = value("to")
        isResubscribe = value("timeout") != nil
    }

    func disposition(appIsRunning: Bool) -> Disposition {
        if appIsRunning || isResubscribe || accountId == nil { return .suppress }
        return .genericMessage
    }
}

/// Asks the app whether it is running, over Darwin notifications: they need no App Group,
/// so this works before the group exists. The app answers in `AppDelegate`
/// (`NotificationExtensionPing`).
enum AppActivityProbe {
    static let query = "net.jami.kmp.nse.isAppRunning" as CFString
    static let answer = "net.jami.kmp.nse.appIsRunning" as CFString

    private static var answered = false
    private final class Token {}
    private static let token = Token()

    static func appIsRunning(timeout: TimeInterval = 0.3) -> Bool {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        let observer = Unmanaged.passUnretained(token).toOpaque()
        answered = false
        CFNotificationCenterAddObserver(center, observer, { _, _, _, _, _ in
            AppActivityProbe.answered = true
        }, answer, nil, .deliverImmediately)
        defer { CFNotificationCenterRemoveObserver(center, observer, CFNotificationName(answer), nil) }
        CFNotificationCenterPostNotification(center, CFNotificationName(query), nil, nil, true)
        let deadline = Date().addingTimeInterval(timeout)
        while !answered && Date() < deadline {
            RunLoop.current.run(mode: .default, before: Date().addingTimeInterval(0.02))
        }
        return answered
    }
}
