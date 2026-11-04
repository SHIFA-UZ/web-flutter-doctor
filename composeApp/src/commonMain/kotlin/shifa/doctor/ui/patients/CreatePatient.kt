package shifa.doctor.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientRequest
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.Gender
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.addPhoneNumber
import shifa.doctor.generated.resources.address
import shifa.doctor.generated.resources.allergies
import shifa.doctor.generated.resources.arabic
import shifa.doctor.generated.resources.birthDate
import shifa.doctor.generated.resources.bloodGroup
import shifa.doctor.generated.resources.clear
import shifa.doctor.generated.resources.createNewPatient
import shifa.doctor.generated.resources.createPatient
import shifa.doctor.generated.resources.createPatientFailed
import shifa.doctor.generated.resources.createPatientInvalid
import shifa.doctor.generated.resources.email
import shifa.doctor.generated.resources.english
import shifa.doctor.generated.resources.female
import shifa.doctor.generated.resources.fullName
import shifa.doctor.generated.resources.fullNameRequired
import shifa.doctor.generated.resources.gender
import shifa.doctor.generated.resources.german
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.invalidEmail
import shifa.doctor.generated.resources.invalidPhone
import shifa.doctor.generated.resources.karakalpak
import shifa.doctor.generated.resources.kazakh
import shifa.doctor.generated.resources.kyrgyz
import shifa.doctor.generated.resources.language
import shifa.doctor.generated.resources.male
import shifa.doctor.generated.resources.notSpecified
import shifa.doctor.generated.resources.other
import shifa.doctor.generated.resources.patientCreated
import shifa.doctor.generated.resources.patientPhoneExists
import shifa.doctor.generated.resources.phoneNumber
import shifa.doctor.generated.resources.remove
import shifa.doctor.generated.resources.russian
import shifa.doctor.generated.resources.selectDate
import shifa.doctor.generated.resources.tajik
import shifa.doctor.generated.resources.turkmen
import shifa.doctor.generated.resources.uzbek
import shifa.doctor.ui.calendar.FieldLabel
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.profile.DiscardChangesSheet
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.register.DobPicker
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.isEmailAddress
import shifa.doctor.utils.tr

/**
 * A patient's language as stored: the code the patient app and the backend's seed use ("uz"); Flutter's doctor app
 * sent English words ("uzbek"), which [PatientLanguage.of] still reads.
 */
enum class PatientLanguage(
    val code: String,
    val word: String,
    val label: StringResource,
) {
    Uzbek("uz", "uzbek", Res.string.uzbek),
    Russian("ru", "russian", Res.string.russian),
    English("en", "english", Res.string.english),
    German("de", "german", Res.string.german),
    Karakalpak("kaa", "karakalpak", Res.string.karakalpak),
    Kazakh("kk", "kazakh", Res.string.kazakh),
    Kyrgyz("ky", "kyrgyz", Res.string.kyrgyz),
    Tajik("tg", "tajik", Res.string.tajik),
    Turkmen("tk", "turkmen", Res.string.turkmen),
    Arabic("ar", "arabic", Res.string.arabic),
    ;

    companion object {
        fun of(raw: String?): PatientLanguage? =
            raw?.trim()?.lowercase()?.let {
                    v ->
                entries.firstOrNull { it.code == v || it.word == v }
            }
    }
}

/** The eight ABO/Rh groups (flow 06/02 §3). */
val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

data class NewPatient(
    val name: String = "",
    val phones: List<String> = listOf(""),
    val email: String = "",
    val address: String = "",
    val language: PatientLanguage = PatientLanguage.Uzbek,
    val gender: Gender? = null,
    val bloodGroup: String? = null,
    val allergies: String = "",
    val birthDate: LocalDate? = null,
) {
    val dirty: Boolean get() = this != NewPatient()
}

data class CreatePatientUiState(
    val form: NewPatient = NewPatient(),
    // Field errors show after the first try (flow 06/02 §9), then follow the edits.
    val tried: Boolean = false,
    val phoneTaken: Boolean = false,
    val saving: Boolean = false,
    val banner: CodeBanner? = null,
    val pickingDate: Boolean = false,
    val askDiscard: Boolean = false,
    val created: Patient? = null,
) {
    val nameMissing: Boolean get() = form.name.isBlank()

    /** Each typed phone row that isn't a phone number (empty rows are just dropped). */
    val badPhones: Set<Int> get() =
        form.phones.indices.filter {
            form.phones[it].isNotBlank() && PhoneE164.normalize(form.phones[it]) == null
        }.toSet()

    val badEmail: Boolean get() = form.email.isNotBlank() && !isEmailAddress(form.email.trim())

    val valid: Boolean get() = !nameMissing && badPhones.isEmpty() && !badEmail
}

