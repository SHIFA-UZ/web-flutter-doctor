package shifa.doctor.ui.documents

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import shifa.doctor.data.model.DocumentFile
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.uploadFailed
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

// DOCS-03: the title & type sheet after a scan, Add tried without a type.
@Composable
internal fun UploadDetailsPreview() =
    PreviewTheme {
        UploadDetailsTray(
            UploadDraft(
                shifa.doctor.data.documents.UploadFile(ByteArray(3), "scan.pdf", "application/pdf"),
                "Scanned document (3 pages)",
                pages = 3,
            ),
            tried = true,
            {},
            {},
            {},
        )
    }

// DOCS-03: a running upload over the list, and a failed one's banner with Retry.
@Composable
internal fun UploadStatusPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.padding(shifa.doctor.ui.theme.ShifaSpacing.Inset.Md),
        ) {
            UploadStatus(UploadUiState(uploading = "Scanned document (3 pages)", progress = 0.42f), {}, {}, {})
            UploadStatus(
                UploadUiState(
                    failed =
                        UploadDraft(
                            shifa.doctor.data.documents.UploadFile(ByteArray(3), "x.pdf", "application/pdf"),
                            "x",
                        ),
                    banner =
                        shifa.doctor.ui.emailcode.CodeBanner(
                            shifa.doctor.generated.resources.Res.string.uploadFailed,
                        ),
                ),
                {},
                {},
                {},
            )
        }
    }

// BE-21: the doctor's own upload being renamed and given a new type, and the delete question.
private val ownUpload =
    shifa.doctor.data.model.PatientDocument(
        7,
        "Treatment notes",
        "2026-10-21",
        url = "f",
        category = "INTERNAL_NOTE",
        canEdit = true,
    )

@Composable
internal fun EditDocumentPreview() =
    PreviewTheme {
        EditDocumentTray(
            DocumentEdit(
                ownUpload,
                title = "Treatment notes, follow-up",
                category = shifa.doctor.data.model.DocumentCategory.APPOINTMENT_NOTE,
            ),
            {},
            {},
            {},
        )
    }

@Composable
internal fun DeleteDocumentPreview() = PreviewTheme { DeleteDocumentDialog(ownUpload.title, busy = false, {}, {}) }
