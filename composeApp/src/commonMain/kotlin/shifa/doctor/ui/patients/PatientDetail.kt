package shifa.doctor.ui.patients

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientAccount
import shifa.doctor.data.model.PatientAppointment
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.data.model.PatientOption
import shifa.doctor.data.model.PatientRequest
import shifa.doctor.data.model.PatientStatus
import shifa.doctor.data.patients.FavoritesRepository
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.favoritesRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.Gender
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.subscription.DoctorFeature
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.accountActive
import shifa.doctor.generated.resources.accountCreateFailed
import shifa.doctor.generated.resources.accountCreated
import shifa.doctor.generated.resources.activePatient
import shifa.doctor.generated.resources.activityTab
import shifa.doctor.generated.resources.addToFavorites
import shifa.doctor.generated.resources.age
import shifa.doctor.generated.resources.aiRiskDetected
import shifa.doctor.generated.resources.aiSummary
import shifa.doctor.generated.resources.allergies
import shifa.doctor.generated.resources.appointments
import shifa.doctor.generated.resources.appointmentsLoadFailed
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.birthDate
import shifa.doctor.generated.resources.bloodGroup
import shifa.doctor.generated.resources.carePartnerships
import shifa.doctor.generated.resources.chronicDisease
import shifa.doctor.generated.resources.chronicDiseaseUpdated
import shifa.doctor.generated.resources.clinicalSummary
import shifa.doctor.generated.resources.copied
import shifa.doctor.generated.resources.copy
import shifa.doctor.generated.resources.createDocument
import shifa.doctor.generated.resources.createPatientAccount
import shifa.doctor.generated.resources.createTreatmentPlan
import shifa.doctor.generated.resources.dealingWithChronicDiseasePatient
import shifa.doctor.generated.resources.documents
import shifa.doctor.generated.resources.done
import shifa.doctor.generated.resources.failedToUpdate
import shifa.doctor.generated.resources.findTherapyPartner
import shifa.doctor.generated.resources.forSecurityPasswordShownOnce
import shifa.doctor.generated.resources.formsTab
import shifa.doctor.generated.resources.gender
import shifa.doctor.generated.resources.iUnderstand
import shifa.doctor.generated.resources.ic_alert_16
import shifa.doctor.generated.resources.ic_alert_20
import shifa.doctor.generated.resources.ic_cal_plus_18
import shifa.doctor.generated.resources.ic_chat_18
import shifa.doctor.generated.resources.ic_check_circle_20
import shifa.doctor.generated.resources.ic_copy_16
import shifa.doctor.generated.resources.ic_file_plus_18
import shifa.doctor.generated.resources.ic_handshake_18
import shifa.doctor.generated.resources.ic_layers_18
import shifa.doctor.generated.resources.ic_sparkles_16
import shifa.doctor.generated.resources.ic_sparkles_22
import shifa.doctor.generated.resources.ic_star_24
import shifa.doctor.generated.resources.ic_star_filled_24
import shifa.doctor.generated.resources.ic_users_16
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.generated.resources.language
import shifa.doctor.generated.resources.lastVisitOn
import shifa.doctor.generated.resources.medicalInfo
import shifa.doctor.generated.resources.newAppointment
import shifa.doctor.generated.resources.noAccountYet
import shifa.doctor.generated.resources.noKnownAllergies
import shifa.doctor.generated.resources.noRecentVisit
import shifa.doctor.generated.resources.none
import shifa.doctor.generated.resources.notSpecified
import shifa.doctor.generated.resources.oneTimePassword
import shifa.doctor.generated.resources.overview
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.patientAccountExists
import shifa.doctor.generated.resources.patientAppAccess
import shifa.doctor.generated.resources.patientDetailsSaved
import shifa.doctor.generated.resources.patientId
import shifa.doctor.generated.resources.patientLoadFailed
import shifa.doctor.generated.resources.patientNotFound
import shifa.doctor.generated.resources.patientWithChronicDisease
import shifa.doctor.generated.resources.phone
import shifa.doctor.generated.resources.phoneUpdated
import shifa.doctor.generated.resources.quickActions
import shifa.doctor.generated.resources.reminder1Hour
import shifa.doctor.generated.resources.reminder24Hours
import shifa.doctor.generated.resources.reminderTiming
import shifa.doctor.generated.resources.removeFromFavorites
import shifa.doctor.generated.resources.reportsSmsNotAllowed
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.saved
import shifa.doctor.generated.resources.sendMessage
import shifa.doctor.generated.resources.shareCredentialsWithPatient
import shifa.doctor.generated.resources.smsReminderDescription
import shifa.doctor.generated.resources.smsReminderEnabled
import shifa.doctor.generated.resources.smsReminderNoPhone
import shifa.doctor.generated.resources.smsReminderSaved
import shifa.doctor.generated.resources.smsReminderTitle
import shifa.doctor.generated.resources.smsSendTest
import shifa.doctor.generated.resources.smsSendTestHint
import shifa.doctor.generated.resources.smsServiceUnavailable
import shifa.doctor.generated.resources.smsTestSent
import shifa.doctor.generated.resources.tabComesLater
import shifa.doctor.generated.resources.takeExtraCareForChronicDiseasePatient
import shifa.doctor.generated.resources.username
import shifa.doctor.ui.calendar.SlotPickSheet
import shifa.doctor.ui.calendar.longDate
import shifa.doctor.ui.documents.DocumentEditSheets
import shifa.doctor.ui.documents.DocumentUploadViewModel
import shifa.doctor.ui.documents.DocumentsActions
import shifa.doctor.ui.documents.PatientDocumentsUiState
import shifa.doctor.ui.documents.PatientDocumentsViewModel
import shifa.doctor.ui.documents.UploadSheets
import shifa.doctor.ui.documents.UploadUiState
import shifa.doctor.ui.documents.documentRows
import shifa.doctor.ui.documents.documentTitle
import shifa.doctor.ui.documents.rememberUploadSources
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.forms.FormTemplateTray
import shifa.doctor.ui.forms.FormsActions
import shifa.doctor.ui.forms.PatientFormsUiState
import shifa.doctor.ui.forms.PatientFormsViewModel
import shifa.doctor.ui.forms.formRows
import shifa.doctor.ui.profile.DiscardChangesSheet
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.speech.rememberAllowed
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaCardSkeleton
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaSwitch
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.TabItem
import shifa.doctor.ui.widgets.UnderlineTabs
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.tr

