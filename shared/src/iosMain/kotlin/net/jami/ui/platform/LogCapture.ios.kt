package net.jami.ui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.bridge.JamiBridgeWrapper

@OptIn(ExperimentalForeignApi::class)
actual fun captureRecentLogs(maxLines: Int): String =
    JamiBridgeWrapper.shared().captureRecentLogs(maxLines) ?: ""
