package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

// The phones read resources synchronously: nothing to warm.
@Composable
actual fun ResourceWarmup() = SideEffect { resourcesReady.value = true }
