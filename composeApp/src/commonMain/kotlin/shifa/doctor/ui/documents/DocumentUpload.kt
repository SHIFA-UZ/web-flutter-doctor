package shifa.doctor.ui.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.documents.UploadFile
import shifa.doctor.data.documents.documentsRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.data.model.DocumentFile
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.countBucket
import shifa.doctor.data.tracking.events.DocumentsEvents
import shifa.doctor.data.tracking.events.documentKind
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.add
import shifa.doctor.generated.resources.addAnotherPage
import shifa.doctor.generated.resources.addPage
import shifa.doctor.generated.resources.cameraAccessOff
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.chooseFromGallery
import shifa.doctor.generated.resources.discard
import shifa.doctor.generated.resources.discardScannedPages
import shifa.doctor.generated.resources.documentCategoryGroupMedical
import shifa.doctor.generated.resources.documentCategoryGroupPrivate
import shifa.doctor.generated.resources.documentCategoryHint
import shifa.doctor.generated.resources.documentCategoryLabel
import shifa.doctor.generated.resources.documentTitle
import shifa.doctor.generated.resources.documentUploadedNamed
import shifa.doctor.generated.resources.fileTooLarge
import shifa.doctor.generated.resources.finish
import shifa.doctor.generated.resources.ic_camera_22
import shifa.doctor.generated.resources.ic_file_22
import shifa.doctor.generated.resources.ic_image_22
import shifa.doctor.generated.resources.ic_layers_22
import shifa.doctor.generated.resources.largeFileWarning
import shifa.doctor.generated.resources.noFileData
import shifa.doctor.generated.resources.onlyPdfOrImages
import shifa.doctor.generated.resources.openSettings
import shifa.doctor.generated.resources.pagesScannedCount
import shifa.doctor.generated.resources.patientNotFound
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.scanFailed
import shifa.doctor.generated.resources.scanMultiPage
import shifa.doctor.generated.resources.scannedDocumentPages
import shifa.doctor.generated.resources.selectDocumentType
import shifa.doctor.generated.resources.takePhoto
import shifa.doctor.generated.resources.uploadCancelled
import shifa.doctor.generated.resources.uploadDocument
import shifa.doctor.generated.resources.uploadFailed
import shifa.doctor.generated.resources.uploadPdf
import shifa.doctor.ui.calendar.FieldLabel
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.imagesToPdf
import shifa.doctor.utils.rememberCameraLauncher
import shifa.doctor.utils.rememberDocumentPickerLauncher
import shifa.doctor.utils.rememberImagePickerLauncher
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

/** The file being named and typed in the title & type sheet (flow 06/07 step 3). */
data class UploadDraft(
    val file: UploadFile,
    // Prefilled from the file name or "Scanned document (3 pages)"; an emptied title falls back to it.
    val suggested: String,
    val title: String = suggested.take(MAX_TITLE),
    val category: DocumentCategory? = null,
    val pages: Int = 0,
) {
    // At most 120 characters whichever way the title came (flow §4).
    val effectiveTitle: String get() = title.trim().ifEmpty { suggested }.take(MAX_TITLE)
    val large: Boolean get() = file.bytes.size > DocumentUploadViewModel.LARGE_FILE
}

data class UploadUiState(
    val options: Boolean = false,
    // A multi-page scan in progress, and the "Add another page?" question after each page.
    val pages: List<ByteArray> = emptyList(),
    val askMore: Boolean = false,
    val draft: UploadDraft? = null,
    val tried: Boolean = false,
    val askDiscard: Boolean = false,
    val cameraDenied: Boolean = false,
    // The upload running (title, sent share or null before the size is known), shown as a card over the list.
    val uploading: String? = null,
    val progress: Float? = null,
    // Kept after a failure so Retry sends the same bytes (flow §7); null once uploaded or dropped.
    val failed: UploadDraft? = null,
    val banner: CodeBanner? = null,
    // Counts finished uploads: the Documents tab reads its list again on each.
    val uploaded: Int = 0,
)

/**
 * DOCS-03 (flow 06/07, spec 06 §4.2): a PDF from the phone, one photo (camera or gallery, uploaded as the JPEG itself:
 * Flutter wrapped it in a PDF) or a multi-page scan made into one PDF (DOCS-02), then a title and a required type,
 * then the upload with progress, Cancel and Retry. Nothing is kept on disk; a failed upload keeps its bytes in memory.
 */
