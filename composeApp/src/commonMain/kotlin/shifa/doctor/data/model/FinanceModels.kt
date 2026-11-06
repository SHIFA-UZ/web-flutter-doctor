package shifa.doctor.data.model

import kotlinx.serialization.Serializable

// The clinic's finance (FIN-01, 09 §3, §8; dev OpenAPI 2026-11-06). Amounts are Long minor units; every row is read
// on its own elsewhere, so one bad row never drops a list (quirk 4: Flutter cast with `as int`).

/** `FinanceDashboardDto`: all-time or month-scoped totals; the share totals only when month-scoped. */
@Serializable
data class FinanceDashboard(
    val totalRevenueMinor: Long = 0,
    val outstandingMinor: Long = 0,
    val overdueCount: Int = 0,
    // Collected / expected, 0–1 (the backend clamps it).
    val collectionRate: Double = 0.0,
    val currency: String = "UZS",
    val doctorEarningsTop: List<DoctorEarning> = emptyList(),
    val totalDoctorShareGrossMinor: Long? = null,
    val totalClinicShareGrossMinor: Long? = null,
    val totalDoctorShareCollectedMinor: Long? = null,
    val totalClinicShareCollectedMinor: Long? = null,
) {
    val collectionPercent: Double get() = collectionRate * 100
}

/** `DoctorEarningRowDto`: a doctor's visits, gross, collected, outstanding and the split (when a share is set). */
@Serializable
data class DoctorEarning(
    val doctorProfileId: Long = 0,
    val visitCount: Int = 0,
    val grossMinor: Long = 0,
    val collectedMinor: Long = 0,
    val outstandingMinor: Long = 0,
    val currency: String = "UZS",
    val revenueSharePercent: Int? = null,
    val doctorShareGrossMinor: Long? = null,
    val clinicShareGrossMinor: Long? = null,
    val doctorShareCollectedMinor: Long? = null,
    val clinicShareCollectedMinor: Long? = null,
)

/** A row of `GET …/finance/appointment-ledger` (`AppointmentLedgerRowDto`): one billable visit. */
@Serializable
data class LedgerRow(
    val appointmentId: Long = 0,
    val startAt: String = "",
    val patientId: Long? = null,
    val patientName: String? = null,
    val doctorProfileId: Long? = null,
    val doctorName: String? = null,
    val treatmentPlanId: Long? = null,
    val services: List<LedgerService> = emptyList(),
    val visitTotalMinor: Long = 0,
    val visitCollectedMinor: Long = 0,
    val currency: String = "UZS",
    val planPaymentStatus: String? = null,
    // Per visit (the backend computes it from the visit's total and collected): NONE, UNPAID, PARTIAL, PAID.
    val planSimplePaymentStatus: String? = null,
) {
    val remainingMinor: Long get() = (visitTotalMinor - visitCollectedMinor).coerceAtLeast(0)
}

@Serializable
data class LedgerService(
    val title: String = "",
    val lineTotalMinor: Long = 0,
)

/** `FinancialRecordDto`: an invoice, receipt, estimate or credit note. */
@Serializable
data class FinancialRecord(
    val id: Long = 0,
    val patientId: Long? = null,
    val treatmentPlanId: Long? = null,
    val recordType: String = "",
    val recordNumber: String? = null,
    val status: String = "",
    // NONE, UNPAID, PARTIAL, PAID (from total and paid); shown localized, never raw (quirk 3).
    val uiPaymentStatus: String? = null,
    val totalMinor: Long = 0,
    val paidMinor: Long = 0,
    val remainingMinor: Long = 0,
    val currency: String = "UZS",
    val createdAt: String = "",
    val issuedAt: String? = null,
    val dueDate: String? = null,
    val notes: String? = null,
)

/** `CreateFinancialRecordRequest` (the board's "New record": no discount or tax lines). */
@Serializable
data class NewFinancialRecord(
    val patientId: Long,
    val recordType: String,
    val subtotalMinor: Long,
    val currency: String,
    val discountMinor: Long = 0,
    val taxMinor: Long = 0,
    val treatmentPlanId: Long? = null,
    val dueDate: String? = null,
    val notes: String? = null,
)

/** `InstallmentItemListDto`: one installment of a plan's schedule. */
@Serializable
data class InstallmentRow(
    val id: Long = 0,
    val installmentPlanId: Long? = null,
    val treatmentPlanId: Long? = null,
    val treatmentPlanTitle: String? = null,
    val patientId: Long? = null,
    val patientName: String? = null,
    val sequenceNumber: Int = 0,
    val amountMinor: Long = 0,
    val currency: String = "UZS",
    val dueDate: String = "",
    // PENDING, PAID, OVERDUE, WAIVED, CANCELLED.
    val status: String = "",
    val paidAt: String? = null,
    val notes: String? = null,
)

@Serializable
data class MarkPaidBody(
    val method: String,
    val memo: String? = null,
)

@Serializable
data class InstallmentStatusBody(
    val status: String,
)

/** A page of a finance list (`{content, totalElements, number}`; payments come as a bare list). */
data class FinancePage<T>(
    val rows: List<T>,
    val total: Long?,
    val last: Boolean,
)
