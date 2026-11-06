package shifa.doctor.ui.clinic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
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
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.finance.FinanceRepository
import shifa.doctor.data.finance.RecordPayment
import shifa.doctor.data.finance.financeRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.LineStatus
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.model.PlanStatus
import shifa.doctor.data.model.PlanVisit
import shifa.doctor.data.model.TreatmentPlanDetail
import shifa.doctor.data.model.TreatmentPlanSummary
import shifa.doctor.data.model.mayManageFinance
import shifa.doctor.data.treatmentplans.TreatmentPlanPdf
import shifa.doctor.data.treatmentplans.TreatmentPlanPdfData
import shifa.doctor.data.treatmentplans.TreatmentPlanPdfLabels
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.data.treatmentplans.treatmentPlansRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.active
import shifa.doctor.generated.resources.appointmentStatusCancelled
import shifa.doctor.generated.resources.appointmentStatusCompleted
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.clinicChangeStatus
import shifa.doctor.generated.resources.clinicFinanceInstallStatusCancelled
import shifa.doctor.generated.resources.clinicFinanceInstallStatusOverdue
import shifa.doctor.generated.resources.clinicFinanceInstallStatusPaid
import shifa.doctor.generated.resources.clinicFinanceInstallStatusPending
import shifa.doctor.generated.resources.clinicFinanceInstallStatusUpdated
import shifa.doctor.generated.resources.clinicFinanceInstallStatusWaived
import shifa.doctor.generated.resources.clinicFinanceInstallments
import shifa.doctor.generated.resources.clinicFinanceInvalidAmount
import shifa.doctor.generated.resources.clinicFinanceOutstanding
import shifa.doctor.generated.resources.clinicFinancePaymentAmountLabel
import shifa.doctor.generated.resources.clinicFinancePaymentConfirm
import shifa.doctor.generated.resources.clinicFinancePaymentDialogTitle
import shifa.doctor.generated.resources.clinicFinancePaymentFailed
import shifa.doctor.generated.resources.clinicFinancePaymentMemoLabel
import shifa.doctor.generated.resources.clinicFinancePaymentRecorded
import shifa.doctor.generated.resources.clinicTreatmentPlanDetailDentalChart
import shifa.doctor.generated.resources.clinicTreatmentPlanDetailNotFound
import shifa.doctor.generated.resources.clinicTreatmentPlanDetailTitle
import shifa.doctor.generated.resources.clinicTreatmentPlanExportPdf
import shifa.doctor.generated.resources.clinicTreatmentPlanExportPdfFailed
import shifa.doctor.generated.resources.clinicTreatmentPlanExportPdfPreparing
import shifa.doctor.generated.resources.clinicTreatmentPlanLineStatusPlanned
import shifa.doctor.generated.resources.clinicTreatmentPlanLineStatusScheduled
import shifa.doctor.generated.resources.clinicTreatmentPlansAll
import shifa.doctor.generated.resources.clinicTreatmentPlansDoctor
import shifa.doctor.generated.resources.clinicTreatmentPlansFilterHint
import shifa.doctor.generated.resources.clinicTreatmentPlansNew
import shifa.doctor.generated.resources.clinicTreatmentPlansNoVisits
import shifa.doctor.generated.resources.clinicTreatmentPlansPaid
import shifa.doctor.generated.resources.clinicTreatmentPlansPatient
import shifa.doctor.generated.resources.clinicTreatmentPlansTotal
import shifa.doctor.generated.resources.clinicTreatmentPlansUntitled
import shifa.doctor.generated.resources.clinicTreatmentPlansUpdated
import shifa.doctor.generated.resources.clinicTreatmentPlansVisitCount
import shifa.doctor.generated.resources.clinicTreatmentPlansVisits
import shifa.doctor.generated.resources.dentalGeneralServicesShort
import shifa.doctor.generated.resources.diagnosis
import shifa.doctor.generated.resources.draft
import shifa.doctor.generated.resources.ic_plus_16
import shifa.doctor.generated.resources.notes
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.patientId
import shifa.doctor.generated.resources.planCancelBody
import shifa.doctor.generated.resources.planCancelTitle
import shifa.doctor.generated.resources.planLineTooth
import shifa.doctor.generated.resources.planLoadFailed
import shifa.doctor.generated.resources.planNoInstallments
import shifa.doctor.generated.resources.planNoLines
import shifa.doctor.generated.resources.planOpen
import shifa.doctor.generated.resources.planOptions
import shifa.doctor.generated.resources.planProgress
import shifa.doctor.generated.resources.planStatusFailed
import shifa.doctor.generated.resources.planStatusOnHold
import shifa.doctor.generated.resources.planVisitsFailed
import shifa.doctor.generated.resources.plansLoadFailed
import shifa.doctor.generated.resources.plansNoneMatch
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.services
import shifa.doctor.generated.resources.statusInProgress
import shifa.doctor.generated.resources.tpPdfAcknowledgement
import shifa.doctor.generated.resources.tpPdfAcknowledgementNote
import shifa.doctor.generated.resources.tpPdfAmountDue
import shifa.doctor.generated.resources.tpPdfCreated
import shifa.doctor.generated.resources.tpPdfDate
import shifa.doctor.generated.resources.tpPdfDiscount
import shifa.doctor.generated.resources.tpPdfDoctors
import shifa.doctor.generated.resources.tpPdfDueDate
import shifa.doctor.generated.resources.tpPdfFooter
import shifa.doctor.generated.resources.tpPdfGenerated
import shifa.doctor.generated.resources.tpPdfInstallments
import shifa.doctor.generated.resources.tpPdfLineStatus
import shifa.doctor.generated.resources.tpPdfLineTotal
import shifa.doctor.generated.resources.tpPdfNotes
import shifa.doctor.generated.resources.tpPdfOtherServices
import shifa.doctor.generated.resources.tpPdfPage
import shifa.doctor.generated.resources.tpPdfPartStatus
import shifa.doctor.generated.resources.tpPdfPatientSignature
import shifa.doctor.generated.resources.tpPdfPlanId
import shifa.doctor.generated.resources.tpPdfProcedures
import shifa.doctor.generated.resources.tpPdfQty
import shifa.doctor.generated.resources.tpPdfSchedule
import shifa.doctor.generated.resources.tpPdfStatus
import shifa.doctor.generated.resources.tpPdfSummary
import shifa.doctor.generated.resources.tpPdfSymptoms
import shifa.doctor.generated.resources.tpPdfTitle
import shifa.doctor.generated.resources.tpPdfTooth
import shifa.doctor.generated.resources.tpPdfUnitPrice
import shifa.doctor.generated.resources.tpPdfUpdated
import shifa.doctor.generated.resources.treatmentPlanTitle
import shifa.doctor.generated.resources.wizardService
import shifa.doctor.ui.calendar.CalendarScope
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.ShellActions
import shifa.doctor.ui.navigation.ShellRoute
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DentalChart
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaActionButton
import shifa.doctor.ui.widgets.ShifaActionVariant
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.StatusBadge
import shifa.doctor.ui.widgets.StatusTone
import shifa.doctor.ui.widgets.ToothMark
import shifa.doctor.ui.widgets.ToothState
import shifa.doctor.ui.widgets.TreatmentPlanCard
import shifa.doctor.utils.Money
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.rememberFileShare
import shifa.doctor.utils.tr
import kotlin.time.Duration.Companion.milliseconds