class DocumentUploadViewModel(
    private val patientId: Long,
    private val clinicId: Long? = null,
    private val appointmentId: Long? = null,
    private val repo: DocumentsRepository = documentsRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(UploadUiState())
    val state: StateFlow<UploadUiState> = _state.asStateFlow()
    private var job: Job? = null

    // The draft whose upload is running, for the cancel event.
    private var sending: UploadDraft? = null

    fun ui(change: (UploadUiState) -> UploadUiState) = _state.update(change)

    /** The PDF picker's answer; null is a cancel (nothing happens). */
    fun pdf(
        bytes: ByteArray?,
        fileName: String?,
    ) {
        if (bytes == null) return
        val name = fileName?.takeIf(String::isNotBlank) ?: "document.pdf"
        // What it really is, by its first bytes (a renamed DOCX isn't a PDF): only PDFs and images go (flow §9).
        val mime = DocumentFile.mime(bytes, null, null)
        // (An empty or too large file says so first, in offer.)
        if (bytes.isNotEmpty() && bytes.size <= MAX_FILE && mime != DocumentFile.PDF && mime !in IMAGES) {
            tracker.track(DocumentsEvents.DocumentUploadFailed(null, null, "unsupported_type"))
            return _state.update { it.copy(banner = CodeBanner(Res.string.onlyPdfOrImages)) }
        }
        offer(UploadFile(bytes, name, mime), name.substringBeforeLast('.'))
    }

    /** One photo (camera or gallery): a JPEG from the pickers, uploaded as it is. */
    fun photo(
        bytes: ByteArray?,
        fileName: String?,
    ) {
        if (bytes == null) return
        val name = fileName?.takeIf(String::isNotBlank) ?: "photo.jpg"
        offer(UploadFile(bytes, name, "image/jpeg"), name.substringBeforeLast('.'))
    }

    /** A scanned page; a cancelled camera with pages already taken finishes the scan with them. */
    fun page(
        bytes: ByteArray?,
        suggested: String,
        fileName: String,
    ) {
        if (bytes == null) {
            if (_state.value.pages.isNotEmpty()) finishScan(suggested, fileName)
            return
        }
        _state.update { it.copy(pages = it.pages + bytes, askMore = true) }
    }

    fun addPage() = _state.update { it.copy(askMore = false) }

    fun finishScan(
        suggested: String,
        fileName: String,
    ) {
        val pages = _state.value.pages
        _state.update { it.copy(askMore = false, pages = emptyList()) }
        if (pages.isEmpty()) return
        val pdf = imagesToPdf(pages)
        if (pdf == null) {
            tracker.track(DocumentsEvents.DocumentUploadFailed("pdf", null, "scan_failed"))
            return _state.update { it.copy(banner = CodeBanner(Res.string.scanFailed)) }
        }
        offer(UploadFile(pdf, fileName, DocumentFile.PDF), suggested, pages.size)
    }

    private fun offer(
        file: UploadFile,
        suggested: String,
        pages: Int = 0,
    ) {
        val refused =
            when {
                file.bytes.isEmpty() -> "empty_file"
                file.bytes.size > MAX_FILE -> "too_large"
                else -> null
            }
        refused?.let { tracker.track(DocumentsEvents.DocumentUploadFailed(documentKind(file.mime), null, it)) }
        when {
            file.bytes.isEmpty() -> _state.update { it.copy(banner = CodeBanner(Res.string.noFileData)) }
            file.bytes.size > MAX_FILE ->
                _state.update { it.copy(banner = CodeBanner(Res.string.fileTooLarge, args = listOf(MAX_FILE_MB))) }
            else ->
                _state.update {
                    it.copy(
                        draft = UploadDraft(file, suggested, pages = pages),
                        tried = false,
                        banner = null,
                    )
                }
        }
    }

    fun edit(change: (UploadDraft) -> UploadDraft) = _state.update { s -> s.copy(draft = s.draft?.let(change)) }

    /** Cancel on the sheet: scanned pages are asked about first (flow §7), a picked file just goes. */
    fun close(): Boolean {
        val s = _state.value
        if ((s.draft?.pages ?: 0) > 0 && !s.askDiscard) {
            return false.also {
                _state.update {
                    it.copy(
                        askDiscard = true,
                    )
                }
            }
        }
        _state.update { it.copy(draft = null, askDiscard = false) }
        return true
    }

    /** "Add": the type is required; the sheet closes and the upload runs as a card over the list. */
    fun add() {
        val draft = _state.value.draft ?: return
        if (draft.category == null) return _state.update { it.copy(tried = true) }
        // One upload at a time: the draft stays until the running one ends.
        if (_state.value.uploading != null) return
        _state.update { it.copy(draft = null) }
        send(draft, retry = false)
    }

    fun retry() {
        val draft = _state.value.failed ?: return
        send(draft, retry = true)
    }

    fun cancelUpload() {
        sending?.let {
            tracker.track(
                DocumentsEvents.DocumentUploadCancelled(documentKind(it.file.mime), it.category!!),
            )
        }
        sending = null
        job?.cancel()
        job = null
        _state.update {
            it.copy(uploading = null, progress = null, banner = CodeBanner(Res.string.uploadCancelled, error = false))
        }
    }

    private fun send(
        draft: UploadDraft,
        retry: Boolean,
    ) {
        if (_state.value.uploading != null) return
        sending = draft
        val title = draft.effectiveTitle
        _state.update { it.copy(uploading = title, progress = null, failed = null, banner = null) }
        job =
            viewModelScope.launch {
                val result =
                    repo.upload(
                        patientId,
                        draft.file,
                        title,
                        draft.category!!.name,
                        clinicId,
                        appointmentId,
                    ) { p -> _state.update { if (it.uploading != null) it.copy(progress = p) else it } }
                sending = null
                val kind = documentKind(draft.file.mime)
                tracker.track(
                    result.fold(
                        {
                            DocumentsEvents.DocumentUploaded(
                                kind,
                                draft.category!!,
                                draft.pages > 0,
                                draft.pages.takeIf { it > 0 }?.let(::countBucket),
                                retry,
                            )
                        },
                        { DocumentsEvents.DocumentUploadFailed(kind, draft.category, it.toFailureReason()) },
                    ),
                )
                _state.update { s ->
                    result.fold(
                        {
                            s.copy(
                                uploading = null,
                                progress = null,
                                uploaded = s.uploaded + 1,
                                banner =
                                    CodeBanner(
                                        Res.string.documentUploadedNamed,
                                        error = false,
                                        args = listOf(title),
                                    ),
                            )
                        },
                        { e ->
                            val text =
                                if (e is ApiException.NotFound) {
                                    Res.string.patientNotFound
                                } else {
                                    e.toUserMessage(
                                        Res.string.uploadFailed,
                                    )
                                }
                            s.copy(
                                uploading = null,
                                progress = null,
                                failed = draft,
                                banner = CodeBanner(text, requestId = e.requestId),
                            )
                        },
                    )
                }
            }
    }

    companion object {
        const val MAX_FILE_MB = 25
        const val MAX_FILE = MAX_FILE_MB * 1024 * 1024
        const val LARGE_FILE = 10 * 1024 * 1024
    }
}

