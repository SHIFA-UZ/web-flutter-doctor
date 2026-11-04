package shifa.doctor.utils

import shifa.doctor.BuildConfig

actual val isDebugBuild: Boolean = BuildConfig.DEBUG

actual val appVersion: String = BuildConfig.VERSION_NAME

actual val platformName: String = "android"
