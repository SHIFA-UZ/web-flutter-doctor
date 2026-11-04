package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.catalog.CatalogRepository
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.model.ProfileUpdateRequest
import shifa.doctor.data.profile.Gender
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cameraAccessNeeded
import shifa.doctor.generated.resources.changePhoto
import shifa.doctor.generated.resources.chooseFromGallery
import shifa.doctor.generated.resources.chooseOneOrMore
import shifa.doctor.generated.resources.dateOfBirth
import shifa.doctor.generated.resources.discard
import shifa.doctor.generated.resources.discardChangesBody
import shifa.doctor.generated.resources.discardChangesTitle
import shifa.doctor.generated.resources.enterFirstName
import shifa.doctor.generated.resources.enterLastName
import shifa.doctor.generated.resources.female
import shifa.doctor.generated.resources.firstName
import shifa.doctor.generated.resources.gender
import shifa.doctor.generated.resources.ic_alert_circle_16
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.keepEditing
import shifa.doctor.generated.resources.lastName
import shifa.doctor.generated.resources.male
import shifa.doctor.generated.resources.nameInvalid
import shifa.doctor.generated.resources.notSet
import shifa.doctor.generated.resources.openSettings
import shifa.doctor.generated.resources.other
import shifa.doctor.generated.resources.personalInformation
import shifa.doctor.generated.resources.photoUpdated
import shifa.doctor.generated.resources.photoUploadFailed
import shifa.doctor.generated.resources.profession
import shifa.doctor.generated.resources.professional
import shifa.doctor.generated.resources.profileInformationSaved
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.takePhoto
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.register.DobPicker
import shifa.doctor.ui.register.displayDate
import shifa.doctor.ui.register.label
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPickField
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSpinner
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.utils.PickedImage
import shifa.doctor.utils.openAppSettings
import shifa.doctor.utils.rememberCameraLauncher
import shifa.doctor.utils.rememberImagePickerLauncher
import shifa.doctor.utils.tr

/** What the form holds; compared with the saved values to know what changed (flow 08/02 §5: only those are sent). */
data class PersonalInfo(
    val firstName: String = "",
    val lastName: String = "",
    val dob: LocalDate? = null,
    val gender: Gender? = null,
    val professions: List<Profession> = emptyList(),
)

data class PersonalInfoUiState(
    val saved: PersonalInfo = PersonalInfo(),
    val form: PersonalInfo = saved,
    val photoUrl: String? = null,
    val firstNameError: StringResource? = null,
    val lastNameError: StringResource? = null,
    val saving: Boolean = false,
    val uploading: Boolean = false,
    val banner: CodeBanner? = null,
    val photoSheet: Boolean = false,
    val pickingDob: Boolean = false,
    val genderOpen: Boolean = false,
    val professionsOpen: Boolean = false,
    val query: String = "",
    val catalog: List<Profession> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogFailed: Boolean = false,
    val askDiscard: Boolean = false,
    val cameraDenied: Boolean = false,
    // Saved: the screen closes (the root shows "Profile information saved").
    val done: Boolean = false,
) {
    val dirty: Boolean get() = form != saved
}

/**
 * PROF-03 (flow 08/02, DaProfile `profEdit`): names, birth date, gender and professions, saved together with Save
 * (only the changed keys, one `PATCH /doctors/me/profile`); the photo uploads as soon as it is picked.
 */
