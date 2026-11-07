package shifa.doctor.ui.clinic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.finance.FinanceRepository
import shifa.doctor.data.finance.RecordPayment
import shifa.doctor.data.finance.financeRepository
import shifa.doctor.data.model.ClinicMember
import shifa.doctor.data.model.ClinicPatient
import shifa.doctor.data.model.InstallmentSpec
import shifa.doctor.data.model.NewInstallmentPlan
import shifa.doctor.data.model.NewTreatmentPlan
import shifa.doctor.data.model.PlanLineRequest
import shifa.doctor.data.model.PlanServiceOption
import shifa.doctor.data.model.PlanStatus
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.data.treatmentplans.treatmentPlansRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.clinicFinanceInstallments
import shifa.doctor.generated.resources.doctors
import shifa.doctor.generated.resources.failedToLoad
import shifa.doctor.generated.resources.ic_calendar_16
import shifa.doctor.generated.resources.ic_plus_16
import shifa.doctor.generated.resources.ic_trash_16
import shifa.doctor.generated.resources.next
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.remove
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.review
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.services
import shifa.doctor.generated.resources.treatmentPlanDiagnosis
import shifa.doctor.generated.resources.treatmentPlanDiagnosisHint
import shifa.doctor.generated.resources.treatmentPlanNotes
import shifa.doctor.generated.resources.treatmentPlanNotesHint
import shifa.doctor.generated.resources.treatmentPlanTitle
import shifa.doctor.generated.resources.treatmentPlanTitleHint
import shifa.doctor.generated.resources.treatmentPlanTitleRequired
import shifa.doctor.generated.resources.treatmentPlanWizardAddRow
import shifa.doctor.generated.resources.treatmentPlanWizardCouldNotCreatePlan
import shifa.doctor.generated.resources.treatmentPlanWizardCouldNotSaveServices
import shifa.doctor.generated.resources.treatmentPlanWizardInitialPaymentAmount
import shifa.doctor.generated.resources.treatmentPlanWizardInitialPaymentAmountHint
import shifa.doctor.generated.resources.treatmentPlanWizardInitialPaymentExceedsTotal
import shifa.doctor.generated.resources.treatmentPlanWizardInitialPaymentFailed
import shifa.doctor.generated.resources.treatmentPlanWizardInitialPaymentInvalid
import shifa.doctor.generated.resources.treatmentPlanWizardInitialPaymentMemoDefault
import shifa.doctor.generated.resources.treatmentPlanWizardInitialPaymentSection
import shifa.doctor.generated.resources.treatmentPlanWizardInstallAmount
import shifa.doctor.generated.resources.treatmentPlanWizardInstallFailed
import shifa.doctor.generated.resources.treatmentPlanWizardInstallRemaining
import shifa.doctor.generated.resources.treatmentPlanWizardInstallRemove
import shifa.doctor.generated.resources.treatmentPlanWizardInstallTotal
import shifa.doctor.generated.resources.treatmentPlanWizardMembersError
import shifa.doctor.generated.resources.treatmentPlanWizardNeedTwoInstallments
import shifa.doctor.generated.resources.treatmentPlanWizardNoCatalog
import shifa.doctor.generated.resources.treatmentPlanWizardNoDoctors
import shifa.doctor.generated.resources.treatmentPlanWizardNoPatients
import shifa.doctor.generated.resources.treatmentPlanWizardNoServicesForDoctors
import shifa.doctor.generated.resources.treatmentPlanWizardPickServices
import shifa.doctor.generated.resources.treatmentPlanWizardReminderDay1
import shifa.doctor.generated.resources.treatmentPlanWizardReminderDays
import shifa.doctor.generated.resources.treatmentPlanWizardReminderDaysHelp
import shifa.doctor.generated.resources.treatmentPlanWizardReminderDaysN
import shifa.doctor.generated.resources.treatmentPlanWizardSearchPatient
import shifa.doctor.generated.resources.treatmentPlanWizardSectionBasics
import shifa.doctor.generated.resources.treatmentPlanWizardSectionPayment
import shifa.doctor.generated.resources.treatmentPlanWizardStep
import shifa.doctor.generated.resources.wizardActivate
import shifa.doctor.generated.resources.wizardActivateHint
import shifa.doctor.generated.resources.wizardAddService
import shifa.doctor.generated.resources.wizardCareTeamServices
import shifa.doctor.generated.resources.wizardInstallmentsSummary
import shifa.doctor.generated.resources.wizardLines
import shifa.doctor.generated.resources.wizardNone
import shifa.doctor.generated.resources.wizardPickDoctor
import shifa.doctor.generated.resources.wizardResume
import shifa.doctor.generated.resources.wizardScheduleOff
import shifa.doctor.generated.resources.wizardScheduleOk
import shifa.doctor.generated.resources.wizardService
import shifa.doctor.generated.resources.wizardServicesOtherCurrency
import shifa.doctor.generated.resources.wizardStatusFailed
import shifa.doctor.generated.resources.wizardTitleNew
import shifa.doctor.generated.resources.wizardTooth
import shifa.doctor.generated.resources.wizardToothInvalid
import shifa.doctor.generated.resources.wizardUseInstallments
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.ShellActions
import shifa.doctor.ui.navigation.ShellRoute
import shifa.doctor.ui.patients.Choice
import shifa.doctor.ui.profile.DiscardChangesSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ActionBar
import shifa.doctor.ui.widgets.FormSection
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDatePickerDialog
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaSwitchRow
import shifa.doctor.ui.widgets.Stepper
import shifa.doctor.utils.Money
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.tr
import kotlin.time.Duration.Companion.milliseconds

