package shifa.doctor.data.finance

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows

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

/** A ledger row (`PaymentHistoryDto`, the fields the visit uses). */
@Serializable
data class PaymentRow(
    val id: Long,
    val treatmentPlanId: Long? = null,
    val amountMinor: Long = 0,
    val currency: String = "",
    val method: String = "",
    val linkedAppointmentId: Long? = null,
)

/** The clinic ledger (area 09, FIN-01 grows it): for now a payment against a treatment plan, recorded in a visit. */
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

    private companion object {
        const val LEDGER_PAGE = 50
    }
}

val financeRepository = FinanceRepository()
