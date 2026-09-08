package net.jami.ui.platform

import android.os.Process

/**
 * Captures recent logcat output for this process.
 *
 * Filters by PID rather than the "Jami" tag: the daemon's native (libjami) layer logs each
 * line under its own source-filename tag via `__android_log_write`, never under "Jami" —
 * a tag filter silently excludes all daemon-level logs. PID scoping picks up both the app's
 * own "Jami"-tagged logs and every daemon log line in one pass.
 */
actual fun captureRecentLogs(maxLines: Int): String = try {
    val pid = Process.myPid().toString()
    val process = Runtime.getRuntime().exec(
        arrayOf("logcat", "-d", "-t", maxLines.toString(), "-v", "time", "--pid=$pid")
    )
    process.inputStream.bufferedReader().readText()
} catch (e: Exception) {
    "Error capturing logs: ${e.message}"
}
