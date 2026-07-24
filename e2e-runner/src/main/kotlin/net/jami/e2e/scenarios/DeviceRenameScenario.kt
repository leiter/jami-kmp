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
package net.jami.e2e.scenarios

import kotlinx.coroutines.delay
import net.jami.e2e.Scenario
import net.jami.e2e.ScenarioContext
import net.jami.e2e.Verdict
import net.jami.e2e.protocol.AccountAdded
import net.jami.e2e.protocol.CreateBareAccount
import net.jami.e2e.protocol.GetKnownDevices
import net.jami.e2e.protocol.KnownDevices
import net.jami.e2e.protocol.RegistrationStateChanged
import net.jami.e2e.protocol.RenameDevice

/**
 * Device **naming** — the single-device half of device management.
 *
 * `renameDevice` writes `ACCOUNT_DEVICE_NAME` through `setAccountDetails`; `getKnownRingDevices`
 * reads the daemon's own `deviceId → deviceName` registry. Together they are the whole
 * user-visible "this device is called X" surface, and both are observable from one device — so
 * this lands without the two-device link flow (`addDevice` / `confirmAddDevice`) that the rest of
 * device management needs.
 *
 * The proof is a **read-back through the daemon**, not the setter's return value (it has none):
 *  - baseline: read the registry, remember this device's id and name;
 *  - rename to a stamped unique name → the registry reports the new name **under the same
 *    device id**, with the key set unchanged (a rename must not add or drop a device);
 *  - rename back to the baseline name → the registry follows again, proving a live read-write
 *    channel rather than a one-shot write.
 *
 * Reuse-first and **non-consuming**: claim an unprotected pool asset, install it, rename the
 * phone copy, and remove that copy on teardown — the host archive is never rewritten.
 *
 * Unlike the account-state scenarios there is no event to await here: `getKnownRingDevices` is a
 * plain synchronous getter (`onKnownDevicesChanged` is not among the Flows the agent collects),
 * so the read-back is polled against a deadline — the same shape as the `GetAccountUri` retry.
 */
object DeviceRenameScenario : Scenario {
    override val id = "device-rename"
    override val requiredRoles = 1

    override suspend fun run(ctx: ScenarioContext): Verdict {
        if (!ensureNoAccounts(ctx, "A").clean) {
            return Verdict(false, "could not reach a no-account state before device rename")
        }

        val asset = ctx.memory.claim(hasPassword = false)
        val accountId: String = if (asset != null) {
            ctx.log("reusing asset ${asset.fingerprint} from pool (non-consuming)")
            ctx.installAsset("A", asset) ?: return Verdict(false, "failed to install claimed asset")
        } else {
            ctx.log("no unprotected asset in pool — creating a bare account")
            ctx.send("A", CreateBareAccount(displayName = "harness_device_${System.currentTimeMillis() / 1000}"))
            (ctx.await("A", 20_000) { it is AccountAdded } as AccountAdded).accountId
        }

        try {
            // Account details are only reliably writable once the account is fully loaded — the
            // same gate the password operations need.
            ctx.await("A", 60_000) {
                it is RegistrationStateChanged && it.accountId == accountId && it.state == "REGISTERED"
            }
            ctx.log("account REGISTERED — device registry is readable")

            // Baseline. The count is not asserted: repeated imports of the same identity can
            // leave earlier device ids known to the account, which is not this test's subject.
            val baseline = readDevices(ctx, accountId)
            if (baseline.isEmpty()) {
                return Verdict(false, "device registry is empty — no device to rename")
            }
            ctx.log("baseline registry (${baseline.size} device(s)): $baseline")

            // Rename → the registry must report the new name under an id it already knew.
            val newName = "harness-renamed-${System.currentTimeMillis() / 1000}"
            ctx.send("A", RenameDevice(accountId, newName))
            val renamed = awaitDeviceNamed(ctx, accountId, newName)
                ?: return Verdict(false, "registry never reported the renamed device '$newName'")
            val deviceId = renamed.entries.single { it.value == newName }.key
            if (deviceId !in baseline) {
                return Verdict(false, "renamed device id $deviceId was not in the baseline registry")
            }
            if (renamed.keys != baseline.keys) {
                return Verdict(false, "rename changed the device set: ${baseline.keys} → ${renamed.keys}")
            }
            val originalName = baseline.getValue(deviceId)
            ctx.log("renamed device $deviceId: '$originalName' → '$newName' (device set unchanged)")

            // Rename back — proves the channel is live in both directions, and leaves the
            // account as we found it.
            ctx.send("A", RenameDevice(accountId, originalName))
            val restored = awaitDeviceNamed(ctx, accountId, originalName)
                ?: return Verdict(false, "registry never reported the restored name '$originalName'")
            if (restored != baseline) {
                return Verdict(false, "restored registry differs from baseline: $baseline → $restored")
            }
            ctx.log("restored device $deviceId to '$originalName' — registry matches the baseline")

            return Verdict(
                true,
                "device $deviceId renamed to '$newName' and back to '$originalName', " +
                    "read back from the daemon registry both times",
            )
        } finally {
            // Non-consuming: remove the phone copy; the host blob is never rewritten.
            runCatching { ensureNoAccounts(ctx, "A") }
        }
    }

    /** One synchronous read of the daemon's `deviceId → deviceName` registry. */
    private suspend fun readDevices(ctx: ScenarioContext, accountId: String): Map<String, String> {
        ctx.send("A", GetKnownDevices(accountId))
        return (ctx.await("A", 10_000) {
            it is KnownDevices && it.accountId == accountId
        } as KnownDevices).devices
    }

    /**
     * Poll the registry until exactly one device carries [name], and return that registry — or
     * null if the deadline passes. Bounded polling, because the write goes through the daemon
     * and there is no completion event to await.
     */
    private suspend fun awaitDeviceNamed(
        ctx: ScenarioContext,
        accountId: String,
        name: String,
        timeoutMillis: Long = 15_000,
    ): Map<String, String>? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            val devices = readDevices(ctx, accountId)
            if (devices.values.count { it == name } == 1) return devices
            if (System.currentTimeMillis() >= deadline) return null
            delay(250)
        }
    }
}
