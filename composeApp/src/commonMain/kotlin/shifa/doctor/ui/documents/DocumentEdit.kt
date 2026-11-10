package shifa.doctor.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.delete
import shifa.doctor.generated.resources.deleteDocument
import shifa.doctor.generated.resources.deleteDocumentBody
import shifa.doctor.generated.resources.deleteDocumentQuestion
import shifa.doctor.generated.resources.documentCategoryHint
import shifa.doctor.generated.resources.documentCategoryLabel
import shifa.doctor.generated.resources.documentTitle
import shifa.doctor.generated.resources.editDocument
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.selectDocumentType
import shifa.doctor.ui.calendar.FieldLabel
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaLinkVariant
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.tr

/**
 * BE-21: the doctor's own upload, renamed, recategorised or removed. [category] starts as the document's when it is one
 * the doctor could pick (an old upload without one must get one, as an upload does).
 */
data class DocumentEdit(
    val document: PatientDocument,
    val title: String = document.title.take(MAX_TITLE),
    val category: DocumentCategory? = document.kind?.takeIf { it.pickable },
    val tried: Boolean = false,
    val confirmDelete: Boolean = false,
    // A save or a removal on its way.
    val busy: Boolean = false,
    val error: CodeBanner? = null,
) {
    // An emptied title keeps the old one.
    val effectiveTitle: String get() = title.trim().ifEmpty { document.title }.take(MAX_TITLE)

    val changed: Boolean get() = effectiveTitle != document.title || category != document.kind
}

/** The edit sheet and its delete question, hosted by each screen that lists documents (the record, a visit). */
@Composable
fun DocumentEditSheets(
    edit: DocumentEdit?,
    vm: PatientDocumentsViewModel,
) {
    edit ?: return
    if (edit.confirmDelete) {
        val keep = { if (!edit.busy) vm.editDraft { it.copy(confirmDelete = false) } }
        // A drag or a scrim tap can't hide either sheet while it saves or removes (its error shows there).
        ProfileSheet(keep, confirmHide = { !edit.busy }) {
            DeleteDocumentDialog(edit.document.title, edit.busy, vm::delete, keep)
        }
    } else {
        ProfileSheet(vm::closeEdit, confirmHide = { !edit.busy }) {
            EditDocumentTray(edit, vm::editDraft, vm::saveEdit, vm::closeEdit)
        }
    }
}

/** The title and the type, as the upload asks them (flow 06/07 step 3), with Save and the way to Delete. */
@Composable
internal fun EditDocumentTray(
    edit: DocumentEdit,
    change: ((DocumentEdit) -> DocumentEdit) -> Unit,
    save: () -> Unit,
    close: () -> Unit,
) = ShifaTray(tr(Res.string.editDocument), close) {
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        ShifaInputField(
            edit.title,
            { v -> change { it.copy(title = v.take(MAX_TITLE)) } },
            tr(Res.string.documentTitle),
            Modifier.fillMaxWidth(),
            supportingText = edit.document.title.takeIf { edit.title.isBlank() },
        )
        FieldLabel(tr(Res.string.documentCategoryLabel))
        Text(
            tr(Res.string.documentCategoryHint),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
        CategoryChips(edit.category) { k -> change { it.copy(category = k) } }
        if (edit.category == null) {
            Text(
                tr(Res.string.selectDocumentType),
                style = ShifaTextStyles.metaSmall(),
                color = if (edit.tried) ShifaColors.Text.Error else ShifaColors.Text.Secondary,
            )
        }
        codeBanner(edit.error) { change { it.copy(error = null) } }?.invoke()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        ShifaSecondaryButton(tr(Res.string.cancel), close, Modifier.weight(1f), enabled = !edit.busy)
        ShifaPrimaryButton(
            tr(Res.string.save),
            save,
            Modifier.weight(1f),
            enabled = edit.changed && edit.category != null,
            loading = edit.busy,
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShifaLink(
            tr(Res.string.deleteDocument),
            { change { it.copy(confirmDelete = true, error = null) } },
            variant = ShifaLinkVariant.Danger,
            enabled = !edit.busy,
        )
    }
}

/** "Delete this document?", red, naming it; the confirm spins while the removal runs. */
@Composable
internal fun DeleteDocumentDialog(
    title: String,
    busy: Boolean,
    delete: () -> Unit,
    keep: () -> Unit,
) = ShifaDialogSheet(
    tr(Res.string.deleteDocumentQuestion),
    tr(Res.string.delete),
    delete,
    body = tr(Res.string.deleteDocumentBody, title),
    tone = DialogTone.Destructive,
    cancelText = tr(Res.string.cancel),
    onCancel = keep,
    confirmLoading = busy,
)
