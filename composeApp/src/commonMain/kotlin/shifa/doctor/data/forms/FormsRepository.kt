package shifa.doctor.data.forms

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.FiledDocument
import shifa.doctor.data.model.FormTemplates
import shifa.doctor.data.model.PatientForm

/** An ICD-10 search hit (`GET /icd10/search`, dev): the code, its title in the asked language, the block. */
@Serializable
data class Icd10Hit(
    val code: String = "",
    val title: String = "",
    val subtitle: String? = null,
)

/**
 * A patient's forms (FORM-02, 06 §7): the list per patient (one bad row never drops the rest), kept so the record's
 * Forms tab, the documents' "Edit" and a new 025-2's prefill read the same copy; create and update (each answer
 * replaces the cached form), request the patient's signature, link the form's PDF, and the ICD-10 search.
 */
class FormsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    private val cache = MutableStateFlow<Map<Long, List<PatientForm>>>(emptyMap())

    // A sign-out moves it on: nothing that started before (a read or a save) lands in the next doctor's cache.
    private val sessions = atomic(0)

    // Every change moves it on: a list read that a save, a signature request or a link overtook is dropped.
    private val changes = atomic(0)

    /** The cached forms of a patient, newest first; null until read. */
    fun forms(patientId: Long): Flow<List<PatientForm>?> = cache.map { it[patientId] }.distinctUntilChanged()

    suspend fun list(patientId: Long): Result<List<PatientForm>> {
        val session = sessions.value
        val change = changes.value
        return apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.patientForms(patientId)) }
            .map { it.rows(PatientForm.serializer(), "patient forms").sortedWith(NEWEST) }
            .onSuccess { list ->
                if (sessions.value == session && changes.value == change) cache.update { it + (patientId to list) }
            }
    }

    /** `POST /patients/{id}/forms` (no id) or `PUT …/forms/{formId}`. */
    suspend fun save(form: PatientForm): Result<PatientForm> {
        val session = sessions.value
        val id = form.id
        val result =
            apiCall<PatientForm> {
                val body: HttpClientBody = {
                    contentType(ContentType.Application.Json)
                    setBody(form.request())
                }
                if (id == null) {
                    client().post(ApiConfig.Endpoints.patientForms(form.patientId), body)
                } else {
                    client().put(ApiConfig.Endpoints.patientForm(form.patientId, id), body)
                }
            }
        return result.onSuccess { remember(it, session) }
    }

    /** Asks the patient to sign in their app; the answer carries `signatureRequested` (06 §3.7). */
    suspend fun requestSignature(
        patientId: Long,
        formId: Long,
    ): Result<PatientForm> {
        val session = sessions.value
        return apiCall<PatientForm> {
            client().put(ApiConfig.Endpoints.patientForm(patientId, formId) + "/request-signature") {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        }.onSuccess { remember(it, session) }
    }

    /** Links the form to its PDF document (`POST …/link-document?documentId=`), so the document shows as the form. */
    suspend fun linkDocument(
        patientId: Long,
        formId: Long,
        documentId: Long,
    ): Result<PatientForm> {
        val session = sessions.value
        return apiCall<PatientForm> {
            client().post(ApiConfig.Endpoints.patientForm(patientId, formId) + "/link-document") {
                parameter("documentId", documentId)
            }
        }.onSuccess { remember(it, session) }
    }

    /**
     * BE-01: the server renders the 025-2, files it as the patient's document (replacing an earlier one) and links it
     * (`POST …/pdf-document`, idempotent). A backend without it answers 404 ([endpointMissing]): the app renders then.
     */
    suspend fun filePdfDocument(
        patientId: Long,
        formId: Long,
    ): Result<FiledDocument> =
        apiCall<FiledDocument> { client().post(ApiConfig.Endpoints.patientForm(patientId, formId) + "/pdf-document") }

    /** The ICD-10 search in [lang] (the app's language; dev answers en, ru and uz titles), at most [limit] hits. */
    suspend fun searchIcd10(
        query: String,
        lang: String,
        limit: Int = 20,
    ): Result<List<Icd10Hit>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.ICD10_SEARCH) {
                parameter("q", query.trim())
                parameter("limit", limit)
                parameter("lang", lang)
            }
        }.map { it.rows(Icd10Hit.serializer(), "icd10 hits").filter { h -> h.code.isNotBlank() } }

    fun clear() {
        sessions.incrementAndGet()
        cache.value = emptyMap()
    }

    /** A saved form replaces its cached copy, unless the doctor signed out meanwhile. */
    private fun remember(
        form: PatientForm,
        session: Int,
    ) {
        val id = form.id ?: return
        if (sessions.value != session) return
        changes.incrementAndGet()
        cache.update { all ->
            val list = all[form.patientId] ?: return@update all
            all + (form.patientId to (list.filter { it.id != id } + form).sortedWith(NEWEST))
        }
    }

    companion object {
        /** Newest first: by date, then by id (06 §6, `last0252FormForPatientProvider`). */
        val NEWEST: Comparator<PatientForm> =
            compareByDescending<PatientForm> {
                it.date.take(10)
            }.thenByDescending { it.id ?: 0 }

        /** The patient's latest 025-2, which a new one starts from (06 §3.4). */
        fun latest0252(forms: List<PatientForm>): PatientForm? =
            forms.filter { it.templateId == FormTemplates.F025_2 }.sortedWith(NEWEST).firstOrNull()
    }
}

private typealias HttpClientBody = io.ktor.client.request.HttpRequestBuilder.() -> Unit

val formsRepository by lazy { FormsRepository() }
