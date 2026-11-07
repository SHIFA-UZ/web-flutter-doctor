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
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject

// A copy in the app's sandbox, so it's readable without security-scoped access.
@Composable
actual fun rememberDocumentPickerLauncher(
    kinds: DocumentKinds,
    onDocumentSelected: (DocumentPickerResult?) -> Unit,
): () -> Unit {
    val onSelected by rememberUpdatedState(onDocumentSelected)
    val currentKinds by rememberUpdatedState(kinds)
    val delegate =
        remember {
            object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>,
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return onSelected(null)
                    val bytes = NSData.dataWithContentsOfURL(url)?.toByteArray()
                    val name = url.lastPathComponent ?: "document.pdf"
                    onSelected(
                        when {
                            bytes != null -> DocumentPickerResult(url.absoluteString.orEmpty(), name, bytes)
                            // A chat attachment that can't be read says so (not a silent cancel).
                            currentKinds == DocumentKinds.ChatAttachment ->
                                DocumentPickerResult(
                                    url.absoluteString.orEmpty(),
                                    name,
                                    ByteArray(0),
                                    readFailed = true,
                                )
                            else -> null
                        },
                    )
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onSelected(null)
            }
        }
    return {
        val types =
            when (kinds) {
                DocumentKinds.Pdf -> listOf(UTTypePDF)
                DocumentKinds.ChatAttachment ->
                    CHAT_DOCUMENT_EXTENSIONS.mapNotNull {
                        UTType.typeWithFilenameExtension(
                            it,
                        )
                    }
            }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
            picker,
            animated = true,
            completion = null,
        )
    }
}
