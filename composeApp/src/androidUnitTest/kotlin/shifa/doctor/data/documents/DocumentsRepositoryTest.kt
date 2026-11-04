@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.documents

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.DocumentCategory

class DocumentsRepositoryTest {
    private val calls = mutableListOf<String>()
    private val bodies = mutableListOf<String>()
    private val engine =
        MockEngine { req ->
            calls += "${req.method.value} ${req.url.encodedPath}?${req.url.encodedQuery}"
            bodies += req.body.toByteArray().decodeToString()
            val path = req.url.encodedPath
            when {
                path.endsWith("/documents") ->
                    respond(
                        // Dev's shape; one row unreadable (a string id), a missing date and an unknown category.
                        """[{"id":5,"title":"Blood test","date":"2026-10-22","url":"/files/x","canView":true,"creatorLabel":"Ulugbek Karimov","category":"BLOOD_TEST","isSharedWithTeam":true,"uploadedByPatient":false,"creatorType":"DOCTOR","appointmentId":1},
                           {"id":"x"},
                           {"id":6,"title":"ECG","date":null,"url":null,"canView":false,"creatorLabel":"Jasur Aliyev","category":"SOMETHING_NEW","creatorType":"PATIENT"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                path.endsWith("/5/download") ->
                    respond(
                        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00),
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ContentType to listOf("application/octet-stream"),
                            HttpHeaders.ContentDisposition to
                                listOf(
                                    "attachment; filename=\"scan.bin\"; filename*=UTF-8''%D0%90%D0%BD%D0%B0%D0%BB%D0%B8%D0%B7.jpg",
                                ),
                        ),
                    )
                path.endsWith("/6/download") -> respond("", HttpStatusCode.NotFound)
                else -> respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    private val classToTest = DocumentsRepository { createHttpClient({ null }, engine) }

    @Test
    fun `the list keeps good rows, reads dates and categories leniently, and passes the clinic`() =
        runBlocking<Unit> {
            val docs = classToTest.list(14, clinicId = 3).getOrThrow()
            assertThat(docs.map { it.id }).containsExactly(5L, 6L).inOrder()
            assertThat(docs[0].day).isEqualTo(LocalDate(2026, 10, 22))
            assertThat(docs[0].kind).isEqualTo(DocumentCategory.BLOOD_TEST)
            assertThat(docs[1].day).isNull()
            assertThat(docs[1].kind).isNull()
            assertThat(docs[1].locked).isTrue()
            assertThat(calls.last()).isEqualTo("GET /api/patients/14/documents?clinicId=3")
        }

    @Test
    fun `a download is named from the header and typed by its bytes, a locked one is not found`() =
        runBlocking<Unit> {
            val file = classToTest.download(14, 5).getOrThrow()
            assertThat(file.fileName).isEqualTo("Анализ.jpg")
            assertThat(file.mime).isEqualTo("image/jpeg")
            assertThat(file.isImage).isTrue()
            assertThat(classToTest.download(14, 6).exceptionOrNull()).isInstanceOf(ApiException.NotFound::class.java)
        }

    @Test
    fun `request access posts an empty body in the clinic's scope`() =
        runBlocking<Unit> {
            assertThat(classToTest.requestAccess(14, 6, clinicId = 3).isSuccess).isTrue()
            assertThat(calls.last()).isEqualTo("POST /api/patients/14/documents/6/request-access?clinicId=3")
            assertThat(bodies.last()).isEqualTo("{}")
        }
}
