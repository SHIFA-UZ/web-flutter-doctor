package shifa.doctor.utils

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

// On iPad the sheet is a popover and needs an anchor, or UIKit throws: the centre of the window.
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberTextShare(): (text: String) -> Unit =
    { text ->
        UIApplication.sharedApplication.keyWindow?.rootViewController?.let { root ->
            val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            sheet.popoverPresentationController?.apply {
                sourceView = root.view
                root.view.bounds.useContents { sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
            }
            root.presentViewController(sheet, animated = true, completion = null)
        }
    }
