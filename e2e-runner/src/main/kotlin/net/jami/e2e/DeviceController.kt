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

    /**
     * Capture the device screen into [file] as a PNG (`adb exec-out screencap -p`).
     * Reads raw bytes — must NOT merge stderr, which would corrupt the image. Returns
     * true on success; logs and returns false otherwise (never throws — diagnostics
     * must not derail a run or flip a verdict).
     */
    fun screenshot(file: File): Boolean {
        file.parentFile?.mkdirs()
        return try {
            val cmd = listOf(adbPath(), "-s", serial, "exec-out", "screencap", "-p")
            val p = ProcessBuilder(cmd).redirectErrorStream(false).start()
            val bytes = p.inputStream.readBytes()
            val err = p.errorStream.bufferedReader().readText()
            val code = p.waitFor()
            if (code != 0 || bytes.isEmpty()) {
                System.err.println("[adb] screenshot exit=$code on $serial: ${err.trim()}")
                false
            } else {
                file.writeBytes(bytes)
                true
            }
        } catch (e: Exception) {
            System.err.println("[adb] screenshot failed on $serial: ${e.message}")
            false
        }
    }

    /**
     * Pull a file from the harness app's private `filesDir` into [local]. Uses
     * `run-as <app> cat files/<name>` (the harness build is debuggable, so this works
     * without root and is immune to scoped-storage restrictions on external dirs).
     * Reads raw bytes — must NOT merge stderr. Returns true on success.
     */
    fun pull(deviceFileName: String, local: File): Boolean {
        local.parentFile?.mkdirs()
        return try {
            val cmd = listOf(
                adbPath(), "-s", serial, "exec-out", "run-as", HARNESS_APP_ID, "cat", "files/$deviceFileName",
            )
            val p = ProcessBuilder(cmd).redirectErrorStream(false).start()
            val bytes = p.inputStream.readBytes()
            val err = p.errorStream.bufferedReader().readText()
            val code = p.waitFor()
            if (code != 0 || bytes.isEmpty()) {
                System.err.println("[adb] pull exit=$code on $serial ($deviceFileName): ${err.trim()}")
                false
            } else {
                local.writeBytes(bytes)
                true
            }
        } catch (e: Exception) {
            System.err.println("[adb] pull failed on $serial ($deviceFileName): ${e.message}")
            false
        }
    }

    /**
     * Push [local] into the harness app's private `filesDir` as <name>. Stages through
     * `/data/local/tmp` (app-writable target needs `run-as cp` from there). Returns true
     * on success.
     */
    fun push(local: File, deviceFileName: String): Boolean {
        if (!local.exists()) {
            System.err.println("[adb] push source missing: ${local.absolutePath}")
            return false
        }
        val tmp = "/data/local/tmp/harness_$deviceFileName"
        return try {
            if (adbExit("-s", serial, "push", local.absolutePath, tmp) != 0) return false
            val copied = adbExit(
                "-s", serial, "shell", "run-as", HARNESS_APP_ID, "cp", tmp, "files/$deviceFileName",
            ) == 0
            adbExit("-s", serial, "shell", "rm", "-f", tmp)
            copied
        } catch (e: Exception) {
            System.err.println("[adb] push failed on $serial ($deviceFileName): ${e.message}")
            false
        }
    }

    private fun adb(vararg args: String) {
        adbExit(*args)
    }

    /** Run an adb command; logs and returns the exit code (non-zero is surfaced). */
    private fun adbExit(vararg args: String): Int {
        val cmd = listOf(adbPath()) + args
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0) {
            System.err.println("[adb] exit=$code  ${cmd.joinToString(" ")}\n$out")
        }
        return code
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
