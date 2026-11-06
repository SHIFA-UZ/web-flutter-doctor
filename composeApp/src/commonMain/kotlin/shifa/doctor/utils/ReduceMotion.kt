package shifa.doctor.utils

import androidx.compose.runtime.Composable

/** The system asks for less motion (Android's animator scale at 0, iOS's Reduce Motion): animations stay still. */
@Composable
expect fun rememberReduceMotion(): Boolean