/** Step 1 (flow §3): where the document comes from. */
@Composable
internal fun UploadOptionsTray(
    onPdf: () -> Unit,
    onPhoto: () -> Unit,
    onGallery: () -> Unit,
    onScan: () -> Unit,
    onClose: () -> Unit,
) = ShifaTray(tr(Res.string.uploadDocument), onClose) {
    ShifaSettingsItem(tr(Res.string.uploadPdf), onPdf, icon = Res.drawable.ic_file_22)
    ShifaSettingsItem(tr(Res.string.takePhoto), onPhoto, icon = Res.drawable.ic_camera_22)
    ShifaSettingsItem(tr(Res.string.chooseFromGallery), onGallery, icon = Res.drawable.ic_image_22)
    ShifaSettingsItem(tr(Res.string.scanMultiPage), onScan, icon = Res.drawable.ic_layers_22, divider = false)
}

/**
 * Step 3: the title (prefilled) and the type, required: results shared with every doctor of the patient, then private
 * types only the uploader sees (flow §3). Chips instead of a dropdown with disabled group rows: the groups stay headings
 * a screen reader can tell from the choices.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UploadDetailsTray(
    draft: UploadDraft,
    tried: Boolean,
    edit: ((UploadDraft) -> UploadDraft) -> Unit,
    add: () -> Unit,
    close: () -> Unit,
) = ShifaTray(tr(Res.string.uploadDocument), close) {
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        ShifaInputField(
            draft.title,
            { v -> edit { it.copy(title = v.take(MAX_TITLE)) } },
            tr(Res.string.documentTitle),
            Modifier.fillMaxWidth(),
            supportingText = draft.suggested.takeIf { draft.title.isBlank() },
        )
        if (draft.large) {
            Text(
                tr(Res.string.largeFileWarning),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Warning,
            )
        }
        FieldLabel(tr(Res.string.documentCategoryLabel))
        Text(
            tr(Res.string.documentCategoryHint),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
        CategoryChips(draft.category) { k -> edit { it.copy(category = k) } }
        // Add's disabled reason, said where the choice is (flow §8).
        if (draft.category == null) {
            Text(
                tr(Res.string.selectDocumentType),
                style = ShifaTextStyles.metaSmall(),
                color = if (tried) ShifaColors.Text.Error else ShifaColors.Text.Secondary,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        ShifaSecondaryButton(tr(Res.string.cancel), close, Modifier.weight(1f))
        ShifaPrimaryButton(tr(Res.string.add), add, Modifier.weight(1f), enabled = draft.category != null)
    }
}

/** The types a doctor can pick, grouped: results shared with every doctor of the patient, then private ones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryChips(
    selected: DocumentCategory?,
    pick: (DocumentCategory) -> Unit,
) = listOf(
    Res.string.documentCategoryGroupMedical to DocumentCategory.entries.filter { it.pickable && it.medical },
    Res.string.documentCategoryGroupPrivate to DocumentCategory.entries.filter { it.pickable && !it.medical },
).forEach { (group, kinds) ->
    Text(tr(group), style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        kinds.forEach { k -> ShifaChip(tr(k.label), selected == k, { pick(k) }) }
    }
}

/** The scan loop's question after each page: "Add another page?" with the count. */
@Composable
internal fun MorePages(
    count: Int,
    onAdd: () -> Unit,
    onFinish: () -> Unit,
) = ProfileSheet(onFinish) {
    ShifaDialogSheet(
        tr(Res.string.addAnotherPage),
        tr(Res.string.addPage),
        onAdd,
        body = trPlural(Res.plurals.pagesScannedCount, count, count),
        cancelText = tr(Res.string.finish),
        onCancel = onFinish,
    )
}