class PersonalInfoViewModel(
    private val profile: ProfileRepository = profileRepository,
    private val catalogs: CatalogRepository = CatalogRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(PersonalInfoUiState())
    val state: StateFlow<PersonalInfoUiState> = _state.asStateFlow()

    /**
     * Called each time the screen shows. The ViewModel lives in the shell's store, so a tab switch keeps unsaved edits;
     * an unchanged form re-reads the saved profile, and a finished or discarded edit starts clean.
     */
    fun open() {
        _state.update { if (it.dirty && !it.done) it else fresh(it) }
        if (_state.value.catalog.isEmpty() && !_state.value.catalogLoading) retryCatalog()
    }

    /** The screen has closed after Save or Discard; a later open must not see the old "done". */
    fun closed() = _state.update { it.copy(done = false) }

    /** The camera permission was refused: say why and offer the OS settings (flow 08/02 §5). */
    fun cameraDenied() = _state.update { it.copy(photoSheet = false, cameraDenied = true) }

    /** Back on the discard question: the edits go and the next open starts from the saved profile. */
    fun discard() = _state.update { it.copy(form = it.saved, askDiscard = false, done = true) }

    private fun fresh(old: PersonalInfoUiState): PersonalInfoUiState {
        val p = profile.me.value?.profile
        val saved =
            PersonalInfo(
                p?.firstName.orEmpty(),
                p?.lastName.orEmpty(),
                p?.dob?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                Gender.entries.firstOrNull { it.wire.equals(p?.gender, ignoreCase = true) },
                p?.profession.orEmpty().split(
                    ",",
                ).map { it.trim() }.filter { it.isNotEmpty() }.map { Profession(it, null) }
                    .withLabels(old.catalog),
            )
        return PersonalInfoUiState(
            saved = saved,
            photoUrl = p?.photoUrl,
            catalog = old.catalog,
            catalogFailed = old.catalogFailed,
            catalogLoading = old.catalogLoading,
        )
    }

    fun edit(change: (PersonalInfo) -> PersonalInfo) =
        _state.update { it.copy(form = change(it.form), firstNameError = null, lastNameError = null) }

    fun toggleProfession(p: Profession) =
        edit { f ->
            val on = f.professions.any { it.english == p.english }
            f.copy(professions = if (on) f.professions.filterNot { it.english == p.english } else f.professions + p)
        }

    fun ui(change: (PersonalInfoUiState) -> PersonalInfoUiState) = _state.update(change)

    /** The bundled list shows at once (offline included), the backend's replaces it when it answers (flow 08/02 §6). */
    fun retryCatalog() {
        _state.update { it.copy(catalogLoading = true) }
        viewModelScope.launch {
            if (_state.value.catalog.isEmpty()) {
                val bundled = catalogs.bundledProfessions()
                _state.update {
                    it.copy(
                        catalog = bundled,
                        saved = it.saved.copy(professions = it.saved.professions.withLabels(bundled)),
                        form = it.form.copy(professions = it.form.professions.withLabels(bundled)),
                    )
                }
            }
            val loaded = catalogs.professionCatalog()
            _state.update {
                it.copy(
                    catalog = loaded.professions,
                    catalogFailed = loaded.failed,
                    catalogLoading = false,
                    // Uzbek labels for the saved professions.
                    saved = it.saved.copy(professions = it.saved.professions.withLabels(loaded.professions)),
                    form = it.form.copy(professions = it.form.professions.withLabels(loaded.professions)),
                )
            }
        }
    }

    /** Back: a changed form asks first (flow 08/02 §7). True when the screen may close. */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.saving -> false
            s.askDiscard -> {
                _state.update { it.copy(askDiscard = false) }
                false
            }
            s.dirty -> {
                _state.update { it.copy(askDiscard = true) }
                false
            }
            else -> true
        }
    }

    fun save() {
        val s = _state.value
        // Not while a photo uploads: an older PATCH answer could land last and drop the new photo locally.
        if (s.saving || s.uploading || !s.dirty) return
        val first = cleanName(s.form.firstName)
        val last = cleanName(s.form.lastName)
        val firstError = nameError(first, Res.string.enterFirstName)
        val lastError = nameError(last, Res.string.enterLastName)
        if (firstError != null || lastError != null) {
            return _state.update { it.copy(firstNameError = firstError, lastNameError = lastError) }
        }
        val f = s.form.copy(firstName = first, lastName = last)
        val o = s.saved
        // Only what changed; "" clears a text field (the backend ignores nulls: PROF-01).
        val request =
            ProfileUpdateRequest(
                firstName = first.takeIf { it != o.firstName },
                lastName = last.takeIf { it != o.lastName },
                dob = f.dob?.toString()?.takeIf { f.dob != o.dob },
                gender = (f.gender?.wire ?: "").takeIf { f.gender != o.gender },
                profession = f.professions.joinToString(", ") { it.english }.takeIf { f.professions != o.professions },
            )
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            profile.update(request).fold(
                onSuccess = {
                    ShellNotice.pending.value = Notice(Res.string.profileInformationSaved, success = true)
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

    /** A picked or taken photo uploads at once (Flutter did too); the avatar shows a spinner meanwhile. */
    fun upload(photo: PickedImage?) {
        _state.update { it.copy(photoSheet = false) }
        if (photo == null) return
        _state.update { it.copy(uploading = true, banner = null) }
        viewModelScope.launch {
            profile.uploadPhoto(photo).fold(
                onSuccess = { url ->
                    _state.update {
                        it.copy(
                            uploading = false,
                            photoUrl = url,
                            banner = CodeBanner(Res.string.photoUpdated, error = false),
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            uploading = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.photoUploadFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    private companion object {
        fun List<Profession>.withLabels(catalog: List<Profession>): List<Profession> {
            val byEnglish = catalog.associateBy { it.english }
            return map { byEnglish[it.english] ?: it }
        }

        // Letters (any script), spaces, hyphens, apostrophes (straight and ʼ ’), 1–60 (flow 08/02 §4).
        val NAME = Regex("""[\p{L}\p{M}' ʼ’-]{1,60}""")

        fun cleanName(name: String) = name.trim().replace(Regex("\\s+"), " ")

        fun nameError(
            name: String,
            empty: StringResource,
        ): StringResource? =
            when {
                name.isEmpty() -> empty
                !NAME.matches(name) -> Res.string.nameInvalid
                else -> null
            }
    }
}

/** Personal information as a pushed screen with its own bar: Back and a Save check (flow 08/02 §3). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PersonalInfoScreen(
    onClose: () -> Unit,
    viewModel: PersonalInfoViewModel = viewModel { PersonalInfoViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(Unit) { viewModel.open() }
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.closed()
            close()
        }
    }
    val back = { if (viewModel.back()) onClose() }
    BackHandler(onBack = back)
    val gallery = rememberImagePickerLauncher(viewModel::upload)
    val camera = rememberCameraLauncher(viewModel::upload, viewModel::cameraDenied)
    PersonalInfoContent(
        state,
        PersonalInfoActions(
            back = back,
            save = viewModel::save,
            edit = viewModel::edit,
            ui = viewModel::ui,
            toggleProfession = viewModel::toggleProfession,
            retryCatalog = viewModel::retryCatalog,
            gallery = gallery,
            camera = camera,
            discard = viewModel::discard,
        ),
    )
}

class PersonalInfoActions(
    val back: () -> Unit = {},
    val save: () -> Unit = {},
    val edit: ((PersonalInfo) -> PersonalInfo) -> Unit = {},
    val ui: ((PersonalInfoUiState) -> PersonalInfoUiState) -> Unit = {},
    val toggleProfession: (Profession) -> Unit = {},
    val retryCatalog: () -> Unit = {},
    val gallery: () -> Unit = {},
    val camera: () -> Unit = {},
    val discard: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoContent(
    state: PersonalInfoUiState,
    actions: PersonalInfoActions,
) {
    val enabled = !state.saving
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        ShifaSubHeader(
            tr(Res.string.personalInformation),
            Modifier.statusBarsPadding(),
            onBack = actions.back,
            onCheck = if (state.dirty && enabled && !state.uploading) actions.save else null,
            checkDescription = Res.string.save,
        )
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
        ) {
            codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
            if (state.cameraDenied) {
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                    ShifaBanner(
                        tr(Res.string.cameraAccessNeeded),
                        Res.drawable.ic_alert_circle_16,
                        ShifaBannerType.Warning,
                        onClose = { actions.ui { it.copy(cameraDenied = false) } },
                    )
                    ShifaLink(tr(Res.string.openSettings), ::openAppSettings)
                }
            }
            // Photo block: 96 avatar and its label, one button for the sheet; a spinner over it while uploading.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.clickable(enabled = !state.uploading && enabled, role = Role.Button) {
                        actions.ui { it.copy(photoSheet = true) }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ShifaAvatar(state.photoUrl, ShifaSize.S96)
                        if (state.uploading) ShifaSpinner(ShifaColors.Interactive.Primary)
                    }
                    Text(
                        tr(Res.string.changePhoto),
                        Modifier.padding(top = ShifaSize.S8),
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Brand,
                    )
                }
            }
            ShifaSection(tr(Res.string.personalInformation)) {
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                    NameField(state.form.firstName, Res.string.firstName, state.firstNameError, enabled) { v ->
                        actions.edit { it.copy(firstName = v) }
                    }
                    NameField(state.form.lastName, Res.string.lastName, state.lastNameError, enabled) { v ->
                        actions.edit { it.copy(lastName = v) }
                    }
                    ShifaPickField(
                        tr(Res.string.dateOfBirth),
                        state.form.dob?.let(::displayDate).orEmpty(),
                        tr(Res.string.dateOfBirth),
                        { actions.ui { it.copy(pickingDob = true) } },
                        Modifier.fillMaxWidth(),
                        enabled = enabled,
                    )
                    val genders = listOf(null) + Gender.entries
                    ShifaDropdown(
                        tr(Res.string.gender),
                        genders.map { tr(it.label()) },
                        state.form.gender?.let { tr(it.label()) },
                        onSelect = { i ->
                            actions.edit { it.copy(gender = genders[i]) }
                            actions.ui { it.copy(genderOpen = false) }
                        },
                        expanded = state.genderOpen,
                        onExpandedChange = { open -> if (enabled) actions.ui { it.copy(genderOpen = open) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ShifaSection(tr(Res.string.professional)) {
                ShifaPickField(
                    tr(Res.string.profession),
                    "",
                    tr(Res.string.chooseOneOrMore),
                    { actions.ui { it.copy(professionsOpen = true, query = "") } },
                    Modifier.fillMaxWidth(),
                    enabled = enabled,
                    chips = state.form.professions.map { it.label() },
                )
            }
        }
    }
    if (state.pickingDob) {
        DobPicker(state.form.dob) { picked ->
            if (picked != null) actions.edit { it.copy(dob = picked) }
            actions.ui { it.copy(pickingDob = false) }
        }
    }
    if (state.professionsOpen) {
        Sheet({
            ProfessionPickerSheet(
                state.query,
                { q -> actions.ui { it.copy(query = q) } },
                state.catalog,
                state.form.professions,
                state.catalogLoading,
                state.catalogFailed,
                actions.retryCatalog,
                actions.toggleProfession,
                { actions.edit { it.copy(professions = emptyList()) } },
                { actions.ui { it.copy(professionsOpen = false) } },
            )
        }, { actions.ui { it.copy(professionsOpen = false) } })
    }
    if (state.photoSheet) {
        // Take photo / Choose from gallery; "Remove photo" waits for a backend endpoint (BE-30).
        Sheet({
            ShifaDialogSheet(
                tr(Res.string.changePhoto),
                tr(Res.string.chooseFromGallery),
                actions.gallery,
                cancelText = tr(Res.string.takePhoto),
                onCancel = actions.camera,
            )
        }, { actions.ui { it.copy(photoSheet = false) } })
    }
    if (state.askDiscard) {
        Sheet({
            ShifaDialogSheet(
                tr(Res.string.discardChangesTitle),
                tr(Res.string.discard),
                actions.discard,
                body = tr(Res.string.discardChangesBody),
                tone = DialogTone.Destructive,
                icon = Res.drawable.ic_trash_22,
                cancelText = tr(Res.string.keepEditing),
                onCancel = { actions.ui { it.copy(askDiscard = false) } },
            )
        }, { actions.ui { it.copy(askDiscard = false) } })
    }
}

@Composable
private fun NameField(
    value: String,
    label: StringResource,
    error: StringResource?,
    enabled: Boolean,
    onValue: (String) -> Unit,
) = ShifaInputField(
    value,
    onValue,
    tr(label),
    Modifier.fillMaxWidth(),
    supportingText = error?.let { tr(it) },
    isError = error != null,
    enabled = enabled,
    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
)

private fun Gender?.label() =
    when (this) {
        null -> Res.string.notSet
        Gender.Male -> Res.string.male
        Gender.Female -> Res.string.female
        Gender.Other -> Res.string.other
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(
    content: @Composable () -> Unit,
    onDismiss: () -> Unit,
) = ModalBottomSheet(onDismiss, shape = RectangleShape, containerColor = Color.Transparent, dragHandle = null) {
    content()
}
