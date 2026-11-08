package shifa.doctor.utils

import shifa.doctor.BuildConfig

actual val isDebugBuild: Boolean = BuildConfig.DEBUG

actual val isDevelopmentSite: Boolean = false

actual val appVersion: String = BuildConfig.VERSION_NAME

actual val appBuild: String = BuildConfig.VERSION_CODE.toString()

actual val platformName: String = "android"
