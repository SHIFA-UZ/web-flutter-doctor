package shifa.doctor.data.documents

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.DocumentFile
import shifa.doctor.data.model.PatientDocument

/**
 * A patient's documents (DOCS-01, 06 §4.3–4.6), in a clinic's scope when [clinicId] is given: the list (one bad row
 * never drops the rest), the authenticated download, and asking for access to a locked one. Uploads come with DOCS-03.
 */
class DocumentsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    suspend fun list(
        patientId: Long,
        clinicId: Long? = null,
    ): Result<List<PatientDocument>> =
        apiCall<List<JsonElement>> {
            client().get(
                ApiConfig.Endpoints.patientDocuments(patientId),
            ) { clinicId?.let { parameter("clinicId", it) } }
        }.map { it.rows(PatientDocument.serializer(), "patient documents") }

    /**
     * The file through `/download` (never the list's `url`, 06 §4.4), named from `Content-Disposition` and typed by its
     * bytes. A locked document answers 404 (dev), which comes back as [shifa.doctor.data.model.ApiException.NotFound].
     */
    suspend fun download(
        patientId: Long,
        documentId: Long,
        clinicId: Long? = null,
    ): Result<DocumentFile> =
        apiCall<HttpResponse> {
            client().get(ApiConfig.Endpoints.patientDocumentDownload(patientId, documentId)) {
                clinicId?.let { parameter("clinicId", it) }
            }
        }.map { response ->
            val bytes = response.readBytes()
            val name = DocumentFile.fileName(response.headers[HttpHeaders.ContentDisposition])
            DocumentFile(bytes, name, DocumentFile.mime(bytes, response.headers[HttpHeaders.ContentType], name))
        }

    suspend fun requestAccess(
        patientId: Long,
        documentId: Long,
        clinicId: Long? = null,
    ): Result<Unit> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.patientDocumentRequestAccess(patientId, documentId)) {
                clinicId?.let { parameter("clinicId", it) }
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        }
}

val documentsRepository by lazy { DocumentsRepository() }
