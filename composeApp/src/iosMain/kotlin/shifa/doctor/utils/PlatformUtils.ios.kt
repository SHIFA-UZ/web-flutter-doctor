package shifa.doctor.utils

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary
