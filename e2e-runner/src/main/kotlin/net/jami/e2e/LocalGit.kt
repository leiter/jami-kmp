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

/**
 * Host-side git operations against a conversation repo pulled off a device (see
 * [DeviceController.pullConversationRepo]) — used to deliberately rewind one peer's local copy
 * of a swarm conversation, so a scenario can observe whether the daemon's peer sync self-heals
 * it. The daemon itself never sees this happen live: the app is stopped for the whole
 * pull → mutate → push round-trip.
 */
object LocalGit {
    /** `git rev-list --count HEAD` — total commits reachable from HEAD. */
    fun commitCount(repoDir: File): Int = run(repoDir, "rev-list", "--count", "HEAD").trim().toIntOrNull() ?: 0

    /**
     * `git reset --hard HEAD~n`, clamped so it never rewinds past the repo's own root commit.
     * Returns the number of commits actually reverted (may be less than [n], or 0).
     */
    fun resetHardBack(repoDir: File, n: Int): Int {
        val total = commitCount(repoDir)
        val clamped = n.coerceIn(0, (total - 1).coerceAtLeast(0))
        if (clamped > 0) run(repoDir, "reset", "--hard", "HEAD~$clamped")
        return clamped
    }

    private fun run(repoDir: File, vararg args: String): String {
        val cmd = listOf("git", "-C", repoDir.absolutePath) + args
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0) {
            System.err.println("[git] exit=$code ${cmd.joinToString(" ")}\n$out")
        }
        return out
    }
}
