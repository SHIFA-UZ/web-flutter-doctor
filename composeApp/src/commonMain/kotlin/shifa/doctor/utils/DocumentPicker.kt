package shifa.doctor.utils

import androidx.compose.runtime.Composable

data class DocumentPickerResult(
    val uri: String,
    val fileName: String,
    val fileBytes: ByteArray,
    // A chat attachment that was picked but couldn't be read (no bytes): not a cancel (CHAT-05: "Error uploading file").
    val readFailed: Boolean = false,
)

/** What the picker offers: a PDF (11: patient documents, certificates), or a chat attachment (CHAT-05, 07 §1.4). */
enum class DocumentKinds { Pdf, ChatAttachment }

/** The chat's attachments: pdf, doc, docx, jpg, jpeg, png (07 §1.4; the backend takes these and more). */
val CHAT_DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "jpg", "jpeg", "png")

@Composable
expect fun rememberDocumentPickerLauncher(
    kinds: DocumentKinds = DocumentKinds.Pdf,
    onDocumentSelected: (DocumentPickerResult?) -> Unit,
): () -> Unit
