package shifa.doctor.data.care

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.CarePartnerHit
import shifa.doctor.data.model.CarePartnership
import shifa.doctor.data.model.CareProgress
import shifa.doctor.data.model.PartnerInvite
import shifa.doctor.data.model.ProgressBody

/** The four ways a partnership changes (each `POST /care-partnerships/{id}/<path>` with `{}`). */
enum class PartnershipAction(
    val path: String,
) {
    Accept("accept"),
    Decline("decline"),
    Cancel("cancel"),
    Complete("complete"),
}

/**
 * Care partnerships (CARE-01, 06 §5.2–5.3, 15 §2.7): doctors co-treating one patient. Any 2xx is success (Flutter took
 * only 200); lists are read row by row.
 */
class CarePartnershipsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /**
     * `GET /care-partnerships/partners/search?country&profession&q&limit`: the server leaves the signed-in doctor out
     * and filters by country (Uzbekistan when none is sent, as Flutter hard-coded it).
     */
    suspend fun search(
        q: String,
        profession: String,
        // The doctor's own practice country (flow 06/12 §4); none lets the server use Uzbekistan.
        country: String? = null,
        limit: Int = SEARCH_LIMIT,
    ): Result<List<CarePartnerHit>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.CARE_PARTNER_SEARCH) {
                country?.trim()?.takeIf { it.isNotEmpty() }?.let { parameter("country", it) }
                q.trim().takeIf { it.isNotEmpty() }?.let { parameter("q", it) }
                profession.trim().takeIf { it.isNotEmpty() }?.let { parameter("profession", it) }
                parameter("limit", limit)
            }
        }.map { it.rows(CarePartnerHit.serializer(), "care partners") { h -> h.takeIf { it.id > 0 } } }

    /** `GET /care-partnerships`: every partnership the doctor is part of. */
    suspend fun mine(): Result<List<CarePartnership>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.CARE_PARTNERSHIPS) }
            .map { it.rows(CarePartnership.serializer(), "care partnerships") { p -> p.takeIf { it.id > 0 } } }

    suspend fun get(id: Long): Result<CarePartnership> =
        apiCall<CarePartnership> { client().get(ApiConfig.Endpoints.carePartnership(id)) }

    /** `POST /care-partnerships`: the new partnership (PENDING); 409 when the same invite is pending already. */
    suspend fun invite(invite: PartnerInvite): Result<CarePartnership> =
        apiCall<CarePartnership> {
            client().post(ApiConfig.Endpoints.CARE_PARTNERSHIPS) {
                contentType(ContentType.Application.Json)
                setBody(invite)
            }
        }

    suspend fun act(
        id: Long,
        action: PartnershipAction,
    ): Result<CarePartnership> =
        apiCall<CarePartnership> {
            client().post(ApiConfig.Endpoints.carePartnershipAction(id, action.path)) {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        }

    suspend fun progress(id: Long): Result<List<CareProgress>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.carePartnershipProgress(id)) }
            .map { it.rows(CareProgress.serializer(), "partnership progress") }

    suspend fun addProgress(
        id: Long,
        body: String,
    ): Result<CareProgress> =
        apiCall<CareProgress> {
            client().post(ApiConfig.Endpoints.carePartnershipProgress(id)) {
                contentType(ContentType.Application.Json)
                setBody(ProgressBody(body))
            }
        }

    companion object {
        const val SEARCH_LIMIT = 30
    }
}

val carePartnershipsRepository by lazy { CarePartnershipsRepository() }
