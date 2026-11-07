package shifa.doctor.ui.clinic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.finance.FinanceMonth
import shifa.doctor.data.model.DoctorEarning
import shifa.doctor.data.model.InstallmentRow
import shifa.doctor.data.model.LedgerRow
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.model.mayManageFinance
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.all
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.clinicEarningsColClinicShareCollected
import shifa.doctor.generated.resources.clinicEarningsColClinicShareGross
import shifa.doctor.generated.resources.clinicEarningsColCollected
import shifa.doctor.generated.resources.clinicEarningsColDoctorShareCollected
import shifa.doctor.generated.resources.clinicEarningsColDoctorShareGross
import shifa.doctor.generated.resources.clinicEarningsColGross
import shifa.doctor.generated.resources.clinicEarningsColOutstanding
import shifa.doctor.generated.resources.clinicEarningsColSharePercent
import shifa.doctor.generated.resources.clinicEarningsColVisits
import shifa.doctor.generated.resources.clinicEarningsSearchHint
import shifa.doctor.generated.resources.clinicEarningsShare
import shifa.doctor.generated.resources.clinicEarningsShareNotSet
import shifa.doctor.generated.resources.clinicFinanceByAppointment
import shifa.doctor.generated.resources.clinicFinanceCollectionRate
import shifa.doctor.generated.resources.clinicFinanceCreatedOn
import shifa.doctor.generated.resources.clinicFinanceDashboard
import shifa.doctor.generated.resources.clinicFinanceDashboardCollectionHint
import shifa.doctor.generated.resources.clinicFinanceDashboardDoctorEarningsTop
import shifa.doctor.generated.resources.clinicFinanceDashboardNoOutstanding
import shifa.doctor.generated.resources.clinicFinanceDashboardNoOverdue
import shifa.doctor.generated.resources.clinicFinanceDashboardNoOverdueMonth
import shifa.doctor.generated.resources.clinicFinanceDashboardOutstandingDetail
import shifa.doctor.generated.resources.clinicFinanceDashboardOutstandingHint
import shifa.doctor.generated.resources.clinicFinanceDashboardOverdueDetail
import shifa.doctor.generated.resources.clinicFinanceDashboardOverdueHint
import shifa.doctor.generated.resources.clinicFinanceDoctorEarnings
import shifa.doctor.generated.resources.clinicFinanceDueOn
import shifa.doctor.generated.resources.clinicFinanceExportCsv
import shifa.doctor.generated.resources.clinicFinanceExportFailed
import shifa.doctor.generated.resources.clinicFinanceExportNoRows
import shifa.doctor.generated.resources.clinicFinanceHowCalculated
import shifa.doctor.generated.resources.clinicFinanceInstallColDue
import shifa.doctor.generated.resources.clinicFinanceInstallColPlan
import shifa.doctor.generated.resources.clinicFinanceInstallColStatus
import shifa.doctor.generated.resources.clinicFinanceInstallFilterOverdue
import shifa.doctor.generated.resources.clinicFinanceInstallFilterPaid
import shifa.doctor.generated.resources.clinicFinanceInstallFilterPending
import shifa.doctor.generated.resources.clinicFinanceInstallSearchHint
import shifa.doctor.generated.resources.clinicFinanceInstallStatusCancelled
import shifa.doctor.generated.resources.clinicFinanceInstallStatusOverdue
import shifa.doctor.generated.resources.clinicFinanceInstallStatusPaid
import shifa.doctor.generated.resources.clinicFinanceInstallStatusPending
import shifa.doctor.generated.resources.clinicFinanceInstallStatusWaived
import shifa.doctor.generated.resources.clinicFinanceInstallTotalsItems
import shifa.doctor.generated.resources.clinicFinanceInstallTotalsOutstanding
import shifa.doctor.generated.resources.clinicFinanceInstallTotalsPaidSum
import shifa.doctor.generated.resources.clinicFinanceInstallTotalsScheduled
import shifa.doctor.generated.resources.clinicFinanceInstallmentNumber
import shifa.doctor.generated.resources.clinicFinanceInstallments
import shifa.doctor.generated.resources.clinicFinanceInvalidAmount
import shifa.doctor.generated.resources.clinicFinanceLoadMore
import shifa.doctor.generated.resources.clinicFinanceMarkInstallmentPaid
import shifa.doctor.generated.resources.clinicFinanceMonthAllTime
import shifa.doctor.generated.resources.clinicFinanceMonthFilter
import shifa.doctor.generated.resources.clinicFinanceNothing
import shifa.doctor.generated.resources.clinicFinanceNotifyInstallment
import shifa.doctor.generated.resources.clinicFinanceOutstanding
import shifa.doctor.generated.resources.clinicFinanceOverdueCount
import shifa.doctor.generated.resources.clinicFinancePayByCard
import shifa.doctor.generated.resources.clinicFinancePayByCash
import shifa.doctor.generated.resources.clinicFinancePayByOther
import shifa.doctor.generated.resources.clinicFinancePayByTransfer
import shifa.doctor.generated.resources.clinicFinancePayCustomAmount
import shifa.doctor.generated.resources.clinicFinancePaymentAmountLabel
import shifa.doctor.generated.resources.clinicFinancePaymentConfirm
import shifa.doctor.generated.resources.clinicFinancePaymentDialogTitle
import shifa.doctor.generated.resources.clinicFinancePaymentMemoLabel
import shifa.doctor.generated.resources.clinicFinancePayments
import shifa.doctor.generated.resources.clinicFinancePaymentsInPeriod
import shifa.doctor.generated.resources.clinicFinancePlanNumber
import shifa.doctor.generated.resources.clinicFinanceRecords
import shifa.doctor.generated.resources.clinicFinanceShowingRows
import shifa.doctor.generated.resources.clinicFinanceTotalRevenue
import shifa.doctor.generated.resources.clinicFinanceTotalRevenueHint
import shifa.doctor.generated.resources.clinicFinanceVisitServices
import shifa.doctor.generated.resources.clinicLedgerColServices
import shifa.doctor.generated.resources.clinicLedgerPayMenu
import shifa.doctor.generated.resources.clinicLedgerSearchHint
import shifa.doctor.generated.resources.clinicPatientsSearchHint
import shifa.doctor.generated.resources.clinicPaymentMethodCard
import shifa.doctor.generated.resources.clinicPaymentMethodCash
import shifa.doctor.generated.resources.clinicPaymentMethodClick
import shifa.doctor.generated.resources.clinicPaymentMethodOther
import shifa.doctor.generated.resources.clinicPaymentMethodTransfer
import shifa.doctor.generated.resources.clinicPaymentStatusNone
import shifa.doctor.generated.resources.clinicPaymentStatusPaid
import shifa.doctor.generated.resources.clinicPaymentStatusPartial
import shifa.doctor.generated.resources.clinicPaymentStatusUnpaid
import shifa.doctor.generated.resources.clinicPaymentsColAmount
import shifa.doctor.generated.resources.clinicPaymentsColDate
import shifa.doctor.generated.resources.clinicPaymentsColDoctor
import shifa.doctor.generated.resources.clinicPaymentsColId
import shifa.doctor.generated.resources.clinicPaymentsColMemo
import shifa.doctor.generated.resources.clinicPaymentsColMethod
import shifa.doctor.generated.resources.clinicPaymentsColPatient
import shifa.doctor.generated.resources.clinicPaymentsSearchHint
import shifa.doctor.generated.resources.clinicRecordStatusIssued
import shifa.doctor.generated.resources.clinicRecordStatusOverdue
import shifa.doctor.generated.resources.clinicRecordStatusPartiallyPaid
import shifa.doctor.generated.resources.clinicRecordStatusVoid
import shifa.doctor.generated.resources.clinicRecordTypeCreditNote
import shifa.doctor.generated.resources.clinicRecordTypeEstimate
import shifa.doctor.generated.resources.clinicRecordTypeInvoice
import shifa.doctor.generated.resources.clinicRecordTypeReceipt
import shifa.doctor.generated.resources.clinicRecordsColPaid
import shifa.doctor.generated.resources.clinicRecordsColRemaining
import shifa.doctor.generated.resources.clinicRecordsColStatus
import shifa.doctor.generated.resources.clinicRecordsColTotal
import shifa.doctor.generated.resources.clinicRecordsFormCreate
import shifa.doctor.generated.resources.clinicRecordsFormDueDate
import shifa.doctor.generated.resources.clinicRecordsFormNotes
import shifa.doctor.generated.resources.clinicRecordsFormPatient
import shifa.doctor.generated.resources.clinicRecordsFormPlanOptional
import shifa.doctor.generated.resources.clinicRecordsFormPlanTotalsHint
import shifa.doctor.generated.resources.clinicRecordsFormTitle
import shifa.doctor.generated.resources.clinicRecordsFormType
import shifa.doctor.generated.resources.clinicRecordsNewRecord
import shifa.doctor.generated.resources.clinicRecordsPickPatient
import shifa.doctor.generated.resources.clinicRecordsSearchHint
import shifa.doctor.generated.resources.clinicServiceCurrencyLabel
import shifa.doctor.generated.resources.draft
import shifa.doctor.generated.resources.ic_bell_16
import shifa.doctor.generated.resources.ic_chev_d_16
import shifa.doctor.generated.resources.ic_download_18
import shifa.doctor.generated.resources.ic_plus_16
import shifa.doctor.generated.resources.ic_wallet_18
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.visitsCount
import shifa.doctor.ui.calendar.monthTitle
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.patients.Choice
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.LocalWideLayout
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDatePickerDialog
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.Money
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.csv
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.rememberFileShare
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

