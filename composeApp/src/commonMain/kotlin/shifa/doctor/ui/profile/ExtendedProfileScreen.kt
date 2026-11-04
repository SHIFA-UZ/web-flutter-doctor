package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ProfileUpdateRequest
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.add
import shifa.doctor.generated.resources.addService
import shifa.doctor.generated.resources.biography
import shifa.doctor.generated.resources.biographyHelper
import shifa.doctor.generated.resources.certificateUploaded
import shifa.doctor.generated.resources.certificates
import shifa.doctor.generated.resources.chooseFromGallery
import shifa.doctor.generated.resources.choosePdf
import shifa.doctor.generated.resources.delete
import shifa.doctor.generated.resources.deleteCertificateTitle
import shifa.doctor.generated.resources.extendedProfile
import shifa.doctor.generated.resources.extendedProfileSaved
import shifa.doctor.generated.resources.fileTooLarge
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.instagram
import shifa.doctor.generated.resources.invalidHandle
import shifa.doctor.generated.resources.noCertificatesYet
import shifa.doctor.generated.resources.removeItem
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.serviceTagDuplicate
import shifa.doctor.generated.resources.serviceTagLimit
import shifa.doctor.generated.resources.serviceTagTooLong
import shifa.doctor.generated.resources.serviceTagsHelper
import shifa.doctor.generated.resources.services
import shifa.doctor.generated.resources.socialHelper
import shifa.doctor.generated.resources.socialMedia
import shifa.doctor.generated.resources.takePhoto
import shifa.doctor.generated.resources.telegram
import shifa.doctor.generated.resources.uploadCertificate
import shifa.doctor.generated.resources.uploadFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSpinner
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.DocumentPickerResult
import shifa.doctor.utils.PickedImage
import shifa.doctor.utils.rememberCameraLauncher
import shifa.doctor.utils.rememberDocumentPickerLauncher
import shifa.doctor.utils.rememberImagePickerLauncher
import shifa.doctor.utils.rememberInAppBrowser
import shifa.doctor.utils.tr

/** What Save sends; the tags and certificates save on their own as they change (flow 08/07 §4). */
data class ExtendedForm(
    val biography: String = "",
    val telegram: String = "",
    val instagram: String = "",
)

data class ExtendedUiState(
    val saved: ExtendedForm = ExtendedForm(),
    val form: ExtendedForm = saved,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val tagError: StringResource? = null,
    val certificates: List<String> = emptyList(),
    val uploading: Boolean = false,
    val uploadSheet: Boolean = false,
    val askDelete: String? = null,
    val cameraDenied: Boolean = false,
    val telegramError: StringResource? = null,
    val instagramError: StringResource? = null,
    val saving: Boolean = false,
    val banner: CodeBanner? = null,
    val askDiscard: Boolean = false,
    val done: Boolean = false,
) {
    val dirty: Boolean get() = form != saved
}

/**
 * PROF-08 (flow 08/07, DaProfile `profExtended`): biography and the Telegram / Instagram handles saved with Save (one
 * `PATCH /doctors/me/profile`, changed keys only); service tags and certificates saved at once (optimistic, reverted on
 * failure). Flutter's per-keystroke PATCHes are gone (flow §12).
 */
