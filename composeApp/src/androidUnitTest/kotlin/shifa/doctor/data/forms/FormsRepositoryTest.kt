@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.forms

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.documents.UploadFile
import shifa.doctor.data.model.PatientForm

class FormsRepositoryTest {
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var hold: CompletableDeferred<Unit>? = null
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val body = req.body.toByteArray().decodeToString()
            calls += "${req.method.value} ${req.url.encodedPath}?${req.url.encodedQuery} $body"
            hold?.await()
            val path = req.url.encodedPath
            when {
                path.endsWith("/forms") && req.method.value == "GET" ->
                    respond(
                        """[{"id":1,"patientId":14,"templateId":"025-2","date":"2026-01-10","fullName":"A"},
                           {"id":"bad"},
                           {"id":3,"patientId":14,"templateId":"025-2","date":"2026-03-12","fullName":"A"},
                           {"id":2,"patientId":14,"templateId":"025-2","date":"2026-03-12","fullName":"A"}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                path.endsWith("/forms") -> respond(body.dropLast(1) + ""","id":9}""", HttpStatusCode.OK, json)
                path.contains("/forms/") ->
                    respond(
                        """{"id":${path.split(
                            '/',
                        )[5]},"patientId":14,"templateId":"025-2","date":"2026-04-01","fullName":"A","signatureRequested":true,"documentId":77}""",
                        HttpStatusCode.OK,
                        json,
                    )
                path.endsWith("/icd10/search") ->
                    respond(
                        """[{"code":"K02.0","title":"Caries enamel","subtitle":"K02"},{"code":"","title":"no code"}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                else -> respond("""{"id":77,"title":"Form","canView":true}""", HttpStatusCode.OK, json)
            }
        }
    private val classToTest = FormsRepository { createHttpClient({ null }, engine) }

    @Test
    fun `the list keeps readable rows newest first, by date then id, and caches them`() =
        runBlocking<Unit> {
            val forms = classToTest.list(14).getOrThrow()
            assertThat(forms.map { it.id }).containsExactly(3L, 2L, 1L).inOrder()
            assertThat(classToTest.forms(14).first()?.map { it.id }).containsExactly(3L, 2L, 1L).inOrder()
            assertThat(FormsRepository.latest0252(forms)?.id).isEqualTo(3L)
            assertThat(FormsRepository.latest0252(emptyList())).isNull()
        }

    @Test
    fun `a new form is posted, an existing one put, and the cached list takes each answer`() =
        runBlocking<Unit> {
            classToTest.list(14)
            val made =
                classToTest.save(
                    PatientForm(patientId = 14, date = "2026-04-02", fullName = "A", patientSignedAt = "x"),
                ).getOrThrow()
            assertThat(made.id).isEqualTo(9L)
            assertThat(calls.last()).startsWith("POST /api/patients/14/forms? ")
            assertThat(calls.last()).doesNotContain("patientSignedAt")
            classToTest.save(made.copy(diagnosis = "K02.0"))
            assertThat(calls.last()).startsWith("PUT /api/patients/14/forms/9? ")
            assertThat(classToTest.forms(14).first()?.first()?.id).isEqualTo(9L)
        }

    @Test
    fun `signature request, document link and ICD search go where the backend expects`() =
        runBlocking<Unit> {
            assertThat(classToTest.requestSignature(14, 3).getOrThrow().signatureRequested).isTrue()
            assertThat(calls.last()).startsWith("PUT /api/patients/14/forms/3/request-signature? {}")
            assertThat(classToTest.linkDocument(14, 3, 77).getOrThrow().documentId).isEqualTo(77L)
            assertThat(calls.last()).startsWith("POST /api/patients/14/forms/3/link-document?documentId=77")
            val hits = classToTest.searchIcd10(" K02 ", "ru").getOrThrow()
            assertThat(hits.map { it.code }).containsExactly("K02.0")
            assertThat(calls.last()).startsWith("GET /api/icd10/search?q=K02&limit=20&lang=ru")
        }

    @Test
    fun `a read that ends after a sign-out caches nothing`() =
        runBlocking<Unit> {
            hold = CompletableDeferred()
            // On another thread: this one waits for the request below.
            val read = async(Dispatchers.Default) { classToTest.list(14) }
            while (calls.isEmpty()) Thread.sleep(5)
            classToTest.clear()
            hold!!.complete(Unit)
            read.await()
            assertThat(classToTest.forms(14).first()).isNull()
        }

    @Test
    fun `a document's file is replaced with a multipart put of the file alone`() =
        runBlocking<Unit> {
            val docs = DocumentsRepository { createHttpClient({ null }, engine) }
            docs.replaceFile(
                14,
                77,
                UploadFile("%PDF-1.4".encodeToByteArray(), "form_025-2.pdf", "application/pdf"),
                clinicId = 3,
            ).getOrThrow()
            assertThat(calls.last()).startsWith("PUT /api/patients/14/documents/77?clinicId=3")
            assertThat(calls.last()).contains("filename=\"form_025-2.pdf\"")
            assertThat(calls.last()).doesNotContain("name=title")
        }

    @Test
    fun `a read overtaken by a save is dropped, and a save that ends after a sign-out caches nothing`() =
        runBlocking<Unit> {
            classToTest.list(14)
            hold = CompletableDeferred()
            val read = async(Dispatchers.Default) { classToTest.list(14) }
            while (calls.size < 2) Thread.sleep(5)
            val first = hold!!
            hold = null
            classToTest.requestSignature(14, 3)
            first.complete(Unit)
            read.await()
            // The signature request's answer stays; the older read didn't put the old copy back.
            assertThat(classToTest.forms(14).first()?.first { it.id == 3L }?.signatureRequested).isTrue()

            hold = CompletableDeferred()
            val save =
                async(Dispatchers.Default) {
                    classToTest.save(PatientForm(id = 2, patientId = 14, date = "2026-03-12", fullName = "A"))
                }
            while (calls.last().startsWith("GET")) Thread.sleep(5)
            classToTest.clear()
            hold!!.complete(Unit)
            save.await()
            assertThat(classToTest.forms(14).first()).isNull()
        }
}
