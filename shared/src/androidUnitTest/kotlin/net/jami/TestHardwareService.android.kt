package net.jami

import android.content.ContextWrapper
import net.jami.services.expect.HardwareService

// Placeholder Context: HardwareService only touches it lazily (audio manager on first use).
actual fun testHardwareService(): HardwareService = HardwareService(ContextWrapper(null))
