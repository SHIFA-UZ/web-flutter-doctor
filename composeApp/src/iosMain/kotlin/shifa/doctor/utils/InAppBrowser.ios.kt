package shifa.doctor.utils

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberInAppBrowser(): (url: String) -> Unit =
    { url ->
        NSURL.URLWithString(url)?.let {
            UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
                SFSafariViewController(uRL = it),
                animated = true,
                completion = null,
            )
        }
    }