/** What the finance content can do (the section wires them to [ClinicFinanceViewModel]; previews leave them empty). */
class FinanceActions(
    val view: (FinanceView) -> Unit = {},
    val month: (FinanceMonth?) -> Unit = {},
    val query: (String) -> Unit = {},
    val chip: (Int) -> Unit = {},
    val kpi: (FinanceKpi) -> Unit = {},
    val load: () -> Unit = {},
    val loadMore: () -> Unit = {},
    val payMenu: (LedgerRow?) -> Unit = {},
    val payFull: (LedgerRow, PayMethod) -> Unit = { _, _ -> },
    val customPay: (LedgerRow) -> Unit = {},
    val editPay: ((PaySheet) -> PaySheet) -> Unit = {},
    val savePay: () -> Unit = {},
    val closePay: () -> Unit = {},
    val installmentMenu: (InstallmentRow?) -> Unit = {},
    val openMarkPaid: (InstallmentRow) -> Unit = {},
    val editMarkPaid: ((MarkPaidSheet) -> MarkPaidSheet) -> Unit = {},
    val markPaid: () -> Unit = {},
    val closeMarkPaid: () -> Unit = {},
    val setStatus: (InstallmentRow, String) -> Unit = { _, _ -> },
    val remind: (InstallmentRow) -> Unit = {},
    val openRecord: () -> Unit = {},
    val editRecord: ((RecordSheet) -> RecordSheet) -> Unit = {},
    val searchPatient: (String) -> Unit = {},
    val pickPatient: (shifa.doctor.data.model.ClinicPatient) -> Unit = {},
    val pickPlan: (shifa.doctor.data.model.TreatmentPlanSummary?) -> Unit = {},
    val saveRecord: () -> Unit = {},
    val closeRecord: () -> Unit = {},
    val info: (InfoSheet?) -> Unit = {},
    val dismiss: () -> Unit = {},
    val export: (String, ByteArray) -> Boolean = { _, _ -> false },
    // A plan's balance (the dashboard's all-time Outstanding) opens the plan (TP-03).
    val openPlan: (Long) -> Unit = {},
)

@Composable
internal fun ColumnScope.FinanceSection(
    clinic: MyClinic,
    onOpenPlan: (Long) -> Unit = {},
    viewModel: ClinicFinanceViewModel =
        viewModel(key = "clinic-finance-${clinic.clinicId}") {
            ClinicFinanceViewModel(clinic, PracticeClock(clinic.timeZone).today())
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from a plan (a payment, a status change) or the app: the view reads again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    val share = rememberFileShare()
    val vm = viewModel
    FinanceContent(
        state,
        clinic,
        FinanceActions(
            vm::view, vm::month, vm::query, vm::chip, vm::kpi, vm::load, vm::loadMore, vm::payMenu, vm::payFull,
            vm::openCustomPay, vm::editPay, vm::savePay, vm::closePay, vm::installmentMenu, vm::openMarkPaid,
            vm::editMarkPaid, vm::markPaid, vm::closeMarkPaid, vm::setStatus, vm::remind, vm::openRecord,
            vm::editRecord, vm::searchPatient, vm::pickPatient, vm::pickPlan, vm::saveRecord, vm::closeRecord,
            vm::info, vm::dismiss,
            { name, bytes -> share(name, "text/csv", bytes) },
            onOpenPlan,
        ),
    )
}

/** Board `DaClinic` Finance (phone, cards): views, month and Export, search and chips, then the view's list. */
@Composable
internal fun ColumnScope.FinanceContent(
    state: ClinicFinanceUiState,
    clinic: MyClinic,
    actions: FinanceActions,
) {
    codeBanner(state.banner) { actions.dismiss() }?.let { it() }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        FinanceView.entries.forEach { v -> ShifaChip(tr(viewLabel(v)), state.view == v, { actions.view(v) }) }
    }
    val rows = csvRows(state)
    var exportError by remember { mutableStateOf<StringResource?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        if (state.byMonth) {
            Box(Modifier.weight(1f)) {
                MonthSelect(state.month, clinic, actions.month)
            }
        } else {
            Box(Modifier.weight(1f))
        }
        ShifaSecondaryButton(
            tr(Res.string.clinicFinanceExportCsv),
            {
                exportError =
                    when {
                        rows.size <= 1 -> Res.string.clinicFinanceExportNoRows
                        !actions.export(
                            "shifa_${state.view.name.lowercase()}.csv",
                            csv(rows),
                        ) -> Res.string.clinicFinanceExportFailed
                        else -> null
                    }
            },
            icon = Res.drawable.ic_download_18,
            size = ShifaButtonSize.Compact,
        )
    }
    exportError?.let { Text(tr(it), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Error) }
    searchHint(state.view)?.let { ShifaInputField(state.query, actions.query, tr(it), Modifier.fillMaxWidth()) }
    val chips = chipLabels(state.view)
    if (chips.isNotEmpty()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            chips.forEachIndexed { i, label -> ShifaChip(tr(label), state.chip == i, { actions.chip(i) }) }
        }
    }
    state.error?.let { e ->
        if (!state.viewLoaded) {
            // Nothing read yet: the view would be blank, so the error takes its place.
            ShifaLoadError(tr(e.text), actions.load, requestId = e.requestId)
            return
        }
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
            codeBanner(e)?.let { it() }
            ShifaLink(tr(Res.string.retry), actions.load)
        }
    }
    val canManage = clinic.mayManageFinance
    when (state.view) {
        FinanceView.Dashboard -> Dashboard(state, actions)
        FinanceView.Ledger -> Ledger(state, actions, canManage)
        FinanceView.Payments -> Payments(state, actions)
        FinanceView.Records -> Records(state, actions, canManage)
        FinanceView.Installments -> Installments(state, actions, canManage)
        FinanceView.Earnings -> Earnings(state)
    }
    state.payMenu?.let { PayMenu(it, actions) }
    state.pay?.let { PaySheetView(it, state.zone, actions) }
    state.installmentMenu?.let { InstallmentMenu(it, actions) }
    state.markPaid?.let { MarkPaidView(it, actions) }
    state.record?.let { RecordSheetView(it, clinic, actions) }
    state.info?.let { InfoSheetView(it, actions) }
}

