package shifa.doctor.ui.documents

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.documents.documentsRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.DocumentFile
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.cantPreview
import shifa.doctor.generated.resources.couldNotLoadDocument
import shifa.doctor.generated.resources.couldNotOpenPdf
import shifa.doctor.generated.resources.document
import shifa.doctor.generated.resources.documentGone
import shifa.doctor.generated.resources.documentNoAccess
import shifa.doctor.generated.resources.documentSaveFailed
import shifa.doctor.generated.resources.documentSaved
import shifa.doctor.generated.resources.ic_file_22
import shifa.doctor.generated.resources.requestAccess
import shifa.doctor.generated.resources.requestAccessSent
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.saveToDevice
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ZoomState
import shifa.doctor.ui.widgets.zoomable
import shifa.doctor.utils.FileSaver
import shifa.doctor.utils.PdfView
import shifa.doctor.utils.rememberFileSaver
import shifa.doctor.utils.safeFileName
import shifa.doctor.utils.tr

/** What the viewer shows (flow 06/08 §6). */
sealed interface Viewed {
    data object Loading : Viewed

    class Ready(
        val file: DocumentFile,
    ) : Viewed

    /** 403: no access (Request access); 404: deleted, or locked again (Back). */
    data object NoAccess : Viewed

    data object Gone : Viewed

    data class Failed(
        val error: CodeBanner,
    ) : Viewed
}

data class DocumentViewerUiState(
    val viewed: Viewed = Viewed.Loading,
    val banner: CodeBanner? = null,
    val requesting: Boolean = false,
    // Request access went through: the screen goes back with "Request sent" (flow 06/08 §10).
    val requested: Boolean = false,
)

/**
 * DOCS-05 (flow 06/08, spec 06 §2.10): one document through the authenticated download, never the list's `url`;
 * whole file in memory, nothing written to disk unless the doctor saves it.
 */
class DocumentViewerViewModel(
    private val patientId: Long,
    private val documentId: Long,
    private val clinicId: Long?,
    private val repo: DocumentsRepository = documentsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DocumentViewerUiState())
    val state: StateFlow<DocumentViewerUiState> = _state.asStateFlow()

    // The download and the access request of this opening; cancelled when the screen leaves.
    private val work = mutableListOf<Job>()

    fun load() {
        work.forEach { it.cancel() }
        work.clear()
        _state.update { it.copy(viewed = Viewed.Loading, banner = null) }
        work +=
            viewModelScope.launch {
                val viewed =
                    repo.download(patientId, documentId, clinicId).fold(
                        onSuccess = { file ->
                            // An empty answer is a failure, not a blank page (flow §6).
                            if (file.bytes.isEmpty()) failed(null) else Viewed.Ready(file)
                        },
                        onFailure = { e ->
                            when (e) {
                                is ApiException.Forbidden -> Viewed.NoAccess
                                is ApiException.NotFound -> Viewed.Gone
                                else -> failed(e)
                            }
                        },
                    )
                _state.update { it.copy(viewed = viewed) }
            }
    }

    fun requestAccess() {
        if (_state.value.requesting) return
        _state.update { it.copy(requesting = true, banner = null) }
        work +=
            viewModelScope.launch {
                repo.requestAccess(patientId, documentId, clinicId).fold(
                    onSuccess = { _state.update { it.copy(requesting = false, requested = true) } },
                    onFailure = { e ->
                        _state.update {
                            it.copy(
                                requesting = false,
                                banner =
                                    CodeBanner(
                                        e.toUserMessage(Res.string.couldNotLoadDocument),
                                        requestId = e.requestId,
                                    ),
                            )
                        }
                    },
                )
            }
    }

    /** Saves the file under its own name (or the title with the type's extension); says how it went. */
    fun save(
        saver: FileSaver,
        title: String,
    ) {
        val file = (_state.value.viewed as? Viewed.Ready)?.file ?: return
        val result =
            saver.saveFile(
                file.fileName?.let(::safeFileName) ?: (safeFileName(title) + extension(file.mime)),
                file.bytes,
            )
        _state.update {
            it.copy(
                banner =
                    if (result.isSuccess) {
                        CodeBanner(Res.string.documentSaved, error = false)
                    } else {
                        CodeBanner(Res.string.documentSaveFailed)
                    },
            )
        }
    }

    fun ui(change: (DocumentViewerUiState) -> DocumentViewerUiState) = _state.update(change)

    /**
     * The screen left: the file's bytes go (the shell keeps this ViewModel, and a document can be megabytes) and a
     * finished request is forgotten, so opening the document again starts afresh.
     */
    fun release() {
        // A late answer must not bring the file (or "requested") back after the screen left.
        work.forEach { it.cancel() }
        work.clear()
        _state.value = DocumentViewerUiState()
    }

    private fun failed(e: Throwable?) =
        Viewed.Failed(
            CodeBanner(
                e?.toUserMessage(Res.string.couldNotLoadDocument) ?: Res.string.couldNotLoadDocument,
                requestId = e?.requestId,
            ),
        )

    private fun extension(mime: String) =
        when (mime) {
            DocumentFile.PDF -> ".pdf"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            else -> ""
        }
}

