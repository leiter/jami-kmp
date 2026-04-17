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
package net.jami.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Web (JS) implementation of OsmMapView.
 * TODO: Could implement using Leaflet.js directly
 */
@Composable
actual fun OsmMapView(
    modifier: Modifier,
    myLocation: GeoLocation?,
    contactMarkers: List<ContactMarker>,
    centerOnMyLocation: Boolean,
    onLocationUpdate: (GeoLocation) -> Unit,
) {
    // Placeholder - Leaflet.js implementation would go here
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE8E8E8)),
        contentAlignment = Alignment.Center,
    ) {
        val locationText = if (myLocation != null) {
            "My Location: ${myLocation.latitude}, ${myLocation.longitude}"
        } else {
            "Waiting for location..."
        }
        val contactsText = if (contactMarkers.isNotEmpty()) {
            "\nContacts sharing: ${contactMarkers.size}"
        } else {
            ""
        }
        Text(
            text = "$locationText$contactsText\n(Leaflet.js integration pending)",
            color = Color.Gray,
        )
    }
}