private fun viewLabel(v: FinanceView): StringResource =
    when (v) {
        FinanceView.Dashboard -> Res.string.clinicFinanceDashboard
        FinanceView.Ledger -> Res.string.clinicFinanceByAppointment
        FinanceView.Installments -> Res.string.clinicFinanceInstallments
        FinanceView.Earnings -> Res.string.clinicFinanceDoctorEarnings
        FinanceView.Records -> Res.string.clinicFinanceRecords
        FinanceView.Payments -> Res.string.clinicFinancePayments
    }

private fun searchHint(v: FinanceView): StringResource? =
    when (v) {
        FinanceView.Dashboard -> null
        FinanceView.Ledger -> Res.string.clinicLedgerSearchHint
        FinanceView.Installments -> Res.string.clinicFinanceInstallSearchHint
        FinanceView.Earnings -> Res.string.clinicEarningsSearchHint
        FinanceView.Records -> Res.string.clinicRecordsSearchHint
        FinanceView.Payments -> Res.string.clinicPaymentsSearchHint
    }

private fun chipLabels(v: FinanceView): List<StringResource> =
    when (v) {
        FinanceView.Ledger ->
            listOf(
                Res.string.all,
                Res.string.clinicPaymentStatusPaid,
                Res.string.clinicPaymentStatusPartial,
                Res.string.clinicPaymentStatusUnpaid,
            )
        FinanceView.Payments ->
            listOf(
                Res.string.all,
                Res.string.clinicPaymentMethodCash,
                Res.string.clinicPaymentMethodCard,
                Res.string.clinicPaymentMethodTransfer,
                Res.string.clinicPaymentMethodOther,
            )
        FinanceView.Records ->
            listOf(
                Res.string.all,
                Res.string.clinicRecordStatusIssued,
                Res.string.clinicPaymentStatusPaid,
                Res.string.clinicRecordStatusPartiallyPaid,
                Res.string.clinicRecordStatusOverdue,
                Res.string.clinicRecordStatusVoid,
            )
        FinanceView.Installments ->
            listOf(
                Res.string.all,
                Res.string.clinicFinanceInstallFilterPending,
                Res.string.clinicFinanceInstallFilterOverdue,
                Res.string.clinicFinanceInstallFilterPaid,
            )
        else -> emptyList()
    }

/** All time, then this month and the 17 before it (the board's list), in the clinic's zone. */
@Composable
private fun MonthSelect(
    month: FinanceMonth?,
    clinic: MyClinic,
    onPick: (FinanceMonth?) -> Unit,
) {
    val current = remember(clinic.timeZone) { FinanceMonth.of(PracticeClock(clinic.timeZone).today()) }
    val months = remember(current) { generateSequence(current) { it.previous() }.take(MONTHS).toList() }
    val options = listOf<FinanceMonth?>(null) + months
    val labels =
        options.map {
                m ->
            m?.let { monthTitle(LocalDate(it.year, it.month, 1)) } ?: tr(Res.string.clinicFinanceMonthAllTime)
        }
    var open by remember { mutableStateOf(false) }
    ShifaDropdown(
        tr(Res.string.clinicFinanceMonthFilter),
        labels,
        labels[options.indexOf(month).coerceAtLeast(0)],
        onSelect = { i ->
            open = false
            onPick(options[i])
        },
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.fillMaxWidth(),
    )
}

// --- Dashboard (09 §3.2) ---

@Composable
private fun ColumnScope.Dashboard(
    state: ClinicFinanceUiState,
    actions: FinanceActions,
) {
    val d = state.dashboard
    if (d == null) {
        if (state.loading) repeat(2) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S36)) }
        return
    }
    val cards =
        listOf(
            Triple(
                FinanceKpi.Revenue,
                Res.string.clinicFinanceTotalRevenue,
                Money.format(d.totalRevenueMinor, d.currency),
            ),
            Triple(
                FinanceKpi.Outstanding,
                Res.string.clinicFinanceOutstanding,
                Money.format(d.outstandingMinor, d.currency),
            ),
            Triple(FinanceKpi.Overdue, Res.string.clinicFinanceOverdueCount, d.overdueCount.toString()),
            Triple(FinanceKpi.Rate, Res.string.clinicFinanceCollectionRate, percent(d.collectionPercent)),
        )
    // Wide: the four in a row.
    cards.chunked(if (LocalWideLayout.current) 4 else 2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
            pair.forEach { (k, label, value) ->
                KpiTile(
                    tr(label),
                    value,
                    tr(kpiHint(k)),
                    kpiColor(k),
                    state.kpi == k,
                    Modifier.weight(1f),
                ) { actions.kpi(k) }
            }
        }
    }
    when (state.kpi) {
        FinanceKpi.Revenue -> {
            ListTitle(Res.string.clinicFinanceDashboardDoctorEarningsTop)
            if (d.doctorEarningsTop.isEmpty()) Nothing(Res.string.clinicFinanceNothing)
            d.doctorEarningsTop.forEach { e ->
                FinanceRow(
                    tr(Res.string.clinicEarningsColCollected),
                    state.doctorName(e.doctorProfileId) ?: "#${e.doctorProfileId}",
                    null,
                    Money.format(e.collectedMinor, e.currency),
                    ShifaColors.Text.BrandStrong,
                ) { actions.view(FinanceView.Earnings) }
            }
        }
        FinanceKpi.Outstanding -> {
            ListTitle(Res.string.clinicFinanceDashboardOutstandingDetail)
            OwedList(state.balances, state, actions, Res.string.clinicFinanceDashboardNoOutstanding)
        }
        FinanceKpi.Overdue -> {
            ListTitle(Res.string.clinicFinanceDashboardOverdueDetail)
            if (state.month != null) {
                Nothing(Res.string.clinicFinanceDashboardNoOverdueMonth)
            } else {
                OwedList(state.overdue, state, actions, Res.string.clinicFinanceDashboardNoOverdue)
            }
        }
        FinanceKpi.Rate -> {
            ListTitle(Res.string.clinicFinanceHowCalculated)
            FinanceRow(
                tr(Res.string.clinicEarningsColCollected),
                tr(Res.string.clinicFinancePaymentsInPeriod),
                null,
                Money.format(d.totalRevenueMinor, d.currency),
            )
            // The backend sends the ratio only (clamped to 1): the charged amount isn't rebuilt from it.
            FinanceRow(
                tr(Res.string.clinicFinanceCollectionRate),
                tr(Res.string.clinicFinanceDashboardCollectionHint),
                null,
                percent(d.collectionPercent),
            )
        }
    }
}

