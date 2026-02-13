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
package net.jami.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.jami.model.settings.Draft
import net.jami.model.settings.DraftsContainer
import net.jami.model.settings.SettingsKeys
import net.jami.services.DaemonBridge
import net.jami.utils.Log

/**
 * Repository for managing message drafts stored in daemon account details.
 *
 * Drafts are stored as JSON in daemon account details with KMP.Drafts key.
 * Features:
 * - Debounced saves to avoid excessive daemon calls
 * - Conflict resolution via timestamps
 * - Auto-sync across devices via DHT
 *
 * ## Usage
 * ```kotlin
 * val repo = DraftRepository(daemonBridge, scope)
 * repo.observeAccount("accountId")
 *
 * // Update draft as user types
 * repo.updateDraft("conversationId", "Hello wor")
 * repo.updateDraft("conversationId", "Hello world")
 *
 * // Get draft for display
 * repo.getDraftText("conversationId").collect { text ->
 *     textField.value = text
 * }
 *
 * // Clear draft on send
 * repo.clearDraft("conversationId")
 * ```
 */
class DraftRepository(
    private val daemonBridge: DaemonBridge,
    private val scope: CoroutineScope
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** Currently observed account ID */
    private var currentAccountId: String? = null

    /** Debounce delay in milliseconds */
    private val debounceDelay = 1500L

    /** Pending save jobs, keyed by conversation ID */
    private val pendingSaves = mutableMapOf<String, Job>()

    // ==================== StateFlows ====================

    private val _drafts = MutableStateFlow(DraftsContainer())
    /** All drafts container */
    val drafts: StateFlow<DraftsContainer> = _drafts.asStateFlow()

    // ==================== Account Observation ====================

    /**
     * Start observing drafts for the given account.
     * Loads existing drafts from daemon account details.
     *
     * @param accountId Account to observe
     */
    fun observeAccount(accountId: String) {
        currentAccountId = accountId
        loadDrafts(accountId)
    }

    /**
     * Stop observing the current account.
     * Saves any pending drafts and clears local state.
     */
    fun stopObserving() {
        // Cancel pending saves and save immediately
        pendingSaves.values.forEach { it.cancel() }
        pendingSaves.clear()

        currentAccountId?.let { saveDraftsImmediately(it) }
        currentAccountId = null
        _drafts.value = DraftsContainer()
    }

    /**
     * Handle account details changed event from daemon.
     * Called when drafts are updated remotely.
     *
     * @param accountId Account that was updated
     * @param details Updated account details
     */
    fun onAccountDetailsChanged(accountId: String, details: Map<String, String>) {
        if (accountId != currentAccountId) return

        // Check if drafts were changed
        val remoteDraftsJson = details[SettingsKeys.DRAFTS] ?: return

        try {
            val remoteDrafts = json.decodeFromString<DraftsContainer>(remoteDraftsJson)

            // Merge remote drafts with local, preferring newer timestamps
            mergeDrafts(remoteDrafts)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse remote drafts: ${e.message}")
        }
    }

    // ==================== Draft Operations ====================

    /**
     * Update draft text for a conversation.
     * Uses debouncing to avoid excessive saves.
     *
     * @param conversationId Conversation to update
     * @param text New draft text
     */
    fun updateDraft(conversationId: String, text: String) {
        val currentTime = currentTimeMillis()
        val current = _drafts.value.drafts.toMutableMap()
        val existingDraft = current[conversationId] ?: Draft()

        // Update draft locally
        val newDraft = existingDraft.copy(
            text = text,
            lastModified = currentTime
        )
        current[conversationId] = newDraft
        _drafts.value = _drafts.value.copy(drafts = current)

        // Schedule debounced save
        scheduleSave(conversationId)
    }

    /**
     * Set reply-to message for a draft.
     *
     * @param conversationId Conversation to update
     * @param replyToMessageId Message ID being replied to (empty to clear)
     */
    fun setReplyTo(conversationId: String, replyToMessageId: String) {
        val currentTime = currentTimeMillis()
        val current = _drafts.value.drafts.toMutableMap()
        val existingDraft = current[conversationId] ?: Draft()

        val newDraft = existingDraft.copy(
            replyTo = replyToMessageId,
            lastModified = currentTime
        )
        current[conversationId] = newDraft
        _drafts.value = _drafts.value.copy(drafts = current)

        // Save immediately for reply-to changes
        saveImmediately(conversationId)
    }

    /**
     * Add attachment to a draft.
     *
     * @param conversationId Conversation to update
     * @param attachmentPath File path or URI of attachment
     */
    fun addAttachment(conversationId: String, attachmentPath: String) {
        val currentTime = currentTimeMillis()
        val current = _drafts.value.drafts.toMutableMap()
        val existingDraft = current[conversationId] ?: Draft()

        val newDraft = existingDraft.copy(
            attachments = existingDraft.attachments + attachmentPath,
            lastModified = currentTime
        )
        current[conversationId] = newDraft
        _drafts.value = _drafts.value.copy(drafts = current)

        // Save immediately for attachments
        saveImmediately(conversationId)
    }

    /**
     * Remove attachment from a draft.
     *
     * @param conversationId Conversation to update
     * @param attachmentPath File path or URI of attachment to remove
     */
    fun removeAttachment(conversationId: String, attachmentPath: String) {
        val currentTime = currentTimeMillis()
        val current = _drafts.value.drafts.toMutableMap()
        val existingDraft = current[conversationId] ?: return

        val newDraft = existingDraft.copy(
            attachments = existingDraft.attachments - attachmentPath,
            lastModified = currentTime
        )
        current[conversationId] = newDraft
        _drafts.value = _drafts.value.copy(drafts = current)

        saveImmediately(conversationId)
    }

    /**
     * Clear draft for a conversation (typically after sending).
     *
     * @param conversationId Conversation to clear
     */
    fun clearDraft(conversationId: String) {
        // Cancel any pending save
        pendingSaves[conversationId]?.cancel()
        pendingSaves.remove(conversationId)

        val current = _drafts.value.drafts.toMutableMap()
        if (current.remove(conversationId) != null) {
            _drafts.value = _drafts.value.copy(
                drafts = current,
                lastSynced = currentTimeMillis()
            )
            saveDraftsImmediately(currentAccountId ?: return)
        }
    }

    /**
     * Clear all drafts (typically on logout or account removal).
     */
    fun clearAllDrafts() {
        pendingSaves.values.forEach { it.cancel() }
        pendingSaves.clear()
        _drafts.value = DraftsContainer()

        currentAccountId?.let { accountId ->
            scope.launch {
                try {
                    val details = daemonBridge.getAccountDetails(accountId).toMutableMap()
                    details.remove(SettingsKeys.DRAFTS)
                    daemonBridge.setAccountDetails(accountId, details)
                    Log.d(TAG, "Cleared all drafts for account $accountId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear drafts: ${e.message}")
                }
            }
        }
    }

    // ==================== Draft Getters ====================

    /**
     * Get draft for a specific conversation.
     *
     * @param conversationId Conversation ID
     * @return Draft or null if no draft exists
     */
    fun getDraft(conversationId: String): Draft? {
        return _drafts.value.drafts[conversationId]
    }

    /**
     * Get draft text as a StateFlow for reactive UI.
     *
     * @param conversationId Conversation ID
     * @return StateFlow of draft text
     */
    fun getDraftText(conversationId: String): StateFlow<String> {
        return MutableStateFlow(_drafts.value.drafts[conversationId]?.text ?: "").also { flow ->
            scope.launch {
                _drafts.map { it.drafts[conversationId]?.text ?: "" }
                    .collect { flow.value = it }
            }
        }
    }

    /**
     * Check if a conversation has a non-empty draft.
     *
     * @param conversationId Conversation ID
     * @return true if draft exists and is not empty
     */
    fun hasDraft(conversationId: String): Boolean {
        val draft = _drafts.value.drafts[conversationId] ?: return false
        return draft.text.isNotEmpty() || draft.attachments.isNotEmpty()
    }

    /**
     * Get all conversation IDs that have drafts.
     *
     * @return Set of conversation IDs with drafts
     */
    fun getConversationsWithDrafts(): Set<String> {
        return _drafts.value.drafts.filter { (_, draft) ->
            draft.text.isNotEmpty() || draft.attachments.isNotEmpty()
        }.keys
    }

    // ==================== Private Helpers ====================

    private fun loadDrafts(accountId: String) {
        scope.launch {
            try {
                val details = daemonBridge.getAccountDetails(accountId)
                val draftsJson = details[SettingsKeys.DRAFTS]

                if (draftsJson != null) {
                    _drafts.value = runCatching {
                        json.decodeFromString<DraftsContainer>(draftsJson)
                    }.getOrDefault(DraftsContainer())
                }

                Log.d(TAG, "Loaded ${_drafts.value.drafts.size} drafts for account $accountId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load drafts for account $accountId: ${e.message}")
            }
        }
    }

    private fun scheduleSave(conversationId: String) {
        // Cancel existing save job for this conversation
        pendingSaves[conversationId]?.cancel()

        // Schedule new save with debounce
        pendingSaves[conversationId] = scope.launch {
            delay(debounceDelay)
            saveImmediately(conversationId)
            pendingSaves.remove(conversationId)
        }
    }

    private fun saveImmediately(conversationId: String) {
        // Cancel debounced save if exists
        pendingSaves[conversationId]?.cancel()
        pendingSaves.remove(conversationId)

        val accountId = currentAccountId ?: return
        saveDraftsImmediately(accountId)
    }

    private fun saveDraftsImmediately(accountId: String) {
        scope.launch {
            try {
                val container = _drafts.value.copy(lastSynced = currentTimeMillis())
                _drafts.value = container

                val draftsJson = json.encodeToString(container)
                val details = daemonBridge.getAccountDetails(accountId).toMutableMap()
                details[SettingsKeys.DRAFTS] = draftsJson
                daemonBridge.setAccountDetails(accountId, details)

                Log.d(TAG, "Saved ${container.drafts.size} drafts for account $accountId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save drafts: ${e.message}")
            }
        }
    }

    private fun mergeDrafts(remoteDrafts: DraftsContainer) {
        val localDrafts = _drafts.value.drafts.toMutableMap()
        var updated = false

        for ((convId, remoteDraft) in remoteDrafts.drafts) {
            val localDraft = localDrafts[convId]
            if (localDraft == null || remoteDraft.lastModified > localDraft.lastModified) {
                // Remote is newer, accept it
                localDrafts[convId] = remoteDraft
                updated = true
            }
        }

        // Also check for drafts that exist locally but not remotely
        // (might have been cleared on another device)
        if (remoteDrafts.lastSynced > _drafts.value.lastSynced) {
            val remoteKeys = remoteDrafts.drafts.keys
            val toRemove = localDrafts.keys.filter { it !in remoteKeys }
            for (key in toRemove) {
                localDrafts.remove(key)
                updated = true
            }
        }

        if (updated) {
            _drafts.value = DraftsContainer(
                drafts = localDrafts,
                lastSynced = maxOf(_drafts.value.lastSynced, remoteDrafts.lastSynced)
            )
        }
    }

    private fun currentTimeMillis(): Long {
        return net.jami.utils.currentTimeMillis()
    }

    companion object {
        private const val TAG = "DraftRepository"
    }
}