// The statuses a person picks (09 §4.5), in the board's chip order; Unknown is never offered.
internal val PLAN_STATUSES =
    listOf(
        PlanStatus.Active,
        PlanStatus.InProgress,
        PlanStatus.OnHold,
        PlanStatus.Draft,
        PlanStatus.Completed,
        PlanStatus.Cancelled,
    )

internal fun PlanStatus.wire(): String =
    when (this) {
        PlanStatus.Draft -> "DRAFT"
        PlanStatus.Active -> "ACTIVE"
        PlanStatus.InProgress -> "IN_PROGRESS"
        PlanStatus.OnHold -> "ON_HOLD"
        PlanStatus.Completed -> "COMPLETED"
        PlanStatus.Cancelled -> "CANCELLED"
        PlanStatus.Unknown -> ""
    }

internal fun planStatusLabel(s: PlanStatus): StringResource =
    when (s) {
        PlanStatus.Draft -> Res.string.draft
        PlanStatus.Active -> Res.string.active
        PlanStatus.InProgress -> Res.string.statusInProgress
        PlanStatus.OnHold -> Res.string.planStatusOnHold
        PlanStatus.Completed -> Res.string.appointmentStatusCompleted
        PlanStatus.Cancelled -> Res.string.appointmentStatusCancelled
        PlanStatus.Unknown -> Res.string.draft
    }

/** The board's tones: draft and completed muted, active brand, in progress info, on hold warning, cancelled error. */
internal fun planStatusTone(s: PlanStatus): StatusTone =
    when (s) {
        PlanStatus.Active -> StatusTone.Brand
        PlanStatus.InProgress -> StatusTone.Info
        PlanStatus.OnHold -> StatusTone.Warn
        PlanStatus.Cancelled -> StatusTone.Err
        else -> StatusTone.Muted
    }

/** The status sheet: the plan's status (cancelling asks first, flow 09/17), in the list and the detail. */
data class StatusSheet(
    val plan: TreatmentPlanSummary,
    val confirmCancel: Boolean = false,
)

data class ClinicPlansUiState(
    val typed: String = "",
    // Null for All, else the status chip.
    val status: PlanStatus? = null,
    val plans: List<TreatmentPlanSummary>? = null,
    val error: CodeBanner? = null,
    // Expanded plans and their visits (read on first open; a failure is kept per plan).
    val open: Set<Long> = emptySet(),
    val visits: Map<Long, List<PlanVisit>> = emptyMap(),
    val visitsFailed: Set<Long> = emptySet(),
    val statusSheet: StatusSheet? = null,
    val busy: Boolean = false,
    val banner: CodeBanner? = null,
)

/**
 * The clinic's treatment plans (TP-02, 09 §4.1, flow 09/14, board `DaClinic` Plans): searched (`q`) and filtered by
 * status on the server, each with its visits on demand and its status menu. An answer for an older search is dropped.
 */
class ClinicPlansViewModel(
    private val clinicId: Long,
    private val repo: TreatmentPlansRepository = treatmentPlansRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClinicPlansUiState())
    val state: StateFlow<ClinicPlansUiState> = _state.asStateFlow()
    private var generation = 0
    private var debounce: Job? = null

    init {
        load()
    }

    /** The list (also on return and after a change): open plans' visits read again, closed ones forgotten. */
    fun load() {
        val gen = ++generation
        val s = _state.value
        _state.update { it.copy(error = null, visits = it.visits.filterKeys { id -> id in it.open }) }
        viewModelScope.launch {
            repo.plans(clinicId, null, s.status?.wire(), q = s.typed).fold(
                { list -> if (gen == generation) _state.update { it.copy(plans = list) } },
                {
                        e ->
                    if (gen == generation) _state.update { it.copy(error = failure(e, Res.string.plansLoadFailed)) }
                },
            )
        }
        s.open.forEach { loadVisits(it) }
    }

    fun type(text: String) {
        _state.update { it.copy(typed = text) }
        debounce?.cancel()
        debounce =
            viewModelScope.launch {
                delay(DEBOUNCE)
                load()
            }
    }

    fun status(s: PlanStatus?) {
        _state.update { it.copy(status = s, plans = null) }
        load()
    }

    /** Expands a plan's visits (read once; Retry after a failure reads again). */
    fun toggle(plan: TreatmentPlanSummary) {
        val open = plan.id in _state.value.open
        _state.update { it.copy(open = if (open) it.open - plan.id else it.open + plan.id) }
        if (!open && (plan.id !in _state.value.visits || plan.id in _state.value.visitsFailed)) loadVisits(plan.id)
    }

    fun loadVisits(planId: Long) {
        _state.update { it.copy(visitsFailed = it.visitsFailed - planId) }
        viewModelScope.launch {
            repo.visits(planId).fold(
                { v -> _state.update { it.copy(visits = it.visits + (planId to v)) } },
                { _state.update { it.copy(visitsFailed = it.visitsFailed + planId) } },
            )
        }
    }

    fun statusSheet(sheet: StatusSheet?) = _state.update { if (it.busy) it else it.copy(statusSheet = sheet) }

    /** A status from the menu: Cancelled asks first; the list reads again after (the plan may leave the filter). */
    fun setStatus(status: PlanStatus) {
        val sheet = _state.value.statusSheet ?: return
        if (status == sheet.plan.status) return statusSheet(null)
        if (status == PlanStatus.Cancelled && !sheet.confirmCancel) {
            return _state.update {
                it.copy(statusSheet = sheet.copy(confirmCancel = true))
            }
        }
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, banner = null) }
        viewModelScope.launch {
            repo.setStatus(sheet.plan.id, status.wire()).fold(
                {
                    _state.update {
                        it.copy(
                            busy = false,
                            statusSheet = null,
                            banner = CodeBanner(Res.string.clinicFinanceInstallStatusUpdated, error = false),
                        )
                    }
                    load()
                },
                {
                        e ->
                    _state.update {
                        it.copy(
                            busy = false,
                            statusSheet = null,
                            banner = failure(e, Res.string.planStatusFailed),
                        )
                    }
                },
            )
        }
    }

    fun dismiss() = _state.update { it.copy(banner = null) }

    private fun failure(
        e: Throwable,
        fallback: StringResource,
    ) = CodeBanner(e.toUserMessage(fallback), requestId = e.requestId)

    companion object {
        val DEBOUNCE = 350.milliseconds
    }
}

