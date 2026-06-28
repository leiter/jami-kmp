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
package net.jami.e2e

import java.io.File

/** harness flavor application id (standard id + `.harness` suffix). */
const val HARNESS_APP_ID = "net.jami.android.harness"
const val MAIN_ACTIVITY = "net.jami.android.MainActivity"
const val AGENT_SERVICE = "net.jami.android.harness.HarnessAgentService"

/** Thin wrapper over `adb`/`am` to install/reverse/launch on one device. */
class DeviceController(val serial: String) {

    fun adbReverse(port: Int) = adb("-s", serial, "reverse", "tcp:$port", "tcp:$port")

    fun adbReverseRemove(port: Int) = adb("-s", serial, "reverse", "--remove", "tcp:$port")

    /** Launch the host app (boots Koin + daemon + foreground daemon service). */
    fun startApp() = adb("-s", serial, "shell", "am", "start", "-n", "$HARNESS_APP_ID/$MAIN_ACTIVITY")

    /** Start the on-device harness agent service. */
    fun startAgent() =
        adb("-s", serial, "shell", "am", "start-foreground-service", "-n", "$HARNESS_APP_ID/$AGENT_SERVICE")

    private fun adb(vararg args: String) {
        val cmd = listOf(adbPath()) + args
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0) {
            System.err.println("[adb] exit=$code  ${cmd.joinToString(" ")}\n$out")
        }
    }

    companion object {
        private fun adbPath(): String {
            val home = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            if (home != null) {
                val f = File(home, "platform-tools/adb")
                if (f.exists()) return f.absolutePath
            }
            return "adb"
        }

        /** Parse `adb devices` for online device serials. */
        fun listDevices(): List<String> {
            val p = ProcessBuilder(listOf(adbPath(), "devices"))
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            return out.lineSequence()
                .drop(1)
                .mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2 && parts[1] == "device") parts[0] else null
                }
                .toList()
        }
    }
}
