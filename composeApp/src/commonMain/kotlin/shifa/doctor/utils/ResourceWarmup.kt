package shifa.doctor.utils

import androidx.compose.runtime.Composable

/** Web only: loads every resource once from the root (see the web actual); nothing on the phones. */
@Composable
expect fun ResourceWarmup()