/** What the plans list can do (the section wires them; previews leave them empty). */
class PlansActions(
    val type: (String) -> Unit = {},
    val status: (PlanStatus?) -> Unit = {},
    val load: () -> Unit = {},
    val toggle: (TreatmentPlanSummary) -> Unit = {},
    val loadVisits: (Long) -> Unit = {},
    val statusSheet: (StatusSheet?) -> Unit = {},
    val setStatus: (PlanStatus) -> Unit = {},
    val dismiss: () -> Unit = {},
    val openPlan: (Long) -> Unit = {},
    val openVisit: (PlanVisit) -> Unit = {},
    val newPlan: (() -> Unit)? = null,
)

/** A plan's visit opened as an appointment of its doctor's calendar, in the clinic's zone (quirk 6). */
internal fun ShellActions.openPlanVisit(
    visit: PlanVisit,
    plan: TreatmentPlanSummary?,
    clinic: MyClinic,
) {
    val start = PracticeClock.parseInstant(visit.startAt) ?: return
    val end = PracticeClock.parseInstant(visit.endAt) ?: start
    val entry =
        CalendarEntry.Appointment(
            start, end, visit.appointmentId, plan?.patientId, plan?.patientName, null, visit.status,
            visit.visitPaymentStatus, visit.services.filterNotNull().joinToString(", "), visit.location,
        )
    val scope =
        visit.doctorProfileId?.let {
            CalendarScope(it, visit.doctorName, clinic.timeZone, clinic.address, clinic.clinicId)
        }
    push(ShellRoute.Appointment(entry, shifa.doctor.utils.currentInstant().toEpochMilliseconds(), scope))
}

@Composable
internal fun ColumnScope.PlansSection(
    clinic: MyClinic,
    actions: ShellActions,
    viewModel: ClinicPlansViewModel =
        viewModel(key = "clinic-plans-${clinic.clinicId}") { ClinicPlansViewModel(clinic.clinicId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from a plan or a visit: read again (a status or a payment may have changed it).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    PlansContent(
        state,
        clinic,
        PlansActions(
            viewModel::type, viewModel::status, viewModel::load, viewModel::toggle, viewModel::loadVisits,
            viewModel::statusSheet, viewModel::setStatus, viewModel::dismiss,
            openPlan = { actions.push(ShellRoute.PlanDetail(it, clinic.clinicId)) },
            openVisit = {
                    v ->
                actions.openPlanVisit(
                    v,
                    state.plans?.firstOrNull {
                            p ->
                        state.visits[p.id]?.contains(v) == true
                    },
                    clinic,
                )
            },
            newPlan = {
                actions.push(
                    ShellRoute.PlanWizard(clinic.clinicId, shifa.doctor.utils.currentInstant().toEpochMilliseconds()),
                )
            },
        ),
    )
}

/** Board `DaClinic` Plans (phone): search, New plan, status chips, a card per plan with its visits and status menu. */
@Composable
internal fun ColumnScope.PlansContent(
    state: ClinicPlansUiState,
    clinic: MyClinic,
    actions: PlansActions,
) {
    codeBanner(state.banner) { actions.dismiss() }?.let { it() }
    ShifaInputField(state.typed, actions.type, tr(Res.string.clinicTreatmentPlansFilterHint), Modifier.fillMaxWidth())
    actions.newPlan?.let {
        ShifaPrimaryButton(
            tr(Res.string.clinicTreatmentPlansNew),
            it,
            icon = Res.drawable.ic_plus_16,
            size = ShifaButtonSize.Compact,
        )
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        ShifaChip(tr(Res.string.clinicTreatmentPlansAll), state.status == null, { actions.status(null) })
        PLAN_STATUSES.forEach { s -> ShifaChip(tr(planStatusLabel(s)), state.status == s, { actions.status(s) }) }
    }
    state.error?.let { e ->
        codeBanner(e)?.let { it() }
        ShifaLink(tr(Res.string.retry), actions.load)
    }
    val plans = state.plans
    when {
        plans == null && state.error != null -> Unit
        plans == null ->
            repeat(
                SKELETON_ROWS,
            ) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S36)) }
        plans.isEmpty() ->
            Text(
                tr(Res.string.plansNoneMatch),
                Modifier.fillMaxWidth().background(
                    ShifaColors.Bg.Surface,
                    ShifaRadius.ControlLg,
                ).padding(ShifaSize.S24),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Secondary,
            )
        else -> plans.forEach { p -> PlanCard(p, state, clinic, actions) }
    }
    state.statusSheet?.let { StatusSheetView(it, state.busy, actions.statusSheet, actions.setStatus) }
}