/** A planned service: one line of the plan, on a tooth (FDI) or for the whole mouth ([tooth] empty). */
data class WizardLine(
    val service: PlanServiceOption,
    val tooth: String = "",
)

/** An installment row as typed: its due date and amount. */
data class WizardInstallment(
    val due: LocalDate,
    val amount: String,
)

/** The save requests in order; the ones done are kept, so Retry goes on from the one that failed. */
enum class SaveStep { Plan, Lines, Payment, Installments, Status }

@androidx.compose.runtime.Stable
data class PlanWizardUiState(
    val step: Int = 1,
    // 2 when the patient came with the wizard (from their record): there is no patient step to go back to.
    val firstStep: Int = 1,
    val currency: String = "UZS",
    // Step 1: the clinic's patients searched on the server.
    val query: String = "",
    val matches: List<ClinicPatient>? = null,
    val searchError: CodeBanner? = null,
    val patient: ClinicPatient? = null,
    // Step 2.
    val title: String = "",
    val diagnosis: String = "",
    val notes: String = "",
    val reminderDays: Int = 3,
    // Step 3: the clinic's doctors (members with a doctor profile) and the plan services.
    val doctors: List<ClinicMember>? = null,
    val doctorsError: CodeBanner? = null,
    val picked: List<Long> = emptyList(),
    val services: List<PlanServiceOption>? = null,
    val servicesError: CodeBanner? = null,
    val serviceKey: String? = null,
    val tooth: String = "",
    val toothTried: Boolean = false,
    val lines: List<WizardLine> = emptyList(),
    // Step 4.
    val initial: String = "",
    val method: PayMethod = PayMethod.Cash,
    val useInstallments: Boolean = false,
    val installments: List<WizardInstallment> = emptyList(),
    // Step 5.
    val activate: Boolean = true,
    // The steps whose Next was pressed with something missing: their errors show.
    val tried: Set<Int> = emptySet(),
    val dirty: Boolean = false,
    val askDiscard: Boolean = false,
    val pickingDue: Int? = null,
    // Saving: the plan once created, the requests done, the one that failed.
    val busy: Boolean = false,
    val planId: Long? = null,
    val done: Set<SaveStep> = emptySet(),
    val error: CodeBanner? = null,
    val saved: Boolean = false,
) {
    /** The services the line picker offers: active, offered by a picked doctor (or all), in the plan's currency. */
    val offered: List<PlanServiceOption>
        get() =
            services.orEmpty().filter { s ->
                s.active &&
                    (picked.isEmpty() || s.offeredByDoctorIds.isEmpty() || s.offeredByDoctorIds.any { it in picked }) &&
                    (lines.isEmpty() || s.currency.equals(planCurrency, ignoreCase = true))
            }

    // A plan has one currency: its first line's (the clinic's until then).
    val planCurrency: String get() = lines.firstOrNull()?.service?.currency?.takeIf { it.isNotBlank() } ?: currency

    val total: Long get() = lines.sumOf { it.service.defaultPriceMinor }

    // Blank is no payment; null is not an amount.
    val initialMinor: Long? get() = if (initial.isBlank()) 0L else Money.parse(initial, planCurrency)

    val remaining: Long get() = (total - (initialMinor ?: 0L)).coerceAtLeast(0)

    val installmentsOn: Boolean get() = useInstallments && remaining > 0

    val scheduleSum: Long get() = installments.sumOf { Money.parse(it.amount, planCurrency) ?: 0L }

    val toothValid: Boolean get() = tooth.isEmpty() || FDI.matches(tooth)

    fun valid(step: Int): Boolean =
        when (step) {
            1 -> patient != null
            2 -> title.isNotBlank()
            3 -> picked.isNotEmpty() && lines.isNotEmpty()
            4 -> initialValid && (!installmentsOn || scheduleValid)
            else -> true
        }

    val initialValid: Boolean get() = initialMinor.let { it != null && it >= 0 && it <= total }

    // Two rows or more, each with an amount above 0, adding up to the remaining balance exactly (09 §4.2).
    val scheduleValid: Boolean
        get() =
            installments.size >= 2 &&
                installments.all { (Money.parse(it.amount, planCurrency) ?: 0L) > 0 } &&
                scheduleSum == remaining

    companion object {
        // FDI: permanent 11–48 (quadrants 1–4, teeth 1–8), primary 51–85 (quadrants 5–8, teeth 1–5).
        val FDI = Regex("^([1-4][1-8]|[5-8][1-5])$")
        const val STEPS = 5
        val REMINDER_DAYS = listOf(1, 2, 3, 5, 7, 14, 30)
    }
}

