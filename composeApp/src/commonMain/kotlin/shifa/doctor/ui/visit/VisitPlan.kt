package shifa.doctor.ui.visit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.appointments.appointmentsRepository
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.finance.FinanceRepository
import shifa.doctor.data.finance.RecordPayment
import shifa.doctor.data.finance.financeRepository
import shifa.doctor.data.forms.DentalChartCodec
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.DentalRecord
import shifa.doctor.data.model.FulfillmentCandidate
import shifa.doctor.data.model.LineStatus
import shifa.doctor.data.model.PlanLine
import shifa.doctor.data.model.TreatmentPlanDetail
import shifa.doctor.data.model.TreatmentPlanSummary
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.data.treatmentplans.treatmentPlansRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appointmentPlanApplyFailed
import shifa.doctor.generated.resources.appointmentPlanLoadFailed
import shifa.doctor.generated.resources.appointmentPlanPaymentFailed
import shifa.doctor.generated.resources.plansLoadFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.utils.Money

/** How the patient paid at the chair (`RecordPaymentRequest.method`). */
enum class PayMethod(
    val wire: String,
) {
    Cash("CASH"),
    Card("CARD_EXTERNAL"),
    Transfer("TRANSFER"),
}

/** A payment sent: the matching ledger rows (this visit, plan, amount, method) before it, to recognise it after. */
data class PaymentAttempt(
    val minor: Long,
    val method: PayMethod,
    val rowsBefore: Int,
)

data class VisitPlanState(
    val clinicId: Long? = null,
    // The patient's active comprehensive plans in the clinic; null until read (or without a clinic).
    val plans: List<TreatmentPlanSummary>? = null,
    val loadError: CodeBanner? = null,
    // The plan the appointment is already part of.
    val linkedId: Long? = null,
    val selectedId: Long? = null,
    val detail: TreatmentPlanDetail? = null,
    val candidates: List<FulfillmentCandidate>? = null,
    // The plan's lines or open lines couldn't be read.
    val linesError: CodeBanner? = null,
    // Lines this visit completed already (the appointment's `fulfilledLineIds`, then the ones applied at End).
    val fulfilled: Set<Long> = emptySet(),
    // Lines ticked now, applied at End.
    val ticks: Set<Long> = emptySet(),
    // The payment typed for this visit (major units, as typed), its method, and what End recorded.
    val amount: String = "",
    val method: PayMethod = PayMethod.Cash,
    val recorded: Long? = null,
    // The tooth (or the general key) whose fulfil sheet is open.
    val sheet: String? = null,
    // Ticks were applied to the selected plan in this visit.
    val ticksApplied: Boolean = false,
    // A payment sent whose answer didn't say whether it was recorded; amount and method are held until End checks.
    val attempt: PaymentAttempt? = null,
) {
    // Something was sent for the selected plan: it can't be swapped for another (it belongs to this one).
    val locked: Boolean get() = ticksApplied || attempt != null || recorded != null

    val selected: TreatmentPlanSummary?
        get() = detail?.summary?.takeIf { it.id == selectedId } ?: plans?.firstOrNull { it.id == selectedId }

    val currency: String? get() = detail?.summary?.takeIf { it.id == selectedId }?.currency?.takeIf { it.isNotBlank() }

    // Null while the plan's currency is unknown or the text isn't an amount.
    val amountMinor: Long? get() = currency?.let { Money.parse(amount, it) }

    // The board's "Enter an amount greater than 0" (blank is no payment).
    val amountInvalid: Boolean get() = amount.isNotBlank() && currency != null && (amountMinor ?: 0) <= 0

    val overOutstanding: Boolean get() = (amountMinor ?: 0) > (detail?.summary?.owedMinor ?: Long.MAX_VALUE)

    val pendingPayment: Boolean get() = selectedId != null && recorded == null && amount.isNotBlank()

    // Ticks and a typed payment live only here until End (flow 05/08 §8, 05/09 §10).
    val hasPending: Boolean get() = ticks.isNotEmpty() || pendingPayment

    private val activeLines: List<PlanLine> get() = detail?.lines.orEmpty().filter { it.status != LineStatus.Cancelled }

    fun done(line: PlanLine): Boolean = line.status == LineStatus.Completed || line.id in fulfilled || line.id in ticks

    /** The plan's lines on [key] (a tooth, or the general key for lines without one), cancelled ones left out. */
    fun lines(key: String): List<PlanLine> = activeLines.filter { (it.tooth ?: DentalChartCodec.GENERAL_KEY) == key }

    fun tickable(line: PlanLine): Boolean =
        line.status != LineStatus.Completed && line.id !in fulfilled &&
            candidates.orEmpty().any { it.lineId == line.id }

    /** Each tooth's lines: all done → done, some → partly done, none → planned (ticks count as done). */
    val toothStates: Map<String, ToothPlanState>
        get() =
            activeLines.filter { it.tooth != null }.groupBy { it.tooth!! }.mapValues { (_, lines) ->
                val done = lines.count(::done)
                when (done) {
                    lines.size -> ToothPlanState.Done
                    0 -> ToothPlanState.Planned
                    else -> ToothPlanState.Partial
                }
            }

    val toothCounts: Map<String, Int>
        get() = activeLines.mapNotNull { it.tooth }.groupingBy { it }.eachCount()

    val dentition: shifa.doctor.data.forms.Dentition
        get() =
            DentalRecord.read(
                detail?.dentalPlanDocumentation?.let(::parseOrNull),
            )?.dentitionKind ?: shifa.doctor.data.forms.Dentition.Permanent

    val allDone: Boolean get() = activeLines.isNotEmpty() && activeLines.all { it.status == LineStatus.Completed }

    val progress: Pair<Int, Int>? get() = selected?.let { it.linesCompletedCount to it.linesTotalCount }
}

