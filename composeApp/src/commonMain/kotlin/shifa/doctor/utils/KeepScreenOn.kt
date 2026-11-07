package shifa.doctor.utils

import androidx.compose.runtime.Composable

/** Keeps the screen awake while in composition (a video call, VIDEO-02). */
@Composable
expect fun KeepScreenOn()