/**
 * The treatment plan wizard (TP-04, 09 §4.2, flow 09/15, board `DaClinic` plan wizard): patient → plan basics → care
 * team & services → payment → review, then a save that runs the backend's requests in order: the plan (DRAFT), its
 * lines, the initial payment, the installment schedule, and ACTIVE when chosen. The schedule is checked against the
 * balance before anything is sent (Flutter skipped a wrong one silently after the plan existed); a failed request
 * stops there with the plan kept as a draft, and Retry goes on from that request.
 */
class PlanWizardViewModel(
    private val clinicId: Long,
    patient: ClinicPatient?,
    currency: String?,
    private val today: LocalDate,
    private val ownDoctorId: Long?,
    // The desk payment's memo (Flutter's "Initial payment at desk"; the board has no memo field).
    private val initialMemo: String? = null,
    private val plans: TreatmentPlansRepository = treatmentPlansRepository,
    private val clinics: ClinicsRepository = clinicsRepository,
    private val finance: FinanceRepository = financeRepository,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            PlanWizardUiState(
                step = if (patient != null) 2 else 1,
                firstStep = if (patient != null) 2 else 1,
                patient = patient,
                currency = currency?.takeIf { it.isNotBlank() } ?: "UZS",
            ),
        )
    val state: StateFlow<PlanWizardUiState> = _state.asStateFlow()
    private var search: Job? = null

    init {
        loadDoctors()
        loadServices()
    }

    fun loadDoctors() {
        _state.update { it.copy(doctorsError = null) }
        viewModelScope.launch {
            clinics.members(clinicId).fold(
                { list ->
                    val doctors = list.filter { (it.doctorProfileId ?: 0) > 0 }.sortedBy { it.displayName.orEmpty() }
                    _state.update { s ->
                        // The signed-in doctor is on the team to start with (the board's), when a member.
                        val own = ownDoctorId?.takeIf { id -> doctors.any { it.doctorProfileId == id } }
                        s.copy(
                            doctors = doctors,
                            picked = if (s.picked.isEmpty() && own != null) listOf(own) else s.picked,
                        )
                    }
                },
                {
                        e ->
                    _state.update { it.copy(doctorsError = banner(e, Res.string.treatmentPlanWizardMembersError)) }
                },
            )
        }
    }

    fun loadServices() {
        _state.update { it.copy(servicesError = null) }
        viewModelScope.launch {
            plans.planServices(clinicId).fold(
                { list -> _state.update { it.copy(services = list) } },
                { e -> _state.update { it.copy(servicesError = banner(e, Res.string.failedToLoad)) } },
            )
        }
    }

    private fun edit(change: (PlanWizardUiState) -> PlanWizardUiState) =
        _state.update { s -> if (s.busy || s.planId != null) s else change(s).copy(dirty = true) }

    // --- Step 1 ---

    /** Typing searches the clinic's patients from 2 letters on, 250 ms after the last key (Flutter's). */
    fun search(text: String) {
        val q = text.trim()
        // Another query's rows never stay pickable while this one is read.
        edit { it.copy(query = text, matches = if (q == it.query.trim()) it.matches else null, searchError = null) }
        search?.cancel()
        if (q.length < 2) return
        search =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE)
                val result = clinics.patients(clinicId, 0, q, size = PATIENT_MATCHES)
                _state.update { s ->
                    if (s.query.trim() != q) {
                        s
                    } else {
                        result.fold(
                            { page -> s.copy(matches = page.rows) },
                            { e -> s.copy(searchError = banner(e, Res.string.failedToLoad)) },
                        )
                    }
                }
            }
    }

    fun pickPatient(p: ClinicPatient) = edit { it.copy(patient = p, step = 2) }

    // --- Step 2 ---

    fun basics(change: (PlanWizardUiState) -> PlanWizardUiState) = edit(change)

    // --- Step 3 ---

    /** A doctor off the team takes the lines only they offer (flow §7); with nobody picked the lines stay. */
    fun toggleDoctor(id: Long) =
        edit { s ->
            val picked = if (id in s.picked) s.picked - id else s.picked + id
            val lines =
                if (picked.isEmpty()) {
                    s.lines
                } else {
                    s.lines.filter {
                            l ->
                        l.service.offeredByDoctorIds.let { it.isEmpty() || it.any { d -> d in picked } }
                    }
                }
            s.copy(picked = picked, lines = lines)
        }

    fun pickService(key: String) = edit { it.copy(serviceKey = key) }

    fun tooth(text: String) = edit { it.copy(tooth = text.filter(Char::isDigit).take(2), toothTried = false) }

    fun addLine() =
        edit { s ->
            val service = s.offered.firstOrNull { it.key == s.serviceKey } ?: s.offered.firstOrNull()
            when {
                !s.toothValid -> s.copy(toothTried = true)
                service == null -> s
                else -> s.copy(lines = s.lines + WizardLine(service, s.tooth), tooth = "", toothTried = false)
            }
        }

    fun removeLine(index: Int) = edit { s -> s.copy(lines = s.lines.filterIndexed { i, _ -> i != index }) }

    // --- Step 4 ---

    fun payment(change: (PlanWizardUiState) -> PlanWizardUiState) = edit(change)

    /** On: two rows due in 30 and 60 days sharing the balance (the board's); off keeps the rows for later. */
    fun useInstallments(on: Boolean) =
        edit { s ->
            // Whole units of the currency (UZS has no fractions), the odd one on the first row.
            val unit = if (s.planCurrency.equals("UZS", ignoreCase = true)) 100L else 1L
            val half = (s.remaining / unit + 1) / 2 * unit

            val rows =
                s.installments.ifEmpty {
                    listOf(
                        WizardInstallment(today.plus(DatePeriod(days = 30)), priceText(half, s.planCurrency)),
                        WizardInstallment(
                            today.plus(DatePeriod(days = 60)),
                            priceText(s.remaining - half, s.planCurrency),
                        ),
                    )
                }
            s.copy(useInstallments = on, installments = if (on) rows else s.installments)
        }

    /** A new row, 30 days after the last, with what the schedule still lacks. */
    fun addInstallment() =
        edit { s ->
            val due = (s.installments.lastOrNull()?.due ?: today).plus(DatePeriod(days = 30))
            val left = (s.remaining - s.scheduleSum).coerceAtLeast(0)
            s.copy(installments = s.installments + WizardInstallment(due, priceText(left, s.planCurrency)))
        }

    fun editInstallment(
        index: Int,
        change: (WizardInstallment) -> WizardInstallment,
    ) = edit { s -> s.copy(installments = s.installments.mapIndexed { i, r -> if (i == index) change(r) else r }) }

    fun removeInstallment(index: Int) =
        edit { s ->
            if (s.installments.size <= 2) {
                s
            } else {
                s.copy(
                    installments =
                        s.installments.filterIndexed {
                                i,
                                _,
                            ->
                            i != index
                        },
                )
            }
        }

    fun pickDue(index: Int?) = _state.update { it.copy(pickingDue = index) }

    // --- Moving ---

    fun next() {
        val s = _state.value
        if (s.busy) return
        if (!s.valid(s.step)) return _state.update { it.copy(tried = it.tried + s.step) }
        if (s.step == PlanWizardUiState.STEPS) return save()
        _state.update { it.copy(step = it.step + 1) }
    }

    fun back() {
        val s = _state.value
        if (s.busy || s.planId != null || s.step <= s.firstStep) return
        _state.update { it.copy(step = it.step - 1) }
    }

    /** Close: true when the wizard may go. With input it asks first; once a draft exists it stays (and is listed). */
    fun close(): Boolean {
        val s = _state.value
        if (s.busy) return false
        // Only what was entered after the patient asks (the board's): a search alone just closes.
        if (s.dirty && s.step > 1 && s.planId == null && !s.askDiscard) {
            return false.also {
                _state.update {
                    it.copy(
                        askDiscard = true,
                    )
                }
            }
        }
        return true
    }

    fun keepEditing() = _state.update { it.copy(askDiscard = false) }

    fun dismiss() = _state.update { it.copy(error = null) }

    // --- Saving ---

    fun save() {
        val s = _state.value
        val patient = s.patient ?: return
        if (s.busy || (1..4).any { !s.valid(it) }) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            var done = s.done
            var planId = s.planId
            val currency = s.planCurrency

            suspend fun run(
                step: SaveStep,
                failed: StringResource,
                call: suspend () -> Result<*>,
            ): Boolean {
                if (step in done) return true
                val r = call()
                r.exceptionOrNull()?.let { e ->
                    _state.update { it.copy(busy = false, planId = planId, done = done, error = banner(e, failed)) }
                    return false
                }
                done = done + step
                return true
            }
            val created =
                run(SaveStep.Plan, Res.string.treatmentPlanWizardCouldNotCreatePlan) {
                    plans.create(
                        NewTreatmentPlan(
                            clinicId,
                            patient.patientId,
                            s.title.trim(),
                            s.diagnosis.trim().ifEmpty { null },
                            s.notes.trim().ifEmpty { null },
                            s.reminderDays,
                            s.picked.firstOrNull(),
                            s.picked,
                        ),
                    ).onSuccess { planId = it.id }
                }
            val id = planId
            if (!created || id == null) return@launch
            if (!run(SaveStep.Lines, Res.string.treatmentPlanWizardCouldNotSaveServices) {
                    plans.addLines(id, s.lineRequests())
                }
            ) {
                return@launch
            }
            val initial = s.initialMinor ?: 0L
            if (initial > 0 &&
                !run(SaveStep.Payment, Res.string.treatmentPlanWizardInitialPaymentFailed) {
                    finance.recordPayment(
                        clinicId,
                        RecordPayment(id, initial, currency, s.method.wire, memo = initialMemo),
                    )
                }
            ) {
                return@launch
            }
            if (s.installmentsOn &&
                !run(SaveStep.Installments, Res.string.treatmentPlanWizardInstallFailed) {
                    val rows = s.installments.sortedBy { it.due }
                    finance.createInstallmentPlan(
                        clinicId,
                        NewInstallmentPlan(
                            id,
                            s.remaining,
                            currency,
                            rows.size,
                            rows.first().due.toString(),
                            rows.map { InstallmentSpec(it.due.toString(), Money.parse(it.amount, currency) ?: 0L) },
                        ),
                    )
                }
            ) {
                return@launch
            }
            if (s.activate &&
                !run(SaveStep.Status, Res.string.wizardStatusFailed) { plans.setStatus(id, PlanStatus.Active.wire()) }
            ) {
                return@launch
            }
            _state.update { it.copy(busy = false, planId = id, done = done, saved = true) }
        }
    }

    private fun banner(
        e: Throwable,
        fallback: StringResource,
    ) = CodeBanner(e.toUserMessage(fallback), requestId = e.requestId)

    companion object {
        val SEARCH_DEBOUNCE = 250.milliseconds
        const val PATIENT_MATCHES = 40
    }
}

