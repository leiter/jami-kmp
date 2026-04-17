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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Location data for the map.
 *
 * @param latitude Latitude in degrees
 * @param longitude Longitude in degrees
 */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Contact location marker data.
 *
 * @param contactUri URI of the contact
 * @param displayName Name to show in marker
 * @param latitude Latitude in degrees
 * @param longitude Longitude in degrees
 */
data class ContactMarker(
    val contactUri: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * OpenStreetMap view composable.
 *
 * Platform implementations:
 * - Android: Uses osmdroid MapView
 * - iOS: Uses MapKit MKMapView (or stub)
 * - Desktop/Web: Shows placeholder
 *
 * @param modifier Modifier for the map view
 * @param myLocation Current user location to show on map
 * @param contactMarkers Contact locations to display on the map
 * @param centerOnMyLocation When true, center the map on myLocation
 * @param onLocationUpdate Callback when user's location is updated (from GPS)
 */
@Composable
expect fun OsmMapView(
    modifier: Modifier = Modifier,
    myLocation: GeoLocation? = null,
    contactMarkers: List<ContactMarker> = emptyList(),
    centerOnMyLocation: Boolean = true,
    onLocationUpdate: (GeoLocation) -> Unit = {},
)