/** The dashboard's Outstanding / Overdue rows: skeleton, nothing (the error is above), empty, or the rows. */
@Composable
private fun ColumnScope.OwedList(
    list: List<Owed>?,
    state: ClinicFinanceUiState,
    actions: FinanceActions,
    empty: StringResource,
) {
    when {
        list == null && state.error != null -> Unit
        list == null -> ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S24))
        list.isEmpty() -> Nothing(empty)
        else ->
            list.forEach { o ->
                val first =
                    listOfNotNull(
                        o.date?.let { date(it, state.zone) },
                        o.recordType?.let { tr(recordTypeLabel(it)) },
                        o.detail,
                    )
                FinanceRow(
                    first.joinToString(" · "),
                    o.name,
                    null,
                    Money.format(o.amountMinor, o.currency),
                    ShifaColors.Text.Error,
                    onClick =
                        o.planId?.let {
                                id ->
                            { actions.openPlan(id) }
                        } ?: o.view?.let {
                                v ->
                            { actions.view(v) }
                        },
                )
            }
    }
}

private fun kpiHint(k: FinanceKpi): StringResource =
    when (k) {
        FinanceKpi.Revenue -> Res.string.clinicFinanceTotalRevenueHint
        FinanceKpi.Outstanding -> Res.string.clinicFinanceDashboardOutstandingHint
        FinanceKpi.Overdue -> Res.string.clinicFinanceDashboardOverdueHint
        FinanceKpi.Rate -> Res.string.clinicFinanceDashboardCollectionHint
    }

private fun kpiColor(k: FinanceKpi): Color =
    when (k) {
        FinanceKpi.Revenue, FinanceKpi.Rate -> ShifaColors.Text.BrandStrong
        FinanceKpi.Outstanding -> ShifaColors.Text.Warning
        FinanceKpi.Overdue -> ShifaColors.Text.Error
    }

