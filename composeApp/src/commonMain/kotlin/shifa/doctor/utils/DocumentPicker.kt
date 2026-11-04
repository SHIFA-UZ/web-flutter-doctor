package shifa.doctor.utils

import androidx.compose.runtime.Composable

data class DocumentPickerResult(
    val uri: String,
    val fileName: String,
    val fileBytes: ByteArray,
)

/** A PDF (11: the picker takes nothing else). */
@Composable
expect fun rememberDocumentPickerLauncher(onDocumentSelected: (DocumentPickerResult?) -> Unit): () -> Unit