@Composable
internal fun DiscardPages(
    count: Int,
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
) = ProfileSheet(onKeep) {
    ShifaDialogSheet(
        tr(Res.string.discardScannedPages),
        tr(Res.string.discard),
        onDiscard,
        body = trPlural(Res.plurals.pagesScannedCount, count, count),
        tone = DialogTone.Destructive,
        cancelText = tr(Res.string.cancel),
        onCancel = onKeep,
    )
}

/** Camera refused for good: the way to Settings (flow §6). */
@Composable
internal fun CameraOff(onClose: () -> Unit) =
    ProfileSheet(onClose) {
        ShifaDialogSheet(
            tr(Res.string.cameraAccessOff),
            tr(Res.string.openSettings),
            {
                onClose()
                shifa.doctor.ui.navigation.openPermissionSettings()
            },
            icon = Res.drawable.ic_camera_22,
            cancelText = tr(Res.string.cancel),
            onCancel = onClose,
        )
    }

/** The running upload over the list (flow §3 step 4): its title, the bar (once the size is known) and Cancel. */
@Composable
internal fun UploadCard(
    title: String,
    progress: Float?,
    onCancel: () -> Unit,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S12),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
        Text(
            title,
            Modifier.weight(1f),
            style = ShifaTextStyles.buttonCompact(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        progress?.let {
            Text("${(it * 100).toInt()}%", style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        }
        ShifaLink(tr(Res.string.cancel), onCancel)
    }
    Box(Modifier.fillMaxWidth().height(ShifaSize.S6).background(ShifaColors.Bg.Disabled, ShifaRadius.Sm)) {
        Box(
            Modifier
                .fillMaxWidth(progress ?: INDETERMINATE)
                .height(ShifaSize.S6)
                .background(ShifaColors.Interactive.Primary, ShifaRadius.Sm),
        )
    }
}

/** The upload's banners and its card, above the documents. */
@Composable
internal fun UploadStatus(
    state: UploadUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
    state.uploading?.let { UploadCard(it, state.progress, onCancel) }
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        codeBanner(state.banner, onDismiss)?.invoke()
        if (state.failed != null && state.uploading == null) ShifaLink(tr(Res.string.retry), onRetry)
    }
}

