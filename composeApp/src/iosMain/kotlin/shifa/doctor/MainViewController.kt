package shifa.doctor

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSProcessInfo
import shifa.doctor.ui.debug.spikeAutoRun
import shifa.doctor.utils.isDebugBuild

// Called from Swift, so it keeps the type-like name.
@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        App()
    }.also {
        // CORE-07: `xcrun simctl launch … -spikeAutoRun` checks AAC record and play without a UI driver.
        spikeAutoRun = isDebugBuild && NSProcessInfo.processInfo.arguments.contains("-spikeAutoRun")
    }
