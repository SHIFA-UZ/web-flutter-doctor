package shifa.doctor.data.legal

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.ApiException

/** `GET /public/legal`: the legal pages' addresses and their version (BE-16). */
@Serializable
data class LegalLinks(
    val terms: String? = null,
    val privacy: String? = null,
    val licences: String? = null,
    val version: String? = null,
    val updatedAt: String? = null,
)

/** `GET /doctors/me/contract`: the doctor's early-partner contract (BE-16). */
@Serializable
data class DoctorContract(
    val contractNumber: String = "",
    val effectiveDate: String? = null,
    val endDate: String? = null,
    val termMonths: Int? = null,
    val issuedAt: String? = null,
    val status: String? = null,
) {
    val from: LocalDate? get() = effectiveDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/** About's legal pages and the doctor's own contract (LEGAL-01/02, BE-16, backend 2026-11-07). */
class LegalRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** A backend without the route (404/405) answers like none: About keeps the public privacy page. */
    suspend fun links(lang: String): Result<LegalLinks?> =
        apiCall<LegalLinks> { client().get(ApiConfig.Endpoints.PUBLIC_LEGAL) { parameter("lang", lang) } }
            .orNoneWhenMissing()

    /** The contract, or null when the doctor has none (404 `NO_CONTRACT`, or a backend without the route). */
    suspend fun contract(): Result<DoctorContract?> =
        apiCall<DoctorContract> { client().get(ApiConfig.Endpoints.DOCTOR_CONTRACT) }
            .orNoneWhenMissing()

    /** The contract as the backend renders it (`application/pdf`). */
    suspend fun contractPdf(): Result<ByteArray> =
        apiCall<ByteArray> { client().get(ApiConfig.Endpoints.DOCTOR_CONTRACT_PDF) }

    private fun <T : Any> Result<T>.orNoneWhenMissing(): Result<T?> =
        fold({ Result.success(it) }, { e ->
            if (e is ApiException && (e.status == NOT_FOUND || e.status == NOT_ALLOWED)) {
                Result.success(
                    null,
                )
            } else {
                Result.failure(e)
            }
        })

    private companion object {
        const val NOT_FOUND = 404
        const val NOT_ALLOWED = 405
    }
}

val legalRepository = LegalRepository()
