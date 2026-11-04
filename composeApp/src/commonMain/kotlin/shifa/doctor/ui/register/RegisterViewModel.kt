package shifa.doctor.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.auth.ExistingPatient
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.catalog.CatalogRepository
import shifa.doctor.data.catalog.Clinic
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CheckExistingPatientRequest
import shifa.doctor.data.model.ProfileUpdateRequest
import shifa.doctor.data.model.RegisterRequest
import shifa.doctor.data.profile.Gender
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.session.Session
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.doctorAccountExists
import shifa.doctor.generated.resources.doctorPhoneExists
import shifa.doctor.generated.resources.enterEmail
import shifa.doctor.generated.resources.enterFirstName
import shifa.doctor.generated.resources.enterLastName
import shifa.doctor.generated.resources.invalidPhone
import shifa.doctor.generated.resources.profileExtrasNotSaved
import shifa.doctor.generated.resources.registrationConflict
import shifa.doctor.generated.resources.registrationFailed
import shifa.doctor.generated.resources.requestTimeout
import shifa.doctor.generated.resources.timezoneDetectionFailed
import shifa.doctor.ui.auth.newPasswordErrors
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.TimeZones
import shifa.doctor.utils.UzbekCyrillic
import shifa.doctor.utils.isEmailAddress
import shifa.doctor.utils.normalizedEmail

/** Flow 01/07: the form (step 1), the existing-patient confirmation (3b), and step 2 (flow 01/08, AUTH-07). */
enum class RegisterStep { Form, ExistingPatient, Details }

/** The searchable sheets (DaAuth picker). */
enum class RegisterPicker { TimeZone, Clinic, Profession }

data class RegisterUiState(
    val invite: Invite,
    val firstName: String = "",
    val lastName: String = "",
    // The backend only accepts the address the invitation went to, so that one is filled in and locked.
    val email: String = invite.email.orEmpty(),
    val phone: String = "",
    val password: String = "",
    val confirm: String = "",
    val passwordVisible: Boolean = false,
    val confirmVisible: Boolean = false,
    val firstNameError: StringResource? = null,
    val lastNameError: StringResource? = null,
    val emailError: StringResource? = null,
    val phoneError: StringResource? = null,
    val passwordError: StringResource? = null,
    val confirmError: StringResource? = null,
    val step: RegisterStep = RegisterStep.Form,
    val existing: ExistingPatient? = null,
    val timeZone: String? = null,
    val timeZoneError: StringResource? = null,
    val detectedTimeZone: String? = null,
    // Step 2 (flow 01/08): all optional, PATCHed once after the account exists.
    val dob: LocalDate? = null,
    val pickingDob: Boolean = false,
    val gender: Gender? = null,
    val genderOpen: Boolean = false,
    val address: String = "",
    val clinic: Clinic? = null,
    val professions: List<Profession> = emptyList(),
    val picker: RegisterPicker? = null,
    val query: String = "",
    val clinics: List<Clinic> = emptyList(),
    val catalog: List<Profession> = emptyList(),
    val pickerLoading: Boolean = false,
    val pickerFailed: Boolean = false,
    val loading: Boolean = false,
    val banner: CodeBanner? = null,
    val askDiscard: Boolean = false,
    val signedIn: Session? = null,
) {
    val emailLocked get() = invite.email != null

    // Flow §7 target: Back on a filled-in form asks first ("Discard registration?").
    val dirty get() =
        listOf(firstName, lastName, phone, password, confirm).any { it.isNotEmpty() } ||
            (!emailLocked && email.isNotEmpty())

    val timeZoneOptions get() = TimeZones.options(query, detectedTimeZone)

    // Both labels are searched, whatever the language (08 §4.1 `matches`).
    val professionOptions get() =
        catalog.filter {
                p ->
            // The Ўзбекча label is shown transliterated, so it is matched that way too.
            query.isBlank() ||
                listOfNotNull(p.english, p.uzbek, p.uzbek?.let(UzbekCyrillic::fromLatin))
                    .any { it.contains(query.trim(), true) }
        }

    val extras get() =
        ProfileUpdateRequest(
            dob = dob?.toString(),
            gender = gender?.wire,
            address = address.trim().ifEmpty { null },
            clinicId = clinic?.id,
            profession = professions.joinToString(", ") { it.english }.ifEmpty { null },
        )
}

