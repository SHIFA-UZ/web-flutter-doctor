package shifa.doctor.ui.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.documents.documentsRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.anotherUser
import shifa.doctor.generated.resources.doctor
import shifa.doctor.generated.resources.documentDeleteFailed
import shifa.doctor.generated.resources.documentDeletedNamed
import shifa.doctor.generated.resources.documentHistory
import shifa.doctor.generated.resources.documentUpdated
import shifa.doctor.generated.resources.documentsEmptyHint
import shifa.doctor.generated.resources.documentsLoadFailed
import shifa.doctor.generated.resources.documentsNoMatch
import shifa.doctor.generated.resources.editDocument
import shifa.doctor.generated.resources.editForm
import shifa.doctor.generated.resources.formDocumentTitle
import shifa.doctor.generated.resources.formsUnavailableNotice
import shifa.doctor.generated.resources.ic_edit_16
import shifa.doctor.generated.resources.ic_external_16
import shifa.doctor.generated.resources.ic_lock_16
import shifa.doctor.generated.resources.ic_more_v_16
import shifa.doctor.generated.resources.ic_users_16
import shifa.doctor.generated.resources.noDocuments
import shifa.doctor.generated.resources.openDocument
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.requestAccess
import shifa.doctor.generated.resources.requestAccessSent
import shifa.doctor.generated.resources.requestAlreadySent
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.search
import shifa.doctor.generated.resources.sharedWithTeamTooltip
import shifa.doctor.generated.resources.upload
import shifa.doctor.generated.resources.uploadedByName
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaActionButton
import shifa.doctor.ui.widgets.ShifaActionVariant
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.tr

data class PatientDocumentsUiState(
    // Null until the first read.
    val documents: List<PatientDocument>? = null,
    val error: CodeBanner? = null,
    val banner: CodeBanner? = null,
    val query: String = "",
    // Documents whose access request is on its way (one at a time per document).
    val requesting: Set<Long> = emptySet(),
    // BE-21: the doctor's own upload being renamed, recategorised or removed.
    val edit: DocumentEdit? = null,
) {
    /** Title search, instant and case-insensitive (flow 06/06 §3.2); newest first as the server sends them. */
    val shown: List<PatientDocument>
        get() = documents.orEmpty().filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
}

/**
 * DOCS-04 (flow 06/06, spec 06 §2.6, §4.6): a patient's documents in the record's Documents tab. Read when the tab
 * opens and each time it shows again (back from the viewer: access may have changed), pulled to refresh.
 */