/** Patients whose chronic-disease warning was shown this session (flow 06/04: once per patient, not every open). */
internal val chronicWarned = mutableSetOf<Long>()

/** The record's sections (flow 06/03 §3.5); PAT-06/07, DOCS-04, FORM-* and the activity ticket fill the rest. */
enum class RecordTab(
    val label: StringResource,
) {
    Overview(Res.string.overview),
    Medical(Res.string.medicalInfo),
    Appointments(Res.string.appointments),
    Documents(Res.string.documents),
    Forms(Res.string.formsTab),
    Activity(Res.string.activityTab),
}

data class PatientDetailUiState(
    val patient: Patient? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    // The patient couldn't be read and none is shown: a full-screen error with Retry, or "Patient not found".
    val error: CodeBanner? = null,
    val notFound: Boolean = false,
    val banner: CodeBanner? = null,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val lastVisit: LocalDate? = null,
    val favorite: Boolean = false,
    val smsAllowed: Boolean = false,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val creatingAccount: Boolean = false,
    // The new login, shown once in a dialog that only Done closes.
    val account: PatientAccount? = null,
    val chronicWarning: Boolean = false,
    val tab: RecordTab = RecordTab.Overview,
    // Medical info (PAT-06): phones and allergies are typed, then saved; null = not edited (the patient's own).
    val phones: List<String>? = null,
    val allergies: String? = null,
    val phonesTried: Boolean = false,
    val phoneTaken: Boolean = false,
    val askRemovePhones: Boolean = false,
    // Leaving the tab (to [leaveTo], or back when null) with typed phones or allergies asks first.
    val askDiscard: Boolean = false,
    val leaveTo: RecordTab? = null,
    // "Edit details" sheet open (a nonce: a fresh form each time).
    val editing: Long? = null,
    // PAT-07: this doctor's visits with the patient, newest first, on the practice clock; null until read.
    val visits: List<Visit>? = null,
    val visitsError: CodeBanner? = null,
    // CARE-02/03: the ⋮ sheet (Find therapy partner, Care partnerships).
    val moreOpen: Boolean = false,
    // The "New appointment" slot sheet (a nonce).
    val booking: Long? = null,
) {
    val phoneRows: List<String> get() = phones ?: patient?.allPhones?.ifEmpty { null } ?: listOf("")

    val allergyText: String get() = allergies ?: patient?.allergies.orEmpty()

    val phonesDirty: Boolean
        get() = phones != null && phones.map(String::trim).filter(String::isNotEmpty) != patient?.allPhones

    val allergiesDirty: Boolean get() = allergies != null && allergies.trim() != patient?.allergies?.trim().orEmpty()

    val medicalDirty: Boolean get() = phonesDirty || allergiesDirty

    /** Typed phone rows that aren't phone numbers, once Save was tried. */
    val badPhones: Set<Int>
        get() =
            if (!phonesTried) {
                emptySet()
            } else {
                phoneRows.indices.filter {
                    phoneRows[it].isNotBlank() && PhoneE164.normalize(
                        phoneRows[it],
                    ) == null
                }.toSet()
            }
}

/** An appointment with this doctor on the practice clock (`GET /patients/{id}/appointments`, 06 §2.5). */
data class Visit(
    val id: Long,
    val at: Instant,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime?,
    val status: String?,
    val video: Boolean,
    val location: String?,
    val reason: String?,
    // Starts after now: shown apart from the history (flow 06/11 §8).
    val upcoming: Boolean = false,
)

/** The signed-in doctor as this screen needs them: id (favourites), practice zone (dates), SMS allowed. */
data class DetailDoctor(
    val id: Long? = null,
    val zone: String? = null,
    val smsAllowed: Boolean = false,
)

/**
 * PAT-05 (flow 06/03, spec 06 §2.1–2.3, §1.7): one patient. The directory's copy shows at once, then a fresh read;
 * "Last visit" is the last completed appointment on the practice date (Flutter used the newest document). SMS
 * reminder changes save at once and roll back on failure; the patient-app login is created here and shown once.
 */
