package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject

// A copy in the app's sandbox, so it's readable without security-scoped access.
@Composable
actual fun rememberDocumentPickerLauncher(onDocumentSelected: (DocumentPickerResult?) -> Unit): () -> Unit {
    val onSelected by rememberUpdatedState(onDocumentSelected)
    val delegate =
        remember {
            object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>,
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return onSelected(null)
                    val bytes = NSData.dataWithContentsOfURL(url)?.toByteArray()
                    onSelected(
                        bytes?.let {
                            DocumentPickerResult(
                                url.absoluteString.orEmpty(),
                                url.lastPathComponent ?: "document.pdf",
                                it,
                            )
                        },
                    )
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onSelected(null)
            }
        }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF), asCopy = true)
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
            picker,
            animated = true,
            completion = null,
        )
    }
}
