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

import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import kotlinx.coroutines.runBlocking
import net.jami.IOSConstants
import net.jami.utils.Log
import org.jetbrains.compose.resources.getString
import kotlin.concurrent.Volatile

/**
 * Kotlin side of the iOS Notification Service Extension (see
 * `ios-app/jamiNotificationExtension/NotificationService.swift`).
 *
 * The extension runs in its own process with a ~30 s wall-clock budget. This object owns the
 * logic for turning an opaque APNs push into notification content, so that logic stays in
 * shared, testable Kotlin rather than in Swift.
 *
 * ## Status
 *
 * - Payload parsing, call/message classification, the App Group path, and a localized
 *   **fallback** notification are implemented and safe to ship: today the extension does not
 *   exist at all, so even the fallback ("New message" / "Incoming call") is strictly better
 *   than the current silent drop.
 * - The **daemon-driven decrypt** path — spin up a headless libjami against the shared working
 *   directory, feed it the push, wait for `MessageReceived` / `DataTransferEvent` /
 *   `IncomingCall`, and present the real sender and text — is marked `TODO(nse-daemon)` below.
 *   It needs the Xcode extension target linking libjami (see the extension README) and
 *   on-device verification against the real DHT proxy payload, neither of which can be done on
 *   a Linux host. The shape of that work is described inline.
 */
object NotificationExtensionHandler {

    /** Internal classification. Crosses to Swift as [Result.kind], a lowercase String. */
    enum class Kind {
        MESSAGE, FILE, CALL, REQUEST, FALLBACK, SUPPRESS;

        val wire: String get() = name.lowercase()
    }

    /**
     * Value type handed back to Swift. Deliberately flat — only String / Map<String,String> —
     * so it crosses the Kotlin/Native bridge without enum- or model-mangling surprises. Swift
     * switches on [kind] ("message" | "file" | "call" | "request" | "fallback" | "suppress").
     */
    data class Result(
        val kind: String,
        val title: String,
        val body: String,
        val conversationId: String,
        val categoryIdentifier: String,
        val userInfo: Map<String, String>,
    )

    @Volatile
    private var cancelled = false

    // ---- Swift-facing constant forwarders (exported as NotificationExtensionHandlerKt.*) ----

    /** @see IOSConstants.APP_GROUP_IDENTIFIER */
    fun appGroupIdentifier(): String = IOSConstants.APP_GROUP_IDENTIFIER

    fun darwinQueryAppActive(): String = IOSConstants.DARWIN_QUERY_APP_ACTIVE

    fun darwinAppActiveResponse(): String = IOSConstants.DARWIN_APP_ACTIVE_RESPONSE

    fun pendingNotificationsKey(): String = IOSConstants.SHARED_DEFAULTS_PENDING_NOTIFICATIONS

    /** Absolute path to the shared libjami working directory, or "" when unavailable. */
    fun appGroupDataPath(): String = IOSConstants.appGroupDataPath().orEmpty()

    // ---- Entry points ----

    /**
     * Called from `NotificationService.didReceive`. Runs the classification + (eventually)
     * daemon decrypt, then invokes [completion] exactly once.
     *
     * @param payload flattened APNs `userInfo`
     * @param dataPath shared App Group working directory (may be "")
     * @param timeoutMs budget for the daemon path; the Swift side has its own hard backstop
     */
    fun handlePush(
        payload: Map<String, String>,
        dataPath: String,
        timeoutMs: Long,
        completion: (Result) -> Unit,
    ) {
        cancelled = false
        val classified = classify(payload)
        Log.i(TAG, "handlePush: kind=$classified dataPath=${if (dataPath.isEmpty()) "<none>" else "set"}")

        // A call push must hand off to the app (CallKit / PushKit owns the UX); the extension
        // should not raise a competing local notification for it.
        if (classified == Kind.CALL) {
            completion(Result(Kind.SUPPRESS.wire, "", "", conversationId(payload), "", emptyMap()))
            return
        }

        // TODO(nse-daemon): drive a headless libjami here.
        //   1. DaemonBridge().apply { init(<no-op DaemonCallbacks>) ; start() } against dataPath
        //      (the App Group container — same DB the app uses, so no re-clone).
        //   2. accountService-less: call the bridge's pushNotificationReceived(payload) directly.
        //   3. Suspend on a channel fed by onMessageReceived / onDataTransferEvent for
        //      conversationId(payload), with withTimeout(timeoutMs).
        //   4. Resolve the sender display name (RegisteredNameFound / vCard) for the title.
        //   5. libjami::fini() before returning — the app must be able to take the daemon back.
        //   Blocked on: the Xcode extension target + libjami link (see extension README) and
        //   on-device verification of the real proxy payload keys.

        val result = buildFallback(classified, payload)
        if (!cancelled) completion(result)
    }

    /** Called from `serviceExtensionTimeWillExpire` — stop waiting on the daemon path. */
    fun cancel() {
        cancelled = true
    }

    // ---- Classification ----

    private fun classify(payload: Map<String, String>): Kind = when {
        // The DHT proxy marks VoIP pushes; exact keys must be confirmed on device. These are
        // the fields the CallKit push path (IOSPushServiceManager.onVoipPushReceived) reads.
        payload.containsKey("peerId") || payload["type"] == "call" -> Kind.CALL
        payload["type"] == "file" -> Kind.FILE
        payload.containsKey("request") -> Kind.REQUEST
        else -> Kind.MESSAGE
    }

    private fun conversationId(payload: Map<String, String>): String =
        payload["conversationId"] ?: payload["conversation"] ?: ""

    private fun accountId(payload: Map<String, String>): String =
        payload["to"] ?: payload["accountId"] ?: ""

    // ---- Fallback content ----

    private fun buildFallback(kind: Kind, payload: Map<String, String>): Result {
        val (title, body) = runBlocking {
            val appName = getString(Res.string.notif_generic_title)
            when (kind) {
                Kind.FILE -> appName to getString(Res.string.notif_new_file)
                Kind.REQUEST -> appName to getString(Res.string.new_invitation_request_title)
                else -> appName to getString(Res.string.notif_new_message)
            }
        }
        return Result(
            kind = (if (kind == Kind.CALL) Kind.FALLBACK else kind).wire,
            title = title,
            body = body,
            conversationId = conversationId(payload),
            categoryIdentifier = "",
            userInfo = mapOf(
                "accountId" to accountId(payload),
                "conversationId" to conversationId(payload),
            ),
        )
    }

    private const val TAG = "NotificationExtensionHandler"
}

// ---- Top-level exports so Swift sees NotificationExtensionHandlerKt.<name>() ----

fun handlePush(
    payload: Map<String, String>,
    dataPath: String,
    timeoutMs: Long,
    completion: (NotificationExtensionHandler.Result) -> Unit,
) = NotificationExtensionHandler.handlePush(payload, dataPath, timeoutMs, completion)

fun cancel() = NotificationExtensionHandler.cancel()

fun appGroupDataPath(): String = NotificationExtensionHandler.appGroupDataPath()
fun appGroupIdentifier(): String = NotificationExtensionHandler.appGroupIdentifier()
fun darwinQueryAppActive(): String = NotificationExtensionHandler.darwinQueryAppActive()
fun darwinAppActiveResponse(): String = NotificationExtensionHandler.darwinAppActiveResponse()
fun pendingNotificationsKey(): String = NotificationExtensionHandler.pendingNotificationsKey()
