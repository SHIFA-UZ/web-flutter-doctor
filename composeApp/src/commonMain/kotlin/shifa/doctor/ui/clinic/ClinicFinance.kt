package shifa.doctor.ui.clinic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.finance.FinanceMonth
import shifa.doctor.data.finance.FinanceRepository
import shifa.doctor.data.finance.PaymentRow
import shifa.doctor.data.finance.RecordPayment
import shifa.doctor.data.finance.financeRepository
import shifa.doctor.data.model.ClinicMember
import shifa.doctor.data.model.ClinicPatient
import shifa.doctor.data.model.DoctorEarning
import shifa.doctor.data.model.FinanceDashboard
import shifa.doctor.data.model.FinancialRecord
import shifa.doctor.data.model.InstallmentRow
import shifa.doctor.data.model.LedgerRow
import shifa.doctor.data.model.MarkPaidBody
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.model.NewFinancialRecord
import shifa.doctor.data.model.TreatmentPlanSummary
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.data.treatmentplans.treatmentPlansRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clinicFinanceInstallStatusUpdateFailed
import shifa.doctor.generated.resources.clinicFinanceInstallStatusUpdated
import shifa.doctor.generated.resources.clinicFinanceLoadFailed
import shifa.doctor.generated.resources.clinicFinancePaymentFailed
import shifa.doctor.generated.resources.clinicFinancePaymentRecorded
import shifa.doctor.generated.resources.clinicFinanceReminderFailed
import shifa.doctor.generated.resources.clinicFinanceReminderSent
import shifa.doctor.generated.resources.clinicRecordsFormFailed
import shifa.doctor.generated.resources.clinicRecordsFormSuccess
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.utils.Money
import kotlin.time.Duration.Companion.milliseconds

/** The finance views in the board's order (`DaClinic` Finance; Flutter's six sub-tabs, 09 §3). */
enum class FinanceView { Dashboard, Ledger, Installments, Earnings, Records, Payments }

/** The dashboard's KPI cards; the selected one picks the list below (09 §3.2). */
enum class FinanceKpi { Revenue, Outstanding, Overdue, Rate }

/** Desk payment methods the clinic records (`PaymentMethod`; WAIVED, CLICK and STRIPE come from elsewhere). */
enum class PayMethod(val wire: String) { Cash("CASH"), Card("CARD_EXTERNAL"), Transfer("TRANSFER"), Other("OTHER") }

/** The custom-amount payment sheet for a visit (09 §3.5): the amount, the method and a memo. */
data class PaySheet(
    val row: LedgerRow,
    val amount: String,
    val method: PayMethod = PayMethod.Cash,
    val memo: String = "",
    val tried: Boolean = false,
    val busy: Boolean = false,
    val error: CodeBanner? = null,
) {
    val minor: Long? get() = Money.parse(amount, row.currency)?.takeIf { it > 0 }
}

/** "Mark paid" for an installment: the method and a memo (quirk 8: Flutter always sent CASH and no memo). */
data class MarkPaidSheet(
    val row: InstallmentRow,
    val method: PayMethod = PayMethod.Cash,
    val memo: String = "",
    val busy: Boolean = false,
    val error: CodeBanner? = null,
)

/** The board's "record" sheet: patient (searched in the clinic), type, total, due date, notes. */
data class RecordSheet(
    val patient: ClinicPatient? = null,
    val query: String = "",
    val matches: List<ClinicPatient>? = null,
    val type: String = RECORD_TYPES.first(),
    val amount: String = "",
    val due: LocalDate? = null,
    val notes: String = "",
    val tried: Boolean = false,
    val busy: Boolean = false,
    val error: CodeBanner? = null,
    val pickingDue: Boolean = false,
    // The patient's plans in this clinic (read once a patient is picked); a chosen plan gives the totals (09 §3.4).
    val plans: List<TreatmentPlanSummary>? = null,
    val plan: TreatmentPlanSummary? = null,
)

