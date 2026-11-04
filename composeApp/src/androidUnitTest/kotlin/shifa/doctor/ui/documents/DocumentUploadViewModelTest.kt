@file:Suppress("ktlint:standard:max-line-length") // byte fixtures

package shifa.doctor.ui.documents

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.documentUploadedNamed
import shifa.doctor.generated.resources.fileTooLarge
import shifa.doctor.generated.resources.noFileData
import shifa.doctor.generated.resources.onlyPdfOrImages
import shifa.doctor.generated.resources.uploadCancelled
import shifa.doctor.generated.resources.uploadFailed
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentUploadViewModelTest {
    private var status = HttpStatusCode.OK
    private var hold: CompletableDeferred<Unit>? = null
    private val sent = mutableListOf<String>()
    private val engine =
        MockEngine { req ->
            sent += "${req.method.value} ${req.url.encodedPath}?${req.url.encodedQuery}\n" + req.body.toByteArray().decodeToString()
            hold?.await()
            if (status == HttpStatusCode.OK) {
                respond(
                    """{"id":77,"title":"x","date":"2026-11-04","canView":true}""",
                    status,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    """{"status":${status.value},"code":"X","requestId":"req-up"}""",
                    status,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
    private val created = mutableListOf<DocumentUploadViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened(clinicId: Long? = null) =
        DocumentUploadViewModel(14, clinicId, null, DocumentsRepository { createHttpClient({ null }, engine) })
            .also { created += it }

    // The smallest JPEG the PDF writer takes: SOI, a 3-channel 8-bit frame header with its components, EOI.
    private val jpeg =
        (
            listOf(0xFF, 0xD8, 0xFF, 0xC0, 0x00, 0x11, 0x08, 0x00, 0x0A, 0x00, 0x0A, 0x03) +
                List(9) {
                    0
                } + List(4) { 0 } + listOf(0xFF, 0xD9)
        )
            .map { it.toByte() }
            .toByteArray()

    @Test
    fun `a PDF is offered with its name as the title, an empty or too large one is refused`() {
        val classToTest = opened()
        classToTest.pdf(null, null)
        assertThat(classToTest.state.value.draft).isNull()
        classToTest.pdf(ByteArray(0), "x.pdf")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.noFileData)
        classToTest.pdf(ByteArray(DocumentUploadViewModel.MAX_FILE + 1), "big.pdf")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.fileTooLarge)
        assertThat(classToTest.state.value.banner?.args).containsExactly(25)
        // A DOCX renamed to .pdf is refused by its bytes; a PNG picked as a file goes as an image.
        classToTest.pdf("PK\u0003\u0004word".encodeToByteArray(), "notes.pdf")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.onlyPdfOrImages)
        classToTest.pdf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D), "scan.png")
        assertThat(classToTest.state.value.draft?.file?.mime).isEqualTo("image/png")
        // A long file name is cut to 120 characters, edited or not.
        classToTest.pdf("%PDF-1.4".encodeToByteArray(), "x".repeat(200) + ".pdf")
        assertThat(classToTest.state.value.draft!!.effectiveTitle).hasLength(120)
        classToTest.edit { it.copy(title = "") }
        assertThat(classToTest.state.value.draft!!.effectiveTitle).hasLength(120)
        classToTest.pdf("%PDF-1.4".encodeToByteArray(), "Blood test March.pdf")
        assertThat(classToTest.state.value.draft?.title).isEqualTo("Blood test March")
        assertThat(classToTest.state.value.draft?.file?.mime).isEqualTo("application/pdf")
    }

    @Test
    fun `the type is required, then the file goes as multipart with title, category and the clinic`() {
        val classToTest = opened(clinicId = 3)
        classToTest.photo(jpeg, "photo.jpg")
        classToTest.edit { it.copy(title = "  ") }
        classToTest.add()
        assertThat(classToTest.state.value.tried).isTrue()
        assertThat(sent).isEmpty()
        classToTest.edit { it.copy(category = DocumentCategory.XRAY) }
        classToTest.add()
        settle { classToTest.state.value.uploaded == 1 }
        val body = sent.single()
        assertThat(body).startsWith("POST /api/patients/14/documents?clinicId=3")
        assertThat(body).contains("Content-Type: image/jpeg")
        assertThat(body).contains("filename=\"photo.jpg\"")
        // An emptied title falls back to the file name.
        assertThat(body).containsMatch("name=\"?title\"?\\r\\n(.*\\r\\n)*\\r\\nphoto\\r\\n")
        assertThat(body).containsMatch("name=\"?category\"?\\r\\n(.*\\r\\n)*\\r\\nXRAY\\r\\n")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.documentUploadedNamed)
        assertThat(classToTest.state.value.banner?.args).containsExactly("photo")
        assertThat(classToTest.state.value.draft).isNull()
    }

    @Test
    fun `a scan asks for more pages, becomes one PDF, and cancelling it asks first`() {
        val classToTest = opened()
        classToTest.page(jpeg, "Scanned document (1 page)", "scan.pdf")
        assertThat(classToTest.state.value.askMore).isTrue()
        classToTest.addPage()
        classToTest.page(jpeg, "Scanned document (2 pages)", "scan.pdf")
        classToTest.finishScan("Scanned document (2 pages)", "scan_20261104_0930.pdf")
        val draft = classToTest.state.value.draft!!
        assertThat(draft.pages).isEqualTo(2)
        assertThat(draft.title).isEqualTo("Scanned document (2 pages)")
        assertThat(draft.file.fileName).isEqualTo("scan_20261104_0930.pdf")
        assertThat(draft.file.bytes.decodeToString()).contains("/Count 2")
        assertThat(classToTest.close()).isFalse()
        assertThat(classToTest.state.value.askDiscard).isTrue()
        assertThat(classToTest.close()).isTrue()
        assertThat(classToTest.state.value.draft).isNull()
    }

    @Test
    fun `a failed upload keeps the file for Retry, Cancel stops a running one`() {
        status = HttpStatusCode.InternalServerError
        val classToTest = opened()
        classToTest.photo(jpeg, "photo.jpg")
        classToTest.edit { it.copy(category = DocumentCategory.OTHER_PRIVATE) }
        classToTest.add()
        settle { classToTest.state.value.failed != null }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.uploadFailed)
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-up")
        status = HttpStatusCode.OK
        classToTest.retry()
        settle { classToTest.state.value.uploaded == 1 }
        assertThat(sent).hasSize(2)
        assertThat(classToTest.state.value.failed).isNull()

        hold = CompletableDeferred()
        classToTest.photo(jpeg, "photo.jpg")
        classToTest.edit { it.copy(category = DocumentCategory.OTHER_PRIVATE) }
        classToTest.add()
        assertThat(classToTest.state.value.uploading).isEqualTo("photo")
        // A second file waits: its draft isn't lost while the first one runs.
        classToTest.photo(jpeg, "second.jpg")
        classToTest.edit { it.copy(category = DocumentCategory.OTHER_PRIVATE) }
        classToTest.add()
        assertThat(classToTest.state.value.draft?.title).isEqualTo("second")
        classToTest.cancelUpload()
        hold!!.complete(Unit)
        Thread.sleep(200)
        assertThat(classToTest.state.value.uploaded).isEqualTo(1)
        assertThat(classToTest.state.value.uploading).isNull()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.uploadCancelled)
    }
}