class PatientDetailViewModel(
    private val id: Long,
    private val repo: PatientsRepository = patientsRepository,
    private val favorites: FavoritesRepository = favoritesRepository,
    doctors: Flow<DetailDoctor> =
        profileRepository.me.map {
            DetailDoctor(it?.profile?.id, it?.profile?.timeZone, it?.profile?.smsRemindersAllowed == true)
        },
    private val clockOf: (String?) -> PracticeClock = { PracticeClock(it) },
    private val warned: MutableSet<Long> = chronicWarned,
    // The clinic whose workspace opened the patient (CLINIC-06), else the doctor's own scope.
    private val clinicId: Long? = null,
) : ViewModel() {
    private var clock = clockOf(profileRepository.me.value?.profile?.timeZone)
    private var appointments: List<PatientAppointment>? = null
    private val _state =
        MutableStateFlow(
            PatientDetailUiState(patient = repo.patients.value?.firstOrNull { it.id == id }, today = clock.today()),
        )
    val state: StateFlow<PatientDetailUiState> = _state.asStateFlow()

    init {
        _state.value.patient?.let(::warnOnce)
        viewModelScope.launch {
            // A fresher copy (an edit, a refresh) replaces the shown one, but not over an SMS change being saved.
            repo.patients.collect { list ->
                val fresh = list?.firstOrNull { it.id == id } ?: return@collect
                if (!_state.value.saving) _state.update { it.copy(patient = fresh) }
            }
        }
        viewModelScope.launch {
            doctors.distinctUntilChanged().collect { d ->
                favorites.load(d.id)
                clock = clockOf(d.zone)
                _state.update { it.copy(smsAllowed = d.smsAllowed, today = clock.today()) }
                showVisits()
            }
        }
        viewModelScope.launch { favorites.ids.collect { ids -> _state.update { it.copy(favorite = id in ids) } } }
    }

    /** On open and on pull / Retry: the patient, then the appointments for "Last visit". */
    fun load(refresh: Boolean = false) {
        _state.update {
            it.copy(
                loading = it.patient == null,
                refreshing = refresh && it.patient != null,
                error = null,
                notFound = false,
            )
        }
        viewModelScope.launch {
            repo.patient(id, clinicId).fold(
                onSuccess = { p ->
                    _state.update { it.copy(patient = if (it.saving) it.patient else p, loading = false) }
                    warnOnce(p)
                },
                onFailure = { e ->
                    val shown = _state.value.patient != null
                    val banner = CodeBanner(e.toUserMessage(Res.string.patientLoadFailed), requestId = e.requestId)
                    _state.update {
                        when {
                            // Deleted elsewhere: the record (and its actions) goes, even when a copy was shown.
                            e is ApiException.NotFound -> it.copy(loading = false, notFound = true)
                            shown -> it.copy(loading = false, banner = banner)
                            else -> it.copy(loading = false, error = banner)
                        }
                    }
                },
            )
            readVisits()
            _state.update { it.copy(refreshing = false) }
        }
    }

    /** The Appointments and Activity tabs' Retry. */
    fun retryVisits() {
        _state.update { it.copy(visitsError = null) }
        viewModelScope.launch { readVisits() }
    }

    // A failed read leaves "No recent visit" in the header and an error with Retry in the tabs (flow 06/03 §6).
    private suspend fun readVisits() {
        repo.appointments(id, clinicId).fold(
            onSuccess = {
                appointments = it
                _state.update { s -> s.copy(visitsError = null) }
                showVisits()
            },
            onFailure = { e ->
                val banner = CodeBanner(e.toUserMessage(Res.string.appointmentsLoadFailed), requestId = e.requestId)
                _state.update { it.copy(visitsError = banner) }
            },
        )
    }

    /** Times on the practice clock (Flutter used the phone's); "Last visit" is the last completed one up to now. */
    private fun showVisits() {
        val list = appointments ?: return
        val now = clock.nowInstant()
        val visits =
            list
                .mapNotNull { a ->
                    val at = PracticeClock.parseInstant(a.startAt) ?: return@mapNotNull null
                    val start = clock.local(at)
                    Visit(
                        a.id,
                        at,
                        start.date,
                        start.time,
                        PracticeClock.parseInstant(a.endAt)?.let { clock.local(it).time },
                        a.status,
                        a.isVideo,
                        a.location?.trim()?.takeIf(String::isNotEmpty),
                        a.reason?.trim()?.takeIf(String::isNotEmpty),
                        // Still to happen: ahead, and not released (cancelled, expired, payment failed…).
                        upcoming = at > now && a.status?.uppercase() in BOOKED,
                    )
                }.sortedByDescending { it.at }
        val last = visits.firstOrNull { it.at <= now && it.status.equals("COMPLETED", ignoreCase = true) }
        _state.update { it.copy(visits = visits, lastVisit = last?.date) }
    }

    fun openBooking() = _state.update { it.copy(booking = currentInstant().toEpochMilliseconds()) }

    fun closeBooking() = _state.update { it.copy(booking = null) }

    fun ui(change: (PatientDetailUiState) -> PatientDetailUiState) = _state.update(change)

    /** Typed phones or allergies make leaving Medical info ask first (flow 06/05 §7). */
    fun select(tab: RecordTab) =
        _state.update {
            if (it.tab == RecordTab.Medical && tab != it.tab && it.medicalDirty) {
                it.copy(askDiscard = true, leaveTo = tab)
            } else {
                it.copy(tab = tab)
            }
        }

    /** Back: false while it asks to discard first. */
    fun back(): Boolean {
        if (!_state.value.medicalDirty) return true
        _state.update { it.copy(askDiscard = true, leaveTo = null) }
        return false
    }

    /** Drops the typed phones and allergies and goes where the doctor was going (the screen goes back when null). */
    fun discard(): RecordTab? {
        val to = _state.value.leaveTo
        _state.update {
            it.copy(
                phones = null,
                allergies = null,
                phonesTried = false,
                phoneTaken = false,
                askDiscard = false,
                leaveTo = null,
                tab = to ?: it.tab,
            )
        }
        return to
    }

    fun editPhones(rows: List<String>) =
        _state.update { if (it.saving) it else it.copy(phones = rows, phoneTaken = false) }

    fun editAllergies(text: String) = _state.update { if (it.saving) it else it.copy(allergies = text) }

    /** The whole list in E.164, once each; removing every phone asks first ([confirmed]). */
    fun savePhones(confirmed: Boolean = false) {
        val s = _state.value
        val patient = s.patient ?: return
        if (s.saving || !s.phonesDirty) return
        val bad = s.phoneRows.any { it.isNotBlank() && PhoneE164.normalize(it) == null }
        if (bad) return _state.update { it.copy(phonesTried = true) }
        val phones = s.phoneRows.mapNotNull { PhoneE164.normalize(it) }.distinct()
        if (phones.isEmpty() && patient.allPhones.isNotEmpty() && !confirmed) {
            return _state.update { it.copy(askRemovePhones = true) }
        }
        _state.update { it.copy(askRemovePhones = false) }
        patch(
            PatientRequest(phones = phones, phone = phones.firstOrNull().orEmpty()),
            Res.string.phoneUpdated,
            done = { it.copy(phones = null, phonesTried = false, phoneTaken = false) },
            conflict = { it.copy(phoneTaken = true) },
        )
    }

    fun saveAllergies() {
        val s = _state.value
        if (s.saving || !s.allergiesDirty) return
        val text = s.allergyText.trim()
        patch(
            PatientRequest(allergies = text),
            Res.string.saved,
            { it.copy(allergies = text) },
            done = { it.copy(allergies = null) },
            // Rolled back like the dropdowns (flow 06/05 §5): the field shows the stored value again.
            failed = { it.copy(allergies = null) },
        )
    }

    /** "None" clears it on the server (""); the wire value is the English name (flow 06/05 §4). */
    fun setChronic(disease: ChronicDisease) {
        val wire = if (disease == ChronicDisease.None) "" else disease.wire
        patch(
            PatientRequest(chronicDisease = wire),
            Res.string.chronicDiseaseUpdated,
            { it.copy(chronicDisease = wire) },
        )
    }

    fun setGender(gender: Gender?) {
        val wire = gender?.wire.orEmpty()
        patch(PatientRequest(gender = wire), Res.string.saved, { it.copy(gender = wire) })
    }

    fun setBloodGroup(group: String?) {
        val wire = group.orEmpty()
        patch(PatientRequest(bloodGroup = wire), Res.string.saved, { it.copy(bloodGroup = wire) })
    }

    fun openEdit() =
        _state.update {
            if (it.saving || it.patient == null) it else it.copy(editing = currentInstant().toEpochMilliseconds())
        }

    /** The "Edit details" sheet saved [p]. */
    fun edited(p: Patient) =
        _state.update {
            // The sheet sends only what changed: typing left in the tab is superseded only where the sheet changed it.
            val old = it.patient
            val phonesChanged = old?.allPhones != p.allPhones
            val allergiesChanged = old?.allergies != p.allergies
            it.copy(
                patient = p,
                editing = null,
                phones = it.phones.takeUnless { phonesChanged },
                allergies = it.allergies.takeUnless { allergiesChanged },
                phonesTried = it.phonesTried && !phonesChanged,
                phoneTaken = it.phoneTaken && !phonesChanged,
                banner = CodeBanner(Res.string.patientDetailsSaved, error = false),
            )
        }

    fun closeEdit() = _state.update { it.copy(editing = null) }

    fun toggleFavorite() = favorites.toggle(id)

    fun acknowledgeChronic() = _state.update { it.copy(chronicWarning = false) }

    fun setSms(on: Boolean) = sms(PatientRequest(smsReminderEnabled = on)) { it.copy(smsReminderEnabled = on) }

    fun setSmsHours(hours: Int) =
        sms(PatientRequest(smsReminderHoursBefore = hours)) {
            it.copy(smsReminderHoursBefore = kotlinx.serialization.json.JsonPrimitive(hours))
        }

    private fun sms(
        request: PatientRequest,
        local: (Patient) -> Patient,
    ) {
        if (_state.value.patient?.allPhones.isNullOrEmpty()) return
        patch(request, Res.string.smsReminderSaved, local)
    }

    fun testSms() {
        if (_state.value.testing) return
        _state.update { it.copy(testing = true, banner = null) }
        viewModelScope.launch {
            val result = repo.sendTestSms(id)
            _state.update { s ->
                s.copy(
                    testing = false,
                    banner =
                        result.fold(
                            { CodeBanner(Res.string.smsTestSent, error = false) },
                            { e ->
                                val text =
                                    when ((e as? ApiException)?.status) {
                                        403 -> Res.string.reportsSmsNotAllowed
                                        503 -> Res.string.smsServiceUnavailable
                                        else -> e.toUserMessage(Res.string.smsServiceUnavailable)
                                    }
                                CodeBanner(text, requestId = e.requestId)
                            },
                        ),
                )
            }
        }
    }

    fun createAccount() {
        if (_state.value.creatingAccount) return
        _state.update { it.copy(creatingAccount = true, banner = null) }
        viewModelScope.launch {
            repo.createAccount(id).fold(
                onSuccess = { a -> _state.update { it.copy(creatingAccount = false, account = a) } },
                onFailure = { e ->
                    val text =
                        if (e is ApiException.Conflict) {
                            Res.string.patientAccountExists
                        } else {
                            e.toUserMessage(Res.string.accountCreateFailed)
                        }
                    _state.update {
                        it.copy(
                            creatingAccount = false,
                            banner = CodeBanner(text, requestId = e.requestId),
                        )
                    }
                },
            )
            // The account flag (and username) come from a fresh read, after success or a "has one already".
            repo.patient(id, clinicId).onSuccess { p -> _state.update { it.copy(patient = p) } }
        }
    }

    fun closeAccount() = _state.update { it.copy(account = null) }

    /**
     * One field's PATCH (only that field is sent, so a concurrent edit of another field isn't overwritten), one at a
     * time: shown at once ([local]), rolled back on failure. [done] runs after a save, [conflict] on a 409 instead of
     * the banner (a phone another patient has).
     */
    private fun patch(
        request: PatientRequest,
        saved: StringResource,
        local: (Patient) -> Patient = { it },
        done: (PatientDetailUiState) -> PatientDetailUiState = { it },
        conflict: ((PatientDetailUiState) -> PatientDetailUiState)? = null,
        failed: (PatientDetailUiState) -> PatientDetailUiState = { it },
    ) {
        val before = _state.value.patient ?: return
        if (_state.value.saving) return
        _state.update { it.copy(patient = local(before), saving = true, banner = null) }
        viewModelScope.launch {
            repo.update(id, request).fold(
                onSuccess = { p ->
                    _state.update {
                        done(it.copy(patient = p, saving = false, banner = CodeBanner(saved, error = false)))
                    }
                },
                onFailure = { e ->
                    _state.update {
                        val back = it.copy(patient = before, saving = false)
                        when {
                            // Deleted elsewhere: the record leaves, as on a read (flow 06/05 §8).
                            e is ApiException.NotFound -> back.copy(notFound = true)
                            conflict != null && e is ApiException.Conflict -> conflict(back)
                            else ->
                                failed(
                                    back.copy(
                                        banner =
                                            CodeBanner(
                                                e.toUserMessage(Res.string.failedToUpdate),
                                                requestId = e.requestId,
                                            ),
                                    ),
                                )
                        }
                    }
                },
            )
        }
    }

    private fun warnOnce(p: Patient) {
        if (p.chronic != null && warned.add(p.id)) _state.update { it.copy(chronicWarning = true) }
    }
}

