package shifa.doctor.utils

import platform.Foundation.NSBundle
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary

actual val isDevelopmentSite: Boolean = false

actual val appVersion: String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0"

actual val appBuild: String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "0"

actual val platformName: String = "ios"
