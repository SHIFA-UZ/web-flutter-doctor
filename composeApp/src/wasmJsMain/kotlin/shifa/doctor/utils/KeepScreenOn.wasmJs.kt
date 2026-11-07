package shifa.doctor.utils

import androidx.compose.runtime.Composable

// ponytail: only video calls ask for it, and they come with WEB-08 (the Screen Wake Lock API then).
@Composable
actual fun KeepScreenOn() = Unit
