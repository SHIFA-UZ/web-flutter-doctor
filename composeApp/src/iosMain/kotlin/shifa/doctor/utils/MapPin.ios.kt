package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSSelectorFromString
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UITapGestureRecognizer
import platform.darwin.NSObject

private const val SPAN_METERS = 800.0

// A tap moves the pin (PROF-07) when [onPick] is set; pinch and pan stay MapKit's own.
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MapPin(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
    onPick: ((latitude: Double, longitude: Double) -> Unit)?,
) {
    val pin = remember { MKPointAnnotation() }
    val pick by rememberUpdatedState(onPick)
    val target = remember { MapTapTarget { lat, lng -> pick?.invoke(lat, lng) } }
    UIKitView(
        factory = {
            MKMapView().apply {
                addAnnotation(pin)
                target.map = this
                addGestureRecognizer(UITapGestureRecognizer(target, NSSelectorFromString("tapped:")))
            }
        },
        modifier = modifier,
        update = { map ->
            val centre = CLLocationCoordinate2DMake(latitude, longitude)
            pin.setCoordinate(centre)
            map.setRegion(MKCoordinateRegionMakeWithDistance(centre, SPAN_METERS, SPAN_METERS), animated = false)
        },
    )
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class MapTapTarget(
    private val onTap: (Double, Double) -> Unit,
) : NSObject() {
    var map: MKMapView? = null

    @ObjCAction
    fun tapped(recognizer: UITapGestureRecognizer) {
        val view = map ?: return
        val point = recognizer.locationInView(view)
        view.convertPoint(point, toCoordinateFromView = view).useContents { onTap(latitude, longitude) }
    }
}
