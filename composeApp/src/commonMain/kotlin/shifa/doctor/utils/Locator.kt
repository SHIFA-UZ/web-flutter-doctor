package shifa.doctor.utils

import androidx.compose.runtime.Composable

/**
 * The device's position once, for the map picker's "Use my location" (flow 08/04): asks for location access first
 * (when in use only), then one fix. Null when access is refused or no fix comes within a few seconds.
 * Android: the platform location service (no Google Play services); iOS: Core Location; web: the browser's.
 */
@Composable
expect fun rememberLocator(): suspend () -> Pair<Double, Double>?

/** How long a fix may take before the picker says the location isn't available. */
internal const val LOCATE_TIMEOUT_MS = 15_000L