private fun parseOrNull(raw: String) = runCatching { shifa.doctor.data.api.apiJson.parseToJsonElement(raw) }.getOrNull()

/**
 * The treatment plan in a visit (DENTAL-03, flows 05/08 and 05/09, board `DaVisit` treatment plan panel, plan mode and
 * "Plan finances"): the patient's active comprehensive plans in the doctor's clinic, the linked or only one chosen, its
 * lines and the ones this visit may complete, ticks and a payment kept until End, which applies them ([applyTicks],
 * [recordPayment]) before completing the visit. A payment is recorded once per visit.
 */
class VisitPlan(
    private val appointmentId: Long,
    private val scope: CoroutineScope,
    private val plans: TreatmentPlansRepository = treatmentPlansRepository,
    private val finance: FinanceRepository = financeRepository,
    private val clinics: ClinicsRepository = clinicsRepository,
    // The line ids the appointment lists as fulfilled by it (read again after a failed fulfil).
    private val fulfilledByVisit: suspend () -> Result<List<Long>> = {
        appointmentsRepository.detail(appointmentId).map { it.fulfilledLineIds }
    },
) {
    private val _state = MutableStateFlow(VisitPlanState())
    val state: StateFlow<VisitPlanState> = _state.asStateFlow()

    private var patientId: Long? = null

    // Each lines read moves it on; only the newest may write (a slow read of the plan chosen before can't win).
    private var reads = 0

    /** Reads the patient's plans; chooses the linked one, else the only one (flow 05/08 §2). */
    fun load(
        patient: Long,
        linkedId: Long?,
        fulfilled: List<Long>,
    ) {
        patientId = patient
        _state.update { it.copy(linkedId = linkedId, fulfilled = it.fulfilled + fulfilled, loadError = null) }
        scope.launch {
            val clinic =
                clinics.selected().getOrElse { e -> return@launch failLoad(e) }
                    ?: return@launch _state.update { it.copy(plans = emptyList()) }
            val list =
                plans.plans(clinic.clinicId, patient, status = TreatmentPlansRepository.ACTIVE)
                    .getOrElse { e -> return@launch failLoad(e) }
            val pick = list.firstOrNull { it.id == linkedId }?.id ?: list.singleOrNull()?.id
            _state.update { it.copy(clinicId = clinic.clinicId, plans = list) }
            if (pick != null && _state.value.selectedId == null) select(pick)
        }
    }

    fun retry() {
        val s = _state.value
        val patient = patientId ?: return
        if (s.plans == null) load(patient, s.linkedId, emptyList()) else loadLines()
    }

    private fun failLoad(e: Throwable) =
        _state.update {
            it.copy(
                loadError = CodeBanner(e.toUserMessage(Res.string.plansLoadFailed), requestId = e.requestId),
            )
        }

    /** "None / bill separately" is null. Ticks and the typed payment belong to the plan they were made for. */
    fun select(id: Long?) {
        if (id == _state.value.selectedId || _state.value.locked) return
        _state.update {
            it.copy(
                selectedId = id,
                detail = null,
                candidates = null,
                linesError = null,
                ticks = emptySet(),
                amount = "",
            )
        }
        if (id != null) loadLines()
    }

    private fun loadLines() {
        val id = _state.value.selectedId ?: return
        val read = ++reads
        _state.update { it.copy(linesError = null) }
        scope.launch {
            val open = async { plans.candidates(id, appointmentId) }
            val detail = plans.detail(id)
            val candidates = open.await()
            if (read != reads) return@launch
            val failure = detail.exceptionOrNull() ?: candidates.exceptionOrNull()
            _state.update {
                it.copy(
                    detail = detail.getOrNull() ?: it.detail,
                    candidates = candidates.getOrNull() ?: it.candidates,
                    linesError =
                        failure?.let { e ->
                            CodeBanner(e.toUserMessage(Res.string.appointmentPlanLoadFailed), requestId = e.requestId)
                        },
                )
            }
        }
    }

    /** The fulfil sheet for a tooth (or the general key); false when it has no lines (the caller says so). */
    fun open(key: String?): Boolean {
        if (key != null && _state.value.lines(key).isEmpty()) return false
        _state.update { it.copy(sheet = key) }
        return true
    }

    fun toggle(lineId: Long) =
        _state.update { s ->
            val line = s.detail?.lines?.firstOrNull { it.id == lineId }
            if (line == null ||
                !s.tickable(
                    line,
                )
            ) {
                s
            } else {
                s.copy(ticks = if (lineId in s.ticks) s.ticks - lineId else s.ticks + lineId)
            }
        }

    /** As typed: anything that isn't an amount (a minus sign too) shows as invalid, never as a different amount. */
    fun amount(text: String) {
        if (_state.value.recorded != null || _state.value.attempt != null) return
        _state.update { it.copy(amount = text) }
    }

    fun method(m: PayMethod) =
        _state.update {
            if (it.recorded != null || it.attempt != null) {
                it
            } else {
                it.copy(
                    method = m,
                )
            }
        }

    /** End's "Applying treatment plan…": the ticked lines completed by this visit; the failure's message, or null. */
    suspend fun applyTicks(): StringResource? {
        val s = _state.value
        val id = s.selectedId ?: return null
        if (s.ticks.isEmpty()) return null
        val detail =
            plans.fulfill(id, appointmentId, s.ticks.toList()).getOrElse {
                // The answer may have been lost after the server completed them: the next End checks first.
                reconcileTicks(id)
                return Res.string.appointmentPlanApplyFailed
            }
        _state.update {
            it.copy(detail = detail, fulfilled = it.fulfilled + s.ticks, ticks = emptySet(), ticksApplied = true)
        }
        return null
    }

    /**
     * After a failed fulfil: ticked lines the appointment now lists as fulfilled were applied (a lost answer) and move to
     * [VisitPlanState.fulfilled]; ticked lines completed by another visit meanwhile are no longer open and are dropped
     * (they aren't this visit's). Nothing changes when either can't be read.
     */
    private suspend fun reconcileTicks(planId: Long) {
        val ours = fulfilledByVisit().getOrNull()?.toSet() ?: return
        val detail = plans.detail(planId).getOrNull() ?: return
        val done = detail.lines.filter { it.status == LineStatus.Completed }.map { it.id }.toSet()
        _state.update {
            val applied = it.ticks intersect ours
            it.copy(
                detail = detail,
                fulfilled = it.fulfilled + applied,
                ticks = it.ticks - ours - done,
                ticksApplied = it.ticksApplied || applied.isNotEmpty(),
            )
        }
    }

    /** End's "Recording payment…": once per visit; the failure's message, or null (nothing typed is no payment). */
    suspend fun recordPayment(): StringResource? {
        val s = _state.value
        if (!s.pendingPayment) return null
        val id = s.selectedId ?: return null
        val clinic = s.clinicId ?: return Res.string.appointmentPlanPaymentFailed
        val currency = s.currency ?: return Res.string.appointmentPlanPaymentFailed
        val minor = s.amountMinor?.takeIf { it > 0 } ?: return Res.string.appointmentPlanPaymentFailed
        // The ledger makes a row per call (BE-13: no idempotency key yet). A payment whose answer was lost is looked for
        // there (this visit's rows for this plan, amount and method) before it is sent again, never by the totals.
        val attempt =
            s.attempt ?: PaymentAttempt(
                minor,
                s.method,
                ledgerRows(clinic, id, minor, s.method).getOrElse { return Res.string.appointmentPlanPaymentFailed },
            )
        if (s.attempt != null) {
            val now =
                ledgerRows(clinic, id, attempt.minor, attempt.method).getOrElse {
                    return Res.string.appointmentPlanPaymentFailed
                }
            if (now > attempt.rowsBefore) return recorded(attempt.minor, plans.detail(id).getOrNull())
        }
        _state.update { it.copy(attempt = attempt) }
        finance.recordPayment(
            clinic,
            RecordPayment(id, attempt.minor, currency, attempt.method.wire, linkedAppointmentId = appointmentId),
        ).getOrElse { e ->
            // A clear refusal (a 4xx) recorded nothing: the amount and the plan are free again to change or clear.
            if (e is ApiException && e.status in 400..499 && e.status != 408) _state.update { it.copy(attempt = null) }
            return Res.string.appointmentPlanPaymentFailed
        }
        // The new paid and outstanding for the card and the summary; if they can't be read, the payment is added to
        // the ones known (the summary never shows a payment beside the totals from before it).
        return recorded(attempt.minor, plans.detail(id).getOrNull())
    }

    private suspend fun ledgerRows(
        clinic: Long,
        planId: Long,
        minor: Long,
        method: PayMethod,
    ): Result<Int> =
        finance.payments(clinic).map { rows ->
            rows.count {
                it.linkedAppointmentId == appointmentId && it.treatmentPlanId == planId && it.amountMinor == minor &&
                    it.method == method.wire
            }
        }

    private fun recorded(
        minor: Long,
        fresh: TreatmentPlanDetail?,
    ): StringResource? {
        _state.update { s ->
            val detail =
                fresh ?: s.detail?.let { d ->
                    d.copy(
                        summary =
                            d.summary.copy(
                                paidMinor = d.summary.paidMinor + minor,
                                owedMinor = d.summary.owedMinor - minor,
                            ),
                    )
                }
            s.copy(recorded = minor, attempt = null, detail = detail)
        }
        return null
    }
}
