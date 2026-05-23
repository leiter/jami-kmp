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
 * Tracks video loss state during calls for network resilience feedback.
 */
data class VideoLossState(
    val isVideoLost: Boolean = false,
    val lostSinkId: String = "",
    val lossDurationSeconds: Long = 0L,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 5,
    val isRetrying: Boolean = false,
    val isFallbackToAudioOnly: Boolean = false
) {
    val retryPercentage: Float
        get() = if (maxRetries > 0) (retryAttempt.toFloat() / maxRetries) * 100f else 0f

    val canRetry: Boolean
        get() = retryAttempt < maxRetries

    val displayMessage: String
        get() = when {
            isFallbackToAudioOnly -> "Audio only — video unavailable"
            isRetrying && retryAttempt > 0 -> "Reconnecting video... (Attempt ${retryAttempt + 1}/$maxRetries)"
            isVideoLost -> "Video disconnected — reconnecting..."
            else -> ""
        }
}