/** The plan's lines, in the order added: each on its tooth (`{fdi, dentition, source}`), or with no tooth. */
internal fun PlanWizardUiState.lineRequests(): List<PlanLineRequest> =
    lines.mapIndexed { i, l ->
        PlanLineRequest(
            catalogItemId = l.service.catalogItemId,
            title = l.service.title,
            unitPriceMinor = l.service.defaultPriceMinor,
            discountMinor = 0,
            currency = planCurrency,
            sortOrder = i,
            specialtyMetadata =
                l.tooth.takeIf { it.isNotEmpty() }?.let { t ->
                    buildJsonObject {
                        put("fdi", t)
                        put("dentition", if (t.first() >= '5') "primary" else "permanent")
                        put("source", "PLAN_WIZARD")
                    }.toString()
                },
        )
    }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlanWizardScreen(
    clinicId: Long,
    opened: Long,
    patientId: Long?,
    patientName: String?,
    actions: ShellActions,
) {
    val clinics by clinicsRepository.state.collectAsStateWithLifecycle()
    val clinic = clinics.clinics.firstOrNull { it.clinicId == clinicId }
    val today = PracticeClock(clinic?.timeZone).today()
    val memo = tr(Res.string.treatmentPlanWizardInitialPaymentMemoDefault)
    val viewModel =
        viewModel(key = "plan-wizard-$clinicId-$opened") {
            PlanWizardViewModel(
                clinicId,
                patientId?.let { ClinicPatient(it, patientName) },
                clinic?.currency,
                today,
                profileRepository.me.value?.profile?.id,
                memo,
            )
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        val id = state.planId
        if (state.saved && id != null) {
            actions.back()
            actions.push(ShellRoute.PlanDetail(id, clinicId))
        }
    }
    // Closing after a partial save shows the draft that was kept (payments and status can be finished there).
    val close = {
        if (viewModel.close()) {
            val draft = viewModel.state.value.planId
            actions.back()
            if (draft != null) actions.push(ShellRoute.PlanDetail(draft, clinicId))
        }
    }
    BackHandler(onBack = close)
    PlanWizardContent(
        state,
        today,
        PlanWizardActions(
            close = close,
            next = viewModel::next,
            back = viewModel::back,
            search = viewModel::search,
            pickPatient = viewModel::pickPatient,
            basics = viewModel::basics,
            retryDoctors = viewModel::loadDoctors,
            retryServices = viewModel::loadServices,
            toggleDoctor = viewModel::toggleDoctor,
            pickService = viewModel::pickService,
            tooth = viewModel::tooth,
            addLine = viewModel::addLine,
            removeLine = viewModel::removeLine,
            payment = viewModel::payment,
            useInstallments = viewModel::useInstallments,
            addInstallment = viewModel::addInstallment,
            editInstallment = viewModel::editInstallment,
            removeInstallment = viewModel::removeInstallment,
            pickDue = viewModel::pickDue,
            dismiss = viewModel::dismiss,
        ),
    )
    if (state.askDiscard) DiscardChangesSheet(onDiscard = actions.back, onKeep = viewModel::keepEditing)
}

