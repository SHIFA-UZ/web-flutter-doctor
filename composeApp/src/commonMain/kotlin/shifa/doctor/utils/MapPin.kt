package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A native map centred on one pin (CORE-07 spike, D-06): MapKit on iOS, OpenStreetMap tiles through osmdroid on
 * Android, so no Google key ships in the app (BE-15). A tap moves the pin and reports it through [onPick].
 */
@Composable
expect fun MapPin(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    onPick: ((latitude: Double, longitude: Double) -> Unit)? = null,
)
