package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

@Composable
actual fun rememberDocumentPickerLauncher(
    kinds: DocumentKinds,
    onDocumentSelected: (DocumentPickerResult?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val result = rememberUpdatedState(onDocumentSelected)
    val accept =
        when (kinds) {
            DocumentKinds.Pdf -> "application/pdf,.pdf"
            DocumentKinds.ChatAttachment -> CHAT_DOCUMENT_EXTENSIONS.joinToString(",") { ".$it" }
        }
    return remember(accept) {
        {
            chooseFile(accept, camera = false) { file ->
                if (file == null) {
                    result.value(null)
                } else {
                    scope.launch {
                        val name = fileName(file)
                        val bytes = readFile(file)
                        result.value(
                            DocumentPickerResult(name, name, bytes ?: ByteArray(0), readFailed = bytes == null),
                        )
                    }
                }
            }
        }
    }
}