class ExtendedProfileViewModel(
    private val profile: ProfileRepository = profileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ExtendedUiState())
    val state: StateFlow<ExtendedUiState> = _state.asStateFlow()

    // Every tag and certificate write waits its turn and sends the latest list, so rapid edits can't overtake each
    // other and a delete can't drop an upload still in flight (flow 08/07 §8). "Confirmed" is what the server last took.
    private val lists = Mutex()
    private var confirmedTags = emptyList<String>()
    private var confirmedCertificates = emptyList<String>()

    fun open() =
        _state.update { s ->
            val p = profile.me.value?.profile
            // While a list write is still running (left and reopened), the lists on screen are the newer ones.
            val busy = lists.isLocked
            if (!busy) {
                confirmedTags = p?.serviceTags.orEmpty()
                confirmedCertificates = p?.certificateUrls.orEmpty()
            }
            val tags = if (busy) s.tags else confirmedTags
            val certificates = if (busy) s.certificates else confirmedCertificates
            if (s.dirty && !s.done) {
                s.copy(tags = tags, certificates = certificates)
            } else {
                val saved = ExtendedForm(p?.biography.orEmpty(), p?.telegram.orEmpty(), p?.instagram.orEmpty())
                ExtendedUiState(saved = saved, tags = tags, certificates = certificates, uploading = s.uploading)
            }
        }

    fun edit(change: (ExtendedForm) -> ExtendedForm) =
        _state.update { it.copy(form = change(it.form), telegramError = null, instagramError = null) }

    fun ui(change: (ExtendedUiState) -> ExtendedUiState) = _state.update(change)

    fun back(): Boolean {
        val s = _state.value
        return when (backStep(s.saving, s.askDiscard, s.dirty)) {
            BackStep.Close -> true
            BackStep.Ask -> false.also { _state.update { it.copy(askDiscard = true) } }
            BackStep.CloseQuestion -> false.also { _state.update { it.copy(askDiscard = false) } }
            BackStep.Stay -> false
        }
    }

    fun discard() = _state.update { it.copy(form = it.saved, askDiscard = false, done = true) }

    fun closed() = _state.update { it.copy(done = false) }

    /** Trimmed, 1–40 characters, no duplicate (any case), at most 30 (flow 08/07 §4). */
    fun addTag() {
        val s = _state.value
        val tag = s.tagInput.trim().replace(Regex("\\s+"), " ")
        val error =
            when {
                tag.isEmpty() -> return
                tag.length > TAG_MAX -> Res.string.serviceTagTooLong
                s.tags.any { it.equals(tag, ignoreCase = true) } -> Res.string.serviceTagDuplicate
                s.tags.size >= TAGS_MAX -> Res.string.serviceTagLimit
                else -> null
            }
        if (error != null) return _state.update { it.copy(tagError = error) }
        writeTags(s.tags + tag, clearInput = true)
    }

    fun removeTag(tag: String) = writeTags(_state.value.tags - tag, clearInput = false)

    private fun writeTags(
        tags: List<String>,
        clearInput: Boolean,
    ) {
        _state.update {
            it.copy(
                tags = tags,
                tagError = null,
                tagInput = if (clearInput) "" else it.tagInput,
                banner = null,
            )
        }
        viewModelScope.launch {
            lists.withLock {
                val target = _state.value.tags
                if (target == confirmedTags) return@withLock
                profile.saveServiceTags(target).fold(
                    onSuccess = { confirmedTags = target },
                    onFailure = { e -> failed(e, tags = true) },
                )
            }
        }
    }

    /**
     * A failed write shows the server's version of that list again (or the last confirmed one when it can't be read);
     * the other list keeps its queued edits.
     */
    private suspend fun failed(
        e: Throwable,
        tags: Boolean,
    ) {
        val me = profile.refresh().getOrNull()
        _state.update {
            if (tags) {
                me?.let { confirmedTags = it.profile.serviceTags }
                it.copy(
                    tags = confirmedTags,
                    banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                )
            } else {
                me?.let { confirmedCertificates = it.profile.certificateUrls }
                it.copy(
                    certificates = confirmedCertificates,
                    banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                )
            }
        }
    }

    fun uploadImage(image: PickedImage?) = upload(image?.let { Triple(it.fileName, it.bytes, "image/jpeg") })

    fun uploadPdf(pdf: DocumentPickerResult?) =
        upload(
            pdf?.let { Triple(it.fileName, it.fileBytes, "application/pdf") },
        )

    fun cameraDenied() = _state.update { it.copy(uploadSheet = false, cameraDenied = true) }

    private fun upload(file: Triple<String, ByteArray, String>?) {
        _state.update { it.copy(uploadSheet = false) }
        if (file == null || _state.value.uploading) return
        // The backend takes 25 MB at most; say so here rather than after sending it.
        if (file.second.size > UPLOAD_MAX_MB * 1024 * 1024) {
            return _state.update { it.copy(banner = CodeBanner(Res.string.fileTooLarge)) }
        }
        _state.update { it.copy(uploading = true, banner = null) }
        viewModelScope.launch {
            lists.withLock {
                profile.uploadCertificate(file.first, file.second, file.third).fold(
                    onSuccess = { url ->
                        if (url !in confirmedCertificates) confirmedCertificates = confirmedCertificates + url
                        _state.update {
                            it.copy(
                                uploading = false,
                                certificates = if (url in it.certificates) it.certificates else it.certificates + url,
                                banner = CodeBanner(Res.string.certificateUploaded, error = false),
                            )
                        }
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(
                                uploading = false,
                                banner = CodeBanner(e.toUserMessage(Res.string.uploadFailed), requestId = e.requestId),
                            )
                        }
                    },
                )
            }
        }
    }

    fun deleteCertificate() {
        val url = _state.value.askDelete ?: return
        _state.update { it.copy(askDelete = null, certificates = it.certificates - url, banner = null) }
        viewModelScope.launch {
            lists.withLock {
                // Computed in turn: an upload that finished meanwhile stays in the list.
                val target = _state.value.certificates
                if (target == confirmedCertificates) return@withLock
                profile.saveCertificates(target).fold(
                    onSuccess = { confirmedCertificates = target },
                    onFailure = { e -> failed(e, tags = false) },
                )
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty) return
        val f = ExtendedForm(s.form.biography.trim(), s.form.telegram.trim(), s.form.instagram.trim())
        val telegramError =
            Res.string.invalidHandle.takeUnless {
                f.telegram.isEmpty() ||
                    isHandle(
                        f.telegram,
                        TELEGRAM,
                    )
            }
        val instagramError =
            Res.string.invalidHandle.takeUnless {
                f.instagram.isEmpty() ||
                    isHandle(
                        f.instagram,
                        INSTAGRAM,
                    )
            }
        if (telegramError != null || instagramError != null) {
            return _state.update { it.copy(telegramError = telegramError, instagramError = instagramError) }
        }
        val o = s.saved
        // "" clears; the backend ignores null keys (PROF-01).
        val request =
            ProfileUpdateRequest(
                biography = f.biography.takeIf { it != o.biography },
                telegram = f.telegram.takeIf { it != o.telegram },
                instagram = f.instagram.takeIf { it != o.instagram },
            )
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            // In the same queue: its answer carries the whole profile, so it must not land before a list write's.
            lists.withLock { profile.update(request) }.fold(
                onSuccess = {
                    ShellNotice.pending.value = Notice(Res.string.extendedProfileSaved, success = true)
                    _state.update { it.copy(saving = false, saved = f, form = f, done = true) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            saving = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    internal companion object {
        const val TAG_MAX = 40
        const val TAGS_MAX = 30
        const val BIOGRAPHY_MAX = 2000
        const val UPLOAD_MAX_MB = 25

        // Telegram: 5–32 letters, digits, "_"; Instagram: 1–30 letters, digits, "." and "_". Stored as typed.
        val TELEGRAM = Regex("[A-Za-z0-9_]{5,32}")
        val INSTAGRAM = Regex("[A-Za-z0-9._]{1,30}")

        /** "@name", "name" or a profile link ("https://t.me/name", "instagram.com/name/") (flow 08/07 §8). */
        fun isHandle(
            value: String,
            rule: Regex,
        ): Boolean {
            val name =
                value.removePrefix("https://").removePrefix("http://").removePrefix("www.")
                    .let {
                            v ->
                        listOf("t.me/", "telegram.me/", "instagram.com/").fold(v) { acc, p -> acc.removePrefix(p) }
                    }
                    .removePrefix("@")
                    .trimEnd('/')
            return rule.matches(name)
        }
    }
}

@Composable
fun ExtendedProfileScreen(
    onClose: () -> Unit,
    viewModel: ExtendedProfileViewModel = viewModel { ExtendedProfileViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.open() }
    val back = { if (viewModel.back()) onClose() }
    FormExitEffects(state.done, viewModel::closed, onClose, back)
    val browser = rememberInAppBrowser()
    ExtendedProfileContent(
        state,
        ExtendedActions(
            back = back,
            save = viewModel::save,
            edit = viewModel::edit,
            ui = viewModel::ui,
            discard = viewModel::discard,
            addTag = viewModel::addTag,
            removeTag = viewModel::removeTag,
            gallery = rememberImagePickerLauncher(viewModel::uploadImage),
            camera = rememberCameraLauncher(viewModel::uploadImage, viewModel::cameraDenied),
            pdf = rememberDocumentPickerLauncher(viewModel::uploadPdf),
            open = browser,
            delete = viewModel::deleteCertificate,
        ),
    )
}

class ExtendedActions(
    val back: () -> Unit = {},
    val save: () -> Unit = {},
    val edit: ((ExtendedForm) -> ExtendedForm) -> Unit = {},
    val ui: ((ExtendedUiState) -> ExtendedUiState) -> Unit = {},
    val discard: () -> Unit = {},
    val addTag: () -> Unit = {},
    val removeTag: (String) -> Unit = {},
    val gallery: () -> Unit = {},
    val camera: () -> Unit = {},
    val pdf: () -> Unit = {},
    val open: (String) -> Unit = {},
    val delete: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtendedProfileContent(
    state: ExtendedUiState,
    actions: ExtendedActions,
) {
    val enabled = !state.saving
    val f = state.form

    @Composable
    fun Helper(text: StringResource) =
        Text(tr(text), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    ProfileForm(
        tr(Res.string.extendedProfile),
        actions.back,
        onSave = if (state.dirty && enabled) actions.save else null,
        askDiscard = state.askDiscard,
        onDiscard = actions.discard,
        onKeep = { actions.ui { it.copy(askDiscard = false) } },
    ) {
        codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
        if (state.cameraDenied) CameraDeniedBanner { actions.ui { it.copy(cameraDenied = false) } }
        ShifaSection(tr(Res.string.biography)) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                ShifaInputField(
                    f.biography,
                    { v -> actions.edit { it.copy(biography = v.take(ExtendedProfileViewModel.BIOGRAPHY_MAX)) } },
                    tr(Res.string.biography),
                    Modifier.fillMaxWidth(),
                    // The counter shows near the limit (flow 08/07 §3.2).
                    supportingText =
                        "${f.biography.length} / ${ExtendedProfileViewModel.BIOGRAPHY_MAX}".takeIf {
                            f.biography.length >= ExtendedProfileViewModel.BIOGRAPHY_MAX - 200
                        },
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    multiline = true,
                )
                Helper(Res.string.biographyHelper)
            }
        }
        ShifaSection(tr(Res.string.services)) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
                    verticalAlignment = Alignment.Top,
                ) {
                    ShifaInputField(
                        state.tagInput,
                        { v -> actions.ui { it.copy(tagInput = v, tagError = null) } },
                        tr(Res.string.addService),
                        Modifier.weight(1f),
                        supportingText = state.tagError?.let { tr(it) },
                        isError = state.tagError != null,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions = KeyboardActions(onDone = { actions.addTag() }),
                    )
                    ShifaSecondaryButton(tr(Res.string.add), actions.addTag, enabled = state.tagInput.isNotBlank())
                }
                if (state.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                    ) { state.tags.forEach { tag -> TagChip(tag) { actions.removeTag(tag) } } }
                }
                Helper(Res.string.serviceTagsHelper)
            }
        }
        ShifaSection(tr(Res.string.certificates)) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                if (state.certificates.isEmpty()) Helper(Res.string.noCertificatesYet)
                state.certificates.forEach { url ->
                    CertificateRow(url, { actions.open(url) }) { actions.ui { it.copy(askDelete = url) } }
                }
                if (state.uploading) {
                    ShifaSpinner(ShifaColors.Interactive.Primary)
                } else {
                    ShifaSecondaryButton(
                        tr(Res.string.uploadCertificate),
                        { actions.ui { it.copy(uploadSheet = true) } },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        ShifaSection(tr(Res.string.socialMedia)) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                ShifaInputField(
                    f.telegram,
                    { v -> actions.edit { it.copy(telegram = v) } },
                    tr(Res.string.telegram),
                    Modifier.fillMaxWidth(),
                    supportingText = state.telegramError?.let { tr(it) },
                    isError = state.telegramError != null,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                ShifaInputField(
                    f.instagram,
                    { v -> actions.edit { it.copy(instagram = v) } },
                    tr(Res.string.instagram),
                    Modifier.fillMaxWidth(),
                    supportingText = state.instagramError?.let { tr(it) },
                    isError = state.instagramError != null,
                    enabled = enabled,
                )
                Helper(Res.string.socialHelper)
            }
        }
    }
    if (state.uploadSheet) {
        ProfileSheet({ actions.ui { it.copy(uploadSheet = false) } }) {
            ShifaTray(tr(Res.string.uploadCertificate), { actions.ui { it.copy(uploadSheet = false) } }) {
                ShifaSecondaryButton(tr(Res.string.takePhoto), actions.camera, Modifier.fillMaxWidth())
                ShifaSecondaryButton(tr(Res.string.chooseFromGallery), actions.gallery, Modifier.fillMaxWidth())
                ShifaSecondaryButton(tr(Res.string.choosePdf), actions.pdf, Modifier.fillMaxWidth())
            }
        }
    }
    state.askDelete?.let { url ->
        ProfileSheet({ actions.ui { it.copy(askDelete = null) } }) {
            ShifaDialogSheet(
                tr(Res.string.deleteCertificateTitle),
                tr(Res.string.delete),
                actions.delete,
                body = certificateName(url),
                tone = DialogTone.Destructive,
                icon = Res.drawable.ic_trash_22,
                onCancel = { actions.ui { it.copy(askDelete = null) } },
            )
        }
    }
}

/** The file name: the URL's last path segment without a signature query. */
internal fun certificateName(url: String) = url.substringBefore('?').substringAfterLast('/')

@Composable
private fun TagChip(
    tag: String,
    onRemove: () -> Unit,
) = Row(
    Modifier.background(ShifaColors.Bg.Selected, ShifaRadius.Full).padding(start = ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(tag, style = ShifaTextStyles.chipFilter(), color = ShifaColors.Text.Brand)
    ShifaIconButton(
        Res.drawable.ic_x_16,
        tr(Res.string.removeItem, tag),
        onRemove,
        variant = ShifaIconButtonVariant.Ghost,
    )
}

@Composable
private fun CertificateRow(
    url: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        certificateName(url),
        Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen).padding(vertical = ShifaSize.S8),
        style = ShifaTextStyles.dialogBody(),
        color = ShifaColors.Text.Brand,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    ShifaIconButton(
        Res.drawable.ic_trash_22,
        tr(Res.string.removeItem, certificateName(url)),
        onDelete,
        variant = ShifaIconButtonVariant.Ghost,
    )
}