class PlanWizardActions(
    val close: () -> Unit = {},
    val next: () -> Unit = {},
    val back: () -> Unit = {},
    val search: (String) -> Unit = {},
    val pickPatient: (ClinicPatient) -> Unit = {},
    val basics: ((PlanWizardUiState) -> PlanWizardUiState) -> Unit = {},
    val retryDoctors: () -> Unit = {},
    val retryServices: () -> Unit = {},
    val toggleDoctor: (Long) -> Unit = {},
    val pickService: (String) -> Unit = {},
    val tooth: (String) -> Unit = {},
    val addLine: () -> Unit = {},
    val removeLine: (Int) -> Unit = {},
    val payment: ((PlanWizardUiState) -> PlanWizardUiState) -> Unit = {},
    val useInstallments: (Boolean) -> Unit = {},
    val addInstallment: () -> Unit = {},
    val editInstallment: (Int, (WizardInstallment) -> WizardInstallment) -> Unit = { _, _ -> },
    val removeInstallment: (Int) -> Unit = {},
    val pickDue: (Int?) -> Unit = {},
    val dismiss: () -> Unit = {},
)

private fun stepTitle(step: Int): StringResource =
    when (step) {
        1 -> Res.string.patient
        2 -> Res.string.treatmentPlanWizardSectionBasics
        3 -> Res.string.wizardCareTeamServices
        4 -> Res.string.treatmentPlanWizardSectionPayment
        else -> Res.string.review
    }

