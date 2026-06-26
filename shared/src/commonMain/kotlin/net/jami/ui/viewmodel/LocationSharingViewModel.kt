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
package net.jami.ui.viewmodel

import androidx.lifecycle.ViewModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.jami.model.ContactLocation
import net.jami.model.Uri
import net.jami.services.AccountService
import net.jami.services.ConversationFacade
import net.jami.services.LocationUpdate
import net.jami.ui.platform.GeoLocation
import net.jami.utils.Log

/**
 * Duration options for location sharing.
 */
enum class SharingDuration(val seconds: Long) {
    TEN_MINUTES(10 * 60),
    ONE_HOUR(60 * 60),
}

/**
 * Represents a contact's location for display on the map.
 */
data class ContactLocationInfo(
    val contactUri: String,
    val displayName: String,
    val location: ContactLocation,
)

/**
 * State for the location sharing screen.
 */
data class LocationSharingState(
    val conversationId: String = "",
    val conversationTitle: String = "",
    val myLocation: GeoLocation? = null,
    val contactLocations: Map<String, ContactLocationInfo> = emptyMap(),
    val centerOnMyLocation: Boolean = true,
    val selectedDuration: SharingDuration = SharingDuration.ONE_HOUR,
    val isSharing: Boolean = false,
    val remainingSeconds: Long = 0,
)

/**
 * ViewModel for location sharing functionality.
 *
 * Handles:
 * - Location tracking
 * - Duration selection
 * - Sharing start/stop
 * - Sending location updates to the daemon
 */
