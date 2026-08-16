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

/**
 * Other real Jami apps that may be installed on the same lab devices — the standard
 * (non-harness) jami-kmp build and the jami-android-client reference app. Both run a real
 * daemon against the real DHT; if either is left running while the harness drives its own
 * daemon on the same physical device/network, it's a source of resource contention (wake
 * locks, sockets, notifications) and confusing manual-testing crosstalk. Force-stopped as a
 * precondition before every harness run — see [DeviceController.stopCompetingApps].
 */
val COMPETING_APP_IDS = listOf("net.jami.android", "cx.ring")

/** Thin wrapper over `adb`/`am` to install/reverse/launch on one device. */
class DeviceController(val serial: String) {

    fun adbReverse(port: Int) = adb("-s", serial, "reverse", "tcp:$port", "tcp:$port")

    fun adbReverseRemove(port: Int) = adb("-s", serial, "reverse", "--remove", "tcp:$port")

    /** Launch the host app (boots Koin + daemon + foreground daemon service). */
    fun startApp() = adb("-s", serial, "shell", "am", "start", "-n", "$HARNESS_APP_ID/$MAIN_ACTIVITY")

    /**
     * Start the on-device harness agent service. [role], when given, is passed through as an
     * intent extra so the agent requests that exact role on `Hello` instead of taking whatever
     * the server's connect-order queue hands out — needed when relaunching after a mid-scenario
     * restart (e.g. [restoreAppData]), where the role is already fixed and the queue is empty.
     */
    fun startAgent(role: String? = null) {
        val args = mutableListOf("-s", serial, "shell", "am", "start-foreground-service", "-n", "$HARNESS_APP_ID/$AGENT_SERVICE")
        if (role != null) args += listOf("--es", "role", role)
        adb(*args.toTypedArray())
    }

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

    /**
     * Capture the harness app's **entire private data directory** (account archive, cached
     * `profile.vcf`, the SQLDelight history DB, …) as a tar stream into [local]. Unlike [pull]
     * (single file, `files/` only), this is the primitive behind conversation-pair fixtures:
     * seeded message history lives only in the local DB file, which the account-archive export
     * never includes. `run-as` starts in the app's data root, so a bare relative `.` tars
     * everything under it. Relies on toybox `tar` being present under `run-as` (true on the
     * modern lab devices; not guaranteed on very old `minSdk` targets).
     */
    fun snapshotAppData(local: File): Boolean {
        local.parentFile?.mkdirs()
        return try {
            val cmd = listOf(adbPath(), "-s", serial, "exec-out", "run-as", HARNESS_APP_ID, "tar", "-cf", "-", ".")
            val p = ProcessBuilder(cmd).redirectErrorStream(false).start()
            val bytes = p.inputStream.readBytes()
            val err = p.errorStream.bufferedReader().readText()
            val code = p.waitFor()
            if (code != 0 || bytes.isEmpty()) {
                System.err.println("[adb] snapshotAppData exit=$code on $serial: ${err.trim()}")
                false
            } else {
                local.writeBytes(bytes)
                true
            }
        } catch (e: Exception) {
            System.err.println("[adb] snapshotAppData failed on $serial: ${e.message}")
            false
        }
    }

    /**
     * Restore a tar captured by [snapshotAppData] onto the app's private data directory. The
     * app should be fully stopped first ([clearAppData] or a plain no-account state) so a live
     * daemon/db handle doesn't race the overwrite. Stages through `/data/local/tmp` like [push].
     */
    fun restoreAppData(local: File): Boolean {
        if (!local.exists()) {
            System.err.println("[adb] restoreAppData source missing: ${local.absolutePath}")
            return false
        }
        val tmp = "/data/local/tmp/harness_appdata.tar"
        return try {
            if (adbExit("-s", serial, "push", local.absolutePath, tmp) != 0) return false
            val restored = adbExit(
                "-s", serial, "shell", "run-as", HARNESS_APP_ID, "tar", "-xf", tmp,
            ) == 0
            adbExit("-s", serial, "shell", "rm", "-f", tmp)
            restored
        } catch (e: Exception) {
            System.err.println("[adb] restoreAppData failed on $serial: ${e.message}")
            false
        }
    }

    /** Hard reset: wipe the harness app's data/state entirely (`pm clear`), like a fresh install. */
    fun clearAppData(): Boolean = adbExit("-s", serial, "shell", "pm", "clear", HARNESS_APP_ID) == 0

    /**
     * Force-stop [COMPETING_APP_IDS] (the standard jami-kmp build and jami-android-client) on
     * this device. `am force-stop` on an app that isn't installed/running is a harmless no-op
     * (non-zero exit is swallowed here, not surfaced as a run failure), so this is safe to call
     * unconditionally as a precondition on any lab device regardless of what's installed.
     */
    fun stopCompetingApps() {
        for (pkg in COMPETING_APP_IDS) {
            adbExit("-s", serial, "shell", "am", "force-stop", pkg)
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
