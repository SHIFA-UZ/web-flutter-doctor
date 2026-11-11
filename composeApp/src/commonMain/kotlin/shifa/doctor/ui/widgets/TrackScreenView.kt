package shifa.doctor.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import shifa.doctor.data.tracking.TrackScreen
import shifa.doctor.data.tracking.Tracking

/**
 * Sends one screen view when the screen appears, and again only when [step] changes (a wizard's step). Goes in
 * `XxxScreen`, never in `XxxScreenContent`, so previews and screenshot tests send nothing. [properties] are fixed
 * values the screen knows (`"mode" to mode`), never anything read from a route.
 */
@Composable
fun TrackScreenView(
    screen: TrackScreen,
    vararg properties: Pair<String, Any?>,
    step: Any? = null,
) {
    LaunchedEffect(screen, step) { Tracking.screen(screen, properties.toMap()) }
}
