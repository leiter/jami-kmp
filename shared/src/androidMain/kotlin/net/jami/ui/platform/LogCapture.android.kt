package net.jami.ui.platform

actual fun captureRecentLogs(maxLines: Int): String = try {
    val process = Runtime.getRuntime().exec(
        arrayOf("logcat", "-d", "-t", maxLines.toString(), "-v", "time", "Jami:V", "*:S")
    )
    process.inputStream.bufferedReader().readText()
} catch (e: Exception) {
    "Error capturing logs: ${e.message}"
}