class LocationSharingViewModel(
    private val conversationFacade: ConversationFacade,
    private val accountService: AccountService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {
    private val scope = scope

    private val _state = MutableStateFlow(LocationSharingState())
    val state: StateFlow<LocationSharingState> = _state.asStateFlow()

    private var currentAccountId: String? = null
    private var sharingJob: Job? = null
    private var countdownJob: Job? = null
    private var locationUpdatesJob: Job? = null

    /**
     * Load conversation information and start listening for contact locations.
     */
    fun loadConversation(conversationId: String) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            currentAccountId = account.accountId

            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            val conversation = conversationFacade.getConversation(account.accountId, conversationUri)

            val title = conversation?.contact?.displayUsername ?: conversationId

            _state.value = _state.value.copy(
                conversationId = conversationId,
                conversationTitle = title,
            )

            // Load any existing contact locations for this conversation
            loadExistingContactLocations(account.accountId, conversationId)

            // Start listening for location updates
            subscribeToLocationUpdates(account.accountId, conversationId)
        }
    }

    /**
     * Load existing contact locations from AccountService.
     */
    private fun loadExistingContactLocations(accountId: String, conversationId: String) {
        val existingLocations = accountService.getContactLocations(accountId, conversationId)
        if (existingLocations.isNotEmpty()) {
            val account = accountService.getAccount(accountId) ?: return
            val contactLocationInfos = existingLocations.mapValues { (contactUri, location) ->
                val contact = account.getContactFromCache(Uri.fromId(contactUri))
                ContactLocationInfo(
                    contactUri = contactUri,
                    displayName = contact.displayUsername,
                    location = location,
                )
            }
            _state.value = _state.value.copy(contactLocations = contactLocationInfos)
        }
    }

    /**
     * Subscribe to location updates for the current conversation.
     */
    private fun subscribeToLocationUpdates(accountId: String, conversationId: String) {
        locationUpdatesJob?.cancel()
        locationUpdatesJob = scope.launch {
            accountService.locationUpdates
                .filter { it.accountId == accountId && it.conversationId == conversationId }
                .collect { update ->
                    when (update) {
                        is LocationUpdate.Position -> {
                            val account = accountService.getAccount(accountId) ?: return@collect
                            val contact = account.getContactFromCache(Uri.fromId(update.contactUri))
                            val info = ContactLocationInfo(
                                contactUri = update.contactUri,
                                displayName = contact.displayUsername,
                                location = update.location,
                            )
                            val newLocations = _state.value.contactLocations.toMutableMap()
                            newLocations[update.contactUri] = info
                            _state.value = _state.value.copy(contactLocations = newLocations)
                            Log.d(TAG, "Updated contact location: ${contact.displayUsername}")
                        }
                        is LocationUpdate.Stop -> {
                            val newLocations = _state.value.contactLocations.toMutableMap()
                            newLocations.remove(update.contactUri)
                            _state.value = _state.value.copy(contactLocations = newLocations)
                            Log.d(TAG, "Contact stopped sharing: ${update.contactUri}")
                        }
                    }
                }
        }
    }

    /**
     * Update the user's current location.
     */
    fun updateLocation(location: GeoLocation) {
        _state.value = _state.value.copy(myLocation = location)

        // If sharing is active, send location update
        if (_state.value.isSharing) {
            sendLocationUpdate(location)
        }
    }

    /**
     * Center the map on the user's current location.
     */
    fun centerOnMyLocation() {
        _state.value = _state.value.copy(centerOnMyLocation = true)
        // Reset after a short delay to allow subsequent manual panning
        scope.launch {
            delay(500)
            _state.value = _state.value.copy(centerOnMyLocation = false)
        }
    }

    /**
     * Select the sharing duration.
     */
    fun selectDuration(duration: SharingDuration) {
        _state.value = _state.value.copy(selectedDuration = duration)
    }

    /**
     * Start sharing location.
     */
    fun startSharing() {
        val location = _state.value.myLocation
        if (location == null) {
            Log.w(TAG, "Cannot start sharing without location")
            return
        }

        val duration = _state.value.selectedDuration
        _state.value = _state.value.copy(
            isSharing = true,
            remainingSeconds = duration.seconds,
        )

        // Start countdown timer
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive && _state.value.remainingSeconds > 0) {
                delay(1000)
                val newRemaining = _state.value.remainingSeconds - 1
                _state.value = _state.value.copy(remainingSeconds = newRemaining)
                if (newRemaining <= 0) {
                    stopSharing()
                }
            }
        }

        // Start periodic location updates (every 10 seconds)
        sharingJob?.cancel()
        sharingJob = scope.launch {
            while (isActive && _state.value.isSharing) {
                _state.value.myLocation?.let { loc ->
                    sendLocationUpdate(loc)
                }
                delay(10_000) // 10 seconds
            }
        }

        // Send initial location
        sendLocationUpdate(location)
        Log.i(TAG, "Started location sharing for ${duration.seconds} seconds")
    }

    /**
     * Stop sharing location.
     */
    fun stopSharing() {
        sharingJob?.cancel()
        sharingJob = null
        countdownJob?.cancel()
        countdownJob = null

        _state.value = _state.value.copy(
            isSharing = false,
            remainingSeconds = 0,
        )

        // Send stop message
        sendStopMessage()
        Log.i(TAG, "Stopped location sharing")
    }

    /**
     * Send a location update to the conversation.
     */
    private fun sendLocationUpdate(location: GeoLocation) {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = _state.value.conversationId
            if (conversationId.isEmpty()) return@launch

            Log.d(TAG, "Sending location: ${location.latitude}, ${location.longitude}")
            accountService.sendGeolocationPosition(
                accountId = accountId,
                conversationId = conversationId,
                latitude = location.latitude,
                longitude = location.longitude,
            )
        }
    }

    /**
     * Send a stop sharing message.
     */
    private fun sendStopMessage() {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = _state.value.conversationId
            if (conversationId.isEmpty()) return@launch

            Log.d(TAG, "Sending stop location message")
            accountService.sendGeolocationStop(accountId, conversationId)
        }
    }

    /**
     * Clean up when ViewModel is no longer needed.
     */
    public override fun onCleared() {
        stopSharing()
        locationUpdatesJob?.cancel()
        locationUpdatesJob = null
        scope.cancel()
    }

    companion object {
        private const val TAG = "LocationSharingVM"
    }
}