@Composable
private fun PlanCard(
    p: TreatmentPlanSummary,
    state: ClinicPlansUiState,
    clinic: MyClinic,
    actions: PlansActions,
) {
    val open = p.id in state.open
    TreatmentPlanCard(
        p.title?.takeIf(String::isNotBlank) ?: tr(Res.string.clinicTreatmentPlansUntitled),
        listOfNotNull(p.patientName, doctorsOf(p)).joinToString(" · "),
        if (p.linesTotalCount > 0) {
            tr(
                Res.string.planProgress,
                p.linesCompletedCount.toString(),
                p.linesTotalCount.toString(),
            )
        } else {
            tr(Res.string.planNoLines)
        },
        if (p.linesTotalCount > 0) p.linesCompletedCount.toFloat() / p.linesTotalCount else 0f,
        "${tr(Res.string.clinicTreatmentPlansPaid)} ${Money.format(p.paidMinor, p.currency)}",
        "${tr(Res.string.clinicTreatmentPlansTotal)} ${Money.format(p.totalMinor, p.currency)}",
        owed =
            p.owedMinor.takeIf {
                it > 0
            }?.let { "${tr(Res.string.clinicFinanceOutstanding)} ${Money.format(it, p.currency)}" },
        status = {
            StatusBadge(tr(planStatusLabel(p.status)), tone = planStatusTone(p.status), onClick = {
                actions.statusSheet(StatusSheet(p))
            })
        },
        actions = {
            ShifaActionButton(
                { actions.toggle(p) },
                text = tr(Res.string.clinicTreatmentPlansVisitCount, p.visitCount.toString()),
                variant = ShifaActionVariant.Secondary,
                selected = open,
            )
            ShifaActionButton({ actions.openPlan(p.id) }, text = tr(Res.string.planOpen))
        },
    )
    if (open) {
        VisitList(state.visits[p.id], p.id in state.visitsFailed, clinic, {
            actions.loadVisits(p.id)
        }, actions.openVisit)
    }
}

@Composable
private fun doctorsOf(p: TreatmentPlanSummary): String? =
    p.attendingDoctors.mapNotNull {
        it.name.takeIf(
            String::isNotBlank,
        )
    }.ifEmpty { listOfNotNull(p.attendingDoctorName) }
        .joinToString(", ").ifEmpty { null }

/** A plan's visits: date · time (the clinic's zone) · services, its status; cancelled ones can't be opened. */
@Composable
private fun VisitList(
    visits: List<PlanVisit>?,
    failed: Boolean,
    clinic: MyClinic,
    retry: () -> Unit,
    open: (PlanVisit) -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    when {
        failed && visits == null -> {
            Text(tr(Res.string.planVisitsFailed), style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Error)
            ShifaLink(tr(Res.string.retry), retry)
        }
        visits == null -> ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S16))
        visits.isEmpty() ->
            Text(
                tr(Res.string.clinicTreatmentPlansNoVisits),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Secondary,
            )
        else -> {
            val clock = PracticeClock(clinic.timeZone)
            visits.forEach { v ->
                val at = PracticeClock.parseInstant(v.startAt)?.let { clock.local(it) }
                val cancelled = v.timing.equals("CANCELLED", true) || v.status.equals("CANCELLED", true)
                Row(
                    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd)
                        .then(if (!cancelled) Modifier.clickable(role = Role.Button) { open(v) } else Modifier)
                        .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S10),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    Text(
                        listOfNotNull(
                            at?.let {
                                "${displayDate(it.date)} · ${displayTime(it.time)}"
                            },
                            v.doctorName,
                            v.services.filterNotNull().joinToString(", ").ifEmpty { null },
                        )
                            .joinToString(" · "),
                        Modifier.weight(1f),
                        style = ShifaTextStyles.bodyText(),
                        color = if (cancelled) ShifaColors.Text.Tertiary else ShifaColors.Text.Primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(visitStatus(v.status), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
                }
            }
        }
    }
}

@Composable
private fun visitStatus(raw: String?): String = shifa.doctor.ui.calendar.status(raw)?.first ?: raw.orEmpty()

/** The status menu (the board's sheet): the six statuses, the current one marked; Cancelled asks first. */
@Composable
internal fun StatusSheetView(
    sheet: StatusSheet,
    busy: Boolean,
    close: (StatusSheet?) -> Unit,
    pick: (PlanStatus) -> Unit,
) = ProfileSheet({ close(null) }, confirmHide = { !busy }) {
    if (sheet.confirmCancel) {
        ShifaDialogSheet(
            tr(Res.string.planCancelTitle),
            tr(planStatusLabel(PlanStatus.Cancelled)),
            { pick(PlanStatus.Cancelled) },
            body = tr(Res.string.planCancelBody),
            tone = DialogTone.Destructive,
            cancelText = tr(Res.string.cancel),
            onCancel = { close(null) },
            confirmLoading = busy,
        )
    } else {
        ShifaTray(tr(Res.string.clinicChangeStatus), { close(null) }) {
            PLAN_STATUSES.forEachIndexed { i, s ->
                ShifaSettingsItem(
                    tr(planStatusLabel(s)),
                    { if (!busy) pick(s) },
                    selected = s == sheet.plan.status,
                    destructive = s == PlanStatus.Cancelled,
                    divider = i < PLAN_STATUSES.lastIndex,
                )
            }
        }
    }
}

// --- TP-03: the plan's detail ---

/** A desk payment on the plan (board "Record payment"): amount (default the outstanding), method, memo. */
data class PlanPaySheet(
    val amount: String,
    val method: PayMethod = PayMethod.Cash,
    val memo: String = "",
    val tried: Boolean = false,
    val busy: Boolean = false,
    val error: CodeBanner? = null,
)

data class PlanDetailUiState(
    val detail: TreatmentPlanDetail? = null,
    val visits: List<PlanVisit>? = null,
    val visitsFailed: Boolean = false,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val error: CodeBanner? = null,
    val statusSheet: StatusSheet? = null,
    val menu: Boolean = false,
    val pay: PlanPaySheet? = null,
    val busy: Boolean = false,
    val banner: CodeBanner? = null,
    // TP-05: the PDF being made, then the file for the share sheet.
    val exporting: Boolean = false,
    val pdf: PlanPdfFile? = null,
)

/** A made plan PDF for the share sheet (compared by identity: each export is shared once). */
class PlanPdfFile(
    val name: String,
    val bytes: ByteArray,
)

/**
 * A treatment plan (TP-03, 09 §4.3, flow 09/16, board `DaClinic` plan detail): overview with the status menu and the
 * patient, Record payment, the dental chart when lines have teeth, the lines, the visits and the installment schedule.
 * Read again on every return (quirk 10: Flutter's cached detail went stale after a payment).
 */
