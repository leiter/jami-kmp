package net.jami.ui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.bridge.JamiBridgeWrapper
import net.jami.utils.AppLogBuffer
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile

/**
 * The last crash (if any), the app's own log of this launch, then the native/daemon log.
 *
 * The crash text is what the unhandled-exception hook (KoinDiagnostics.ios.kt) saved: the
 * TestFlight crash report has the backtrace but never the exception message.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun captureRecentLogs(maxLines: Int): String = buildString {
    lastCrash()?.let {
        append("===== Last crash =====\n").append(it).append("\n\n")
    }
    append("===== App log (this launch) =====\n")
    append(AppLogBuffer.recent(maxLines)).append("\n\n")
    append("===== Native and daemon log =====\n")
    append(JamiBridgeWrapper.shared().captureRecentLogs(maxLines) ?: "")
}

/** The native/daemon log files, the saved crash report and this launch's app log. */
@OptIn(ExperimentalForeignApi::class)
actual fun clearLogs() {
    JamiBridgeWrapper.shared().clearLogs()
    AppLogBuffer.clear()
}

@OptIn(ExperimentalForeignApi::class)
private fun lastCrash(): String? {
    val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: return null
    return NSString.stringWithContentsOfFile("$docs/jami_last_crash.txt", NSUTF8StringEncoding, null)
}