/**
 * AUTH-06 (flow 01/07, 01 §2.6). Next checks the form, then whether the address already has a doctor account (it
 * stops here, not two screens later), then whether a patient account exists: found → the confirmation view, otherwise
 * step 2. Both end in `POST /auth/register` and a signed-in session. No e-mail code until BE-03 (D-04).
 */
class RegisterViewModel(
    invite: Invite,
    private val auth: AuthRepository = AuthRepository(),
    private val sessions: SessionRepository = sessionRepository,
    private val deviceTimeZone: () -> String? = { TimeZones.device() },
    private val catalogs: CatalogRepository = CatalogRepository(),
    private val profile: ProfileRepository = ProfileRepository(),
) : ViewModel() {
    private var lookup: Job? = null

    private val _state = MutableStateFlow(RegisterUiState(invite))
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onFirstName(v: String) = _state.update { it.copy(firstName = v, firstNameError = null) }

    fun onLastName(v: String) = _state.update { it.copy(lastName = v, lastNameError = null) }

    fun onEmail(v: String) = _state.update { if (it.emailLocked) it else it.copy(email = v, emailError = null) }

    fun onPhone(v: String) = _state.update { it.copy(phone = v, phoneError = null) }

    fun onPassword(v: String) = _state.update { it.copy(password = v, passwordError = null) }

    fun onConfirm(v: String) = _state.update { it.copy(confirm = v, confirmError = null) }

    fun togglePassword() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun toggleConfirm() = _state.update { it.copy(confirmVisible = !it.confirmVisible) }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    fun openPicker(picker: RegisterPicker) {
        _state.update { it.copy(picker = picker, query = "", pickerFailed = false) }
        load(picker, "")
    }

    fun closePicker() {
        lookup?.cancel()
        _state.update { it.copy(picker = null, pickerLoading = false) }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        if (_state.value.picker == RegisterPicker.Clinic) load(RegisterPicker.Clinic, q)
    }

    /** "Couldn't load. Retry" inside the sheet (flow 01/08 §9). */
    fun retryPicker() = _state.value.picker?.let { load(it, _state.value.query) }

    fun pickTimeZone(zone: String) = _state.update { it.copy(timeZone = zone, timeZoneError = null, picker = null) }

    /** A clinic, or null for "No clinic" (flow §7 target: a selection can be cleared). */
    fun pickClinic(clinic: Clinic?) = _state.update { it.copy(clinic = clinic, picker = null) }

    fun toggleProfession(p: Profession) =
        _state.update { s ->
            val on = s.professions.any { it.english == p.english }
            s.copy(professions = if (on) s.professions.filterNot { it.english == p.english } else s.professions + p)
        }

    fun clearProfessions() = _state.update { it.copy(professions = emptyList()) }

    fun openDob() = _state.update { it.copy(pickingDob = true) }

    fun pickDob(date: LocalDate?) = _state.update { it.copy(dob = date ?: it.dob, pickingDob = false) }

    fun toggleGender() = _state.update { it.copy(genderOpen = !it.genderOpen) }

    fun pickGender(gender: Gender?) = _state.update { it.copy(gender = gender, genderOpen = false) }

    fun onAddress(v: String) = _state.update { it.copy(address = v) }

    // Clinics: the whole list under two characters (filtered here), the backend's search from two, 300 ms after the
    // last keystroke (flow 01/08 §8 target). Professions: loaded once, filtered here. Time zones need no loading.
    private fun load(
        picker: RegisterPicker,
        query: String,
    ) {
        lookup?.cancel()
        val q = query.trim()
        when (picker) {
            RegisterPicker.TimeZone -> return
            RegisterPicker.Profession -> if (_state.value.catalog.isNotEmpty() && !_state.value.pickerFailed) return
            RegisterPicker.Clinic -> Unit
        }
        lookup =
            viewModelScope.launch {
                if (picker == RegisterPicker.Clinic && q.length >= MIN_SEARCH) delay(SEARCH_DEBOUNCE_MS)
                _state.update { it.copy(pickerLoading = true, pickerFailed = false) }
                if (picker == RegisterPicker.Profession) {
                    // Offline, the bundled list is shown and "Couldn't load. Retry" stays above it until the backend answers.
                    val result = catalogs.professions()
                    val list = result.getOrNull() ?: catalogs.bundledProfessions()
                    _state.update { it.copy(pickerLoading = false, catalog = list, pickerFailed = result.isFailure) }
                } else {
                    val result = catalogs.clinics(q.takeIf { it.length >= MIN_SEARCH })
                    val list =
                        result.getOrDefault(
                            emptyList(),
                        ).filter { q.length >= MIN_SEARCH || it.name.contains(q, true) }
                    _state.update { it.copy(pickerLoading = false, clinics = list, pickerFailed = result.isFailure) }
                }
            }
    }

    fun keepEditing() = _state.update { it.copy(askDiscard = false) }

    /**
     * Back (arrow or system): true when the registration should close. The confirmation view and step 2 return to the
     * form with everything kept (the patient check runs again on Next); a filled-in form asks first; nothing moves
     * while a request runs.
     */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.loading -> false
            s.askDiscard -> {
                keepEditing()
                false
            }
            s.step != RegisterStep.Form -> {
                _state.update { it.copy(step = RegisterStep.Form, existing = null, banner = null) }
                false
            }
            s.dirty -> {
                _state.update { it.copy(askDiscard = true) }
                false
            }
            else -> true
        }
    }

    fun next() {
        val s = _state.value
        if (s.loading) return
        val email = normalizedEmail(s.email)
        val phone = s.phone.trim().takeIf { it.isNotEmpty() }
        val phoneE164 = phone?.let(PhoneE164::normalize)
        val (pwError, confirmError) = newPasswordErrors(s.password, s.confirm)
        val errors =
            s.copy(
                firstNameError = Res.string.enterFirstName.takeIf { s.firstName.isBlank() },
                lastNameError = Res.string.enterLastName.takeIf { s.lastName.isBlank() },
                emailError = Res.string.enterEmail.takeIf { !isEmailAddress(email) },
                phoneError = Res.string.invalidPhone.takeIf { phone != null && phoneE164 == null },
                passwordError = pwError,
                confirmError = confirmError,
            )
        if (listOf(
                errors.firstNameError,
                errors.lastNameError,
                errors.emailError,
                errors.phoneError,
                pwError,
                confirmError,
            ).any { it != null }
        ) {
            return _state.update { errors }
        }
        _state.update { it.copy(loading = true, banner = null) }
        viewModelScope.launch {
            // The doctor check fails closed: register would replace an existing doctor's password (BE-29), so only a
            // confirmed "no" for the e-mail and for the phone goes on. The patient check fails open except offline: a
            // 500 there counts as "not found" (flow §8).
            val byEmail = auth.isDoctor(email = email)
            val byPhone =
                if (byEmail.getOrNull() == false && phoneE164 != null) {
                    auth.isDoctor(
                        phone = phoneE164,
                    )
                } else {
                    null
                }
            val doctorFailure = byEmail.exceptionOrNull() ?: byPhone?.exceptionOrNull()
            val clear = byEmail.getOrNull() == false && byPhone?.getOrNull() != true && doctorFailure == null
            val patient =
                if (clear) {
                    auth.existingPatient(
                        CheckExistingPatientRequest(s.firstName.trim(), s.lastName.trim(), email, phoneE164),
                    )
                } else {
                    null
                }
            val offline = patient?.exceptionOrNull()?.takeIf { it is ApiException.NetworkError }
            _state.update {
                when {
                    byEmail.getOrNull() == true -> it.copy(loading = false, emailError = Res.string.doctorAccountExists)
                    byPhone?.getOrNull() == true ->
                        it.copy(loading = false, phoneError = Res.string.doctorPhoneExists)
                    doctorFailure != null -> it.copy(loading = false, banner = banner(doctorFailure))
                    offline != null -> it.copy(loading = false, banner = banner(offline))
                    else -> {
                        val existing = patient?.getOrNull()
                        val detected = it.detectedTimeZone ?: deviceTimeZone()
                        val zone = it.timeZone ?: detected
                        it.copy(
                            loading = false,
                            step = if (existing != null) RegisterStep.ExistingPatient else RegisterStep.Details,
                            existing = existing,
                            detectedTimeZone = detected,
                            timeZone = zone,
                            // Detection failed: say so on the field instead of silently using UTC (flow §8).
                            timeZoneError = if (zone == null) Res.string.timezoneDetectionFailed else null,
                        )
                    }
                }
            }
        }
    }

    /** "Confirm registration" (3b) and "Create account" (step 2). */
    fun register() {
        val s = _state.value
        if (s.loading || s.step == RegisterStep.Form) return
        val (pwError, confirmError) = newPasswordErrors(s.password, s.confirm)
        val tzError = Res.string.timezoneDetectionFailed.takeIf { s.timeZone == null }
        if (pwError != null || confirmError != null || tzError != null) {
            return _state.update {
                it.copy(passwordError = pwError, confirmError = confirmError, timeZoneError = tzError)
            }
        }
        _state.update { it.copy(loading = true, banner = null) }
        viewModelScope.launch {
            val request =
                RegisterRequest(
                    firstName = s.firstName.trim(),
                    lastName = s.lastName.trim(),
                    email = normalizedEmail(s.email),
                    password = s.password,
                    key = s.invite.key,
                    phone = s.phone.trim().takeIf { it.isNotEmpty() }?.let(PhoneE164::normalize),
                    timeZone = s.timeZone,
                )
            val result = auth.register(request).mapCatching { sessions.signIn(it).getOrThrow() }
            // Step 2's details, once, now that the account exists (the timezone already went with `register`). The
            // account is made either way: a failure only leaves a note for the shell (flow 01/08 §8 target).
            val extras = s.extras
            var patchFailure: Throwable? = null
            if (result.isSuccess && s.step == RegisterStep.Details && extras != ProfileUpdateRequest()) {
                patchFailure = profile.update(extras).exceptionOrNull()
            }
            // The session may have ended meanwhile (this PATCH, or any other call, got a 401 or a disabled account):
            // then there is nothing to open, whatever the PATCH answered.
            val final =
                when {
                    result.isSuccess && sessions.session.value == null ->
                        Result.failure(patchFailure ?: ApiException.Unauthorized(null, null))
                    else -> result
                }
            if (final.isSuccess && patchFailure != null) ShellNotice.pending.value = Res.string.profileExtrasNotSaved
            _state.update {
                final.fold(
                    // The typed passwords are dropped once they are no longer needed (flow §12).
                    onSuccess = {
                            session ->
                        it.copy(loading = false, password = "", confirm = "", signedIn = session)
                    },
                    onFailure = { e -> it.copy(loading = false, banner = banner(e)) },
                )
            }
        }
    }

    private companion object {
        const val MIN_SEARCH = 2
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private fun banner(e: Throwable): CodeBanner {
        val text =
            when {
                e is ApiException.NetworkError && e.timeout -> Res.string.requestTimeout
                e is ApiException.Conflict -> Res.string.registrationConflict
                else -> e.toUserMessage(Res.string.registrationFailed)
            }
        return CodeBanner(text, requestId = e.requestId)
    }
}