/**
 * A row of the dashboard's Outstanding / Overdue lists: when ([date], an instant or a due date), who ([name]), what
 * ([detail]: a plan title or a doctor; [recordType] for an invoice), how much, and the view that lists it ([view]; a
 * plan's balance opens the plan once its screen exists).
 */
data class Owed(
    val date: String?,
    val name: String,
    val detail: String?,
    val amountMinor: Long,
    val currency: String,
    val view: FinanceView? = null,
    val planId: Long? = null,
    val recordType: String? = null,
)

/** A row's details sheet (a visit's services, a payment): a title and label / value lines. */
data class InfoSheet(
    val title: String,
    val lines: List<Pair<String, String>>,
)

data class ClinicFinanceUiState(
    // The clinic's zone: months, and the day of a visit or a payment, are the clinic's.
    val zone: String? = null,
    val view: FinanceView = FinanceView.Dashboard,
    // Null is all time; the board starts on the current month.
    val month: FinanceMonth? = null,
    val query: String = "",
    val chip: Int = 0,
    val kpi: FinanceKpi = FinanceKpi.Revenue,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: CodeBanner? = null,
    val dashboard: FinanceDashboard? = null,
    // The dashboard's lists: balances (Outstanding), overdue installments and invoices (Overdue).
    val balances: List<Owed>? = null,
    val overdue: List<Owed>? = null,
    val ledger: List<LedgerRow>? = null,
    val ledgerTotal: Long? = null,
    val payments: List<PaymentRow>? = null,
    val records: List<FinancialRecord>? = null,
    val recordsTotal: Long? = null,
    val installments: List<InstallmentRow>? = null,
    val earnings: List<DoctorEarning>? = null,
    val page: Int = 0,
    val last: Boolean = true,
    val members: List<ClinicMember> = emptyList(),
    val payMenu: LedgerRow? = null,
    val pay: PaySheet? = null,
    val installmentMenu: InstallmentRow? = null,
    val markPaid: MarkPaidSheet? = null,
    val record: RecordSheet? = null,
    val info: InfoSheet? = null,
    val busy: Boolean = false,
    val banner: CodeBanner? = null,
) {
    /** The month filter applies where the backend takes a range (payments and records have none: BE-36). */
    val byMonth: Boolean get() = view in setOf(FinanceView.Dashboard, FinanceView.Ledger, FinanceView.Earnings)

    fun doctorName(id: Long?): String? =
        members.firstOrNull {
            it.doctorProfileId == id
        }?.displayName?.takeIf(String::isNotBlank)

    private val q: String get() = query.trim().lowercase()

    private fun matches(vararg fields: String?) = q.isEmpty() || fields.any { it?.lowercase()?.contains(q) == true }

    /** Ledger: chip (All, Paid, Partial, Unpaid) on the visit's own status, then search (09 §3.5). */
    val shownLedger: List<LedgerRow>
        get() =
            ledger.orEmpty().filter { r ->
                val status = r.planSimplePaymentStatus?.uppercase()
                when (chip) {
                    1 -> status == "PAID"
                    2 -> status == "PARTIAL"
                    3 -> status == "UNPAID"
                    else -> true
                }
            }.filter { r ->
                matches(r.patientName, r.doctorName, r.treatmentPlanId?.toString(), r.treatmentPlanId?.let { "#$it" })
            }

    /** Payments: chip (All, Cash, Card, Transfer, Other) and search over patient, doctor, plan, method, memo. */
    val shownPayments: List<PaymentRow>
        get() =
            payments.orEmpty().filter { p ->
                when (chip) {
                    1 -> p.method == PayMethod.Cash.wire
                    2 -> p.method == PayMethod.Card.wire
                    3 -> p.method == PayMethod.Transfer.wire
                    4 -> p.method !in setOf(PayMethod.Cash.wire, PayMethod.Card.wire, PayMethod.Transfer.wire)
                    else -> true
                }
            }.filter { p ->
                matches(
                    p.patientName,
                    p.doctorName,
                    p.treatmentPlanTitle,
                    p.treatmentPlanId?.toString(),
                    p.method,
                    p.memo,
                )
            }

    val shownRecords: List<FinancialRecord>
        get() = records.orEmpty().filter { r -> matches(r.recordNumber, r.recordType, r.notes) }

    val shownInstallments: List<InstallmentRow>
        get() = installments.orEmpty().filter { r -> matches(r.patientName, r.treatmentPlanTitle) }

    val shownEarnings: List<DoctorEarning>
        get() =
            earnings.orEmpty().filter { e ->
                matches(doctorName(e.doctorProfileId), e.doctorProfileId.toString(), "#${e.doctorProfileId}")
            }
}

