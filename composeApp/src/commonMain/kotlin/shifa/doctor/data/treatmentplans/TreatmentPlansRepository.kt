package shifa.doctor.data.treatmentplans

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.FulfillRequest
import shifa.doctor.data.model.FulfillmentCandidate
import shifa.doctor.data.model.PlanServiceOption
import shifa.doctor.data.model.TreatmentPlanDetail
import shifa.doctor.data.model.TreatmentPlanSummary

/**
 * Treatment plans (TP-01, 09 §4–§8), the part the visit needs (pulled forward for M5): a patient's plans in a clinic,
 * a plan's detail, the lines a visit may complete and completing them. Every failure is a failure (Flutter returned
 * null or an empty list, so an error looked like "no plans"). Nothing is cached: the visit reads a patient's plans once
 * when it opens; a cache (scoped to the session and the backend) joins the plan list screens (TP-02).
 */
class TreatmentPlansRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** `GET /treatment-plans?clinicId&patientId&status&planKind`: one unreadable plan is skipped, not the list. */
    suspend fun plans(
        clinicId: Long,
        patientId: Long,
        status: String? = null,
        planKind: String? = "COMPREHENSIVE",
    ): Result<List<TreatmentPlanSummary>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.TREATMENT_PLANS) {
                parameter("clinicId", clinicId)
                parameter("patientId", patientId)
                status?.let { parameter("status", it) }
                planKind?.let { parameter("planKind", it) }
            }
        }.map { it.rows(TreatmentPlanSummary.serializer(), "treatment plans") }

    /** The services a plan can use in a clinic (`GET /treatment-plans/plan-services`), for the plan editor. */
    suspend fun planServices(
        clinicId: Long,
        doctorIds: List<Long> = emptyList(),
    ): Result<List<PlanServiceOption>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.PLAN_SERVICES) {
                parameter("clinicId", clinicId)
                if (doctorIds.isNotEmpty()) parameter("doctorIds", doctorIds.joinToString(","))
            }
        }.map { it.rows(PlanServiceOption.serializer(), "plan services") }

    suspend fun detail(planId: Long): Result<TreatmentPlanDetail> =
        apiCall<JsonElement> { client().get(ApiConfig.Endpoints.treatmentPlanDetail(planId)) }
            .mapCatching { TreatmentPlanDetail.read(it) ?: throw ApiException.ServerError(200, null, null) }

    /** The open lines [appointmentId] may complete (`toothMatch` when the line's tooth fits the visit). */
    suspend fun candidates(
        planId: Long,
        appointmentId: Long,
    ): Result<List<FulfillmentCandidate>> =
        apiCall<List<JsonElement>> {
            client().get(
                ApiConfig.Endpoints.fulfillmentCandidates(planId),
            ) { parameter("appointmentId", appointmentId) }
        }.map { it.rows(FulfillmentCandidate.serializer(), "fulfillment candidates") }

    /** Marks [lineIds] completed by this visit; the plan as it is then. */
    suspend fun fulfill(
        planId: Long,
        appointmentId: Long,
        lineIds: List<Long>,
    ): Result<TreatmentPlanDetail> =
        apiCall<JsonElement> {
            client().post(ApiConfig.Endpoints.fulfillPlan(planId, appointmentId)) {
                contentType(ContentType.Application.Json)
                setBody(FulfillRequest(lineIds))
            }
        }.mapCatching { TreatmentPlanDetail.read(it) ?: throw ApiException.ServerError(200, null, null) }

    companion object {
        const val ACTIVE = "ACTIVE"
    }
}

val treatmentPlansRepository = TreatmentPlansRepository()