/** The board's finance KPI: label, the value in its colour, a hint; selected draws the focus ring. */
@Composable
private fun KpiTile(
    label: String,
    value: String,
    hint: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) = Column(
    modifier
        .background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .border(ShifaSize.S2, if (selected) ShifaColors.Border.Focus else Color.Transparent, ShifaRadius.ControlLg)
        .clickable(role = Role.Tab, onClick = onClick)
        .padding(ShifaSize.S12),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
) {
    Text(
        label,
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    // Money at half the phone's width: the section heading size, wrapping before it would cut the amount.
    Text(value, style = ShifaTextStyles.sectionHeading(), color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(
        hint,
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Secondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

// --- Ledger (09 §3.5) ---

@Composable
private fun ColumnScope.Ledger(
    state: ClinicFinanceUiState,
    actions: FinanceActions,
    canManage: Boolean,
) {
    val all = state.ledger
    if (!listState(all, state.loading)) return
    val shown = state.shownLedger
    Text(
        tr(
            Res.string.clinicFinanceShowingRows,
            shown.size.toString(),
            (state.ledgerTotal ?: all.orEmpty().size.toLong()).toString(),
        ),
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Secondary,
    )
    if (shown.isEmpty()) Nothing(Res.string.clinicFinanceNothing)
    // Strings for the click handlers, resolved here (a handler isn't composable).
    val servicesTitle = servicesTitle()
    val totalL = totalLabel()
    val paidL = paidLabel()
    shown.forEach { r ->
        val status = r.planSimplePaymentStatus?.uppercase()
        // Payable: a known plan, something still owed, and finance actions allowed (per visit, quirk 13).
        val canPay = canManage && r.treatmentPlanId != null && r.remainingMinor > 0
        val services = "${tr(Res.string.clinicLedgerColServices)}: ${r.services.size}"
        FinanceRow(
            ledgerDate(r, state.zone) + (r.doctorName?.let { " · $it" } ?: ""),
            r.patientName.orEmpty(),
            services + (r.treatmentPlanId?.let { " · " + tr(Res.string.clinicFinancePlanNumber, it.toString()) } ?: ""),
            Money.format(r.visitTotalMinor, r.currency),
            pill = Pill(tr(payStatusLabel(status)), payStatusBg(status), canPay) { actions.payMenu(r) },
            onClick = {
                actions.info(
                    InfoSheet(
                        servicesTitle,
                        r.services.map { it.title to Money.format(it.lineTotalMinor, r.currency) } +
                            listOf(
                                totalL to Money.format(r.visitTotalMinor, r.currency),
                                paidL to Money.format(r.visitCollectedMinor, r.currency),
                            ),
                    ),
                )
            },
        )
    }
    LoadMore(state, actions)
}

@Composable
private fun servicesTitle() = tr(Res.string.clinicFinanceVisitServices)

@Composable
private fun totalLabel() = tr(Res.string.clinicRecordsColTotal)

@Composable
private fun paidLabel() = tr(Res.string.clinicRecordsColPaid)

private fun payStatusLabel(status: String?): StringResource =
    when (status) {
        "PAID" -> Res.string.clinicPaymentStatusPaid
        "PARTIAL" -> Res.string.clinicPaymentStatusPartial
        "UNPAID" -> Res.string.clinicPaymentStatusUnpaid
        else -> Res.string.clinicPaymentStatusNone
    }

private fun payStatusBg(status: String?): Color =
    when (status) {
        "PAID" -> ShifaColors.Bg.SuccessSubtle
        "PARTIAL" -> ShifaColors.Bg.WarningSubtle
        "UNPAID" -> ShifaColors.Bg.ErrorSubtle
        else -> ShifaColors.Bg.Canvas
    }

// --- Payments (09 §3.3) ---

@Composable
private fun ColumnScope.Payments(
    state: ClinicFinanceUiState,
    actions: FinanceActions,
) {
    val all = state.payments
    if (!listState(all, state.loading)) return
    val shown = state.shownPayments
    if (shown.isNotEmpty()) {
        val currency = shown.first().currency.ifBlank { "UZS" }
        Text(
            "${shown.size} · " +
                Money.format(
                    shown.filter { it.currency == currency }.sumOf { it.amountMinor },
                    currency,
                ),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    } else {
        Nothing(Res.string.clinicFinanceNothing)
    }
    val idL = tr(Res.string.clinicPaymentsColId)
    val patientL = label(Res.string.clinicPaymentsColPatient)
    val amountL = label(Res.string.clinicPaymentsColAmount)
    val methodL = label(Res.string.clinicPaymentsColMethod)
    val dateL = label(Res.string.clinicPaymentsColDate)
    val doctorL = label(Res.string.clinicPaymentsColDoctor)
    val memoL = label(Res.string.clinicPaymentsColMemo)
    shown.forEach { p ->
        val method = methodLabel(p.method)
        FinanceRow(
            listOfNotNull(p.recordedAt?.let { date(it, state.zone) }, method, p.doctorName).joinToString(" · "),
            p.patientName.orEmpty(),
            listOfNotNull(
                p.treatmentPlanTitle?.let {
                        t ->
                    p.treatmentPlanId?.let { "$t (#$it)" } ?: t
                },
                p.memo?.takeIf(String::isNotBlank),
            )
                .joinToString(" · ").ifEmpty { null },
            Money.format(p.amountMinor, p.currency),
            ShifaColors.Text.BrandStrong,
            icon = Res.drawable.ic_wallet_18,
            onClick = {
                actions.info(
                    InfoSheet(
                        "$idL ${p.id}",
                        listOfNotNull(
                            p.patientName?.let { patientL to it },
                            amountL to Money.format(p.amountMinor, p.currency),
                            methodL to method,
                            p.recordedAt?.let { dateL to date(it, state.zone) },
                            p.doctorName?.let { doctorL to it },
                            p.memo?.takeIf(String::isNotBlank)?.let { memoL to it },
                        ),
                    ),
                )
            },
        )
    }
    LoadMore(state, actions)
}

@Composable
private fun label(r: StringResource) = tr(r)

@Composable
private fun methodLabel(wire: String): String =
    when (wire.uppercase()) {
        "CASH" -> tr(Res.string.clinicPaymentMethodCash)
        "CARD_EXTERNAL" -> tr(Res.string.clinicPaymentMethodCard)
        "TRANSFER" -> tr(Res.string.clinicPaymentMethodTransfer)
        "CLICK" -> tr(Res.string.clinicPaymentMethodClick)
        "WAIVED" -> tr(Res.string.clinicFinanceInstallStatusWaived)
        else -> tr(Res.string.clinicPaymentMethodOther)
    }

// --- Records (09 §3.4) ---

@Composable
private fun ColumnScope.Records(
    state: ClinicFinanceUiState,
    actions: FinanceActions,
    canManage: Boolean,
) {
    if (canManage) {
        ShifaPrimaryButton(
            tr(Res.string.clinicRecordsNewRecord),
            actions.openRecord,
            icon = Res.drawable.ic_plus_16,
            size = ShifaButtonSize.Compact,
        )
    }
    val all = state.records
    if (!listState(all, state.loading)) return
    val shown = state.shownRecords
    if (shown.isEmpty()) Nothing(Res.string.clinicFinanceNothing)
    shown.forEach { r ->
        val created = tr(Res.string.clinicFinanceCreatedOn, date(r.createdAt, state.zone))
        FinanceRow(
            created + (r.dueDate?.let { " · " + tr(Res.string.clinicFinanceDueOn, date(it, state.zone)) } ?: ""),
            listOfNotNull(tr(recordTypeLabel(r.recordType)), r.recordNumber).joinToString(" "),
            "${totalLabel()} ${Money.format(
                r.totalMinor,
                r.currency,
            )} · ${paidLabel()} ${Money.format(r.paidMinor, r.currency)} · " +
                "${tr(Res.string.clinicRecordsColRemaining)} ${Money.format(r.remainingMinor, r.currency)}" +
                (r.notes?.takeIf(String::isNotBlank)?.let { " · $it" } ?: ""),
            Money.format(r.totalMinor, r.currency),
            pill = Pill(tr(recordStatusLabel(r.status)), recordStatusBg(r.status), false) {},
        )
    }
    LoadMore(state, actions)
}

internal fun recordTypeLabel(type: String): StringResource =
    when (type.uppercase()) {
        "RECEIPT" -> Res.string.clinicRecordTypeReceipt
        "ESTIMATE" -> Res.string.clinicRecordTypeEstimate
        "CREDIT_NOTE" -> Res.string.clinicRecordTypeCreditNote
        else -> Res.string.clinicRecordTypeInvoice
    }

/** Localized always (quirk 3: Flutter showed the raw status when the UI status was present). */
private fun recordStatusLabel(status: String): StringResource =
    when (status.uppercase()) {
        "ISSUED" -> Res.string.clinicRecordStatusIssued
        "PAID" -> Res.string.clinicPaymentStatusPaid
        "PARTIALLY_PAID" -> Res.string.clinicRecordStatusPartiallyPaid
        "OVERDUE" -> Res.string.clinicRecordStatusOverdue
        "VOIDED", "VOID" -> Res.string.clinicRecordStatusVoid
        "CANCELLED" -> Res.string.clinicFinanceInstallStatusCancelled
        else -> Res.string.draft
    }

private fun recordStatusBg(status: String): Color =
    when (status.uppercase()) {
        "PAID" -> ShifaColors.Bg.SuccessSubtle
        "PARTIALLY_PAID" -> ShifaColors.Bg.WarningSubtle
        "OVERDUE" -> ShifaColors.Bg.ErrorSubtle
        "ISSUED" -> ShifaColors.Bg.InfoSubtle
        else -> ShifaColors.Bg.Canvas
    }

// --- Installments (09 §3.7) ---

@Composable
private fun ColumnScope.Installments(
    state: ClinicFinanceUiState,
    actions: FinanceActions,
    canManage: Boolean,
) {
    val all = state.installments
    if (!listState(all, state.loading)) return
    val shown = state.shownInstallments
    if (shown.isNotEmpty()) {
        val currency = shown.first().currency
        val scheduled =
            shown.filter {
                it.currency == currency && it.status.uppercase() !in
                    setOf(
                        "CANCELLED",
                        "WAIVED",
                    )
            }
        val paid = scheduled.filter { it.status.uppercase() == "PAID" }.sumOf { it.amountMinor }
        val total = scheduled.sumOf { it.amountMinor }
        // Totals are read, not picked: they wrap so none hides off the edge (a scroll row did on phones).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            listOf(
                "${tr(Res.string.clinicFinanceInstallTotalsItems)}: ${shown.size}",
                "${tr(Res.string.clinicFinanceInstallTotalsScheduled)}: ${Money.format(total, currency)}",
                "${tr(Res.string.clinicFinanceInstallTotalsPaidSum)}: ${Money.format(paid, currency)}",
                "${tr(Res.string.clinicFinanceInstallTotalsOutstanding)}: ${Money.format(total - paid, currency)}",
            ).forEach { ProfileChip(it, ShifaColors.Bg.Surface, ShifaColors.Text.Primary) }
        }
    } else {
        Nothing(Res.string.clinicFinanceNothing)
    }
    shown.forEach { r ->
        val status = r.status.uppercase()
        // A paid installment is final here (FIN-05: PAID rows locked); the others change through the menu.
        val editable = canManage && status != "PAID"
        FinanceRow(
            listOfNotNull(
                r.treatmentPlanTitle,
                tr(Res.string.clinicFinanceInstallmentNumber, r.sequenceNumber.toString()),
            ).joinToString(" · "),
            r.patientName.orEmpty(),
            tr(Res.string.clinicFinanceDueOn, date(r.dueDate, state.zone)),
            Money.format(r.amountMinor, r.currency),
            if (status == "OVERDUE") ShifaColors.Text.Error else ShifaColors.Text.Primary,
            pill = Pill(tr(installmentLabel(status)), installmentBg(status), editable) { actions.installmentMenu(r) },
            bell = if (canManage && status in setOf("PENDING", "OVERDUE")) ({ actions.remind(r) }) else null,
        )
    }
}

private fun installmentLabel(status: String): StringResource =
    when (status) {
        "PAID" -> Res.string.clinicFinanceInstallStatusPaid
        "OVERDUE" -> Res.string.clinicFinanceInstallStatusOverdue
        "WAIVED" -> Res.string.clinicFinanceInstallStatusWaived
        "CANCELLED" -> Res.string.clinicFinanceInstallStatusCancelled
        else -> Res.string.clinicFinanceInstallStatusPending
    }

private fun installmentBg(status: String): Color =
    when (status) {
        "PAID" -> ShifaColors.Bg.SuccessSubtle
        "OVERDUE" -> ShifaColors.Bg.ErrorSubtle
        "WAIVED" -> ShifaColors.Bg.InfoSubtle
        "CANCELLED" -> ShifaColors.Bg.Canvas
        else -> ShifaColors.Bg.WarningSubtle
    }

// --- Doctor earnings (09 §3.6) ---

@Composable
private fun ColumnScope.Earnings(state: ClinicFinanceUiState) {
    val all = state.earnings
    if (!listState(all, state.loading)) return
    val shown = state.shownEarnings
    if (shown.isEmpty()) Nothing(Res.string.clinicFinanceNothing)
    shown.forEach { e ->
        val share = e.revenueSharePercent
        FinanceRow(
            trPlural(Res.plurals.visitsCount, e.visitCount, e.visitCount.toString()) + " · " +
                (
                    share?.let {
                        tr(
                            Res.string.clinicEarningsShare,
                            it.toString(),
                        )
                    } ?: tr(Res.string.clinicEarningsShareNotSet)
                ),
            state.doctorName(e.doctorProfileId) ?: "#${e.doctorProfileId}",
            earningLine(e),
            Money.format(e.collectedMinor, e.currency),
            ShifaColors.Text.BrandStrong,
        )
    }
}

@Composable
private fun earningLine(e: DoctorEarning): String =
    listOfNotNull(
        "${tr(Res.string.clinicEarningsColGross)} ${Money.format(e.grossMinor, e.currency)}",
        "${tr(Res.string.clinicEarningsColCollected)} ${Money.format(e.collectedMinor, e.currency)}",
        "${tr(Res.string.clinicEarningsColOutstanding)} ${Money.format(e.outstandingMinor, e.currency)}",
        e.doctorShareCollectedMinor?.let {
            "${tr(Res.string.clinicEarningsColDoctorShareCollected)} ${Money.format(it, e.currency)}"
        },
        e.clinicShareCollectedMinor?.let {
            "${tr(Res.string.clinicEarningsColClinicShareCollected)} ${Money.format(it, e.currency)}"
        },
    ).joinToString(" · ")

// --- Shared pieces ---

/** Skeletons until the first page, nothing more while it's missing (the error above says why); true to draw rows. */
@Composable
private fun ColumnScope.listState(
    rows: List<*>?,
    loading: Boolean,
): Boolean {
    if (rows != null) return true
    if (loading) repeat(SKELETON_ROWS) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S28)) }
    return false
}