/**
 * The clinic's finance (FIN-01…07, 09 §3, board `DaClinic` Finance): six views on one list, the month (in the clinic's
 * zone) where the backend filters by it, server paging (quirk 1: Flutter filtered the first 50 on the device), the
 * desk payment, installments (mark paid with the method, waive, cancel, remind), a new record, and CSV export. A
 * view's answer that comes back after the filters changed is dropped.
 */
class ClinicFinanceViewModel(
    private val clinic: MyClinic,
    // Today in the clinic's zone: the month the views start on, the new record's due date (+14 days).
    private val today: LocalDate,
    private val repo: FinanceRepository = financeRepository,
    private val clinics: ClinicsRepository = clinicsRepository,
    private val plans: TreatmentPlansRepository = treatmentPlansRepository,
) : ViewModel() {
    private val _state =
        MutableStateFlow(ClinicFinanceUiState(zone = clinic.timeZone, month = FinanceMonth.of(today)))
    val state: StateFlow<ClinicFinanceUiState> = _state.asStateFlow()

    // Every filter change starts a new generation: a late answer of an older one is dropped.
    private var generation = 0
    private var loading: Job? = null
    private var patientSearch: Job? = null

    init {
        viewModelScope.launch {
            clinics.members(clinic.clinicId).onSuccess { m -> _state.update { it.copy(members = m) } }
        }
        load()
    }

    private val range get() = _state.value.month?.range(clinic.timeZone)

    fun view(v: FinanceView) {
        if (v == _state.value.view) return
        _state.update { it.copy(view = v, chip = 0, query = "") }
        load()
    }

    fun month(m: FinanceMonth?) {
        _state.update { it.copy(month = m) }
        load()
    }

    fun query(text: String) = _state.update { it.copy(query = text) }

    /** A chip: client-side filters apply at once; Records and Installments ask the server. */
    fun chip(i: Int) {
        _state.update { it.copy(chip = i) }
        if (_state.value.view in setOf(FinanceView.Records, FinanceView.Installments)) load()
    }

    fun kpi(k: FinanceKpi) {
        _state.update { it.copy(kpi = k) }
        loadDrill()
    }

    /** The current view's first page (also Retry and after a write); rows shown stay until the new ones arrive. */
    fun load() {
        val gen = ++generation
        loading?.cancel()
        val s = _state.value
        // A page still loading for the old filters is dropped with its "loading more" flag.
        _state.update { it.copy(loading = true, loadingMore = false, error = null, page = 0, last = true) }
        loading =
            viewModelScope.launch {
                val id = clinic.clinicId
                val failure: Throwable? =
                    when (s.view) {
                        FinanceView.Dashboard ->
                            repo.dashboard(id, range).fold({ d ->
                                set(gen) { it.copy(dashboard = d) }
                                null
                            }, { it })
                        FinanceView.Ledger ->
                            repo.ledger(id, range, 0).fold({ p ->
                                set(gen) { it.copy(ledger = p.rows, ledgerTotal = p.total, last = p.last) }
                                null
                            }, { it })
                        FinanceView.Payments ->
                            repo.paymentsPage(id, 0).fold({ p ->
                                set(gen) { it.copy(payments = p.rows, last = p.last) }
                                null
                            }, { it })
                        FinanceView.Records ->
                            repo.records(id, RECORD_STATUS_CHIPS[s.chip], 0).fold({ p ->
                                set(gen) { it.copy(records = p.rows, recordsTotal = p.total, last = p.last) }
                                null
                            }, { it })
                        FinanceView.Installments ->
                            repo.installments(id, INSTALLMENT_FILTERS[s.chip]).fold({ l ->
                                set(gen) { it.copy(installments = l) }
                                null
                            }, { it })
                        FinanceView.Earnings ->
                            repo.earnings(id, range).fold({ l ->
                                set(gen) { it.copy(earnings = l) }
                                null
                            }, { it })
                    }
                set(gen) {
                    it.copy(
                        loading = false,
                        error =
                            failure?.let {
                                    e ->
                                failure(e, Res.string.clinicFinanceLoadFailed)
                            },
                    )
                }
                if (s.view == FinanceView.Dashboard && failure == null) loadDrill()
            }
    }

    /** The dashboard's list for the selected KPI (Revenue and Rate come with the dashboard itself). */
    private fun loadDrill() {
        val s = _state.value
        if (s.view != FinanceView.Dashboard) return
        val gen = generation
        val id = clinic.clinicId
        viewModelScope.launch {
            // A failed list says so in the banner above (with Retry), never as an endless skeleton.
            val result: Result<Unit> =
                runCatching {
                    when (s.kpi) {
                        // All time: the plans' balances, as the KPI counts them; a month: that month's visits with a
                        // balance, every page (the KPI's month hint).
                        FinanceKpi.Outstanding -> {
                            val owed =
                                if (s.month == null) {
                                    plans.plans(id, null).getOrThrow().filter { it.owedMinor > 0 }.map {
                                        Owed(
                                            null,
                                            it.patientName.orEmpty(),
                                            it.title,
                                            it.owedMinor,
                                            it.currency,
                                            planId = it.id,
                                        )
                                    }
                                } else {
                                    allPages {
                                            page ->
                                        repo.ledger(id, range, page, DRILL_SIZE)
                                    }.filter { it.remainingMinor > 0 }.map {
                                        Owed(
                                            it.startAt,
                                            it.patientName.orEmpty(),
                                            it.doctorName,
                                            it.remainingMinor,
                                            it.currency,
                                            FinanceView.Ledger,
                                        )
                                    }
                                }
                            set(gen) { it.copy(balances = owed.sortedByDescending { o -> o.amountMinor }) }
                        }
                        // Overdue is all-time only (the backend's count isn't month-scoped): installments and invoices.
                        FinanceKpi.Overdue ->
                            if (s.month == null) {
                                val items =
                                    repo.installments(id, "overdue").getOrThrow().map {
                                        Owed(
                                            it.dueDate,
                                            it.patientName.orEmpty(),
                                            it.treatmentPlanTitle,
                                            it.amountMinor,
                                            it.currency,
                                            FinanceView.Installments,
                                        )
                                    }
                                val invoices =
                                    allPages { page -> repo.records(id, "OVERDUE", page, DRILL_SIZE) }.map {
                                        Owed(
                                            it.dueDate,
                                            it.recordNumber.orEmpty(),
                                            null,
                                            it.remainingMinor,
                                            it.currency,
                                            FinanceView.Records,
                                            recordType = it.recordType,
                                        )
                                    }
                                set(gen) { it.copy(overdue = items + invoices) }
                            }
                        else -> Unit
                    }
                }
            result.exceptionOrNull()?.let { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                set(gen) { it.copy(error = failure(e, Res.string.clinicFinanceLoadFailed)) }
            }
        }
    }

    /** Every page of a paged list (at most [MAX_PAGES], so a runaway list can't loop). */
    private suspend fun <T> allPages(read: suspend (Int) -> Result<shifa.doctor.data.model.FinancePage<T>>): List<T> {
        val all = mutableListOf<T>()
        for (page in 0 until MAX_PAGES) {
            val p = read(page).getOrThrow()
            all += p.rows
            if (p.last) break
        }
        return all
    }

    fun loadMore() {
        val s = _state.value
        if (s.last || s.loadingMore || s.loading) return
        val gen = generation
        val next = s.page + 1
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val id = clinic.clinicId
            val result: Result<Unit> =
                when (s.view) {
                    FinanceView.Ledger ->
                        repo.ledger(id, range, next).map { p ->
                            set(gen) {
                                it.copy(
                                    ledger =
                                        (it.ledger.orEmpty() + p.rows).distinctBy {
                                                r ->
                                            r.appointmentId
                                        },
                                    ledgerTotal = p.total,
                                    last = p.last,
                                )
                            }
                        }
                    FinanceView.Payments ->
                        repo.paymentsPage(id, next).map { p ->
                            set(gen) {
                                it.copy(
                                    payments =
                                        (it.payments.orEmpty() + p.rows).distinctBy {
                                                r ->
                                            r.id
                                        },
                                    last = p.last,
                                )
                            }
                        }
                    FinanceView.Records ->
                        repo.records(id, RECORD_STATUS_CHIPS[s.chip], next).map { p ->
                            set(gen) {
                                it.copy(
                                    records =
                                        (it.records.orEmpty() + p.rows).distinctBy {
                                                r ->
                                            r.id
                                        },
                                    recordsTotal = p.total,
                                    last = p.last,
                                )
                            }
                        }
                    else -> Result.success(Unit)
                }
            set(gen) {
                it.copy(
                    loadingMore = false,
                    page = if (result.isSuccess) next else it.page,
                    error = result.exceptionOrNull()?.let { e -> failure(e, Res.string.clinicFinanceLoadFailed) },
                )
            }
        }
    }

    // --- Desk payment on a visit (09 §3.5) ---

    fun payMenu(row: LedgerRow?) = _state.update { it.copy(payMenu = row) }

    /** "Paid by cash / card / transfer / other": the visit's remaining amount at once. */
    fun payFull(
        row: LedgerRow,
        method: PayMethod,
    ) {
        _state.update { it.copy(payMenu = null) }
        record(row, row.remainingMinor, method, null) { e -> _state.update { it.copy(banner = e) } }
    }

    fun openCustomPay(row: LedgerRow) =
        _state.update {
            it.copy(payMenu = null, pay = PaySheet(row, priceText(row.remainingMinor, row.currency)))
        }

    fun editPay(change: (PaySheet) -> PaySheet) =
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
        if (sheet.busy) return
        val amount = sheet.minor ?: return _state.update { it.copy(pay = sheet.copy(tried = true)) }
        _state.update { it.copy(pay = sheet.copy(tried = true, busy = true)) }
        record(sheet.row, amount, sheet.method, sheet.memo.trim().ifEmpty { null }) { e ->
            _state.update { s -> s.copy(pay = s.pay?.copy(busy = false, error = e)) }
        }
    }

    private fun record(
        row: LedgerRow,
        amount: Long,
        method: PayMethod,
        memo: String?,
        onFailure: (CodeBanner) -> Unit,
    ) {
        val plan = row.treatmentPlanId ?: return
        if (amount <= 0 || _state.value.busy) return
        _state.update { it.copy(busy = true, banner = null) }
        viewModelScope.launch {
            repo.recordPayment(
                clinic.clinicId,
                RecordPayment(plan, amount, row.currency, method.wire, row.appointmentId, memo),
            ).fold(
                {
                    _state.update {
                        it.copy(
                            busy = false,
                            pay = null,
                            banner = CodeBanner(Res.string.clinicFinancePaymentRecorded, error = false),
                        )
                    }
                    load()
                },
                { e ->
                    _state.update { it.copy(busy = false) }
                    onFailure(failure(e, Res.string.clinicFinancePaymentFailed))
                },
            )
        }
    }

    // --- Installments (09 §3.7) ---

    fun installmentMenu(row: InstallmentRow?) = _state.update { it.copy(installmentMenu = row) }

    fun openMarkPaid(row: InstallmentRow) =
        _state.update {
            it.copy(
                installmentMenu = null,
                markPaid = MarkPaidSheet(row),
            )
        }

    fun editMarkPaid(change: (MarkPaidSheet) -> MarkPaidSheet) =
        _state.update { s -> s.copy(markPaid = s.markPaid?.let { change(it).copy(error = null) }) }

    fun closeMarkPaid() = _state.update { if (it.markPaid?.busy == true) it else it.copy(markPaid = null) }

    fun markPaid() {
        val sheet = _state.value.markPaid ?: return
        if (sheet.busy) return
        _state.update { it.copy(markPaid = sheet.copy(busy = true)) }
        viewModelScope.launch {
            repo.markPaid(
                clinic.clinicId,
                sheet.row.id,
                MarkPaidBody(sheet.method.wire, sheet.memo.trim().ifEmpty { null }),
            ).fold(
                {
                    _state.update {
                        it.copy(
                            markPaid = null,
                            banner = CodeBanner(Res.string.clinicFinanceInstallStatusUpdated, error = false),
                        )
                    }
                    load()
                },
                { e ->
                    _state.update {
                            s ->
                        s.copy(
                            markPaid =
                                s.markPaid?.copy(
                                    busy = false,
                                    error = failure(e, Res.string.clinicFinanceInstallStatusUpdateFailed),
                                ),
                        )
                    }
                },
            )
        }
    }

    /** PENDING, WAIVED or CANCELLED (OVERDUE is the backend's to set; PAID goes through [markPaid]). */
    fun setStatus(
        row: InstallmentRow,
        status: String,
    ) {
        if (_state.value.busy || status !in setOf("PENDING", "WAIVED", "CANCELLED")) return
        _state.update { it.copy(installmentMenu = null, busy = true, banner = null) }
        viewModelScope.launch {
            val r = repo.setInstallmentStatus(clinic.clinicId, row.id, status)
            _state.update {
                it.copy(
                    busy = false,
                    banner =
                        r.fold(
                            { CodeBanner(Res.string.clinicFinanceInstallStatusUpdated, error = false) },
                            { e -> failure(e, Res.string.clinicFinanceInstallStatusUpdateFailed) },
                        ),
                )
            }
            if (r.isSuccess) load()
        }
    }

    fun remind(row: InstallmentRow) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, banner = null) }
        viewModelScope.launch {
            val r = repo.notifyInstallment(clinic.clinicId, row.id)
            _state.update {
                it.copy(
                    busy = false,
                    banner =
                        r.fold(
                            {
                                CodeBanner(
                                    Res.string.clinicFinanceReminderSent,
                                    error = false,
                                    args = listOf(row.patientName.orEmpty()),
                                )
                            },
                            { e -> failure(e, Res.string.clinicFinanceReminderFailed) },
                        ),
                )
            }
        }
    }

    // --- New record (09 §3.4) ---

    fun openRecord() = _state.update { it.copy(record = RecordSheet(due = today.plus(DatePeriod(days = DUE_DAYS)))) }

    fun editRecord(change: (RecordSheet) -> RecordSheet) =
        _state.update { s ->
            s.copy(
                record =
                    s.record?.let {
                        change(it).copy(error = null)
                    },
            )
        }

    fun closeRecord() = _state.update { if (it.record?.busy == true) it else it.copy(record = null) }

    /** A patient from the search: their plans in this clinic are read for the optional plan. */
    fun pickPatient(patient: ClinicPatient) {
        editRecord { it.copy(patient = patient, matches = null, plans = null, plan = null) }
        viewModelScope.launch {
            plans.plans(clinic.clinicId, patient.patientId).onSuccess { list ->
                _state.update { s ->
                    s.copy(
                        record =
                            s.record?.takeIf { it.patient?.patientId == patient.patientId }?.copy(
                                plans = list,
                            ) ?: s.record,
                    )
                }
            }
        }
    }

    fun pickPlan(plan: TreatmentPlanSummary?) = editRecord { it.copy(plan = plan) }

    /** The patient field: the clinic's patients searched on the server once typing pauses. */
    fun searchPatient(text: String) {
        editRecord { it.copy(query = text, patient = null, plans = null, plan = null) }
        patientSearch?.cancel()
        if (text.isBlank()) return editRecord { it.copy(matches = null) }
        patientSearch =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE)
                clinics.patients(clinic.clinicId, 0, text, size = PATIENT_MATCHES).onSuccess { p ->
                    _state.update {
                            s ->
                        s.copy(record = s.record?.takeIf { it.query == text }?.copy(matches = p.rows) ?: s.record)
                    }
                }
            }
    }

    fun saveRecord() {
        val sheet = _state.value.record ?: return
        if (sheet.busy) return
        val patient = sheet.patient
        // With a plan the backend takes the totals from its lines; without one the total is required.
        val amount = if (sheet.plan != null) 0L else Money.parse(sheet.amount, currency)?.takeIf { it > 0 }
        if (patient == null || amount == null) return _state.update { it.copy(record = sheet.copy(tried = true)) }
        _state.update { it.copy(record = sheet.copy(tried = true, busy = true)) }
        viewModelScope.launch {
            repo.createRecord(
                clinic.clinicId,
                NewFinancialRecord(
                    patient.patientId,
                    sheet.type,
                    amount,
                    currency,
                    treatmentPlanId = sheet.plan?.id,
                    dueDate = sheet.due?.toString(),
                    notes = sheet.notes.trim().ifEmpty { null },
                ),
            ).fold(
                {
                    _state.update {
                        it.copy(
                            record = null,
                            banner = CodeBanner(Res.string.clinicRecordsFormSuccess, error = false),
                        )
                    }
                    if (_state.value.view == FinanceView.Records) load()
                },
                {
                        e ->
                    _state.update {
                            s ->
                        s.copy(
                            record =
                                s.record?.copy(
                                    busy = false,
                                    error = failure(e, Res.string.clinicRecordsFormFailed),
                                ),
                        )
                    }
                },
            )
        }
    }

    val currency: String get() = clinic.currency?.takeIf { it.isNotBlank() } ?: "UZS"

    fun info(sheet: InfoSheet?) = _state.update { it.copy(info = sheet) }

    fun dismiss() = _state.update { it.copy(banner = null) }

    private fun set(
        gen: Int,
        change: (ClinicFinanceUiState) -> ClinicFinanceUiState,
    ) = _state.update { if (gen == generation) change(it) else it }

    private fun failure(
        e: Throwable,
        fallback: StringResource,
    ) = CodeBanner(e.toUserMessage(fallback), requestId = e.requestId)

    companion object {
        // Records: All, Issued, Paid, Partially paid, Overdue, Void (the backend's statuses; VOIDED, quirk 2).
        val RECORD_STATUS_CHIPS = listOf(null, "ISSUED", "PAID", "PARTIALLY_PAID", "OVERDUE", "VOIDED")

        // Installments: All, Upcoming, Overdue, Paid (the backend's filters).
        val INSTALLMENT_FILTERS = listOf("all", "pending", "overdue", "paid")
        const val DRILL_SIZE = 50
        const val MAX_PAGES = 10
        const val DUE_DAYS = 14
        const val PATIENT_MATCHES = 8
        val SEARCH_DEBOUNCE = 350.milliseconds
    }
}

val RECORD_TYPES = listOf("INVOICE", "RECEIPT", "ESTIMATE", "CREDIT_NOTE")
