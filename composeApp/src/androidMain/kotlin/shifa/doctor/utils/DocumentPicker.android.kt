package shifa.doctor.utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import shifa.doctor.data.api.httpLogger

@Composable
actual fun rememberDocumentPickerLauncher(onDocumentSelected: (DocumentPickerResult?) -> Unit): () -> Unit {
    val context = LocalContext.current

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
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
                        onDocumentSelected(null)
                    }
                } catch (e: Exception) {
                    // HIPAA (CLAUDE.md "Logging"): the class only; the message can carry a file name or path.
                    httpLogger.log("FAILED to read the picked document: ${e::class.simpleName}")
                    onDocumentSelected(null)
                }
            } else {
                onDocumentSelected(null)
            }
        }

    return { launcher.launch("application/pdf") }
}
