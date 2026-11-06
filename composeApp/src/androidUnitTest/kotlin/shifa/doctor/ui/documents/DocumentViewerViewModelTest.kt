package shifa.doctor.ui.documents

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.couldNotLoadDocument
import shifa.doctor.generated.resources.documentSaveFailed
import shifa.doctor.stopAll
import shifa.doctor.utils.FileSaver

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewerViewModelTest {
    private var status = HttpStatusCode.OK
    private var body = "%PDF-1.4 x".encodeToByteArray()
    private val requested = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var hold: CompletableDeferred<Unit>? = null
    private val engine =
        MockEngine { req ->
            hold?.await()
            if (req.url.encodedPath.endsWith("/request-access")) {
                requested += req.url.encodedPath
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else if (status == HttpStatusCode.OK) {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/octet-stream"))
            } else {
                respond(
                    """{"status":${status.value},"code":"X","requestId":"req-doc"}""",
                    status,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
    private val created = mutableListOf<DocumentViewerViewModel>()

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

    private fun opened() =
        DocumentViewerViewModel(14, 5, null, DocumentsRepository { createHttpClient({ null }, engine) }).also {
            created += it
            it.load()
            settle { it.state.value.viewed != Viewed.Loading }
        }

    @Test
    fun `a PDF is shown, typed by its bytes`() {
        val viewed = opened().state.value.viewed
        assertThat(viewed).isInstanceOf(Viewed.Ready::class.java)
        assertThat((viewed as Viewed.Ready).file.isPdf).isTrue()
    }

    @Test
    fun `403 offers request access, 404 says it is gone, other failures retry with the request id, empty fails`() {
        status = HttpStatusCode.Forbidden
        assertThat(opened().state.value.viewed).isEqualTo(Viewed.NoAccess)
        status = HttpStatusCode.NotFound
        assertThat(opened().state.value.viewed).isEqualTo(Viewed.Gone)
        status = HttpStatusCode.InternalServerError
        val failed = opened().state.value.viewed as Viewed.Failed
        assertThat(failed.error.requestId).isEqualTo("req-doc")
        status = HttpStatusCode.OK
        body = ByteArray(0)
        assertThat((opened().state.value.viewed as Viewed.Failed).error.text).isEqualTo(Res.string.couldNotLoadDocument)
    }

    @Test
    fun `request access goes through once and then the screen leaves`() {
        status = HttpStatusCode.Forbidden
        val classToTest = opened()
        classToTest.requestAccess()
        settle { classToTest.state.value.requested }
        assertThat(classToTest.state.value.requested).isTrue()
        assertThat(requested).containsExactly("/api/patients/14/documents/5/request-access")
        // Leaving forgets it: opening the document again (the shell keeps this ViewModel) doesn't leave at once.
        classToTest.release()
        assertThat(classToTest.state.value.requested).isFalse()
        status = HttpStatusCode.OK
        classToTest.load()
        settle { classToTest.state.value.viewed is Viewed.Ready }
        assertThat(classToTest.state.value.viewed).isInstanceOf(Viewed.Ready::class.java)
        // And the file's bytes go with the screen.
        classToTest.release()
        assertThat(classToTest.state.value.viewed).isEqualTo(Viewed.Loading)
    }

    @Test
    fun `a save that fails says so`() {
        val classToTest = opened()
        // No platform context here: the saver fails, as a full disk or a refused write would.
        classToTest.save(FileSaver(), "Blood test")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.documentSaveFailed)
    }

    @Test
    fun `a download or a request that finishes after the screen left changes nothing`() {
        val classToTest = opened()
        hold = CompletableDeferred()
        classToTest.load()
        classToTest.release()
        hold!!.complete(Unit)
        Thread.sleep(200)
        assertThat(classToTest.state.value.viewed).isEqualTo(Viewed.Loading)

        status = HttpStatusCode.Forbidden
        hold = null
        classToTest.load()
        settle { classToTest.state.value.viewed == Viewed.NoAccess }
        hold = CompletableDeferred()
        classToTest.requestAccess()
        classToTest.release()
        hold!!.complete(Unit)
        Thread.sleep(200)
        assertThat(classToTest.state.value.requested).isFalse()
    }
}