@Composable
private fun reminderLabel(days: Int): String =
    if (days == 1) {
        tr(
            Res.string.treatmentPlanWizardReminderDay1,
        )
    } else {
        tr(Res.string.treatmentPlanWizardReminderDaysN, days)
    }

/** Board `DaClinic` plan wizard (phone): the X, the stepper, the step's form and the Back · Next footer. */
@Composable
fun PlanWizardContent(
    state: PlanWizardUiState,
    today: LocalDate,
    actions: PlanWizardActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).imePadding()) {
    ShifaSubHeader(tr(Res.string.wizardTitleNew), Modifier.statusBarsPadding(), onBack = actions.close)
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .wrapContentWidth()
            .widthIn(max = ShifaDimens.Layout.FormWide)
            .padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    ) {
        Stepper(
            tr(Res.string.treatmentPlanWizardStep, state.step, PlanWizardUiState.STEPS),
            tr(stepTitle(state.step)),
            state.step - 1,
            PlanWizardUiState.STEPS,
        )
        state.error?.let { e ->
            codeBanner(e) { actions.dismiss() }?.invoke()
            if (state.planId != null) {
                Text(tr(Res.string.wizardResume), style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
            }
        }
        val tried = state.step in state.tried
        when (state.step) {
            1 -> PatientStep(state, actions)
            2 -> BasicsStep(state, tried, actions)
            3 -> TeamStep(state, tried, actions)
            4 -> PaymentStep(state, tried, actions)
            else -> ReviewStep(state, actions)
        }
    }
    val last = state.step == PlanWizardUiState.STEPS
    ActionBar(
        secondary =
            if (state.step > state.firstStep && state.planId == null) {
                { m -> ShifaSecondaryButton(tr(Res.string.back), actions.back, m, enabled = !state.busy) }
            } else {
                null
            },
    ) { m ->
        ShifaPrimaryButton(
            tr(
                when {
                    !last -> Res.string.next
                    state.planId != null -> Res.string.retry
                    else -> Res.string.save
                },
            ),
            actions.next,
            m,
            loading = state.busy,
        )
    }
    state.pickingDue?.let { i ->
        val row = state.installments.getOrNull(i)
        if (row != null) {
            val first = today.minus(DatePeriod(days = 1))
            ShifaDatePickerDialog(row.due, first.year..first.year + DUE_YEARS, { picked ->
                if (picked != null) actions.editInstallment(i) { it.copy(due = picked) }
                actions.pickDue(null)
            }, selectable = { it >= first })
        }
    }
}

@Composable
private fun ColumnScope.PatientStep(
    state: PlanWizardUiState,
    actions: PlanWizardActions,
) {
    ShifaInputField(
        state.query,
        actions.search,
        tr(Res.string.treatmentPlanWizardSearchPatient),
        Modifier.fillMaxWidth(),
    )
    state.searchError?.let { codeBanner(it)?.invoke() }
    val matches = state.matches

    if (matches != null && matches.isEmpty()) {
        Text(
            tr(Res.string.treatmentPlanWizardNoPatients),
            style = ShifaTextStyles.caption(),
            color = ShifaColors.Text.Secondary,
        )
    }
    matches.orEmpty().forEach { p ->
        ShifaSettingsItem(
            p.fullName ?: "#${p.patientId}",
            { actions.pickPatient(p) },
            subtitle = (p.phone?.let(PhoneE164::display) ?: p.email)?.takeIf(String::isNotBlank),
        )
    }
}

