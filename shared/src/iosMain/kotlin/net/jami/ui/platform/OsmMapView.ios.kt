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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CLLocation.CLLocation
import platform.CLLocationManager.CLLocationManager
import platform.CLLocationManager.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSError
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIView
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun OsmMapView(
    modifier: Modifier,
    myLocation: GeoLocation?,
    contactMarkers: List<ContactMarker>,
    centerOnMyLocation: Boolean,
    onLocationUpdate: (GeoLocation) -> Unit,
) {
    val locationManager = remember { CLLocationManager() }
    val locationDelegate = remember {
        object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                val accuracy = loc.horizontalAccuracy.toFloat()
                loc.coordinate.useContents {
                    onLocationUpdate(GeoLocation(latitude, longitude, accuracy))
                }
            }
            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {}
        }
    }

    DisposableEffect(Unit) {
        locationManager.delegate = locationDelegate
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
        onDispose { locationManager.stopUpdatingLocation() }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            MKMapView().apply {
                showsUserLocation = true
                myLocation?.let { loc ->
                    setRegion(
                        MKCoordinateRegionMakeWithDistance(
                            CLLocationCoordinate2DMake(loc.latitude, loc.longitude),
                            500.0, 500.0,
                        ),
                        animated = false,
                    )
                }
            }
        },
        update = { mapView ->
            mapView.removeAnnotations(mapView.annotations)
            contactMarkers.forEach { cm ->
                val annotation = MKPointAnnotation()
                annotation.coordinate = CLLocationCoordinate2DMake(cm.latitude, cm.longitude)
                annotation.title = cm.displayName
                mapView.addAnnotation(annotation)
            }
            if (centerOnMyLocation) {
                myLocation?.let { loc ->
                    mapView.setRegion(
                        MKCoordinateRegionMakeWithDistance(
                            CLLocationCoordinate2DMake(loc.latitude, loc.longitude),
                            500.0, 500.0,
                        ),
                        animated = true,
                    )
                }
            }
        },
    )
}
