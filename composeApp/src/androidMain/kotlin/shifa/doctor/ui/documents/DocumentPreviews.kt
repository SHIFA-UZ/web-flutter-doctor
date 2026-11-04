package shifa.doctor.ui.documents

import androidx.compose.runtime.Composable
import shifa.doctor.data.model.DocumentFile
import shifa.doctor.ui.theme.PreviewTheme

@Composable
internal fun ViewerLoadingPreview() =
    PreviewTheme { DocumentViewerContent("Complete blood count", DocumentViewerUiState(), DocumentViewerActions()) }

@Composable
internal fun ViewerNoAccessPreview() =
    PreviewTheme { DocumentViewerContent("ECG", DocumentViewerUiState(Viewed.NoAccess), DocumentViewerActions()) }

@Composable
internal fun ViewerCantPreviewPreview() =
    PreviewTheme {
        DocumentViewerContent(
            "Discharge summary",
            DocumentViewerUiState(
                Viewed.Ready(DocumentFile(byteArrayOf(1, 2, 3), "summary.docx", "application/octet-stream")),
            ),
            DocumentViewerActions(),
        )
    }
