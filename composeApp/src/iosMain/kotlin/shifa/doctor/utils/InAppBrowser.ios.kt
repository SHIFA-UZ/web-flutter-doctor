package shifa.doctor.utils

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberInAppBrowser(): (url: String) -> Boolean =
    { url ->
        // SFSafariViewController takes http(s) only (anything else throws).
        val nsUrl = NSURL.URLWithString(url)?.takeIf { it.scheme == "http" || it.scheme == "https" }
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (nsUrl == null || root == null) {
            false
        } else {
            root.presentViewController(SFSafariViewController(uRL = nsUrl), animated = true, completion = null)
            true
        }
    }
