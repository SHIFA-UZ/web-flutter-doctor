package shifa.doctor.utils

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController
import shifa.doctor.data.api.httpLogger

// The file goes to the temporary folder, then the share sheet (anchored mid-window for iPad, as in TextShare).
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@Composable
actual fun rememberFileShare(): (fileName: String, mimeType: String, bytes: ByteArray) -> Boolean =
    { fileName, _, bytes ->
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + safeFileName(fileName))
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (!data.writeToURL(url, atomically = true) || root == null) {
            httpLogger.log("FAILED to share a file")
            false
        } else {
            val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            sheet.popoverPresentationController?.apply {
                sourceView = root.view
                root.view.bounds.useContents { sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
            }
            root.presentViewController(sheet, animated = true, completion = null)
            true
        }
    }
