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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

@Composable
actual fun OsmMapView(
    modifier: Modifier,
    myLocation: GeoLocation?,
    contactMarkers: List<ContactMarker>,
    centerOnMyLocation: Boolean,
    onLocationUpdate: (GeoLocation) -> Unit,
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var accuracyCircle by remember { mutableStateOf<Polygon?>(null) }
    var contactOverlays by remember { mutableStateOf<Map<String, Marker>>(emptyMap()) }
    var locationManager by remember { mutableStateOf<LocationManager?>(null) }

    // Configure osmdroid
    LaunchedEffect(Unit) {
        val osmPath = File(context.cacheDir, "osm")
        Configuration.getInstance().apply {
            osmdroidBasePath = osmPath
            osmdroidTileCache = File(osmPath, "tiles")
            userAgentValue = "net.jami.kmp"
            isMapViewHardwareAccelerated = true
        }
    }

    // Setup location listener
    DisposableEffect(Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        locationManager = lm

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocationUpdate(GeoLocation(location.latitude, location.longitude, location.accuracy))
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // Request location updates if permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                lm?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2500L,
                    1f,
                    locationListener
                )
                // Also try network provider as fallback
                lm?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2500L,
                    1f,
                    locationListener
                )

                // Get last known location for initial position
                val lastLocation = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                lastLocation?.let {
                    onLocationUpdate(GeoLocation(it.latitude, it.longitude, it.accuracy))
                }
            } catch (e: Exception) {
                // Location provider not available
            }
        }

        onDispose {
            try {
                lm?.removeUpdates(locationListener)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Update marker and accuracy circle when location changes
    LaunchedEffect(myLocation) {
        val map = mapView ?: return@LaunchedEffect
        val m = marker ?: return@LaunchedEffect
        if (myLocation != null) {
            val geoPoint = GeoPoint(myLocation.latitude, myLocation.longitude)
            m.position = geoPoint

            // Remove old accuracy circle and redraw with current radius
            accuracyCircle?.let { map.overlays.remove(it) }
            val radiusMeters = myLocation.accuracy.toDouble()
            if (radiusMeters > 0) {
                val newCircle = Polygon(map).apply {
                    points = buildAccuracyCirclePoints(geoPoint, radiusMeters)
                    fillColor = Color.argb(34, 0, 148, 209)
                    strokeColor = Color.argb(180, 0, 148, 209)
                    strokeWidth = 2f
                }
                // Insert below the user marker so the marker stays on top
                val markerIndex = map.overlays.indexOf(m)
                if (markerIndex >= 0) {
                    map.overlays.add(markerIndex, newCircle)
                } else {
                    map.overlays.add(newCircle)
                }
                accuracyCircle = newCircle
            } else {
                accuracyCircle = null
            }

            if (centerOnMyLocation) {
                map.controller.animateTo(geoPoint)
            }
            map.invalidate()
        }
    }

    // Re-center when the "Center on me" FAB is tapped (location may be unchanged)
    LaunchedEffect(centerOnMyLocation) {
        if (centerOnMyLocation) {
            val loc = myLocation ?: return@LaunchedEffect
            val map = mapView ?: return@LaunchedEffect
            map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
        }
    }

    // Update contact markers
    LaunchedEffect(contactMarkers) {
        val map = mapView ?: return@LaunchedEffect

        // Remove markers for contacts that are no longer sharing
        val currentUris = contactMarkers.map { it.contactUri }.toSet()
        contactOverlays.forEach { (uri, m) ->
            if (uri !in currentUris) {
                map.overlays.remove(m)
            }
        }

        // Add or update markers for current contacts
        val newOverlays = mutableMapOf<String, Marker>()
        contactMarkers.forEach { contact ->
            val existingMarker = contactOverlays[contact.contactUri]
            val m = if (existingMarker != null) {
                existingMarker.position = GeoPoint(contact.latitude, contact.longitude)
                existingMarker.title = contact.displayName
                existingMarker
            } else {
                Marker(map).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    position = GeoPoint(contact.latitude, contact.longitude)
                    title = contact.displayName
                    // Use a different color/style for contact markers
                    // (Using default for now - can be customized with setIcon)
                    map.overlays.add(this)
                }
            }
            newOverlays[contact.contactUri] = m
        }
        contactOverlays = newOverlays
        map.invalidate()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                isHorizontalMapRepetitionEnabled = false
                isTilesScaledToDpi = true
                setMapOrientation(0f, false)
                minZoomLevel = 3.0
                maxZoomLevel = 19.0
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setMultiTouchControls(true)
                controller.setZoom(15.0)

                // Create marker for user location
                val locationMarker = Marker(this).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "My Location"
                }
                overlays.add(locationMarker)
                marker = locationMarker

                // Set initial position if available
                myLocation?.let { loc ->
                    val geoPoint = GeoPoint(loc.latitude, loc.longitude)
                    locationMarker.position = geoPoint
                    controller.setCenter(geoPoint)
                } ?: run {
                    // Default to a generic location (Paris) if no location available
                    controller.setCenter(GeoPoint(48.8566, 2.3522))
                }

                mapView = this
            }
        },
        update = { view ->
            // Update is handled by LaunchedEffect above
        },
    )

    // Handle lifecycle
    DisposableEffect(mapView) {
        onDispose {
            mapView?.onDetach()
        }
    }
}

/**
 * Approximates a geodesic circle as a closed polygon suitable for osmdroid overlay.
 * Uses a flat-earth approximation — accurate to within 1% for radii up to ~20 km.
 */
private fun buildAccuracyCirclePoints(center: GeoPoint, radiusMeters: Double): List<GeoPoint> {
    val points = ArrayList<GeoPoint>(37)
    val latDeg = center.latitude
    val lonDeg = center.longitude
    val latDelta = radiusMeters / 111320.0
    val lonDelta = radiusMeters / (111320.0 * cos(latDeg * PI / 180.0))
    for (i in 0..36) { // 37 points — last equals first to close the polygon
        val angle = i * 10.0 * PI / 180.0
        points.add(GeoPoint(latDeg + latDelta * cos(angle), lonDeg + lonDelta * sin(angle)))
    }
    return points
}