@Composable
private fun BasicsStep(
    state: PlanWizardUiState,
    tried: Boolean,
    actions: PlanWizardActions,
) = FormSection(
    state.patient?.let {
        it.fullName ?: "#${it.patientId}"
    } ?: tr(Res.string.treatmentPlanWizardSectionBasics),
) {
    val noTitle = tried && state.title.isBlank()
    ShifaInputField(
        state.title,
        { v -> actions.basics { it.copy(title = v.take(TITLE_CHARS)) } },
        tr(Res.string.treatmentPlanTitle) + " *",
        Modifier.fillMaxWidth(),
        supportingText =
            if (noTitle) {
                tr(
                    Res.string.treatmentPlanTitleRequired,
                )
            } else {
                tr(Res.string.treatmentPlanTitleHint)
            },
        isError = noTitle,
    )
    ShifaInputField(
        state.diagnosis,
        { v -> actions.basics { it.copy(diagnosis = v.take(TEXT_CHARS)) } },
        tr(Res.string.treatmentPlanDiagnosis),
        Modifier.fillMaxWidth(),
        supportingText = tr(Res.string.treatmentPlanDiagnosisHint),
        multiline = true,
        areaMinHeight = ShifaSize.S56,
    )
    ShifaInputField(
        state.notes,
        { v -> actions.basics { it.copy(notes = v.take(TEXT_CHARS)) } },
        tr(Res.string.treatmentPlanNotes),
        Modifier.fillMaxWidth(),
        supportingText = tr(Res.string.treatmentPlanNotesHint),
        multiline = true,
        areaMinHeight = ShifaSize.S56,
    )
    val options = PlanWizardUiState.REMINDER_DAYS
    val labels = options.map { reminderLabel(it) }
    Choice(tr(Res.string.treatmentPlanWizardReminderDays), labels, options.indexOf(state.reminderDays), true) { i ->
        actions.basics { it.copy(reminderDays = options[i]) }
    }
    Text(
        tr(Res.string.treatmentPlanWizardReminderDaysHelp),
        style = ShifaTextStyles.caption(),
        color = ShifaColors.Text.Secondary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TeamStep(
    state: PlanWizardUiState,
    tried: Boolean,
    actions: PlanWizardActions,
) {
    FormSection(tr(Res.string.doctors)) {
        val doctors = state.doctors
        when {
            state.doctorsError != null ->
                ShifaLoadError(
                    tr(state.doctorsError.text),
                    actions.retryDoctors,
                    requestId = state.doctorsError.requestId,
                    compact = true,
                )
            doctors == null -> ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S4))
            doctors.isEmpty() -> Hint(tr(Res.string.treatmentPlanWizardNoDoctors))
            else ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                ) {
                    doctors.forEach { d ->
                        val id = d.doctorProfileId ?: return@forEach
                        ShifaChip(d.displayName ?: "#$id", id in state.picked, { actions.toggleDoctor(id) })
                    }
                }
        }
        if (tried && state.picked.isEmpty()) Error(tr(Res.string.wizardPickDoctor))
    }
    FormSection(tr(Res.string.services)) {
        val all = state.services
        val offered = state.offered
        when {
            state.servicesError != null ->
                ShifaLoadError(
                    tr(state.servicesError.text),
                    actions.retryServices,
                    requestId = state.servicesError.requestId,
                    compact = true,
                )
            all == null -> ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S4))
            all.none { it.active } -> Hint(tr(Res.string.treatmentPlanWizardNoCatalog))
            offered.isEmpty() && state.lines.isEmpty() -> Hint(tr(Res.string.treatmentPlanWizardNoServicesForDoctors))
            else -> {
                val selected = offered.indexOfFirst { it.key == state.serviceKey }.coerceAtLeast(0)
                Choice(
                    tr(Res.string.wizardService),
                    offered.map { "${it.title} · ${Money.format(it.defaultPriceMinor, it.currency)}" },
                    selected,
                    offered.isNotEmpty(),
                ) { i -> actions.pickService(offered[i].key) }
                val badTooth = state.toothTried && !state.toothValid
                ShifaInputField(
                    state.tooth,
                    actions.tooth,
                    tr(Res.string.wizardTooth),
                    Modifier.fillMaxWidth(),
                    supportingText = if (badTooth) tr(Res.string.wizardToothInvalid) else null,
                    isError = badTooth,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ShifaSecondaryButton(
                    tr(Res.string.wizardAddService),
                    actions.addLine,
                    icon = Res.drawable.ic_plus_16,
                    size = ShifaButtonSize.Compact,
                    enabled = offered.isNotEmpty(),
                )
                if (state.lines.isNotEmpty() &&
                    state.services.orEmpty().any {
                        it.active && !it.currency.equals(state.planCurrency, ignoreCase = true)
                    }
                ) {
                    Hint(tr(Res.string.wizardServicesOtherCurrency, state.planCurrency))
                }
            }
        }
        val remove = tr(Res.string.remove)
        state.lines.forEachIndexed { i, l ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            ) {
                Text(
                    l.service.title + (if (l.tooth.isNotEmpty()) " · ${l.tooth}" else ""),
                    Modifier.weight(1f),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Primary,
                )
                Text(
                    Money.format(l.service.defaultPriceMinor, l.service.currency),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Secondary,
                )
                ShifaIconButton(
                    Res.drawable.ic_trash_16,
                    remove,
                    { actions.removeLine(i) },
                    variant = ShifaIconButtonVariant.Ghost,
                )
            }
        }
        if (tried && state.lines.isEmpty()) Error(tr(Res.string.treatmentPlanWizardPickServices))
        Total(tr(Res.string.treatmentPlanWizardInstallTotal), Money.format(state.total, state.planCurrency))
    }
}

