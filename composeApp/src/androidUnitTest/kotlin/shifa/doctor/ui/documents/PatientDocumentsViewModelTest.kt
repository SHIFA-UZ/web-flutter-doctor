@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

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
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.DocumentsEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.documentsLoadFailed
import shifa.doctor.generated.resources.requestAccessSent
import shifa.doctor.generated.resources.requestAlreadySent
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class PatientDocumentsViewModelTest {
    private var listStatus = HttpStatusCode.OK
    private var requestStatus = HttpStatusCode.OK
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private var hold: CompletableDeferred<Unit>? = null
    private val engine =
        MockEngine { req ->
            hold?.await()
            val path = req.url.encodedPath
            val status = if (path.endsWith("/request-access")) requestStatus else listStatus
            when {
                status != HttpStatusCode.OK ->
                    respond(
                        """{"status":${status.value},"code":"X","requestId":"req-d"}""",
                        status,
                        json,
                    )
                path.endsWith("/request-access") -> respond("{}", status, json)
                else ->
                    respond(
                        """[{"id":5,"title":"Blood test","date":"2026-10-22","canView":true,"category":"BLOOD_TEST","isSharedWithTeam":true},
                           {"id":6,"title":"ECG from previous clinic","date":"2026-09-29","canView":false,"category":"ECG","creatorLabel":"Jasur Aliyev"}]""",
                        status,
                        json,
                    )
            }
        }
    private val created = mutableListOf<PatientDocumentsViewModel>()
    private val tracker = RecordingTracker()

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
        PatientDocumentsViewModel(14, null, DocumentsRepository { createHttpClient({ null }, engine) }, tracker).also {
            created += it
            it.load()
            settle { it.state.value.documents != null || it.state.value.error != null }
        }

    @Test
    fun `the list loads and the search filters titles regardless of case`() {
        val classToTest = opened()
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(5L, 6L).inOrder()
        classToTest.search("  ecg ")
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(6L)
        classToTest.search("x-ray")
        assertThat(classToTest.state.value.shown).isEmpty()
    }

    @Test
    fun `a failed first read is the tab's error, a failed refresh a banner over the list`() {
        listStatus = HttpStatusCode.InternalServerError
        val failing = opened()
        assertThat(failing.state.value.error?.requestId).isEqualTo("req-d")
        listStatus = HttpStatusCode.OK
        val classToTest = opened()
        listStatus = HttpStatusCode.InternalServerError
        classToTest.load()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.documentsLoadFailed)
        assertThat(classToTest.state.value.documents).hasSize(2)
        assertThat(classToTest.state.value.error).isNull()
    }

    @Test
    fun `a read that fails after a newer one started changes nothing`() {
        listStatus = HttpStatusCode.InternalServerError
        hold = CompletableDeferred()
        val classToTest =
            PatientDocumentsViewModel(14, null, DocumentsRepository { createHttpClient({ null }, engine) })
        created += classToTest
        classToTest.load()
        val first = hold!!
        hold = null
        listStatus = HttpStatusCode.OK
        classToTest.load()
        settle { classToTest.state.value.documents != null }
        first.complete(Unit)
        Thread.sleep(200)
        assertThat(classToTest.state.value.error).isNull()
        assertThat(classToTest.state.value.documents).hasSize(2)
    }

    @Test
    fun `request access says sent, already sent on a 409, the error otherwise`() {
        val classToTest = opened()
        val locked = classToTest.state.value.documents!!.last()
        classToTest.requestAccess(locked)
        settle { locked.id !in classToTest.state.value.requesting }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.requestAccessSent)
        requestStatus = HttpStatusCode.Conflict
        classToTest.requestAccess(locked)
        settle { classToTest.state.value.banner?.text == Res.string.requestAlreadySent }
        assertThat(classToTest.state.value.banner?.error).isFalse()
        requestStatus = HttpStatusCode.InternalServerError
        classToTest.requestAccess(locked)
        settle { classToTest.state.value.banner?.requestId == "req-d" }
        assertThat(classToTest.state.value.banner?.error).isTrue()
    }

    @Test
    fun `access requests from the list are tracked by their outcome`() {
        val classToTest = opened()
        val locked = classToTest.state.value.documents!!.last()
        classToTest.requestAccess(locked)
        settle { locked.id !in classToTest.state.value.requesting }
        requestStatus = HttpStatusCode.Conflict
        classToTest.requestAccess(locked)
        settle { classToTest.state.value.banner?.text == Res.string.requestAlreadySent }
        requestStatus = HttpStatusCode.InternalServerError
        classToTest.requestAccess(locked)
        settle { classToTest.state.value.banner?.requestId == "req-d" }

        assertThat(tracker.events)
            .containsExactly(
                DocumentsEvents.DocumentAccessRequested("list"),
                DocumentsEvents.DocumentAccessRequestFailed("list", "already_requested"),
                DocumentsEvents.DocumentAccessRequestFailed("list", "server"),
            ).inOrder()
    }
}