@Composable
private fun LoadMore(
    state: ClinicFinanceUiState,
    actions: FinanceActions,
) {
    if (!state.last) {
        ShifaSecondaryButton(
            tr(Res.string.clinicFinanceLoadMore),
            actions.loadMore,
            Modifier.fillMaxWidth(),
            loading = state.loadingMore,
        )
    }
}

@Composable
private fun ListTitle(text: StringResource) =
    Text(tr(text), style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)

@Composable
private fun Nothing(text: StringResource) =
    Text(
        tr(text),
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S24),
        style = ShifaTextStyles.bodyText(),
        color = ShifaColors.Text.Secondary,
    )

/** A row's status pill; [open] (a menu) only when [enabled], with the caret then. */
class Pill(
    val label: String,
    val background: Color,
    val enabled: Boolean,
    val open: () -> Unit,
)

/** The board's finance card: an optional icon, three lines (l1 small, l2 bold, l3 small), the amount and a pill. */
@Composable
private fun FinanceRow(
    l1: String,
    l2: String,
    l3: String?,
    amount: String,
    amountColor: Color = ShifaColors.Text.Primary,
    icon: DrawableResource? = null,
    pill: Pill? = null,
    bell: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = Row(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    icon?.let {
        Box(
            Modifier.size(ShifaSize.S36).background(ShifaColors.Bg.Canvas, ShifaRadius.Full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(it), null, tint = ShifaColors.Icon.Secondary)
        }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            l1,
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            l2,
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        l3?.let {
            Text(
                it,
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        Text(amount, style = ShifaTextStyles.buttonCompact(), color = amountColor, maxLines = 1)
        pill?.let { p ->
            Row(
                Modifier.background(p.background, ShifaRadius.Full)
                    .then(if (p.enabled) Modifier.clickable(role = Role.Button, onClick = p.open) else Modifier)
                    .padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S2),
            ) {
                Text(p.label, style = ShifaTextStyles.tag(), color = ShifaColors.Text.Primary)
                if (p.enabled) {
                    Icon(
                        painterResource(Res.drawable.ic_chev_d_16),
                        null,
                        Modifier.size(ShifaSize.S12),
                        tint = ShifaColors.Icon.Secondary,
                    )
                }
            }
        }
    }
    bell?.let {
        ShifaIconButton(
            Res.drawable.ic_bell_16,
            tr(Res.string.clinicFinanceNotifyInstallment),
            it,
            variant = ShifaIconButtonVariant.Secondary,
        )
    }
}

private fun ledgerDate(
    r: LedgerRow,
    zone: String?,
) = date(r.startAt, zone)

/**
 * dd.MM.yyyy: an instant (a visit's start, a payment) as its day in the clinic's [zone]; a plain date (a due date) as
 * sent; anything else as written.
 */
private fun date(
    iso: String,
    zone: String?,
): String =
    PracticeClock.parseInstant(iso)?.let { displayDate(PracticeClock(zone).local(it).date) }
        ?: runCatching { displayDate(LocalDate.parse(iso.take(DATE_LENGTH))) }.getOrDefault(iso)

private fun percent(p: Double): String {
    val tenths = kotlin.math.round(p * 10).toLong()
    return "${tenths / 10}.${tenths % 10}%"
}

// --- Sheets ---

@Composable
private fun PayMenu(
    row: LedgerRow,
    actions: FinanceActions,
) = ProfileSheet({ actions.payMenu(null) }) {
    ShifaTray(tr(Res.string.clinicLedgerPayMenu), { actions.payMenu(null) }) {
        listOf(
            PayMethod.Cash to Res.string.clinicFinancePayByCash,
            PayMethod.Card to Res.string.clinicFinancePayByCard,
            PayMethod.Transfer to Res.string.clinicFinancePayByTransfer,
            PayMethod.Other to Res.string.clinicFinancePayByOther,
        ).forEach { (m, label) ->
            ShifaSettingsItem(
                tr(label),
                { actions.payFull(row, m) },
                subtitle = Money.format(row.remainingMinor, row.currency),
            )
        }
        ShifaSettingsItem(tr(Res.string.clinicFinancePayCustomAmount), { actions.customPay(row) }, divider = false)
    }
}

@Composable
internal fun MethodChips(
    selected: PayMethod,
    enabled: Boolean,
    onPick: (PayMethod) -> Unit,
) = Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    PayMethod.entries.forEach { m -> ShifaChip(methodLabel(m.wire), selected == m, { if (enabled) onPick(m) }) }
}

@Composable
private fun PaySheetView(
    sheet: PaySheet,
    zone: String?,
    actions: FinanceActions,
) = ProfileSheet({ actions.closePay() }, confirmHide = { !sheet.busy }) {
    ShifaDialogSheet(
        tr(Res.string.clinicFinancePaymentDialogTitle),
        tr(Res.string.clinicFinancePaymentConfirm),
        actions.savePay,
        body = listOfNotNull(sheet.row.patientName, date(sheet.row.startAt, zone)).joinToString(" · "),
        cancelText = tr(Res.string.cancel),
        onCancel = actions.closePay,
        confirmLoading = sheet.busy,
    ) {
        val bad = sheet.tried && sheet.minor == null
        ShifaInputField(
            sheet.amount,
            { v -> actions.editPay { it.copy(amount = v.filter { c -> c.isDigit() || c in " .," }.take(AMOUNT_MAX)) } },
            "${tr(Res.string.clinicFinancePaymentAmountLabel)} (${sheet.row.currency})",
            Modifier.fillMaxWidth(),
            supportingText = if (bad) tr(Res.string.clinicFinanceInvalidAmount) else null,
            isError = bad,
            enabled = !sheet.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        MethodChips(sheet.method, !sheet.busy) { m -> actions.editPay { it.copy(method = m) } }
        ShifaInputField(
            sheet.memo,
            { v -> actions.editPay { it.copy(memo = v.take(MEMO_MAX)) } },
            tr(Res.string.clinicFinancePaymentMemoLabel),
            Modifier.fillMaxWidth(),
            enabled = !sheet.busy,
        )
        sheet.error?.let { e -> codeBanner(e)?.let { it() } }
    }
}

@Composable
private fun InstallmentMenu(
    row: InstallmentRow,
    actions: FinanceActions,
) = ProfileSheet({ actions.installmentMenu(null) }) {
    ShifaTray(
        listOfNotNull(
            row.patientName,
            tr(Res.string.clinicFinanceInstallmentNumber, row.sequenceNumber.toString()),
        ).joinToString(" · "),
        { actions.installmentMenu(null) },
    ) {
        // The backend refuses to mark a cancelled installment paid: reopen it (Pending) first.
        if (row.status.uppercase() != "CANCELLED") {
            ShifaSettingsItem(tr(Res.string.clinicFinanceMarkInstallmentPaid), { actions.openMarkPaid(row) })
        }
        // OVERDUE is the backend's to set; the others are the statuses a person chooses (FIN-05).
        listOf(
            "PENDING" to Res.string.clinicFinanceInstallStatusPending,
            "WAIVED" to Res.string.clinicFinanceInstallStatusWaived,
            "CANCELLED" to Res.string.clinicFinanceInstallStatusCancelled,
        ).filter { (s, _) -> s != row.status.uppercase() }.forEachIndexed { i, (s, label) ->
            ShifaSettingsItem(tr(label), { actions.setStatus(row, s) }, destructive = s == "CANCELLED", divider = i < 1)
        }
    }
}

@Composable
private fun MarkPaidView(
    sheet: MarkPaidSheet,
    actions: FinanceActions,
) = ProfileSheet({ actions.closeMarkPaid() }, confirmHide = { !sheet.busy }) {
    ShifaDialogSheet(
        tr(Res.string.clinicFinanceMarkInstallmentPaid),
        tr(Res.string.clinicFinancePaymentConfirm),
        actions.markPaid,
        body =
            listOfNotNull(
                sheet.row.patientName,
                Money.format(sheet.row.amountMinor, sheet.row.currency),
            ).joinToString(" · "),
        cancelText = tr(Res.string.cancel),
        onCancel = actions.closeMarkPaid,
        confirmLoading = sheet.busy,
    ) {
        MethodChips(sheet.method, !sheet.busy) { m -> actions.editMarkPaid { it.copy(method = m) } }
        ShifaInputField(
            sheet.memo,
            { v -> actions.editMarkPaid { it.copy(memo = v.take(MEMO_MAX)) } },
            tr(Res.string.clinicFinancePaymentMemoLabel),
            Modifier.fillMaxWidth(),
            enabled = !sheet.busy,
        )
        sheet.error?.let { e -> codeBanner(e)?.let { it() } }
    }
}

@Composable
private fun RecordSheetView(
    sheet: RecordSheet,
    clinic: MyClinic,
    actions: FinanceActions,
) = ProfileSheet({ actions.closeRecord() }, confirmHide = { !sheet.busy }) {
    val currency = clinic.currency?.takeIf { it.isNotBlank() } ?: "UZS"
    ShifaDialogSheet(
        tr(Res.string.clinicRecordsFormTitle),
        tr(Res.string.clinicRecordsFormCreate),
        actions.saveRecord,
        cancelText = tr(Res.string.cancel),
        onCancel = actions.closeRecord,
        confirmLoading = sheet.busy,
    ) {
        val enabled = !sheet.busy
        val patient = sheet.patient
        val noPatient = sheet.tried && patient == null
        ShifaInputField(
            patient?.fullName ?: sheet.query,
            actions.searchPatient,
            tr(Res.string.clinicRecordsFormPatient),
            Modifier.fillMaxWidth(),
            supportingText =
                if (noPatient) {
                    tr(Res.string.clinicRecordsPickPatient)
                } else if (patient == null) {
                    tr(Res.string.clinicPatientsSearchHint)
                } else {
                    null
                },
            isError = noPatient,
            enabled = enabled,
        )
        if (patient == null) {
            sheet.matches.orEmpty().forEach { p ->
                ShifaSettingsItem(
                    p.fullName ?: "#${p.patientId}",
                    { actions.pickPatient(p) },
                    subtitle =
                        listOfNotNull(
                            p.phone?.let(PhoneE164::display),
                            p.email,
                        ).joinToString(" · ").ifEmpty { null },
                )
            }
        }
        Choice(
            tr(Res.string.clinicRecordsFormType),
            RECORD_TYPES.map { tr(recordTypeLabel(it)) },
            RECORD_TYPES.indexOf(sheet.type),
            enabled,
        ) { i -> actions.editRecord { it.copy(type = RECORD_TYPES[i]) } }
        // The patient's plans: a chosen one gives the record its totals and payments (09 §3.4).
        val plans = sheet.plans.orEmpty()
        if (patient != null && plans.isNotEmpty()) {
            val options = listOf<shifa.doctor.data.model.TreatmentPlanSummary?>(null) + plans
            Choice(
                tr(Res.string.clinicRecordsFormPlanOptional),
                options.map { p -> p?.let { it.title?.takeIf(String::isNotBlank) ?: "#${it.id}" } ?: "—" },
                options.indexOf(sheet.plan),
                enabled,
            ) { i -> actions.pickPlan(options[i]) }
        }
        if (sheet.plan != null) {
            Text(
                tr(Res.string.clinicRecordsFormPlanTotalsHint),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
        val badAmount =
            sheet.plan == null && sheet.tried && Money.parse(
                sheet.amount,
                currency,
            )?.takeIf { it > 0 } == null
        if (sheet.plan == null) {
            ShifaInputField(
                sheet.amount,
                {
                        v ->
                    actions.editRecord {
                        it.copy(
                            amount =
                                v.filter {
                                        c ->
                                    c.isDigit() || c in " .,"
                                }.take(AMOUNT_MAX),
                        )
                    }
                },
                "${tr(Res.string.clinicRecordsColTotal)} ($currency)",
                Modifier.fillMaxWidth(),
                supportingText = if (badAmount) tr(Res.string.clinicFinanceInvalidAmount) else null,
                isError = badAmount,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        ShifaSettingsItem(
            tr(Res.string.clinicRecordsFormDueDate),
            { if (enabled) actions.editRecord { it.copy(pickingDue = true) } },
            subtitle = sheet.due?.let(::displayDate) ?: "—",
            divider = false,
        )
        ShifaInputField(
            sheet.notes,
            { v -> actions.editRecord { it.copy(notes = v.take(NOTES_MAX)) } },
            tr(Res.string.clinicRecordsFormNotes),
            Modifier.fillMaxWidth(),
            enabled = enabled,
            multiline = true,
        )
        sheet.error?.let { e -> codeBanner(e)?.let { it() } }
    }
    if (sheet.pickingDue) {
        val today = remember(clinic.timeZone) { PracticeClock(clinic.timeZone).today() }
        ShifaDatePickerDialog(sheet.due ?: today, today.year..(today.year + DUE_YEARS), { picked ->
            actions.editRecord { it.copy(pickingDue = false, due = picked ?: it.due) }
        }, selectable = { it >= today })
    }
}

@Composable
private fun InfoSheetView(
    sheet: InfoSheet,
    actions: FinanceActions,
) = ProfileSheet({ actions.info(null) }) {
    ShifaTray(sheet.title, { actions.info(null) }) {
        sheet.lines.forEachIndexed {
                i,
                (k, v),
            ->
            ShifaSettingsItem(k, {}, subtitle = v, divider = i < sheet.lines.lastIndex)
        }
    }
}

// --- CSV (FIN-07): the current view's rows as shown, headers in the app's language ---

@Composable
private fun csvRows(state: ClinicFinanceUiState): List<List<String>> {
    val currencyL = tr(Res.string.clinicServiceCurrencyLabel)
    val dateL = tr(Res.string.clinicPaymentsColDate)
    val patientL = tr(Res.string.clinicPaymentsColPatient)
    val doctorL = tr(Res.string.clinicPaymentsColDoctor)
    val totalL = totalLabel()
    val paidL = paidLabel()
    // Every amount has its currency beside it (flow 09/13: mixed currencies stay apart).
    return when (state.view) {
        FinanceView.Dashboard ->
            state.dashboard?.let { d ->
                listOf(
                    listOf(tr(Res.string.clinicFinanceDashboard), "", currencyL),
                    listOf(tr(Res.string.clinicFinanceTotalRevenue), minor(d.totalRevenueMinor), d.currency),
                    listOf(tr(Res.string.clinicFinanceOutstanding), minor(d.outstandingMinor), d.currency),
                    listOf(tr(Res.string.clinicFinanceOverdueCount), d.overdueCount.toString(), ""),
                    listOf(tr(Res.string.clinicFinanceCollectionRate), percent(d.collectionPercent), ""),
                )
            }.orEmpty()
        FinanceView.Ledger ->
            listOf(
                listOf(dateL, patientL, doctorL, tr(Res.string.clinicFinancePlanNumber, ""), totalL, paidL, currencyL),
            ) +
                state.shownLedger.map {
                    listOf(
                        date(it.startAt, state.zone),
                        it.patientName.orEmpty(),
                        it.doctorName.orEmpty(),
                        it.treatmentPlanId?.toString().orEmpty(),
                        minor(it.visitTotalMinor),
                        minor(it.visitCollectedMinor),
                        it.currency,
                    )
                }
        FinanceView.Payments ->
            listOf(
                listOf(
                    tr(Res.string.clinicPaymentsColId),
                    dateL,
                    patientL,
                    doctorL,
                    tr(Res.string.clinicPaymentsColMethod),
                    tr(Res.string.clinicPaymentsColAmount),
                    currencyL,
                    tr(Res.string.clinicPaymentsColMemo),
                ),
            ) +
                state.shownPayments.map {
                    listOf(
                        it.id.toString(),
                        it.recordedAt?.let {
                                d ->
                            date(d, state.zone)
                        }.orEmpty(),
                        it.patientName.orEmpty(),
                        it.doctorName.orEmpty(),
                        it.method,
                        minor(it.amountMinor),
                        it.currency,
                        it.memo.orEmpty(),
                    )
                }
        FinanceView.Records ->
            listOf(
                listOf(
                    tr(Res.string.clinicRecordsFormType),
                    "#",
                    dateL,
                    tr(Res.string.clinicRecordsColStatus),
                    totalL,
                    paidL,
                    tr(Res.string.clinicRecordsColRemaining),
                    currencyL,
                ),
            ) +
                state.shownRecords.map {
                    listOf(
                        it.recordType,
                        it.recordNumber.orEmpty(),
                        date(it.createdAt, state.zone),
                        it.status,
                        minor(it.totalMinor),
                        minor(it.paidMinor),
                        minor(it.remainingMinor),
                        it.currency,
                    )
                }
        FinanceView.Installments ->
            listOf(
                listOf(
                    patientL,
                    tr(Res.string.clinicFinanceInstallColPlan),
                    "#",
                    tr(Res.string.clinicFinanceInstallColDue),
                    tr(Res.string.clinicPaymentsColAmount),
                    currencyL,
                    tr(Res.string.clinicFinanceInstallColStatus),
                ),
            ) +
                state.shownInstallments.map {
                    listOf(
                        it.patientName.orEmpty(),
                        it.treatmentPlanTitle.orEmpty(),
                        it.sequenceNumber.toString(),
                        it.dueDate,
                        minor(it.amountMinor),
                        it.currency,
                        it.status,
                    )
                }
        // FIN-06: everything a reconciliation needs, the split included.
        FinanceView.Earnings ->
            listOf(
                listOf(
                    doctorL, tr(Res.string.clinicEarningsColVisits), tr(Res.string.clinicEarningsColSharePercent),
                    tr(
                        Res.string.clinicEarningsColGross,
                    ),
                    tr(Res.string.clinicEarningsColCollected), tr(Res.string.clinicEarningsColOutstanding),
                    tr(Res.string.clinicEarningsColDoctorShareGross), tr(Res.string.clinicEarningsColClinicShareGross),
                    tr(
                        Res.string.clinicEarningsColDoctorShareCollected,
                    ),
                    tr(Res.string.clinicEarningsColClinicShareCollected), currencyL,
                ),
            ) +
                state.shownEarnings.map {
                    listOf(
                        state.doctorName(it.doctorProfileId) ?: "#${it.doctorProfileId}", it.visitCount.toString(),
                        it.revenueSharePercent?.toString().orEmpty(), minor(it.grossMinor), minor(it.collectedMinor),
                        minor(it.outstandingMinor), it.doctorShareGrossMinor?.let(::minor).orEmpty(),
                        it.clinicShareGrossMinor?.let(
                            ::minor,
                        ).orEmpty(),
                        it.doctorShareCollectedMinor?.let(::minor).orEmpty(),
                        it.clinicShareCollectedMinor?.let(::minor).orEmpty(), it.currency,
                    )
                }
    }
}

/** Amounts in the CSV as plain major units with two decimals (quirk 5: Flutter truncated tiyin). */
private fun minor(v: Long): String {
    val sign = if (v < 0) "-" else ""
    val a = kotlin.math.abs(v)
    return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
}

private const val MONTHS = 18
private const val DATE_LENGTH = 10
private const val AMOUNT_MAX = 20
private const val MEMO_MAX = 500
private const val NOTES_MAX = 1000
private const val DUE_YEARS = 3
