package shifa.doctor.utils

import androidx.compose.runtime.Composable

// The phones read resources synchronously: nothing to warm.
@Composable
actual fun ResourceWarmup() = Unit
