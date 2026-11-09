package shifa.doctor.data.documents

import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    /**
     * `POST /patients/{id}/documents` (multipart, 06 §4.2): the `file` part, then `title`, `category` and, from a visit,
     * `appointmentId` as form fields (as Flutter sent them; the backend reads them as request parameters). [progress]
     * gets the sent share, 0..1, when the body length is known.
     */
    suspend fun upload(
        patientId: Long,
        file: UploadFile,
        title: String,
        category: String,
        clinicId: Long? = null,
        appointmentId: Long? = null,
        progress: (Float) -> Unit = {},
    ): Result<PatientDocument> =
        apiCall<PatientDocument> {
            client().submitFormWithBinaryData(
                ApiConfig.Endpoints.patientDocuments(patientId),
                formData {
                    append("file", file.bytes, filePart(file))
                    append("title", title)
                    append("category", category)
                    appointmentId?.let { append("appointmentId", it.toString()) }
                },
            ) {
                clinicId?.let { parameter("clinicId", it) }
                onUpload {
                        sent,
                        total,
                    ->
                    if (total != null && total > 0) progress((sent.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }

    /**
     * Replaces a document's file (`PUT …/documents/{id}`, multipart `file` only): a re-saved 025-2 keeps its document
     * and gets the new PDF (06 §3.6). The content type is the file's own (Flutter always declared a PDF).
     */
    suspend fun replaceFile(
        patientId: Long,
        documentId: Long,
        file: UploadFile,
        clinicId: Long? = null,
    ): Result<PatientDocument> =
        apiCall<PatientDocument> {
            client().submitFormWithBinaryData(
                ApiConfig.Endpoints.patientDocument(patientId, documentId),
                formData { append("file", file.bytes, filePart(file)) },
            ) {
                method = HttpMethod.Put
                clinicId?.let { parameter("clinicId", it) }
            }
        }

    private fun filePart(file: UploadFile) =
        Headers.build {
            append(HttpHeaders.ContentType, file.mime)
            // Quotes would end the header value early.
            append(HttpHeaders.ContentDisposition, "filename=\"${file.fileName.replace("\"", "")}\"")
        }

    /** BE-21: renames and recategorises a document the doctor uploaded (`PATCH …/documents/{id}`); 403 for others'. */
    suspend fun edit(
        patientId: Long,
        documentId: Long,
        title: String,
        category: String,
        clinicId: Long? = null,
    ): Result<PatientDocument> =
        apiCall<PatientDocument> {
            client().patch(ApiConfig.Endpoints.patientDocument(patientId, documentId)) {
                clinicId?.let { parameter("clinicId", it) }
                contentType(ContentType.Application.Json)
                setBody(JsonObject(mapOf("title" to JsonPrimitive(title), "category" to JsonPrimitive(category))))
            }
        }

    /** BE-21: removes a document the doctor uploaded (204). The backend keeps it for the record, out of every list. */
    suspend fun delete(
        patientId: Long,
        documentId: Long,
        clinicId: Long? = null,
    ): Result<Unit> =
        apiCall<Unit> {
            client().delete(ApiConfig.Endpoints.patientDocument(patientId, documentId)) {
                clinicId?.let { parameter("clinicId", it) }
            }
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

/** A file ready to upload: its bytes, the name it goes under and its type (PDF or JPEG from the app's pickers). */
class UploadFile(
    val bytes: ByteArray,
    val fileName: String,
    val mime: String,
)