/**
 * PAT-04 (flow 06/02, spec 06 §1.6): one create sheet for every caller (directory, booking, later Home and the clinic),
 * with the superset of fields. Phones are saved in E.164 (once each), the sheet stays open and locked while the request
 * runs (Flutter closed it first and lost the input on an error), a known phone says so under the phone rows, and closing
 * with something typed asks first.
 */
class CreatePatientViewModel(
    private val repo: PatientsRepository = patientsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CreatePatientUiState())
    val state: StateFlow<CreatePatientUiState> = _state.asStateFlow()

    fun edit(change: (NewPatient) -> NewPatient) =
        _state.update { if (it.saving) it else it.copy(form = change(it.form), phoneTaken = false, banner = null) }

    fun ui(change: (CreatePatientUiState) -> CreatePatientUiState) = _state.update(change)

    /** Close (×, scrim, Back): asks first when something was typed; never while saving. */
    fun close(): Boolean {
        val s = _state.value
        if (s.saving) return false
        if (s.form.dirty && !s.askDiscard) return false.also { _state.update { it.copy(askDiscard = true) } }
        return true
    }

    fun request(form: NewPatient): PatientRequest {
        val phones = form.phones.mapNotNull { PhoneE164.normalize(it) }.distinct()
        return PatientRequest(
            name = form.name.trim(),
            phones = phones,
            phone = phones.singleOrNull(),
            email = form.email.trim().ifEmpty { null },
            address = form.address.trim().ifEmpty { null },
            birthDate = form.birthDate?.toString(),
            language = form.language.code,
            gender = form.gender?.wire,
            bloodGroup = form.bloodGroup,
            allergies = form.allergies.trim().ifEmpty { null },
            // Always empty at creation (06 §1.6); set later in Medical info.
            chronicDisease = "",
        )
    }

    fun create() {
        val s = _state.value
        if (s.saving) return
        if (!s.valid) return _state.update { it.copy(tried = true) }
        _state.update { it.copy(saving = true, tried = true, banner = null, phoneTaken = false) }
        viewModelScope.launch {
            repo.create(request(s.form)).fold(
                onSuccess = { patient ->
                    _state.update { it.copy(saving = false, created = patient) }
                },
                onFailure = { e ->
                    // 409 is the known phone (the server's text is never shown); 400 means the details; else retry.
                    val status = (e as? ApiException)?.status
                    _state.update {
                        when (status) {
                            409 -> it.copy(saving = false, phoneTaken = true)
                            400 ->
                                it.copy(
                                    saving = false,
                                    banner = CodeBanner(Res.string.createPatientInvalid, requestId = e.requestId),
                                )
                            else ->
                                it.copy(
                                    saving = false,
                                    banner =
                                        CodeBanner(
                                            e.toUserMessage(Res.string.createPatientFailed),
                                            requestId = e.requestId,
                                        ),
                                )
                        }
                    }
                },
            )
        }
    }
}

/** The sheet for any caller: [onCreated] gets the new patient (the directory already has it), [onClose] on cancel. */
@Composable
fun CreatePatientSheet(
    opened: Long,
    onCreated: (Patient) -> Unit,
    onClose: () -> Unit,
) {
    val viewModel = viewModel(key = "create-patient-$opened") { CreatePatientViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.created) {
        state.created?.let {
            onCreated(it)
            // After the caller's navigation (opening a screen clears success notices), so it stays.
            ShellNotice.pending.value = Notice(Res.string.patientCreated, success = true)
        }
    }
    // A drag or a scrim tap can't hide it while saving or with input (it asks first); the form never vanishes.
    ProfileSheet({ onClose() }, confirmHide = viewModel::close) {
        CreatePatientTray(
            state,
            viewModel::edit,
            viewModel::ui,
            viewModel::create,
            { if (viewModel.close()) onClose() },
        )
    }
    if (state.askDiscard) {
        DiscardChangesSheet(onDiscard = onClose, onKeep = { viewModel.ui { it.copy(askDiscard = false) } })
    }
    if (state.pickingDate) {
        DobPicker(state.form.birthDate) { picked ->
            if (picked != null) viewModel.edit { it.copy(birthDate = picked) }
            viewModel.ui { it.copy(pickingDate = false) }
        }
    }
}

