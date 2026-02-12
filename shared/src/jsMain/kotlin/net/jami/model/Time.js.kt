package net.jami.model

import kotlin.js.Date

internal actual fun currentTimeMillis(): Long = Date.now().toLong()