data class PatientDetailActions(
    val back: () -> Unit = {},
    val refresh: () -> Unit = {},
    val ui: ((PatientDetailUiState) -> PatientDetailUiState) -> Unit = {},
    val select: (RecordTab) -> Unit = {},
    val favorite: () -> Unit = {},
    val sms: (Boolean) -> Unit = {},
    val smsHours: (Int) -> Unit = {},
    val testSms: () -> Unit = {},
    val createAccount: () -> Unit = {},
    val medical: MedicalActions = MedicalActions(),
    val book: () -> Unit = {},
    // CHAT-07: Quick actions → "Send message" (the Chat tab on this patient's thread).
    val message: () -> Unit = {},
    // TP-04: "Create treatment plan" (a clinic's patient only): the plan wizard with this patient.
    val newPlan: (() -> Unit)? = null,
    // CARE-02/03: the ⋮ sheet's entries (doctors only).
    val findPartner: (() -> Unit)? = null,
    val partnerships: (() -> Unit)? = null,
    // AI-09: "AI summary" in the header; null hides it (below PRO, or clinic staff).
    val briefing: (() -> Unit)? = null,
    val retryVisits: () -> Unit = {},
    val documents: DocumentsActions = DocumentsActions(),
    val forms: FormsActions = FormsActions(),
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PatientDetailScreen(
    id: Long,
    onBack: () -> Unit,
    // CLINIC-06: the clinic whose workspace opened the patient (its scope for the patient and the documents).
    clinicId: Long? = null,
    // "New appointment": the picked free slot, with this patient chosen, for the booking screen (CAL-7).
    onBook: (CalendarEntry.FreeSlot, PatientOption) -> Unit = { _, _ -> },
    // A viewable document, for the viewer (DOCS-05): the document and the title to show.
    onOpenDocument: (PatientDocument, String) -> Unit = { _, _ -> },
    // A form in the 025-2 editor (FORM-04): its id, or null for a new one.
    onOpenForm: (Long?) -> Unit = {},
    // "Send message" (CHAT-07): the patient, for the chat header while the conversation resolves.
    onMessage: (Patient) -> Unit = {},
    // TP-04: "Create treatment plan" in a clinic's scope; the patient for the wizard.
    onNewPlan: ((Patient) -> Unit)? = null,
    // CARE-02/03: the ⋮ sheet's "Find therapy partner" and "Care partnerships" (doctors only, flow 06/12 §2).
    onFindPartner: ((Patient) -> Unit)? = null,
    onPartnerships: ((Patient) -> Unit)? = null,
    viewModel: PatientDetailViewModel =
        viewModel(key = "patient-$id-$clinicId") { PatientDetailViewModel(id, clinicId = clinicId) },
    documents: PatientDocumentsViewModel =
        viewModel(key = "patient-documents-$id-$clinicId") { PatientDocumentsViewModel(id, clinicId) },
    uploads: DocumentUploadViewModel =
        viewModel(key = "patient-upload-$id-$clinicId") { DocumentUploadViewModel(id, clinicId) },
    forms: PatientFormsViewModel = viewModel(key = "patient-forms-$id") { PatientFormsViewModel(id) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val docs by documents.state.collectAsStateWithLifecycle()
    val upload by uploads.state.collectAsStateWithLifecycle()
    val formsState by forms.state.collectAsStateWithLifecycle()
    // Each finished upload: the list again, with the new document on top.
    LaunchedEffect(upload.uploaded) { if (upload.uploaded > 0) documents.load() }
    val sources = rememberUploadSources(uploads)
    LaunchedEffect(Unit) { viewModel.load() }
    // Read each time the tab shows, back from the viewer included (access may have changed, flow 06/06 §5).
    // The documents tab reads the forms too: a form's PDF gets "Edit form" (flow 06/06).
    // Not for clinic staff: the forms routes are the doctor's alone, so the tab is hidden and nothing is read.
    val session by sessionRepository.session.collectAsStateWithLifecycle()
    val staff = session?.isClinicStaff == true
    LaunchedEffect(state.tab, staff) {
        if (state.tab == RecordTab.Documents) documents.load()
        if (!staff && (state.tab == RecordTab.Documents || state.tab == RecordTab.Forms)) forms.load()
    }
    val titleOf = @Composable { d: PatientDocument -> documentTitle(d) }
    val back = { if (viewModel.back()) onBack() }
    BackHandler(onBack = back)
    // AI-09: the patient briefing (PRO, doctors only; hidden until the plan is known).
    val briefingAllowed = rememberAllowed(DoctorFeature.PatientBriefing) && session?.isClinicStaff == false
    var briefingOpen by rememberSaveable { mutableStateOf(false) }
    val timelineAllowed = rememberAllowed(DoctorFeature.AiNotes) && session?.isClinicStaff == false
    // The viewer's title is resolved here, in the app's language, as the card shows it.
    var opening by remember { mutableStateOf<PatientDocument?>(null) }
    opening?.let { d ->
        val title = titleOf(d)
        LaunchedEffect(d) {
            opening = null
            onOpenDocument(d, title)
        }
    }
    PatientDetailContent(
        state,
        PatientDetailActions(
            back = back,
            refresh = {
                viewModel.load(refresh = true)
                if (state.tab == RecordTab.Documents) documents.load()
                if (!staff && (state.tab == RecordTab.Documents || state.tab == RecordTab.Forms)) forms.load()
            },
            ui = viewModel::ui,
            select = viewModel::select,
            favorite = viewModel::toggleFavorite,
            sms = viewModel::setSms,
            smsHours = viewModel::setSmsHours,
            testSms = viewModel::testSms,
            createAccount = viewModel::createAccount,
            book = viewModel::openBooking,
            message = { state.patient?.let(onMessage) },
            newPlan = onNewPlan?.let { go -> { state.patient?.let(go) } },
            findPartner =
                onFindPartner?.takeIf { session?.isClinicStaff == false }?.let {
                        go ->
                    { state.patient?.let(go) }
                },
            partnerships =
                onPartnerships?.takeIf { session?.isClinicStaff == false }?.let {
                        go ->
                    { state.patient?.let(go) }
                },
            briefing = { briefingOpen = true }.takeIf { briefingAllowed },
            retryVisits = viewModel::retryVisits,
            documents =
                DocumentsActions(
                    retry = documents::load,
                    search = documents::search,
                    open = { opening = it },
                    requestAccess = documents::requestAccess,
                    ui = documents::ui,
                    upload = { uploads.ui { it.copy(options = true) } },
                    cancelUpload = uploads::cancelUpload,
                    retryUpload = uploads::retry,
                    dismissUpload = { uploads.ui { it.copy(banner = null) } },
                    editForm = onOpenForm,
                    edit = documents::startEdit,
                ),
            forms =
                FormsActions(
                    retry = forms::load,
                    create = { forms.ui { it.copy(picking = true) } },
                    open = { f -> f.id?.let(onOpenForm) },
                    ui = forms::ui,
                ),
            medical =
                MedicalActions(
                    phones = viewModel::editPhones,
                    savePhones = { viewModel.savePhones() },
                    allergies = viewModel::editAllergies,
                    saveAllergies = viewModel::saveAllergies,
                    chronic = viewModel::setChronic,
                    gender = viewModel::setGender,
                    bloodGroup = viewModel::setBloodGroup,
                    editDetails = viewModel::openEdit,
                ),
        ),
        docs,
        upload,
        formsState,
        timeline = if (timelineAllowed) ({ ClinicalTimeline(id) }) else null,
        prophylaxis = clinicId?.let { c -> { ProphylaxisCard(id, c) } },
        tabs = if (staff) RecordTab.entries - RecordTab.Forms else RecordTab.entries,
    )
    UploadSheets(upload, uploads, sources)
    DocumentEditSheets(docs.edit, documents)
    if (briefingOpen && briefingAllowed) {
        PatientBriefingSheet(id, state.patient?.name.orEmpty(), onClose = { briefingOpen = false })
    }
    if (formsState.picking) {
        val close = { forms.ui { it.copy(picking = false) } }
        ProfileSheet(close) {
            FormTemplateTray({
                close()
                onOpenForm(null)
            }, close)
        }
    }
    val patient = state.patient
    val booking = state.booking
    if (patient != null && booking != null) {
        SlotPickSheet(booking, { slot ->
            viewModel.closeBooking()
            onBook(slot, PatientOption(patient.id, patient.name, patient.photoUrl))
        }, viewModel::closeBooking)
    }
    val editing = state.editing
    if (patient != null && editing != null) {
        CreatePatientSheet(
            editing,
            viewModel::edited,
            viewModel::closeEdit,
            editing = patient,
            onGone = { viewModel.ui { it.copy(editing = null, notFound = true) } },
        )
    }
    if (state.askDiscard) {
        DiscardChangesSheet(
            onDiscard = { if (viewModel.discard() == null) onBack() },
            onKeep = { viewModel.ui { it.copy(askDiscard = false, leaveTo = null) } },
        )
    }
    if (state.askRemovePhones) {
        RemovePhones({ viewModel.savePhones(confirmed = true) }) {
            viewModel.ui { it.copy(askRemovePhones = false) }
        }
    }
    state.patient?.chronic?.takeIf {
        state.chronicWarning
    }?.let { ChronicWarning(chronicLabel(it), viewModel::acknowledgeChronic) }
    state.account?.let { Credentials(it, viewModel::closeAccount) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PatientDetailContent(
    state: PatientDetailUiState,
    actions: PatientDetailActions,
    documents: PatientDocumentsUiState = PatientDocumentsUiState(),
    upload: UploadUiState = UploadUiState(),
    forms: PatientFormsUiState = PatientFormsUiState(),
    // AI-10: the clinical timeline card on the overview; null without the AI notes plan (or for clinic staff).
    timeline: (@Composable () -> Unit)? = null,
    // CLINIC-06: the clinic's prophylaxis card under Medical info; only in a clinic's scope.
    prophylaxis: (@Composable () -> Unit)? = null,
    // Clinic staff get no Forms tab (the backend keeps patient forms to doctors).
    tabs: List<RecordTab> = RecordTab.entries,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    // No title: the header below names the patient (flow 06/03 §3.1). ⋮ once the patient is read (CARE-02/03).
    val more = state.patient?.takeIf { actions.findPartner != null || actions.partnerships != null }
    ShifaSubHeader(
        "",
        Modifier.statusBarsPadding(),
        onBack = actions.back,
        onMore = more?.let { { actions.ui { s -> s.copy(moreOpen = true) } } },
    )
    if (state.moreOpen && more != null) MoreSheet(more, actions)
    val patient = state.patient
    val error = state.error
    when {
        state.notFound ->
            EmptyState(tr(Res.string.patientNotFound), Res.drawable.ic_users_22, Modifier.weight(1f)) {
                ShifaSecondaryButton(tr(Res.string.back), actions.back)
            }
        patient != null ->
            PullToRefreshBox(state.refreshing, actions.refresh, Modifier.weight(1f)) {
                val gap = Modifier.padding(horizontal = ShifaSpacing.Inset.Md)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = ShifaSpacing.Inset.Md),
                    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
                ) {
                    state.banner?.let { b ->
                        item { Box(gap) { codeBanner(b) { actions.ui { it.copy(banner = null) } }?.invoke() } }
                    }
                    item { Box(gap) { Header(patient, state, actions) } }
                    item { Box(gap) { Stats(patient, state.today) } }
                    // The tabs pin under the app bar while the record scrolls (flow 06/03 §3.5).
                    stickyHeader {
                        UnderlineTabs(
                            tabs.map { TabItem(tr(it.label)) },
                            tabs.indexOf(state.tab).coerceAtLeast(0),
                            { actions.select(tabs[it]) },
                            Modifier.background(ShifaColors.Bg.Canvas).then(gap),
                        )
                    }
                    when (state.tab) {
                        // Long lists are rows of the lazy list (100+ visits, flow 06/03 §8).
                        RecordTab.Appointments -> appointmentRows(state, actions, gap)
                        RecordTab.Activity -> activityRows(state, actions, gap)
                        RecordTab.Documents ->
                            documentRows(
                                documents,
                                actions.documents,
                                gap,
                                upload,
                                forms.byDocument,
                                forms.failed,
                            )
                        RecordTab.Forms -> formRows(forms, actions.forms, gap)
                        else ->
                            item {
                                Column(gap, verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md)) {
                                    when (state.tab) {
                                        RecordTab.Overview -> Overview(patient, state, actions, timeline)
                                        RecordTab.Medical -> {
                                            MedicalInfo(patient, state, actions.medical)
                                            prophylaxis?.invoke()
                                        }
                                        else -> EmptyState(tr(Res.string.tabComesLater), Res.drawable.ic_users_22)
                                    }
                                }
                            }
                    }
                }
            }
        error != null && !state.loading ->
            ShifaLoadError(tr(error.text), actions.refresh, Modifier.weight(1f), requestId = error.requestId)
        else -> Skeleton()
    }
}

@Composable
private fun Skeleton() =
    Column(
        Modifier.fillMaxWidth().padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaSkeleton(Modifier.size(ShifaSize.S64), ShifaRadius.Full)
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                ShifaSkeleton(Modifier.size(ShifaSize.S160, ShifaSize.S20))
                ShifaSkeleton(Modifier.size(ShifaSize.S96, ShifaSize.S16))
            }
        }
        repeat(3) { ShifaCardSkeleton() }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    p: Patient,
    state: PatientDetailUiState,
    actions: PatientDetailActions,
) = Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
    ShifaAvatar(p.photoUrl, ShifaSize.S64)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        Text(
            p.name.takeIf { it.isNotBlank() } ?: tr(Res.string.patient),
            style = ShifaTextStyles.heroNamePhone(),
            color = ShifaColors.Text.Primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            if (p.status == PatientStatus.Active || p.status == PatientStatus.Unknown) {
                ProfileChip(tr(Res.string.activePatient), ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.Primary)
            } else {
                StatusPill(p.status)
            }
            if (p.atRisk) IconChip(tr(Res.string.aiRiskDetected), Res.drawable.ic_sparkles_16, warning = true)
            p.chronic?.let { IconChip(chronicLabel(it), Res.drawable.ic_alert_16, warning = false) }
        }
        Text(
            state.lastVisit?.let { tr(Res.string.lastVisitOn, longDate(it)) } ?: tr(Res.string.noRecentVisit),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        ShifaIconButton(
            if (state.favorite) Res.drawable.ic_star_filled_24 else Res.drawable.ic_star_24,
            tr(if (state.favorite) Res.string.removeFromFavorites else Res.string.addToFavorites),
            actions.favorite,
            variant = ShifaIconButtonVariant.Secondary,
            selected = state.favorite,
        )
        actions.briefing?.let {
            ShifaIconButton(
                Res.drawable.ic_sparkles_22,
                tr(Res.string.aiSummary),
                it,
                variant = ShifaIconButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun IconChip(
    text: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    warning: Boolean,
) = Row(
    Modifier
        .background(if (warning) ShifaColors.Bg.WarningSubtle else ShifaColors.Bg.ErrorSubtle, ShifaRadius.Full)
        .padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S2),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4),
    verticalAlignment = Alignment.CenterVertically,
) {
    val ink = if (warning) ShifaColors.Text.Warning else ShifaColors.Text.Error
    Icon(painterResource(icon), null, tint = ink)
    Text(text, style = ShifaTextStyles.metaSmall(), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Six tiles, two per row (flow 06/03 §3.3): "—" when unknown, so every tile keeps its height. */
@Composable
private fun Stats(
    p: Patient,
    today: LocalDate,
) {
    val dash = "—"
    val tiles =
        listOf(
            Res.string.age to (p.age(today)?.toString() ?: dash),
            Res.string.gender to (genderLabel(p.gender) ?: dash),
            Res.string.phone to (p.allPhones.firstOrNull()?.let(PhoneE164::display) ?: dash),
            Res.string.birthDate to (p.birth?.let(::displayDate) ?: dash),
            Res.string.patientId to p.displayId,
            Res.string.language to (languageLabel(p.language) ?: dash),
        )
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                row.forEach { (label, value) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd)
                            .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S10),
                        verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
                    ) {
                        Text(tr(label), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
                        Text(
                            value,
                            style = ShifaTextStyles.panelTitle(),
                            color = ShifaColors.Text.Primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The stored code or word in the app's language; anything else as written. */
@Composable
internal fun languageLabel(raw: String?): String? =
    PatientLanguage.of(raw)?.let { tr(it.label) } ?: raw?.trim()?.takeIf(String::isNotEmpty)

@Composable
private fun Overview(
    p: Patient,
    state: PatientDetailUiState,
    actions: PatientDetailActions,
    timeline: (@Composable () -> Unit)?,
) {
    Card(tr(Res.string.clinicalSummary)) {
        SummaryRow(
            tr(Res.string.chronicDisease),
            p.chronic?.let { chronicLabel(it) } ?: tr(Res.string.none),
            p.chronic != null,
        )
        val allergies = p.allergies?.trim()?.takeIf(String::isNotEmpty)
        SummaryRow(tr(Res.string.allergies), allergies ?: tr(Res.string.noKnownAllergies), allergies != null)
        SummaryRow(
            tr(Res.string.bloodGroup),
            p.bloodGroup?.trim()?.takeIf(String::isNotEmpty) ?: tr(Res.string.notSpecified),
            false,
        )
        SummaryRow(tr(Res.string.language), languageLabel(p.language) ?: tr(Res.string.notSpecified), false)
    }
    timeline?.invoke()
    RecentActivity(state, actions)
    QuickActions(actions)
    Card(tr(Res.string.patientAppAccess)) {
        if (p.hasAccount) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(Res.drawable.ic_check_circle_20), null, tint = ShifaColors.Icon.Brand)
                Column {
                    Text(
                        tr(Res.string.accountActive),
                        style = ShifaTextStyles.panelTitle(),
                        color = ShifaColors.Text.Primary,
                    )
                    p.username?.let {
                        Text(
                            it,
                            style = ShifaTextStyles.metaSmall(),
                            color = ShifaColors.Text.Secondary,
                        )
                    }
                }
            }
        } else {
            Text(tr(Res.string.noAccountYet), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
            ShifaPrimaryButton(
                tr(Res.string.createPatientAccount),
                actions.createAccount,
                loading = state.creatingAccount,
            )
        }
    }
    // Only when the doctor's plan allows SMS (06 §1.7).
    if (state.smsAllowed) Sms(p, state, actions)
}

@Composable
private fun Sms(
    p: Patient,
    state: PatientDetailUiState,
    actions: PatientDetailActions,
) = Card(tr(Res.string.smsReminderTitle)) {
    val phone = p.allPhones.isNotEmpty()
    val busy = state.saving
    Text(tr(Res.string.smsReminderDescription), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    if (!phone) {
        Text(
            tr(Res.string.smsReminderNoPhone),
            Modifier.fillMaxWidth().background(
                ShifaColors.Bg.WarningSubtle,
                ShifaRadius.ControlMd,
            ).padding(ShifaSize.S8),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Primary,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12), verticalAlignment = Alignment.CenterVertically) {
        ShifaSwitch(p.smsReminderEnabled, { if (phone && !busy) actions.sms(it) })
        Text(tr(Res.string.smsReminderEnabled), style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
    }
    if (p.smsReminderEnabled && phone) {
        var open by remember { mutableStateOf(false) }
        val options = listOf(24 to Res.string.reminder24Hours, 1 to Res.string.reminder1Hour)
        ShifaDropdown(
            tr(Res.string.reminderTiming),
            options.map { tr(it.second) },
            tr(options.first { it.first == p.smsHours }.second),
            { i ->
                open = false
                if (!busy && options[i].first != p.smsHours) actions.smsHours(options[i].first)
            },
            open,
            { open = it },
        )
    }
    if (phone) {
        ShifaSecondaryButton(tr(Res.string.smsSendTest), actions.testSms, loading = state.testing)
        Text(tr(Res.string.smsSendTestHint), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    }
}

/** The record's ⋮ (board `DaPatients` more sheet): the care partnership entries; the rest are Quick Actions. */
@Composable
private fun MoreSheet(
    patient: Patient,
    actions: PatientDetailActions,
) {
    val close = { actions.ui { s -> s.copy(moreOpen = false) } }
    ProfileSheet(close) {
        ShifaTray(patient.name, close) {
            actions.findPartner?.let { go ->
                ShifaSettingsItem(tr(Res.string.findTherapyPartner), {
                    close()
                    go()
                }, icon = Res.drawable.ic_handshake_18)
            }
            actions.partnerships?.let { go ->
                ShifaSettingsItem(tr(Res.string.carePartnerships), {
                    close()
                    go()
                }, icon = Res.drawable.ic_users_16, divider = false)
            }
        }
    }
}

/**
 * "Quick Actions" (flow 06/03 §7, board `DaPatients` overview): two-column tinted tiles. "More actions" waits for the ⋮
 * sheet (no dead buttons).
 */
@Composable
private fun QuickActions(actions: PatientDetailActions) =
    Card(tr(Res.string.quickActions)) {
        val tiles =
            listOf(
                Triple(Res.string.newAppointment, Res.drawable.ic_cal_plus_18, actions.book),
                Triple(Res.string.sendMessage, Res.drawable.ic_chat_18, actions.message),
                Triple(
                    Res.string.createDocument,
                    Res.drawable.ic_file_plus_18,
                    actions.documents.upload,
                ),
            ) +
                listOfNotNull(
                    actions.newPlan?.let { Triple(Res.string.createTreatmentPlan, Res.drawable.ic_layers_18, it) },
                )
        tiles.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                row.forEach { (label, icon, go) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = ShifaSize.S64)
                            .pressable(go, PressVariant.Tint)
                            .padding(ShifaSize.S10),
                        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6, Alignment.CenterVertically),
                    ) {
                        Icon(painterResource(icon), null, tint = ShifaColors.Icon.Brand)
                        Text(tr(label), style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

@Composable
private fun Card(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    Text(title, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
    content()
}

@Composable
internal fun SummaryRow(
    label: String,
    value: String,
    alert: Boolean,
) = Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
    Text(label, Modifier.weight(1f), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    Text(
        value,
        Modifier.weight(1f),
        style = ShifaTextStyles.metaSmall(),
        color = if (alert) ShifaColors.Text.Error else ShifaColors.Text.Primary,
    )
}

@Composable
internal fun ChronicWarning(
    disease: String,
    onOk: () -> Unit,
) = ProfileSheet(onOk) {
    ShifaDialogSheet(
        tr(Res.string.patientWithChronicDisease),
        tr(Res.string.iUnderstand),
        onOk,
        body =
            tr(Res.string.dealingWithChronicDiseasePatient) + "\n" + disease + "\n\n" +
                tr(Res.string.takeExtraCareForChronicDiseasePatient),
        icon = Res.drawable.ic_alert_20,
    )
}

/** Shown once; only Done closes it (the password can't be read again). */
@Composable
private fun Credentials(
    account: PatientAccount,
    onDone: () -> Unit,
) = ProfileSheet({}, confirmHide = { false }) {
    ShifaDialogSheet(
        tr(Res.string.accountCreated),
        tr(Res.string.done),
        onDone,
        body = tr(Res.string.shareCredentialsWithPatient),
    ) {
        CopyRow(tr(Res.string.username), account.username)
        CopyRow(tr(Res.string.oneTimePassword), account.oneTimePassword)
        Text(
            tr(Res.string.forSecurityPasswordShownOnce),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Warning,
        )
    }
}

@Composable
private fun CopyRow(
    label: String,
    value: String,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(value) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd).padding(ShifaSize.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
            Text(value, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
        }
        ShifaIconButton(
            Res.drawable.ic_copy_16,
            tr(if (copied) Res.string.copied else Res.string.copy),
            {
                clipboard.setText(AnnotatedString(value))
                copied = true
            },
            variant = ShifaIconButtonVariant.Secondary,
            selected = copied,
        )
    }
}

/** Statuses of a visit that still holds its time. */
private val BOOKED = setOf("CONFIRMED", "REQUESTED", "PENDING_PAYMENT", "IN_PROGRESS")