class PatientDocumentsViewModel(
    private val patientId: Long,
    private val clinicId: Long? = null,
    private val repo: DocumentsRepository = documentsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PatientDocumentsUiState())
    val state: StateFlow<PatientDocumentsUiState> = _state.asStateFlow()

    private var reading: Job? = null

    /** A newer read replaces one still running, so a late failure can't hide the list it loaded. */
    fun load() {
        reading?.cancel()
        _state.update { it.copy(error = null) }
        reading =
            viewModelScope.launch {
                repo.list(patientId, clinicId).fold(
                    onSuccess = { list -> _state.update { it.copy(documents = list, error = null) } },
                    onFailure = { e ->
                        val banner =
                            CodeBanner(e.toUserMessage(Res.string.documentsLoadFailed), requestId = e.requestId)
                        // With a list shown, a failed refresh is a banner over it; else the tab's error with Retry.
                        _state.update {
                            if (it.documents != null) {
                                it.copy(
                                    banner = banner,
                                )
                            } else {
                                it.copy(error = banner)
                            }
                        }
                    },
                )
            }
    }

    fun search(query: String) = _state.update { it.copy(query = query) }

    fun ui(change: (PatientDocumentsUiState) -> PatientDocumentsUiState) = _state.update(change)

    fun startEdit(document: PatientDocument) {
        if (document.editable) _state.update { it.copy(edit = DocumentEdit(document), banner = null) }
    }

    /** The sheet's changes; none while a save or a removal runs (what it sends is what the doctor sees). */
    fun editDraft(change: (DocumentEdit) -> DocumentEdit) =
        _state.update { s -> if (s.edit?.busy == true) s else s.copy(edit = s.edit?.let(change)) }

    /** Closes the sheet, unless a save or a removal is on its way. */
    fun closeEdit() = _state.update { if (it.edit?.busy == true) it else it.copy(edit = null) }

    /** Save: the row is replaced by the server's answer; an error stays in the sheet. */
    fun saveEdit() {
        val e = _state.value.edit ?: return
        val category = e.category ?: return _state.update { it.copy(edit = e.copy(tried = true)) }
        if (e.busy) return
        _state.update { it.copy(edit = e.copy(busy = true, error = null)) }
        viewModelScope.launch {
            repo.edit(patientId, e.document.id, e.effectiveTitle, category.name, clinicId).fold(
                { updated ->
                    _state.update { s ->
                        s.copy(
                            edit = null,
                            documents = s.documents?.map { if (it.id == updated.id) updated else it },
                            banner = CodeBanner(Res.string.documentUpdated, error = false),
                        )
                    }
                    // A read still out (an upload's, a refresh) left before the change: one after it replaces it.
                    if (reading?.isActive == true) load()
                },
                { err -> fail(err, Res.string.saveFailed) },
            )
        }
    }

    /** Delete, once confirmed: gone from the list; already gone on the server (404) counts as done. */
    fun delete() {
        val e = _state.value.edit ?: return
        if (e.busy) return
        _state.update { it.copy(edit = e.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val result = repo.delete(patientId, e.document.id, clinicId)
            val gone = result.isSuccess || result.exceptionOrNull() is ApiException.NotFound
            if (!gone) return@launch fail(result.exceptionOrNull(), Res.string.documentDeleteFailed)
            _state.update { s ->
                s.copy(
                    edit = null,
                    documents = s.documents?.filterNot { it.id == e.document.id },
                    banner =
                        CodeBanner(
                            Res.string.documentDeletedNamed,
                            error = false,
                            args = listOf(e.document.title),
                        ),
                )
            }
            if (reading?.isActive == true) load()
        }
    }

    private fun fail(
        err: Throwable?,
        fallback: org.jetbrains.compose.resources.StringResource,
    ) = _state.update { s ->
        s.copy(
            edit =
                s.edit?.copy(
                    busy = false,
                    // Back to the sheet, where the error shows.
                    confirmDelete = false,
                    error = CodeBanner(err?.toUserMessage(fallback) ?: fallback, requestId = err?.requestId),
                ),
        )
    }

    /** The lock stays until the owner approves (a push brings that); a second request may be 409: already sent. */
    fun requestAccess(document: PatientDocument) {
        if (document.id in _state.value.requesting) return
        _state.update { it.copy(requesting = it.requesting + document.id, banner = null) }
        viewModelScope.launch {
            val result = repo.requestAccess(patientId, document.id, clinicId)
            _state.update { s ->
                s.copy(
                    requesting = s.requesting - document.id,
                    banner =
                        result.fold(
                            { CodeBanner(Res.string.requestAccessSent, error = false) },
                            { e ->
                                if (e is ApiException.Conflict) {
                                    CodeBanner(Res.string.requestAlreadySent, error = false)
                                } else {
                                    CodeBanner(e.toUserMessage(Res.string.documentsLoadFailed), requestId = e.requestId)
                                }
                            },
                        ),
                )
            }
        }
    }
}

data class DocumentsActions(
    val retry: () -> Unit = {},
    val search: (String) -> Unit = {},
    val open: (PatientDocument) -> Unit = {},
    val requestAccess: (PatientDocument) -> Unit = {},
    val ui: ((PatientDocumentsUiState) -> PatientDocumentsUiState) -> Unit = {},
    // DOCS-03: the upload sheet, and the running upload's Cancel / Retry / banner close.
    val upload: () -> Unit = {},
    val cancelUpload: () -> Unit = {},
    val retryUpload: () -> Unit = {},
    val dismissUpload: () -> Unit = {},
    // FORM-05: a linked form's PDF opens its form in the editor.
    val editForm: (Long) -> Unit = {},
    // BE-21: the doctor's own upload opens the edit sheet; null where the screen offers no editing.
    val edit: ((PatientDocument) -> Unit)? = null,
)

