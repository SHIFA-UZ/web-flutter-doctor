package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

/** Web only: loads every resource once from the root (see the web actual); on the phones it only sets [resourcesReady]. */
@Composable
expect fun ResourceWarmup()

/**
 * True once the first screens can draw without loading anything: on the web when [ResourceWarmup] has loaded the
 * icons and fonts (index.html's loading frame lifts then, and the splash starts its animation), on the phones at once:
 * they read resources synchronously.
 */
val resourcesReady = mutableStateOf(false)
