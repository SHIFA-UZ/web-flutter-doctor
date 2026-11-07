package shifa.doctor.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ponytail: no map on the web yet (WEB-05: open the place in a map site); the box keeps the layout.
@Composable
actual fun MapPin(
    latitude: Double,
    longitude: Double,
    modifier: Modifier,
    onPick: ((latitude: Double, longitude: Double) -> Unit)?,
) {
    Box(modifier)
}
