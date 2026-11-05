package shifa.doctor.utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import shifa.doctor.data.api.httpLogger

@Composable
actual fun rememberDocumentPickerLauncher(
    kinds: DocumentKinds,
    onDocumentSelected: (DocumentPickerResult?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    // A chat attachment that can't be read says so (not a silent cancel); a PDF picker keeps its null.
    val unreadable = { uri: android.net.Uri ->
        onDocumentSelected(
            if (kinds == DocumentKinds.ChatAttachment) {
                DocumentPickerResult(uri.toString(), uri.lastPathSegment.orEmpty(), ByteArray(0), readFailed = true)
            } else {
                null
            },
        )
    }
    val read: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            try {
                val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

                val fileName =
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        cursor.getString(nameIndex)
                    } ?: uri.lastPathSegment ?: "document_${System.currentTimeMillis()}.pdf"

                if (fileBytes != null) {
                    onDocumentSelected(
                        DocumentPickerResult(
                            uri = uri.toString(),
                            fileName = fileName,
                            fileBytes = fileBytes,
                        ),
                    )
                } else {
                    unreadable(uri)
                }
            } catch (e: Exception) {
                // HIPAA (CLAUDE.md "Logging"): the class only; the message can carry a file name or path.
                httpLogger.log("FAILED to read the picked document: ${e::class.simpleName}")
                unreadable(uri)
            }
        } else {
            onDocumentSelected(null)
        }
    }
    // One type for a PDF (the system's content picker); several for a chat attachment (the documents UI).
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), read)
    val chat = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), read)
    return {
        when (kinds) {
            DocumentKinds.Pdf -> pdf.launch("application/pdf")
            DocumentKinds.ChatAttachment -> chat.launch(CHAT_MIME_TYPES)
        }
    }
}

private val CHAT_MIME_TYPES =
    arrayOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "image/jpeg",
        "image/png",
    )