class PlanDetailViewModel(
    private val planId: Long,
    private val clinicId: Long,
    private val repo: TreatmentPlansRepository = treatmentPlansRepository,
    private val finance: FinanceRepository = financeRepository,
    private val makePdf: (TreatmentPlanPdfData, TreatmentPlanPdfLabels) -> ByteArray? = { d, l ->
        TreatmentPlanPdf().pdf(d, l)
    },
) : ViewModel() {
    private val _state = MutableStateFlow(PlanDetailUiState())
    val state: StateFlow<PlanDetailUiState> = _state.asStateFlow()

    // A newer read (after a payment or a status change) wins over an older one still on its way.
    private var generation = 0

    fun load() {
        val gen = ++generation
        _state.update { it.copy(loading = it.detail == null, error = null, visitsFailed = false) }
        viewModelScope.launch {
            val visits = repo.visits(planId)
            if (gen == generation) {
                _state.update {
                    it.copy(visits = visits.getOrNull() ?: it.visits, visitsFailed = visits.isFailure)
                }
            }
        }
        viewModelScope.launch {
            val result = repo.detail(planId)
            if (gen != generation) return@launch
            result.fold(
                { d -> _state.update { it.copy(detail = d, loading = false, notFound = false) } },
                { e ->
                    _state.update {
                        if (e is ApiException.NotFound) {
                            it.copy(loading = false, notFound = true)
                        } else {
                            it.copy(
                                loading = false,
                                error = CodeBanner(e.toUserMessage(Res.string.planLoadFailed), requestId = e.requestId),
                            )
                        }
                    }
                },
            )
        }
    }

    fun menu(open: Boolean) = _state.update { it.copy(menu = open) }

    fun statusSheet(sheet: StatusSheet?) =
        _state.update {
            if (it.busy) {
                it
            } else {
                it.copy(
                    statusSheet = sheet,
                    menu = false,
                )
            }
        }

    fun setStatus(status: PlanStatus) {
        val sheet = _state.value.statusSheet ?: return
        if (status == sheet.plan.status) return statusSheet(null)
        if (status == PlanStatus.Cancelled && !sheet.confirmCancel) {
            return _state.update {
                it.copy(statusSheet = sheet.copy(confirmCancel = true))
            }
        }
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, banner = null) }
        viewModelScope.launch {
            repo.setStatus(planId, status.wire()).fold(
                {
                    _state.update {
                        it.copy(
                            busy = false,
                            statusSheet = null,
                            banner = CodeBanner(Res.string.clinicFinanceInstallStatusUpdated, error = false),
                        )
                    }
                    load()
                },
                { e ->
                    _state.update {
                        it.copy(
                            busy = false,
                            statusSheet = null,
                            banner = CodeBanner(e.toUserMessage(Res.string.planStatusFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    fun openPay() {
        val p = _state.value.detail?.summary ?: return
        _state.update { it.copy(menu = false, pay = PlanPaySheet(priceText(p.owedMinor.coerceAtLeast(0), p.currency))) }
    }

    fun editPay(change: (PlanPaySheet) -> PlanPaySheet) =
        _state.update { s ->
            s.copy(
                pay =
                    s.pay?.let {
                        change(it).copy(error = null)
                    },
            )
        }

    fun closePay() = _state.update { if (it.pay?.busy == true) it else it.copy(pay = null) }

    fun savePay() {
        val sheet = _state.value.pay ?: return
        val plan = _state.value.detail?.summary ?: return
        if (sheet.busy) return
        val amount =
            Money.parse(sheet.amount, plan.currency)?.takeIf {
                it > 0
            } ?: return _state.update { it.copy(pay = sheet.copy(tried = true)) }
        _state.update { it.copy(pay = sheet.copy(tried = true, busy = true)) }
        viewModelScope.launch {
            finance.recordPayment(
                clinicId,
                RecordPayment(
                    planId,
                    amount,
                    plan.currency,
                    sheet.method.wire,
                    memo =
                        sheet.memo.trim().ifEmpty {
                            null
                        },
                ),
            ).fold(
                {
                    _state.update {
                        it.copy(
                            pay = null,
                            banner = CodeBanner(Res.string.clinicFinancePaymentRecorded, error = false),
                        )
                    }
                    load()
                },
                { e ->
                    _state.update { s ->
                        s.copy(
                            pay =
                                s.pay?.copy(
                                    busy = false,
                                    error =
                                        CodeBanner(
                                            e.toUserMessage(Res.string.clinicFinancePaymentFailed),
                                            requestId = e.requestId,
                                        ),
                                ),
                        )
                    }
                },
            )
        }
    }

    /**
     * Export PDF (TP-05): the plan as it was last read, made in the app (BE-01), named
     * `treatment_plan_<id>_<yyyy-MM-dd>.pdf` (Flutter's), then handed to the share sheet ([pdfShared] after).
     */
    fun exportPdf(
        data: (TreatmentPlanDetail) -> TreatmentPlanPdfData,
        labels: TreatmentPlanPdfLabels,
        day: String,
    ) {
        val detail = _state.value.detail ?: return
        if (_state.value.exporting) return
        _state.update { it.copy(menu = false, exporting = true, banner = null) }
        viewModelScope.launch {
            // Let "Preparing PDF…" draw before the pages are laid out.
            yield()
            val bytes = runCatching { makePdf(data(detail), labels) }.getOrNull()?.takeIf { it.isNotEmpty() }
            _state.update {
                it.copy(
                    exporting = false,
                    pdf = bytes?.let { b -> PlanPdfFile("treatment_plan_${planId}_$day.pdf", b) },
                    banner = if (bytes == null) CodeBanner(Res.string.clinicTreatmentPlanExportPdfFailed) else null,
                )
            }
        }
    }

    /** The share sheet took the file, or couldn't open ([opened] false says so). */
    fun pdfShared(opened: Boolean) =
        _state.update {
            it.copy(
                pdf = null,
                banner = if (opened) it.banner else CodeBanner(Res.string.clinicTreatmentPlanExportPdfFailed),
            )
        }

    fun dismiss() = _state.update { it.copy(banner = null) }
}

@Composable
fun PlanDetailScreen(
    planId: Long,
    clinicId: Long,
    actions: ShellActions,
    viewModel: PlanDetailViewModel = viewModel(key = "plan-$planId") { PlanDetailViewModel(planId, clinicId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clinics by clinicsRepository.state.collectAsStateWithLifecycle()
    val clinic = clinics.clinics.firstOrNull { it.clinicId == clinicId } ?: MyClinic(clinicId)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    val share = rememberFileShare()
    state.pdf?.let { file ->
        LaunchedEffect(file) { viewModel.pdfShared(share(file.name, "application/pdf", file.bytes)) }
    }
    val pdfLabels = planPdfLabels()
    val doctorsText = state.detail?.summary?.let { doctorsOf(it) }
    PlanDetailContent(
        state,
        clinic,
        PlanDetailActions(
            back = actions.back,
            load = viewModel::load,
            menu = viewModel::menu,
            statusSheet = viewModel::statusSheet,
            setStatus = viewModel::setStatus,
            openPay = viewModel::openPay,
            editPay = viewModel::editPay,
            savePay = viewModel::savePay,
            closePay = viewModel::closePay,
            dismiss = viewModel::dismiss,
            openPatient = { actions.push(ShellRoute.Patient(it, clinicId)) },
            openVisit = { v -> actions.openPlanVisit(v, state.detail?.summary, clinic) },
            exportPdf = {
                val clock = PracticeClock(clinic.timeZone)
                val now = clock.now()
                val day = { raw: String? ->
                    raw?.let(PracticeClock::parseInstant)?.let { displayDate(clock.local(it).date) }
                }
                viewModel.exportPdf(
                    { d ->
                        TreatmentPlanPdfData(
                            d,
                            clinic.name.takeIf { it.isNotBlank() },
                            "${displayDate(now.date)} ${twoDigits(now.hour)}:${twoDigits(now.minute)}",
                            day(d.summary.createdAt),
                            day(d.summary.updatedAt),
                            doctorsText,
                        )
                    },
                    pdfLabels,
                    now.date.toString(),
                )
            },
        ),
    )
}

private fun twoDigits(n: Int) = n.toString().padStart(2, '0')

/** The PDF's words in the app's language (TP-05). */
@Composable
private fun planPdfLabels(): TreatmentPlanPdfLabels {
    val planStatuses = PlanStatus.entries.associateWith { tr(planStatusLabel(it)) }
    val lineStatuses = LineStatus.entries.associateWith { tr(lineStatusLabel(it)) }
    val installmentStatuses =
        listOf("PENDING", "PAID", "OVERDUE", "WAIVED", "CANCELLED").associateWith { tr(installmentLabelOf(it)) }
    val planId = tr(Res.string.tpPdfPlanId, "{id}")
    val generated = tr(Res.string.tpPdfGenerated, "{date}")
    val tooth = tr(Res.string.tpPdfTooth, "{fdi}")
    val schedule = tr(Res.string.tpPdfSchedule, "{id}", "{status}", "{total}")
    val page = tr(Res.string.tpPdfPage, "{page}", "{pages}")
    return TreatmentPlanPdfLabels(
        title = tr(Res.string.tpPdfTitle),
        planId = { planId.replace("{id}", "$it") },
        generated = { generated.replace("{date}", it) },
        summary = tr(Res.string.tpPdfSummary),
        patient = tr(Res.string.patient),
        patientId = tr(Res.string.patientId),
        planTitle = tr(Res.string.treatmentPlanTitle),
        diagnosis = tr(Res.string.diagnosis),
        created = tr(Res.string.tpPdfCreated),
        updated = tr(Res.string.tpPdfUpdated),
        doctors = tr(Res.string.tpPdfDoctors),
        status = tr(Res.string.tpPdfStatus),
        total = tr(Res.string.clinicTreatmentPlansTotal),
        paid = tr(Res.string.clinicTreatmentPlansPaid),
        outstanding = tr(Res.string.clinicFinanceOutstanding),
        symptoms = tr(Res.string.tpPdfSymptoms),
        notes = tr(Res.string.tpPdfNotes),
        procedures = tr(Res.string.tpPdfProcedures),
        tooth = { tooth.replace("{fdi}", it) },
        otherServices = tr(Res.string.tpPdfOtherServices),
        service = tr(Res.string.wizardService),
        quantity = tr(Res.string.tpPdfQty),
        unitPrice = tr(Res.string.tpPdfUnitPrice),
        discount = tr(Res.string.tpPdfDiscount),
        lineTotal = tr(Res.string.tpPdfLineTotal),
        lineStatus = tr(Res.string.tpPdfLineStatus),
        installments = tr(Res.string.tpPdfInstallments),
        schedule = { id, status, total ->
            schedule.replace("{id}", "$id").replace("{status}", status).replace("{total}", total)
        },
        dueDate = tr(Res.string.tpPdfDueDate),
        amountDue = tr(Res.string.tpPdfAmountDue),
        partStatus = tr(Res.string.tpPdfPartStatus),
        acknowledgement = tr(Res.string.tpPdfAcknowledgement),
        acknowledgementNote = tr(Res.string.tpPdfAcknowledgementNote),
        patientSignature = tr(Res.string.tpPdfPatientSignature),
        date = tr(Res.string.tpPdfDate),
        footer = tr(Res.string.tpPdfFooter),
        page = { p, n -> page.replace("{page}", "$p").replace("{pages}", "$n") },
        planStatus = { planStatuses.getValue(it) },
        lineStatusOf = { lineStatuses.getValue(it) },
        installmentStatus = { installmentStatuses[it.uppercase()] ?: installmentStatuses.getValue("PENDING") },
        // A schedule is ACTIVE, COMPLETED or CANCELLED (the plan's words); anything else as sent.
        scheduleStatus = {
                raw ->
            PlanStatus.of(raw).takeIf { it != PlanStatus.Unknown }?.let(planStatuses::getValue) ?: raw
        },
    )
}

class PlanDetailActions(
    val back: () -> Unit = {},
    val load: () -> Unit = {},
    val menu: (Boolean) -> Unit = {},
    val statusSheet: (StatusSheet?) -> Unit = {},
    val setStatus: (PlanStatus) -> Unit = {},
    val openPay: () -> Unit = {},
    val editPay: ((PlanPaySheet) -> PlanPaySheet) -> Unit = {},
    val savePay: () -> Unit = {},
    val closePay: () -> Unit = {},
    val dismiss: () -> Unit = {},
    val openPatient: (Long) -> Unit = {},
    val openVisit: (PlanVisit) -> Unit = {},
    val exportPdf: () -> Unit = {},
)

@Composable
fun PlanDetailContent(
    state: PlanDetailUiState,
    clinic: MyClinic,
    actions: PlanDetailActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    val plan = state.detail?.summary
    ShifaSubHeader(
        plan?.let {
            "#${it.id} · " + (
                it.title?.takeIf(
                    String::isNotBlank,
                ) ?: tr(Res.string.clinicTreatmentPlansUntitled)
            )
        }
            ?: tr(Res.string.clinicTreatmentPlanDetailTitle),
        Modifier.statusBarsPadding(),
        onBack = actions.back,
        onMore = plan?.let { { actions.menu(true) } },
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
    ) {
        codeBanner(state.banner) { actions.dismiss() }?.let { it() }
        if (state.exporting) {
            Text(
                tr(Res.string.clinicTreatmentPlanExportPdfPreparing),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Secondary,
            )
        }
        state.error?.let { e ->
            codeBanner(e)?.let { it() }
            ShifaLink(tr(Res.string.retry), actions.load)
        }
        val detail = state.detail
        when {
            state.notFound ->
                Text(
                    tr(Res.string.clinicTreatmentPlanDetailNotFound),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                )
            detail == null && state.loading ->
                repeat(3) {
                    ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S36))
                }
            detail != null -> PlanBody(detail, state, clinic, actions)
        }
    }
    state.statusSheet?.let { StatusSheetView(it, state.busy, actions.statusSheet, actions.setStatus) }
    if (state.menu && plan != null) PlanMenu(plan, clinic, actions)
    state.pay?.let { sheet -> plan?.let { PlanPayView(sheet, it, actions) } }
}

@Composable
private fun ColumnScope.PlanBody(
    detail: TreatmentPlanDetail,
    state: PlanDetailUiState,
    clinic: MyClinic,
    actions: PlanDetailActions,
) {
    val p = detail.summary
    val zone = clinic.timeZone
    Section {
        Text(
            p.title?.takeIf(String::isNotBlank) ?: tr(Res.string.clinicTreatmentPlansUntitled),
            style = ShifaTextStyles.sectionHeading(),
            color = ShifaColors.Text.Primary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBadge(tr(planStatusLabel(p.status)), tone = planStatusTone(p.status), onClick = {
                actions.statusSheet(StatusSheet(p))
            })
            if (p.linesTotalCount > 0) {
                Text(
                    tr(Res.string.planProgress, p.linesCompletedCount.toString(), p.linesTotalCount.toString()),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
        p.patientId?.let { id ->
            KeyValue(
                tr(Res.string.clinicTreatmentPlansPatient),
            ) { ShifaLink(p.patientName ?: "#$id", { actions.openPatient(id) }) }
        }
        doctorsOf(p)?.let { KeyValue(tr(Res.string.clinicTreatmentPlansDoctor), it) }
        KeyValue(tr(Res.string.diagnosis), p.diagnosis?.takeIf(String::isNotBlank) ?: "—")
        KeyValue(tr(Res.string.notes), p.notes?.takeIf(String::isNotBlank) ?: "—")
        KeyValue(tr(Res.string.clinicTreatmentPlansTotal), Money.format(p.totalMinor, p.currency))
        KeyValue(tr(Res.string.clinicTreatmentPlansPaid), Money.format(p.paidMinor, p.currency))
        KeyValue(
            tr(Res.string.clinicFinanceOutstanding),
            Money.format(p.owedMinor, p.currency),
            if (p.owedMinor > 0) ShifaColors.Text.Error else ShifaColors.Text.Primary,
        )
        (p.updatedAt ?: p.createdAt)?.let { raw ->
            PracticeClock.parseInstant(raw)?.let {
                KeyValue(tr(Res.string.clinicTreatmentPlansUpdated), displayDate(PracticeClock(zone).local(it).date))
            }
        }
        if (clinic.mayManageFinance && p.owedMinor > 0 && p.status != PlanStatus.Cancelled) {
            ShifaPrimaryButton(tr(Res.string.clinicFinancePaymentDialogTitle), actions.openPay, Modifier.fillMaxWidth())
        }
    }
    // The chart when lines carry teeth (read-only: done, part done or planned per tooth).
    val teeth =
        detail.lines.filter { it.status != LineStatus.Cancelled }.mapNotNull {
                l ->
            l.tooth?.toIntOrNull()?.let { it to l }
        }.groupBy({ it.first }, { it.second })
    if (teeth.isNotEmpty()) {
        Section(tr(Res.string.clinicTreatmentPlanDetailDentalChart)) {
            DentalChart(
                teeth.mapValues { (_, lines) ->
                    val done = lines.count { it.status == LineStatus.Completed }
                    ToothMark(
                        if (done == lines.size) {
                            ToothState.Done
                        } else if (done > 0) {
                            ToothState.Partial
                        } else {
                            ToothState.Planned
                        },
                        lines.size,
                    )
                },
            )
        }
    }
    Section(tr(Res.string.services)) {
        if (detail.lines.isEmpty()) {
            Text(
                tr(Res.string.planNoLines),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Secondary,
            )
        }
        detail.lines.forEach { l ->
            val cancelled = l.status == LineStatus.Cancelled
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        l.title,
                        style = ShifaTextStyles.panelTitle(),
                        color = if (cancelled) ShifaColors.Text.Tertiary else ShifaColors.Text.Primary,
                        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                    )
                    Text(
                        (
                            l.tooth?.let {
                                tr(
                                    Res.string.planLineTooth,
                                    it,
                                )
                            } ?: tr(Res.string.dentalGeneralServicesShort)
                        ) + " · × ${l.quantity}",
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Secondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Money.format(l.lineTotalMinor, l.currency),
                        style = ShifaTextStyles.buttonCompact(),
                        color = ShifaColors.Text.Primary,
                    )
                    StatusBadge(tr(lineStatusLabel(l.status)), tone = lineStatusTone(l.status))
                }
            }
        }
    }
    Section(tr(Res.string.clinicTreatmentPlansVisits)) {
        val visits = state.visits
        when {
            visits == null && state.visitsFailed -> {
                Text(
                    tr(Res.string.planVisitsFailed),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Error,
                )
                ShifaLink(tr(Res.string.retry), actions.load)
            }
            visits == null -> ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S16))
            visits.isEmpty() ->
                Text(
                    tr(Res.string.clinicTreatmentPlansNoVisits),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                )
            else -> VisitList(visits, false, clinic, {}, actions.openVisit)
        }
    }
    Section(tr(Res.string.clinicFinanceInstallments)) {
        val items = detail.installmentPlans.flatMap { it.items }.sortedBy { it.sequenceNumber }
        if (items.isEmpty()) {
            Text(
                tr(Res.string.planNoInstallments),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Secondary,
            )
        }
        items.forEach { i ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "#${i.sequenceNumber} · " +
                        runCatching {
                            displayDate(kotlinx.datetime.LocalDate.parse(i.dueDate.take(10)))
                        }.getOrDefault(i.dueDate),
                    Modifier.weight(1f),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Primary,
                )
                Text(
                    Money.format(i.amountMinor, i.currency),
                    style = ShifaTextStyles.buttonCompact(),
                    color = ShifaColors.Text.Primary,
                )
                StatusBadge(tr(installmentLabelOf(i.status)), tone = installmentTone(i.status))
            }
        }
    }
}

private fun lineStatusLabel(s: LineStatus): StringResource =
    when (s) {
        LineStatus.Scheduled -> Res.string.clinicTreatmentPlanLineStatusScheduled
        LineStatus.InProgress -> Res.string.statusInProgress
        LineStatus.Completed -> Res.string.appointmentStatusCompleted
        LineStatus.Cancelled -> Res.string.appointmentStatusCancelled
        else -> Res.string.clinicTreatmentPlanLineStatusPlanned
    }

private fun lineStatusTone(s: LineStatus): StatusTone =
    when (s) {
        LineStatus.Completed -> StatusTone.Brand
        LineStatus.Cancelled -> StatusTone.Err
        LineStatus.InProgress, LineStatus.Scheduled -> StatusTone.Info
        else -> StatusTone.Muted
    }

private fun installmentLabelOf(status: String): StringResource =
    when (status.uppercase()) {
        "PAID" -> Res.string.clinicFinanceInstallStatusPaid
        "OVERDUE" -> Res.string.clinicFinanceInstallStatusOverdue
        "WAIVED" -> Res.string.clinicFinanceInstallStatusWaived
        "CANCELLED" -> Res.string.clinicFinanceInstallStatusCancelled
        else -> Res.string.clinicFinanceInstallStatusPending
    }

private fun installmentTone(status: String): StatusTone =
    when (status.uppercase()) {
        "PAID" -> StatusTone.Brand
        "OVERDUE" -> StatusTone.Err
        "WAIVED" -> StatusTone.Info
        "CANCELLED" -> StatusTone.Muted
        else -> StatusTone.Warn
    }

@Composable
private fun Section(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    title?.let { Text(it, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary) }
    content()
}

@Composable
private fun KeyValue(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = ShifaColors.Text.Primary,
) = KeyValue(label) { Text(value, style = ShifaTextStyles.bodyText(), color = color) }

@Composable
private fun KeyValue(
    label: String,
    value: @Composable () -> Unit,
) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
    Text(label, Modifier.weight(KEY_WEIGHT), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    Column(Modifier.weight(1f)) { value() }
}

@Composable
private fun PlanMenu(
    plan: TreatmentPlanSummary,
    clinic: MyClinic,
    actions: PlanDetailActions,
) = ProfileSheet({ actions.menu(false) }) {
    ShifaTray(tr(Res.string.planOptions), { actions.menu(false) }) {
        ShifaSettingsItem(tr(Res.string.clinicChangeStatus), {
            actions.statusSheet(StatusSheet(plan))
        })
        val pay = clinic.mayManageFinance && plan.owedMinor > 0 && plan.status != PlanStatus.Cancelled
        ShifaSettingsItem(tr(Res.string.clinicTreatmentPlanExportPdf), actions.exportPdf, divider = pay)
        if (pay) {
            ShifaSettingsItem(tr(Res.string.clinicFinancePaymentDialogTitle), actions.openPay, divider = false)
        }
    }
}

@Composable
private fun PlanPayView(
    sheet: PlanPaySheet,
    plan: TreatmentPlanSummary,
    actions: PlanDetailActions,
) = ProfileSheet({ actions.closePay() }, confirmHide = { !sheet.busy }) {
    ShifaDialogSheet(
        tr(Res.string.clinicFinancePaymentDialogTitle),
        tr(Res.string.clinicFinancePaymentConfirm),
        actions.savePay,
        body = listOfNotNull(plan.patientName, plan.title).joinToString(" · "),
        cancelText = tr(Res.string.cancel),
        onCancel = actions.closePay,
        confirmLoading = sheet.busy,
    ) {
        val bad = sheet.tried && Money.parse(sheet.amount, plan.currency)?.takeIf { it > 0 } == null
        ShifaInputField(
            sheet.amount,
            {
                    v ->
                actions.editPay {
                    it.copy(
                        amount =
                            v.filter {
                                    c ->
                                c.isDigit() || c in " .,"
                            }.take(AMOUNT_CHARS),
                    )
                }
            },
            "${tr(Res.string.clinicFinancePaymentAmountLabel)} (${plan.currency})",
            Modifier.fillMaxWidth(),
            supportingText = if (bad) tr(Res.string.clinicFinanceInvalidAmount) else null,
            isError = bad,
            enabled = !sheet.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        MethodChips(sheet.method, !sheet.busy) { m -> actions.editPay { it.copy(method = m) } }
        ShifaInputField(
            sheet.memo,
            { v -> actions.editPay { it.copy(memo = v.take(MEMO_CHARS)) } },
            tr(Res.string.clinicFinancePaymentMemoLabel),
            Modifier.fillMaxWidth(),
            enabled = !sheet.busy,
        )
        sheet.error?.let { e -> codeBanner(e)?.let { it() } }
    }
}

private const val KEY_WEIGHT = 0.6f
private const val AMOUNT_CHARS = 20
private const val MEMO_CHARS = 500
