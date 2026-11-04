package shifa.doctor.data.catalog

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.ProfessionDto
import shifa.doctor.data.model.PublicClinicDto
import shifa.doctor.generated.resources.Res

/** A profession of the catalogue; [english] is what the backend stores (08 §4.1). */
data class Profession(
    val english: String,
    val uzbek: String?,
)

data class Clinic(
    val id: Long,
    val name: String,
)

/** The public lookups the registration and profile pickers use (no token: public paths). */
class CatalogRepository(
    private val client: () -> HttpClient = { httpClient },
    private val bundled: suspend () -> String = { Res.readBytes("files/catalog/professions.json").decodeToString() },
) {
    /**
     * `GET /public/professions` (138 on dev and production, 2026-11-04): every entry carries both labels, so one call
     * serves all.
     */
    suspend fun professions(): Result<List<Profession>> =
        apiCall<List<ProfessionDto>> { client().get(ApiConfig.Endpoints.PUBLIC_PROFESSIONS) }.map(::toProfessions)

    /**
     * The copy bundled with the app (production's list of 2026-11-04), for when the backend can't be reached, so a
     * doctor can always pick (flow 01/08 §6, Flutter's built-in list). Empty if even that can't be read.
     */
    suspend fun bundledProfessions(): List<Profession> =
        runCatching {
            toProfessions(
                apiJson.decodeFromString<List<ProfessionDto>>(bundled()),
            )
        }.getOrDefault(emptyList())

    private fun toProfessions(list: List<ProfessionDto>) =
        list.filter { it.english.isNotBlank() }.map { Profession(it.english, it.uzbek) }

    /** `GET /public/clinics?search=`; no [search] lists them all. */
    suspend fun clinics(search: String? = null): Result<List<Clinic>> =
        apiCall<List<PublicClinicDto>> {
            client().get(ApiConfig.Endpoints.PUBLIC_CLINICS) { search?.let { parameter("search", it) } }
        }.map { list -> list.filter { it.name.isNotBlank() }.map { Clinic(it.id, it.name) } }
}
