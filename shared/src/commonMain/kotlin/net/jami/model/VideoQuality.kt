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
package net.jami.model

/**
 * Video quality presets for call bandwidth optimization.
 */
enum class VideoQuality(
    val label: String,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val bitrateLow: Int,      // kbps
    val bitrateOptimal: Int,  // kbps
    val bitrateHigh: Int      // kbps
) {
    LOW("Low", 320, 240, 15, 200, 400, 600),
    MEDIUM("Medium", 640, 480, 24, 800, 1500, 2500),
    HIGH("High", 1280, 720, 30, 2000, 3500, 5000),
    ULTRA("Ultra", 1920, 1080, 30, 4000, 6000, 8000);

    fun getRecommendedBitrate(packetLoss: Float): Int {
        return when {
            packetLoss > 0.10f -> bitrateLow  // >10% loss → minimum bitrate
            packetLoss > 0.05f -> bitrateOptimal  // 5-10% loss → optimal
            else -> bitrateHigh  // <5% loss → full bitrate
        }
    }
}

/**
 * Current video quality and bitrate state.
 */
data class VideoQualityState(
    val quality: VideoQuality = VideoQuality.MEDIUM,
    val currentBitrate: Int = 0,  // kbps, 0 = not measured
    val currentResolution: Pair<Int, Int> = 0 to 0,  // width x height
    val currentFrameRate: Int = 0,  // fps, 0 = not measured
    val packetLoss: Float = 0f,  // 0.0-1.0, calculated from network stats
    val isAutoQuality: Boolean = true,  // Adaptive bitrate enabled
    val isShowingStats: Boolean = false
) {
    val displayBitrate: String
        get() = if (currentBitrate > 0) "$currentBitrate kbps" else "—"

    val displayResolution: String
        get() = if (currentResolution.first > 0) {
            "${currentResolution.first}x${currentResolution.second}"
        } else {
            "—"
        }

    val displayFrameRate: String
        get() = if (currentFrameRate > 0) "$currentFrameRate fps" else "—"

    val displayPacketLoss: String
        get() = "${(packetLoss * 100).toInt()}%"

    val qualityIndicator: String
        get() = when {
            packetLoss > 0.10f -> "Poor"
            packetLoss > 0.05f -> "Fair"
            else -> "Good"
        }
}
