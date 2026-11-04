package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

private const val ZOOM = 16.0

@Composable
actual fun MapPin(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
    onPick: ((latitude: Double, longitude: Double) -> Unit)?,
) {
    val context = LocalContext.current
    val pick = rememberUpdatedState(onPick)
    val map =
        remember {
            // OSM's tile policy asks for an identifying user agent.
            Configuration.getInstance().userAgentValue = context.packageName
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                // Pinch zooms; the built-in +/− buttons would sit under the screen's own controls.
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(ZOOM)
            }
        }
    val marker = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }
    DisposableEffect(map) {
        map.overlays += marker
        // OSM's tile policy: "© OpenStreetMap contributors" on the map.
        map.overlays += CopyrightOverlay(context)
        map.overlays +=
            MapEventsOverlay(
                object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        val onPick = pick.value ?: return false
                        marker.position = p
                        map.invalidate()
                        onPick(p.latitude, p.longitude)
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint) = false
                },
            )
        map.onResume()
        onDispose { map.onDetach() }
    }
    // The MapView draws past its bounds inside a scrolling column unless clipped.
    AndroidView({ map }, modifier.clipToBounds()) {
        val point = GeoPoint(latitude, longitude)
        marker.position = point
        it.controller.setCenter(point)
        it.invalidate()
    }
}