@Composable
private fun ColumnScope.PaymentStep(
    state: PlanWizardUiState,
    tried: Boolean,
    actions: PlanWizardActions,
) {
    val currency = state.planCurrency
    FormSection(tr(Res.string.treatmentPlanWizardInitialPaymentSection)) {
        Total(tr(Res.string.treatmentPlanWizardInstallTotal), Money.format(state.total, currency))
        val bad = !state.initialValid
        ShifaInputField(
            state.initial,
            {
                    v ->
                actions.payment {
                    it.copy(
                        initial =
                            v.filter {
                                    c ->
                                c.isDigit() || c in " .,"
                            }.take(AMOUNT_CHARS),
                    )
                }
            },
            "${tr(Res.string.treatmentPlanWizardInitialPaymentAmount)} ($currency)",
            Modifier.fillMaxWidth(),
            supportingText =
                when {
                    state.initialMinor == null -> tr(Res.string.treatmentPlanWizardInitialPaymentInvalid)
                    bad -> tr(Res.string.treatmentPlanWizardInitialPaymentExceedsTotal)
                    else -> tr(Res.string.treatmentPlanWizardInitialPaymentAmountHint)
                },
            isError = bad,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if ((state.initialMinor ?: 0) > 0) {
            MethodChips(
                state.method,
                true,
            ) { m -> actions.payment { it.copy(method = m) } }
        }
        Total(tr(Res.string.treatmentPlanWizardInstallRemaining), Money.format(state.remaining, currency))
    }
    if (state.remaining > 0) {
        FormSection(tr(Res.string.clinicFinanceInstallments)) {
            ShifaSwitchRow(tr(Res.string.wizardUseInstallments), state.useInstallments, actions.useInstallments)
            if (state.useInstallments) Schedule(state, tried, actions)
        }
    }
}

@Composable
private fun ColumnScope.Schedule(
    state: PlanWizardUiState,
    tried: Boolean,
    actions: PlanWizardActions,
) {
    val currency = state.planCurrency
    val remove = tr(Res.string.treatmentPlanWizardInstallRemove)
    state.installments.forEachIndexed { i, row ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            Text("#${i + 1}", style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Secondary)
            ShifaSecondaryButton(
                shifa.doctor.utils.displayDate(row.due),
                { actions.pickDue(i) },
                Modifier.weight(1f),
                icon = Res.drawable.ic_calendar_16,
                size = ShifaButtonSize.Compact,
            )
            ShifaInputField(
                row.amount,
                {
                        v ->
                    actions.editInstallment(i) {
                        it.copy(
                            amount =
                                v.filter {
                                        c ->
                                    c.isDigit() || c in " .,"
                                }.take(AMOUNT_CHARS),
                        )
                    }
                },
                "${tr(Res.string.treatmentPlanWizardInstallAmount)} ($currency)",
                Modifier.weight(1f),
                isError = tried && (Money.parse(row.amount, currency) ?: 0L) <= 0,
                clearable = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (state.installments.size > 2) {
                ShifaIconButton(Res.drawable.ic_trash_16, remove, {
                    actions.removeInstallment(i)
                }, variant = ShifaIconButtonVariant.Ghost)
            }
        }
    }
    ShifaLink(tr(Res.string.treatmentPlanWizardAddRow), actions.addInstallment)
    val ok = state.scheduleSum == state.remaining
    Text(
        if (ok) {
            tr(Res.string.wizardScheduleOk, Money.format(state.remaining, currency))
        } else {
            tr(
                Res.string.wizardScheduleOff,
                Money.format(state.scheduleSum, currency),
                Money.format(state.remaining, currency),
            )
        },
        style = ShifaTextStyles.caption(),
        color = if (ok) ShifaColors.Text.Brand else ShifaColors.Text.Error,
    )
    // The sum line above says when only the total is off.
    if (tried && (
            state.installments.size < 2 ||
                state.installments.any {
                    (Money.parse(it.amount, currency) ?: 0L) <= 0
                }
        )
    ) {
        Error(tr(Res.string.treatmentPlanWizardNeedTwoInstallments))
    }
}

@Composable
private fun ReviewStep(
    state: PlanWizardUiState,
    actions: PlanWizardActions,
) = FormSection(tr(Res.string.review)) {
    val currency = state.planCurrency
    val names =
        state.picked.map { id -> state.doctors?.firstOrNull { it.doctorProfileId == id }?.displayName ?: "#$id" }
    val rows =
        listOf(
            tr(Res.string.patient) to (state.patient?.fullName ?: state.patient?.let { "#${it.patientId}" } ?: "—"),
            tr(Res.string.treatmentPlanTitle) to state.title.trim(),
            tr(Res.string.doctors) to names.joinToString(", "),
            tr(
                Res.string.services,
            ) to tr(Res.string.wizardLines, state.lines.size, Money.format(state.total, currency)),
            tr(Res.string.treatmentPlanWizardInitialPaymentSection) to Money.format(state.initialMinor ?: 0, currency),
            tr(Res.string.clinicFinanceInstallments) to
                if (state.installmentsOn) {
                    tr(
                        Res.string.wizardInstallmentsSummary,
                        state.installments.size,
                        Money.format(state.remaining, currency),
                    )
                } else {
                    tr(Res.string.wizardNone)
                },
            tr(Res.string.treatmentPlanWizardReminderDays) to reminderLabel(state.reminderDays),
        )
    rows.forEach { (label, value) ->
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
            Text(
                label,
                Modifier.weight(KEY_WEIGHT),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Secondary,
            )
            Text(value, Modifier.weight(1f), style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
        }
    }
    Column {
        ShifaSwitchRow(
            tr(Res.string.wizardActivate),
            state.activate,
            { v -> actions.payment { it.copy(activate = v) } },
        )
        Text(tr(Res.string.wizardActivateHint), style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)

@Composable
private fun Error(text: String) = Text(text, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Error)

@Composable
private fun Total(
    label: String,
    value: String,
) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
    Text(label, Modifier.weight(1f), style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Secondary)
    Text(value, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
}

private const val TITLE_CHARS = 200
private const val TEXT_CHARS = 2000
private const val AMOUNT_CHARS = 20
private const val DUE_YEARS = 5
private const val KEY_WEIGHT = 0.6f
