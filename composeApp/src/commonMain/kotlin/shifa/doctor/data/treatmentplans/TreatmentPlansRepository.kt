package shifa.doctor.data.treatmentplans

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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
import shifa.doctor.data.model.CatalogItem
import shifa.doctor.data.model.CatalogItemPatch
import shifa.doctor.data.model.FulfillRequest
import shifa.doctor.data.model.FulfillmentCandidate
import shifa.doctor.data.model.NewCatalogItem
import shifa.doctor.data.model.NewTreatmentPlan
import shifa.doctor.data.model.PlanLineRequest
import shifa.doctor.data.model.PlanServiceOption
import shifa.doctor.data.model.PlanStatusBody
import shifa.doctor.data.model.PlanVisit
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
        // Null lists the clinic's plans (the finance dashboard's balances, the plans tab).
        patientId: Long?,
        status: String? = null,
        planKind: String? = "COMPREHENSIVE",
        // TP-02: the server's search over patient and plan title.
        q: String? = null,
    ): Result<List<TreatmentPlanSummary>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.TREATMENT_PLANS) {
                parameter("clinicId", clinicId)
                patientId?.let { parameter("patientId", it) }
                status?.let { parameter("status", it) }
                planKind?.let { parameter("planKind", it) }
                q?.trim()?.takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
            }
        }.map { it.rows(TreatmentPlanSummary.serializer(), "treatment plans") }

    /** `POST /treatment-plans` (TP-04): the new plan, DRAFT. */
    suspend fun create(plan: NewTreatmentPlan): Result<TreatmentPlanSummary> =
        apiCall<TreatmentPlanSummary> {
            client().post(ApiConfig.Endpoints.TREATMENT_PLANS) {
                contentType(ContentType.Application.Json)
                setBody(plan)
            }
        }

    /** `POST /treatment-plans/{id}/lines` (TP-04): the plan's services, in order. */
    suspend fun addLines(
        planId: Long,
        lines: List<PlanLineRequest>,
    ): Result<Unit> =
        apiCall<JsonElement> {
            client().post(ApiConfig.Endpoints.treatmentPlanLines(planId)) {
                contentType(ContentType.Application.Json)
                setBody(lines)
            }
        }.map { }

    /** `GET /treatment-plans/{id}/visits` (TP-02/03): the visits linked to the plan. */
    suspend fun visits(planId: Long): Result<List<PlanVisit>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.treatmentPlanVisits(planId)) }
            .map { it.rows(PlanVisit.serializer(), "plan visits") }

    /** `PATCH /treatment-plans/{id}/status {status}`: the plan as it is then. */
    suspend fun setStatus(
        planId: Long,
        status: String,
    ): Result<TreatmentPlanSummary> =
        apiCall<TreatmentPlanSummary> {
            client().patch(ApiConfig.Endpoints.treatmentPlanStatus(planId)) {
                contentType(ContentType.Application.Json)
                setBody(PlanStatusBody(status))
            }
        }

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

    /** The clinic's own catalogue (`GET /treatment-plans/catalog-items?clinicId`), with who each item applies to. */
    suspend fun catalogItems(clinicId: Long): Result<List<CatalogItem>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.CATALOG_ITEMS) { parameter("clinicId", clinicId) }
        }.map { it.rows(CatalogItem.serializer(), "catalog items") }

    /** `POST /treatment-plans/catalog-items` (CLINIC-07). */
    suspend fun createCatalogItem(item: NewCatalogItem): Result<CatalogItem> =
        apiCall<CatalogItem> {
            client().post(ApiConfig.Endpoints.CATALOG_ITEMS) {
                contentType(ContentType.Application.Json)
                setBody(item)
            }
        }

    /** `PATCH /treatment-plans/catalog-items/{id}`: the fields set in [patch] only. */
    suspend fun patchCatalogItem(
        id: Long,
        patch: CatalogItemPatch,
    ): Result<CatalogItem> =
        apiCall<CatalogItem> {
            client().patch(ApiConfig.Endpoints.catalogItem(id)) {
                contentType(ContentType.Application.Json)
                setBody(patch)
            }
        }

    suspend fun detail(planId: Long): Result<TreatmentPlanDetail> =
        apiCall<JsonElement> { client().get(ApiConfig.Endpoints.treatmentPlanDetail(planId)) }
            .mapCatching { TreatmentPlanDetail.read(it) ?: throw ApiException.ServerError(200, null, null) }

    /** BE-01: the plan as a PDF rendered by the backend in [lang] (`en`, `uz`, `ru`); 404 on backends without it. */
    suspend fun pdf(
        planId: Long,
        lang: String,
    ): Result<ByteArray> =
        apiCall<ByteArray> { client().get(ApiConfig.Endpoints.treatmentPlanPdf(planId)) { parameter("lang", lang) } }

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
