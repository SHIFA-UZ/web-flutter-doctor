package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation

private const val SPAN_METERS = 800.0

// ponytail: shows the pin and recentres; tap-to-move (a UITapGestureRecognizer target) comes with PROF-07.
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MapPin(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
    onPick: ((latitude: Double, longitude: Double) -> Unit)?,
) {
    val pin = remember { MKPointAnnotation() }
    UIKitView(
        factory = { MKMapView().apply { addAnnotation(pin) } },
        modifier = modifier,
        update = { map ->
            val centre = CLLocationCoordinate2DMake(latitude, longitude)
            pin.setCoordinate(centre)
            map.setRegion(MKCoordinateRegionMakeWithDistance(centre, SPAN_METERS, SPAN_METERS), animated = false)
            centre.useContents { Unit }
        },
    )
}
