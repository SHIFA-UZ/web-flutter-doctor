package shifa.doctor.utils

import androidx.compose.runtime.Composable

// ponytail: file picking on the web is WEB-05 (<input type="file">); until then it reads as a cancel.
@Composable
actual fun rememberDocumentPickerLauncher(
    kinds: DocumentKinds,
    onDocumentSelected: (DocumentPickerResult?) -> Unit,
): () -> Unit = { onDocumentSelected(null) }