internal const val MAX_TITLE = 120

private val IMAGES = setOf("image/jpeg", "image/png")

// The bar's share before the body length is known.
private const val INDETERMINATE = 0.15f

/** The platform pickers, wired to [vm]: PDF, one photo (camera or gallery), the scan's camera loop. */
class UploadSources(
    val pdf: () -> Unit,
    val photo: () -> Unit,
    val gallery: () -> Unit,
    val scan: () -> Unit,
    // The scan's name for its pages so far ("Scanned document (3 pages)") and its file name.
    val scanTitle: String,
    val scanFile: String,
)

@Composable
fun rememberUploadSources(vm: DocumentUploadViewModel): UploadSources {
    val state by vm.state.collectAsStateWithLifecycle()
    var scanning by remember { mutableStateOf(false) }
    val count = state.pages.size
    val scanTitle = trPlural(Res.plurals.scannedDocumentPages, count, count)
    // scan_20261104_0930.pdf on the practice clock (flow §4).
    val scanFile =
        remember(count) {
            val now = PracticeClock(profileRepository.me.value?.profile?.timeZone).now()

            fun two(n: Int) = n.toString().padStart(2, '0')
            "scan_${now.year}${two(now.monthNumber)}${two(now.dayOfMonth)}_${two(now.hour)}${two(now.minute)}.pdf"
        }
    val pdf = rememberDocumentPickerLauncher { vm.pdf(it?.fileBytes, it?.fileName) }
    val gallery = rememberImagePickerLauncher { vm.photo(it?.bytes, it?.fileName) }
    val camera =
        rememberCameraLauncher(
            { img -> if (scanning) vm.page(img?.bytes, scanTitle, scanFile) else vm.photo(img?.bytes, img?.fileName) },
            { vm.ui { it.copy(cameraDenied = true) } },
        )
    return UploadSources(
        pdf = pdf,
        photo = {
            scanning = false
            camera()
        },
        gallery = gallery,
        scan = {
            scanning = true
            camera()
        },
        scanTitle = scanTitle,
        scanFile = scanFile,
    )
}

/** Every sheet of the flow, hosted by the screen that offers Upload. */
@Composable
fun UploadSheets(
    state: UploadUiState,
    vm: DocumentUploadViewModel,
    sources: UploadSources,
) {
    val closeOptions = { vm.ui { it.copy(options = false) } }
    if (state.options) {
        ProfileSheet(closeOptions) {
            UploadOptionsTray(
                {
                    closeOptions()
                    sources.pdf()
                },
                {
                    closeOptions()
                    sources.photo()
                },
                {
                    closeOptions()
                    sources.gallery()
                },
                {
                    closeOptions()
                    sources.scan()
                },
                closeOptions,
            )
        }
    }
    if (state.askMore) {
        MorePages(
            state.pages.size,
            {
                vm.addPage()
                sources.scan()
            },
            { vm.finishScan(sources.scanTitle, sources.scanFile) },
        )
    }
    state.draft?.let { draft ->
        // Scanned pages aren't dropped by a drag or a scrim tap without asking (flow §7).
        ProfileSheet({ vm.close() }, confirmHide = vm::close) {
            UploadDetailsTray(draft, state.tried, vm::edit, vm::add) { vm.close() }
        }
    }
    if (state.askDiscard) {
        DiscardPages(
            state.draft?.pages ?: 0,
            { vm.ui { it.copy(draft = null, askDiscard = false) } },
            { vm.ui { it.copy(askDiscard = false) } },
        )
    }
    if (state.cameraDenied) CameraOff { vm.ui { it.copy(cameraDenied = false) } }
}
