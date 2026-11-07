package shifa.doctor.data.clinical

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.ClinicalChip
import shifa.doctor.data.model.ClinicalChipSelection
import shifa.doctor.data.model.ClinicalDisease
import shifa.doctor.data.model.ClinicalGroup
import shifa.doctor.data.model.ClinicalSharedTemplate
import shifa.doctor.data.model.ClinicalTopDiagnosis
import shifa.doctor.data.model.SynthesizeAnswer
import shifa.doctor.data.model.SynthesizeRequest

/**
 * The clinical engine (CLIN-01, 02 §5.1; checked against dev): dental diseases by group with their phrase chips, the
 * doctor's top diagnoses, chip search, occlusion chips, shared oral-cavity and X-ray texts, and `synthesize`, which
 * turns a disease and the picked chips into the form's field texts. Rows are read one by one (Flutter's `as String`
 * casts threw on a missing field and lost the list); failures come back typed instead of as empty lists.
 */
class ClinicalEngineRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    // Groups and occlusion chips change with releases, not during a visit: read once per sign-in.
    private var groupsCache: List<ClinicalGroup>? = null
    private var occlusionCache: List<ClinicalChip>? = null

    suspend fun groups(): Result<List<ClinicalGroup>> =
        groupsCache?.let { Result.success(it) }
            ?: list("groups", ClinicalGroup.serializer(), "clinical groups")
                .map { it.filter { g -> g.groupId.isNotBlank() }.sortedBy(ClinicalGroup::sortOrder) }
                .onSuccess { groupsCache = it }

    suspend fun diseases(groupId: String): Result<List<ClinicalDisease>> =
        list("groups/$groupId/diseases", ClinicalDisease.serializer(), "clinical diseases")
            .map { it.filter { d -> d.diseaseId.isNotBlank() }.sortedBy(ClinicalDisease::number) }

    /** One disease with its chips; a bad chip is dropped, the rest kept (as for the lists). */
    suspend fun disease(diseaseId: String): Result<ClinicalDisease> =
        apiCall<JsonObject> { client().get("$BASE/diseases/$diseaseId") }.mapCatching { obj ->
            val chips =
                (obj["chips"] as? JsonArray)?.toList().orEmpty().rows(
                    ClinicalChip.serializer(),
                    "disease chips",
                )
            apiJson.decodeFromJsonElement(ClinicalDisease.serializer(), JsonObject(obj - "chips")).copy(chips = chips)
        }

    suspend fun topDiagnoses(limit: Int = 5): Result<List<ClinicalTopDiagnosis>> =
        list(
            "doctors/me/top-diagnoses",
            ClinicalTopDiagnosis.serializer(),
            "top diagnoses",
        ) { parameter("limit", limit) }
            .map { it.filter { t -> t.diseaseId.isNotBlank() } }

    /** Chips whose text matches [query] (the panel searches from 2 characters). */
    suspend fun searchChips(
        query: String,
        locale: String,
        limit: Int = 15,
    ): Result<List<ClinicalChip>> =
        list("chips/search", ClinicalChip.serializer(), "chip search") {
            parameter("q", query.trim())
            parameter("locale", locale)
            parameter("limit", limit)
        }.map { it.filter { c -> c.chipId.isNotBlank() } }

    suspend fun occlusionChips(): Result<List<ClinicalChip>> =
        occlusionCache?.let { Result.success(it) }
            ?: list("occlusion/chips", ClinicalChip.serializer(), "occlusion chips")
                .map { it.filter { c -> c.chipId.isNotBlank() }.sortedBy(ClinicalChip::priority) }
                .onSuccess { occlusionCache = it }

    /** `mucosa` (oral cavity) or `xray` texts. */
    suspend fun sharedTemplates(type: String): Result<List<ClinicalSharedTemplate>> =
        list("shared/$type", ClinicalSharedTemplate.serializer(), "shared templates")
            .map { it.filter { t -> t.templateId.isNotBlank() }.sortedBy(ClinicalSharedTemplate::priority) }

    /** The form's field texts for [diseaseId] and the picked chips, in [locale]. */
    suspend fun synthesize(
        locale: String,
        diseaseId: String?,
        selections: List<ClinicalChipSelection>,
    ): Result<Map<String, String>> =
        apiCall<SynthesizeAnswer> {
            client().post("$BASE/synthesize") {
                contentType(ContentType.Application.Json)
                setBody(SynthesizeRequest(locale, diseaseId, selections))
            }
        }.map { it.fields }

    /**
     * Counts a diagnosis for the doctor's top list (`POST /record-usage`; Flutter never called it, so the list stayed
     * empty). Sent when a form with a disease is saved.
     */
    suspend fun recordUsage(diseaseId: String): Result<Unit> =
        apiCall<Unit> {
            client().post("$BASE/record-usage") {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(mapOf("diseaseId" to JsonPrimitive(diseaseId))))
            }
        }

    fun clear() {
        groupsCache = null
        occlusionCache = null
    }

    private suspend fun <T : Any> list(
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        what: String,
        params: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): Result<List<T>> =
        apiCall<List<JsonElement>> {
            client().get("$BASE/$path", params)
        }.map { it.rows(serializer, what) }

    private companion object {
        const val BASE = ApiConfig.Endpoints.CLINICAL_ENGINE
    }
}

val clinicalEngineRepository by lazy { ClinicalEngineRepository() }