@Composable
fun CreatePatientTray(
    state: CreatePatientUiState,
    edit: ((NewPatient) -> NewPatient) -> Unit,
    ui: ((CreatePatientUiState) -> CreatePatientUiState) -> Unit,
    create: () -> Unit,
    close: () -> Unit,
) = ShifaTray(tr(Res.string.createNewPatient), close) {
    val f = state.form
    val enabled = !state.saving
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        codeBanner(state.banner) { ui { it.copy(banner = null) } }?.invoke()
        ShifaInputField(
            f.name,
            { v -> edit { it.copy(name = v) } },
            tr(Res.string.fullName) + " *",
            Modifier.fillMaxWidth(),
            supportingText = tr(Res.string.fullNameRequired).takeIf { state.tried && state.nameMissing },
            isError = state.tried && state.nameMissing,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Phones(state, edit, enabled)
        ShifaInputField(
            f.email,
            { v -> edit { it.copy(email = v) } },
            tr(Res.string.email),
            Modifier.fillMaxWidth(),
            supportingText = tr(Res.string.invalidEmail).takeIf { state.tried && state.badEmail },
            isError = state.tried && state.badEmail,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        ShifaInputField(f.address, { v ->
            edit {
                it.copy(address = v)
            }
        }, tr(Res.string.address), Modifier.fillMaxWidth(), enabled = enabled)
        Choice(
            tr(Res.string.language),
            PatientLanguage.entries.map {
                tr(it.label)
            },
            PatientLanguage.entries.indexOf(f.language),
            enabled,
        ) { i ->
            edit { it.copy(language = PatientLanguage.entries[i]) }
        }
        val genders = listOf<Gender?>(null) + Gender.entries
        Choice(
            tr(Res.string.gender),
            genders.map {
                    g ->
                tr(
                    when (g) {
                        null -> Res.string.notSpecified
                        Gender.Male -> Res.string.male
                        Gender.Female -> Res.string.female
                        Gender.Other -> Res.string.other
                    },
                )
            },
            genders.indexOf(f.gender),
            enabled,
        ) { i -> edit { it.copy(gender = genders[i]) } }
        val groups = listOf<String?>(null) + BLOOD_GROUPS
        Choice(
            tr(Res.string.bloodGroup),
            groups.map {
                it ?: tr(Res.string.notSpecified)
            },
            groups.indexOf(f.bloodGroup),
            enabled,
        ) { i ->
            edit { it.copy(bloodGroup = groups[i]) }
        }
        ShifaInputField(f.allergies, { v ->
            edit {
                it.copy(allergies = v)
            }
        }, tr(Res.string.allergies), Modifier.fillMaxWidth(), enabled = enabled)
        ShifaSettingsItem(
            tr(Res.string.birthDate),
            { if (enabled) ui { it.copy(pickingDate = true) } },
            subtitle = f.birthDate?.let(::displayDate) ?: tr(Res.string.selectDate),
            divider = false,
            // A picked date can be taken back (flow 06/02 §7; Flutter couldn't).
            trailing =
                f.birthDate?.let {
                    {
                        ShifaIconButton(Res.drawable.ic_x_16, tr(Res.string.clear), {
                            if (enabled) {
                                edit {
                                        f2 ->
                                    f2.copy(birthDate = null)
                                }
                            }
                        }, variant = ShifaIconButtonVariant.Ghost)
                    }
                },
        )
    }
    ShifaPrimaryButton(
        tr(Res.string.createPatient),
        create,
        Modifier.fillMaxWidth(),
        enabled = enabled && !f.name.isBlank(),
        loading = state.saving,
    )
}

/** Phone rows (one at least), remove on each when there are several, "+ Add phone number"; errors under the group. */
@Composable
private fun Phones(
    state: CreatePatientUiState,
    edit: ((NewPatient) -> NewPatient) -> Unit,
    enabled: Boolean,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    val phones = state.form.phones
    phones.forEachIndexed { i, value ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShifaInputField(
                value,
                { v -> edit { f -> f.copy(phones = f.phones.toMutableList().also { it[i] = v }) } },
                tr(Res.string.phoneNumber),
                Modifier.weight(1f),
                isError = (state.tried && i in state.badPhones) || state.phoneTaken,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            if (phones.size > 1) {
                ShifaIconButton(
                    Res.drawable.ic_trash_22,
                    tr(Res.string.remove),
                    { if (enabled) edit { f -> f.copy(phones = f.phones.filterIndexed { j, _ -> j != i }) } },
                    variant = ShifaIconButtonVariant.Ghost,
                )
            }
        }
    }
    when {
        state.phoneTaken ->
            Text(
                tr(Res.string.patientPhoneExists),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Error,
            )
        state.tried && state.badPhones.isNotEmpty() ->
            Text(
                tr(Res.string.invalidPhone),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Error,
            )
    }
    ShifaLink(tr(Res.string.addPhoneNumber), { if (enabled) edit { it.copy(phones = it.phones + "") } })
}

/** A labelled dropdown that keeps its own open state. */
@Composable
private fun Choice(
    label: String,
    options: List<String>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    FieldLabel(label)
    ShifaDropdown(
        label,
        options,
        options.getOrNull(selected),
        onSelect = { i ->
            onSelect(i)
            open = false
        },
        expanded = open,
        onExpandedChange = { open = it && enabled },
        modifier = Modifier.fillMaxWidth(),
    )
}
