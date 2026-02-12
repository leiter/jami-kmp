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

import net.jami.utils.currentTimeMillis

/**
 * Represents a file transfer interaction in a conversation.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes:
 * - java.io.File → String paths for KMP compatibility
 * - System.currentTimeMillis() → expect/actual currentTimeMillis()
 */
class DataTransfer : Interaction {

    var totalSize: Long = 0
        private set

    var bytesProgress: Long = 0

    private var cachedExtension: String? = null
    var fileId: String? = null

    /** Destination path for the file */
    var destinationPath: String? = null

    /** Daemon-provided path for the file */
    var daemonPath: String? = null

    /**
     * Legacy constructor for creating a data transfer.
     */
    constructor(
        conversation: ConversationHistory?,
        peer: String?,
        account: String?,
        displayName: String,
        isOutgoing: Boolean,
        totalSize: Long,
        bytesProgress: Long,
        fileId: String?
    ) : super() {
        author = if (isOutgoing) null else peer
        this.account = account
        this.conversation = conversation
        this.totalSize = totalSize
        this.bytesProgress = bytesProgress
        body = displayName
        transferStatus = TransferStatus.TRANSFER_CREATED
        type = InteractionType.DATA_TRANSFER
        timestamp = currentTimeMillis()
        mIsRead = 1
        isIncoming = !isOutgoing
        if (fileId != null) {
            this.fileId = fileId
            daemonId = fileId.toULongOrNull()?.toLong()
        }
    }

    constructor(interaction: Interaction) : super() {
        id = interaction.id
        daemonId = interaction.daemonId
        author = interaction.author
        conversation = interaction.conversation
        body = interaction.body
        transferStatus = interaction.transferStatus
        type = interaction.type
        timestamp = interaction.timestamp
        account = interaction.account
        contact = interaction.contact
        mIsRead = 1
        isIncoming = interaction.isIncoming
    }

    constructor(
        fileId: String?,
        accountId: String,
        peerUri: String,
        displayName: String,
        isOutgoing: Boolean,
        timestamp: Long,
        totalSize: Long,
        bytesProgress: Long
    ) : super() {
        account = accountId
        this.fileId = fileId
        body = displayName
        author = peerUri
        isIncoming = !isOutgoing
        this.totalSize = totalSize
        this.bytesProgress = bytesProgress
        this.timestamp = timestamp
        type = InteractionType.DATA_TRANSFER
    }

    /**
     * Get the file extension (lowercase).
     */
    val extension: String?
        get() {
            val filename = body ?: return null
            if (cachedExtension == null) {
                cachedExtension = getFileExtension(filename).lowercase()
            }
            return cachedExtension
        }

    val isPicture: Boolean
        get() = IMAGE_EXTENSIONS.contains(extension)

    val isAudio: Boolean
        get() = AUDIO_EXTENSIONS.contains(extension)

    val isVideo: Boolean
        get() = VIDEO_EXTENSIONS.contains(extension)

    val isComplete: Boolean
        get() = transferStatus == TransferStatus.TRANSFER_FINISHED

    fun showPicture(): Boolean = isPicture && isComplete

    val storagePath: String
        get() {
            val b = body
            return when {
                b != null -> b
                !fileId.isNullOrEmpty() -> fileId!!
                else -> "Error"
            }
        }

    val displayName: String
        get() = body ?: ""

    val isOutgoing: Boolean
        get() = !isIncoming

    val isError: Boolean
        get() = transferStatus.isError

    fun canAutoAccept(maxSize: Int): Boolean {
        return maxSize == UNLIMITED_SIZE || totalSize <= maxSize
    }

    private fun getFileExtension(filename: String): String {
        val lastDot = filename.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < filename.length - 1) {
            filename.substring(lastDot + 1)
        } else {
            ""
        }
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "heic", "heif"
        )
        private val AUDIO_EXTENSIONS = setOf(
            "ogg", "mp3", "aac", "flac", "m4a"
        )
        private val VIDEO_EXTENSIONS = setOf(
            "webm", "mp4", "mkv"
        )
        private const val UNLIMITED_SIZE = 256 * 1024 * 1024
    }
}
