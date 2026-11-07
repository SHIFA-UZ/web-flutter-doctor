package shifa.doctor.data.finance

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.DoctorEarning
import shifa.doctor.data.model.FinanceDashboard
import shifa.doctor.data.model.FinancePage
import shifa.doctor.data.model.FinancialRecord
import shifa.doctor.data.model.InstallmentRow
import shifa.doctor.data.model.InstallmentStatusBody
import shifa.doctor.data.model.LedgerRow
import shifa.doctor.data.model.MarkPaidBody
import shifa.doctor.data.model.NewFinancialRecord
import shifa.doctor.data.model.NewInstallmentPlan

/** `POST /clinics/{id}/finance/payments` body (dev OpenAPI `RecordPaymentRequest`); [method] CASH, CARD_EXTERNAL, TRANSFER. */
@Serializable
data class RecordPayment(
    val treatmentPlanId: Long,
    val amountMinor: Long,
    val currency: String,
    val method: String,
    val linkedAppointmentId: Long? = null,
    val memo: String? = null,
)

/** A payment recorded on a treatment plan (`PaymentHistoryDto`): the visit's ledger and the Payments view (FIN-03). */
@Serializable
data class PaymentRow(
    val id: Long,
    val treatmentPlanId: Long? = null,
    val amountMinor: Long = 0,
    val currency: String = "",
    // CASH, CARD_EXTERNAL, TRANSFER, WAIVED, OTHER, CLICK.
    val method: String = "",
    val linkedAppointmentId: Long? = null,
    val recordedAt: String? = null,
    val treatmentPlanTitle: String? = null,
    val patientId: Long? = null,
    val patientName: String? = null,
    val doctorProfileId: Long? = null,
    val doctorName: String? = null,
    val financialRecordId: Long? = null,
    val memo: String? = null,
)

/**
 * A month in the clinic's zone for the finance filters (FIN-01): `from` = its first instant, `to` = the next month's
 * (the backend takes ISO instants, both or neither). Null means all time.
 */
data class FinanceMonth(
    val year: Int,
    val month: Int,
) {
    fun range(zone: String?): Pair<Instant, Instant> {
        val tz = zone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.UTC
        val first = LocalDate(year, month, 1)
        return first.atStartOfDayIn(tz) to first.plus(DatePeriod(months = 1)).atStartOfDayIn(tz)
    }

    fun previous(): FinanceMonth = if (month == 1) FinanceMonth(year - 1, 12) else FinanceMonth(year, month - 1)

    companion object {
        fun of(date: LocalDate) = FinanceMonth(date.year, date.monthNumber)
    }
}

/**
 * The clinic's finance (FIN-01, 09 §3, §8): the dashboard, the appointment ledger, payments, records, installments and
 * doctor earnings, plus a desk payment (also recorded from a visit). Every read is a fresh one: the views read again
 * after their own writes (no ten-way refresh fan-out, quirk 15); a row that can't be read is skipped (quirk 4).
 */
class FinanceRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    suspend fun recordPayment(
        clinicId: Long,
        payment: RecordPayment,
    ): Result<Unit> =
        apiCall<JsonElement> {
            client().post(ApiConfig.Endpoints.clinicPayments(clinicId)) {
                contentType(ContentType.Application.Json)
                setBody(payment)
            }
        }.map { }

    /** The newest ledger rows (`GET …/finance/payments?page=0&size=50`); one unreadable row is skipped. */
    suspend fun payments(clinicId: Long): Result<List<PaymentRow>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.clinicPayments(clinicId)) {
                parameter("page", 0)
                parameter("size", LEDGER_PAGE)
            }
        }.map { it.rows(PaymentRow.serializer(), "payments") }

    /** `GET …/finance/payments?page&size` (newest first; no date range on the backend: BE-36). */
    suspend fun paymentsPage(
        clinicId: Long,
        page: Int,
        size: Int = PAGE,
    ): Result<FinancePage<PaymentRow>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.clinicPayments(clinicId)) {
                parameter("page", page)
                parameter("size", size)
            }
        }.map { list -> FinancePage(list.rows(PaymentRow.serializer(), "payments"), null, list.size < size) }

    /** `GET …/finance/dashboard[?from&to]`: all time, or the month's. */
    suspend fun dashboard(
        clinicId: Long,
        range: Pair<Instant, Instant>?,
    ): Result<FinanceDashboard> =
        apiCall<FinanceDashboard> {
            client().get(ApiConfig.Endpoints.clinicFinance(clinicId, "dashboard")) { range(range) }
        }

    /** `GET …/finance/appointment-ledger?page&size[&from&to]`: billable visits, newest first. */
    suspend fun ledger(
        clinicId: Long,
        range: Pair<Instant, Instant>?,
        page: Int,
        size: Int = PAGE,
    ): Result<FinancePage<LedgerRow>> =
        apiCall<JsonElement> {
            client().get(ApiConfig.Endpoints.clinicFinance(clinicId, "appointment-ledger")) {
                range(range)
                parameter("page", page)
                parameter("size", size)
            }
        }.mapCatching { pageOf(it, LedgerRow.serializer(), "ledger", page, size) }

    /** `GET …/finance/records?page&size[&status]`, newest first. */
    suspend fun records(
        clinicId: Long,
        status: String?,
        page: Int,
        size: Int = PAGE,
    ): Result<FinancePage<FinancialRecord>> =
        apiCall<JsonElement> {
            client().get(ApiConfig.Endpoints.clinicFinance(clinicId, "records")) {
                status?.let { parameter("status", it) }
                parameter("page", page)
                parameter("size", size)
            }
        }.mapCatching { pageOf(it, FinancialRecord.serializer(), "records", page, size) }

    /** `POST …/finance/records`. */
    suspend fun createRecord(
        clinicId: Long,
        record: NewFinancialRecord,
    ): Result<Unit> =
        apiCall<JsonElement> {
            client().post(ApiConfig.Endpoints.clinicFinance(clinicId, "records")) {
                contentType(ContentType.Application.Json)
                setBody(record)
            }
        }.map { }

    /** `GET …/finance/installment-items?filter=all|pending|overdue|paid` (the backend's filters). */
    suspend fun installments(
        clinicId: Long,
        filter: String,
    ): Result<List<InstallmentRow>> =
        apiCall<List<JsonElement>> {
            client().get(
                ApiConfig.Endpoints.clinicFinance(clinicId, "installment-items"),
            ) { parameter("filter", filter) }
        }.map { it.rows(InstallmentRow.serializer(), "installments") }

    /** `POST …/installment-items/{id}/mark-paid {method, memo}` (records the payment too). */
    suspend fun markPaid(
        clinicId: Long,
        itemId: Long,
        body: MarkPaidBody,
    ): Result<Unit> =
        apiCall<JsonElement> {
            client().post(ApiConfig.Endpoints.clinicFinance(clinicId, "installment-items/$itemId/mark-paid")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.map { }

    /** `PATCH …/installment-items/{id} {status}`: PENDING, WAIVED or CANCELLED (PAID goes through [markPaid]). */
    suspend fun setInstallmentStatus(
        clinicId: Long,
        itemId: Long,
        status: String,
    ): Result<Unit> =
        apiCall<JsonElement> {
            client().patch(ApiConfig.Endpoints.clinicFinance(clinicId, "installment-items/$itemId")) {
                contentType(ContentType.Application.Json)
                setBody(InstallmentStatusBody(status))
            }
        }.map { }

    /** `POST …/installment-items/{id}/notify`: a reminder to the patient. */
    suspend fun notifyInstallment(
        clinicId: Long,
        itemId: Long,
    ): Result<Unit> =
        apiCall<JsonElement> {
            client().post(ApiConfig.Endpoints.clinicFinance(clinicId, "installment-items/$itemId/notify"))
        }.map { }

    /** `POST …/finance/installment-plans` (TP-04): a plan's installment schedule. */
    suspend fun createInstallmentPlan(
        clinicId: Long,
        plan: NewInstallmentPlan,
    ): Result<Unit> =
        apiCall<JsonElement> {
            client().post(ApiConfig.Endpoints.clinicFinance(clinicId, "installment-plans")) {
                contentType(ContentType.Application.Json)
                setBody(plan)
            }
        }.map { }

    /** `GET …/finance/doctor-earnings[?from&to]`. */
    suspend fun earnings(
        clinicId: Long,
        range: Pair<Instant, Instant>?,
    ): Result<List<DoctorEarning>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.clinicFinance(clinicId, "doctor-earnings")) { range(range) }
        }.map { it.rows(DoctorEarning.serializer(), "doctor earnings") }

    private fun HttpRequestBuilder.range(range: Pair<Instant, Instant>?) {
        range?.let { (from, to) ->
            parameter("from", from.toString())
            parameter("to", to.toString())
        }
    }

    /** A Spring-like page (`content`, `totalElements`, `number`); anything else is a failure, not an empty list. */
    private fun <T : Any> pageOf(
        json: JsonElement,
        serializer: KSerializer<T>,
        what: String,
        page: Int,
        size: Int,
    ): FinancePage<T> {
        val obj = json as? JsonObject ?: throw ApiException.ServerError(200, null, null)
        val content = obj["content"] as? JsonArray ?: throw ApiException.ServerError(200, null, null)
        val total = (obj["totalElements"] as? JsonPrimitive)?.longOrNull
        val rows = content.toList().rows(serializer, what)
        val last = total?.let { (page + 1L) * size >= it } ?: (content.size < size)
        return FinancePage(rows, total, last)
    }

    private companion object {
        const val LEDGER_PAGE = 50
        const val PAGE = 30
    }
}

val financeRepository = FinanceRepository()