/** The tab's rows in the record's lazy list (200+ documents stay lazy, flow §8). */
fun LazyListScope.documentRows(
    state: PatientDocumentsUiState,
    actions: DocumentsActions,
    gap: Modifier,
    upload: UploadUiState = UploadUiState(),
    // Each linked document's form (FORM-05), for "Edit form"; [formsFailed]: they couldn't be read (flow §6 notice).
    formOf: Map<Long, Long> = emptyMap(),
    formsFailed: Boolean = false,
) {
    item {
        Column(gap, verticalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr(Res.string.documentHistory),
                    Modifier.weight(1f),
                    style = ShifaTextStyles.panelTitle(),
                    color = ShifaColors.Text.Primary,
                )
                // One upload at a time (flow §8 allows more; the card shows one).
                ShifaPrimaryButton(
                    tr(Res.string.upload),
                    actions.upload,
                    size = ShifaButtonSize.Compact,
                    enabled = upload.uploading == null,
                )
            }
            UploadStatus(upload, actions.cancelUpload, actions.retryUpload, actions.dismissUpload)
            codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
            if (formsFailed && state.documents?.any { it.kind == DocumentCategory.FORM_025_2 } == true) {
                Text(
                    tr(Res.string.formsUnavailableNotice),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            if (state.documents?.isNotEmpty() == true) {
                ShifaSearchField(state.query, actions.search, tr(Res.string.search), Modifier.fillMaxWidth())
            }
        }
    }
    val documents = state.documents
    val error = state.error
    when {
        error != null ->
            item {
                ShifaLoadError(tr(error.text), actions.retry, gap, requestId = error.requestId)
            }
        documents == null -> item { Box(gap) { ShifaListSkeleton(count = 3) } }
        documents.isEmpty() ->
            item {
                Column(
                    gap.background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S24),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    Text(
                        tr(Res.string.noDocuments),
                        style = ShifaTextStyles.panelTitle(),
                        color = ShifaColors.Text.Primary,
                    )
                    Text(
                        tr(Res.string.documentsEmptyHint),
                        style = ShifaTextStyles.bodyText(),
                        color = ShifaColors.Text.Secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        state.shown.isEmpty() ->
            item {
                Text(
                    tr(Res.string.documentsNoMatch, state.query.trim()),
                    gap.padding(ShifaSize.S16),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
        else ->
            items(state.shown, key = { "document-${it.id}" }) { d ->
                Box(gap) { DocumentCard(d, d.id in state.requesting, actions, formOf[d.id]) }
            }
    }
}

/**
 * A document (flow §3.3): lock and title, the date and category chip (teal for results shared with every doctor, grey
 * for private ones), the shared mark, "Uploaded by …" when locked; Open, or Request access when locked. The whole card
 * does the same as its button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocumentCard(
    d: PatientDocument,
    requesting: Boolean,
    actions: DocumentsActions,
    // The form this document is the PDF of: "Edit form" next to Open (flow 06/06 §3.3).
    formId: Long? = null,
) {
    val tap: (() -> Unit)? =
        when {
            d.locked -> ({ actions.requestAccess(d) })
            d.openable -> ({ actions.open(d) })
            else -> null
        }
    val onEdit = actions.edit?.takeIf { d.editable }
    // Large text: the wide Request access button goes under the details instead of squeezing them (flow §8).
    val stacked = d.locked && LocalDensity.current.fontScale >= LARGE_TEXT
    val action: (@Composable () -> Unit)? =
        when {
            d.locked -> {
                {
                    ShifaActionButton(
                        { actions.requestAccess(d) },
                        text = tr(Res.string.requestAccess),
                        variant = ShifaActionVariant.Secondary,
                        enabled = !requesting,
                    )
                }
            }
            // Edit needs the form link and access; Open needs the file too (flow §3.3).
            d.openable || formId != null || onEdit != null -> {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                        onEdit?.let { go ->
                            // ⋮, not the pencil: that one is "Edit form" on a form's PDF.
                            ShifaIconButton(
                                Res.drawable.ic_more_v_16,
                                tr(Res.string.editDocument),
                                { go(d) },
                                variant = ShifaIconButtonVariant.Secondary,
                            )
                        }
                        formId?.let { id ->
                            ShifaIconButton(
                                Res.drawable.ic_edit_16,
                                tr(Res.string.editForm),
                                { actions.editForm(id) },
                                variant = ShifaIconButtonVariant.Secondary,
                            )
                        }
                        if (d.openable) {
                            ShifaIconButton(
                                Res.drawable.ic_external_16,
                                tr(Res.string.openDocument),
                                { actions.open(d) },
                                variant = ShifaIconButtonVariant.Secondary,
                            )
                        }
                    }
                }
            }
            else -> null
        }
    Column(
        Modifier
            .fillMaxWidth()
            .background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
            .then(if (tap != null) Modifier.clickable(role = Role.Button, onClick = tap) else Modifier)
            .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Details(d, Modifier.weight(1f))
            if (!stacked) action?.invoke()
        }
        if (stacked) action?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    d: PatientDocument,
    modifier: Modifier,
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6), verticalAlignment = Alignment.CenterVertically) {
        if (d.locked) Icon(painterResource(Res.drawable.ic_lock_16), null, tint = ShifaColors.Icon.Secondary)
        Text(
            documentTitle(d),
            style = ShifaTextStyles.buttonCompact(),
            color = ShifaColors.Text.Primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(d.day?.let(::displayDate) ?: "—", style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        categoryChip(d)
        if (d.isSharedWithTeam) {
            Icon(
                painterResource(Res.drawable.ic_users_16),
                tr(Res.string.sharedWithTeamTooltip),
                tint = ShifaColors.Icon.Secondary,
            )
        }
    }
    if (d.locked) {
        Text(
            tr(Res.string.uploadedByName, creator(d.creatorLabel)),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    }
}

/** The byline's who: a name as given; the role words the backend may send in the app's language (flow §4). */
@Composable
private fun creator(label: String?): String =
    when (label?.trim()?.lowercase()) {
        null, "", "unknown" -> tr(Res.string.anotherUser)
        "doctor" -> tr(Res.string.doctor)
        "patient" -> tr(Res.string.patient)
        else -> label.trim()
    }

internal const val LARGE_TEXT = 1.5f

/** A 025-2 form's PDF reads "Form 025-2 (12.03.2026)" in the app's language; others as uploaded. */
@Composable
internal fun documentTitle(d: PatientDocument): String =
    if (d.kind == DocumentCategory.FORM_025_2) {
        // The form's own day, which the editor writes into the title ("Form 025-2 (2026-11-05)"); the upload's date
        // can be a day off (the server's, not the practice's, around midnight).
        val formDay =
            FORM_DAY.find(
                d.title,
            )?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        tr(Res.string.formDocumentTitle, (formDay ?: d.day)?.let(::displayDate) ?: "—")
    } else {
        d.title.ifBlank { "—" }
    }

/** The category's label; an unknown new code humanised ("GENETIC_TEST" → "Genetic test"), none without a category. */
@Composable
private fun categoryChip(d: PatientDocument) {
    val kind = d.kind
    val label =
        kind?.let { tr(it.label) }
            ?: d.category?.trim()?.takeIf(
                String::isNotEmpty,
            )?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
            ?: return
    if (kind?.medical == true) {
        ProfileChip(label, ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.Primary)
    } else {
        ProfileChip(label, ShifaColors.Bg.Canvas, ShifaColors.Text.Secondary)
    }
}

private val FORM_DAY = Regex("""\((\d{4}-\d{2}-\d{2})\)""")