data class DocumentViewerActions(
    val back: () -> Unit = {},
    val retry: () -> Unit = {},
    val requestAccess: () -> Unit = {},
    val save: () -> Unit = {},
    val ui: ((DocumentViewerUiState) -> DocumentViewerUiState) -> Unit = {},
)

@Composable
fun DocumentViewerScreen(
    patientId: Long,
    documentId: Long,
    title: String,
    clinicId: Long?,
    onBack: () -> Unit,
    viewModel: DocumentViewerViewModel =
        viewModel(key = "document-$patientId-$documentId-$clinicId") {
            DocumentViewerViewModel(patientId, documentId, clinicId)
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saver = rememberFileSaver()
    LaunchedEffect(viewModel) { viewModel.load() }
    DisposableEffect(viewModel) { onDispose { viewModel.release() } }
    LaunchedEffect(state.requested) {
        if (state.requested) {
            onBack()
            // After going back (opening or leaving a screen clears success notices), so it stays.
            ShellNotice.pending.value = Notice(Res.string.requestAccessSent, success = true)
        }
    }
    DocumentViewerContent(
        title,
        state,
        DocumentViewerActions(
            back = onBack,
            retry = viewModel::load,
            requestAccess = viewModel::requestAccess,
            save = { viewModel.save(saver, title) },
            ui = viewModel::ui,
        ),
    )
}

@Composable
fun DocumentViewerContent(
    title: String,
    state: DocumentViewerUiState,
    actions: DocumentViewerActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    ShifaSubHeader(title.ifBlank { tr(Res.string.document) }, Modifier.statusBarsPadding(), onBack = actions.back)
    codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.let {
        Box(Modifier.padding(horizontal = ShifaSpacing.Inset.Md)) { it() }
    }
    val body = Modifier.weight(1f).fillMaxWidth()
    when (val v = state.viewed) {
        Viewed.Loading ->
            // A page-shaped placeholder (flow §6).
            Box(body.padding(ShifaSpacing.Inset.Md), contentAlignment = Alignment.TopCenter) {
                ShifaSkeleton(Modifier.fillMaxWidth().aspectRatio(A4))
            }
        Viewed.NoAccess ->
            EmptyState(tr(Res.string.documentNoAccess), Res.drawable.ic_file_22, body) {
                ShifaPrimaryButton(tr(Res.string.requestAccess), actions.requestAccess, loading = state.requesting)
            }
        Viewed.Gone ->
            EmptyState(tr(Res.string.documentGone), Res.drawable.ic_file_22, body) {
                ShifaSecondaryButton(tr(Res.string.back), actions.back)
            }
        is Viewed.Failed ->
            ShifaLoadError(tr(v.error.text), actions.retry, body, requestId = v.error.requestId)
        is Viewed.Ready -> Shown(v.file, title, body, actions)
    }
}

@Composable
private fun Shown(
    file: DocumentFile,
    title: String,
    modifier: Modifier,
    actions: DocumentViewerActions,
) {
    val zoom = remember(file) { ZoomState() }
    // Not something we can draw (or a broken file): say so, and offer to save it for another app (flow §8); a PDF that
    // won't open may be a cut-off download, so it can be fetched again too.
    val cantShow: @Composable (String, Boolean) -> Unit = { text, retry ->
        EmptyState(text, Res.drawable.ic_file_22, modifier) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            ) {
                if (retry) ShifaPrimaryButton(tr(Res.string.retry), actions.retry)
                ShifaSecondaryButton(tr(Res.string.saveToDevice), actions.save)
            }
        }
    }
    when {
        file.isPdf ->
            PdfView(file.bytes, modifier.zoomable(zoom)) { cantShow(tr(Res.string.couldNotOpenPdf), true) }
        file.isImage -> {
            val image: ImageBitmap? = remember(file) { runCatching { file.bytes.decodeToImageBitmap() }.getOrNull() }
            if (image == null) {
                cantShow(tr(Res.string.cantPreview), false)
            } else {
                Box(modifier.zoomable(zoom).padding(ShifaSpacing.Inset.Md), contentAlignment = Alignment.Center) {
                    Image(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
        else -> cantShow(tr(Res.string.cantPreview), false)
    }
}

private const val A4 = 595f / 842f
