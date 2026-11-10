@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.visit

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.VisitEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.notesSaveFailed
import shifa.doctor.generated.resources.visitFactsDocumentFailed
import shifa.doctor.generated.resources.visitFactsLoadFailed
import shifa.doctor.stopAll
import shifa.doctor.ui.patients.ClinicalTimelineViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class VisitFactsViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val answers = ArrayDeque<Pair<HttpStatusCode, String>>()
    private val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                asked +=
                    "${req.method.value} ${req.url.encodedPath.substringAfter(
                        "/api/",
                    )} ${req.body.toByteArray().decodeToString()}".trim()
                val (status, body) = answers.removeFirst()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )

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

    private fun fact(
        id: Long,
        type: String,
        status: String = "DRAFT",
        kept: Boolean = true,
    ) =
        """{"id":$id,"patientId":11,"factType":"$type","text":"T$id","status":"$status","reflectionKept":$kept,"createdAt":"2026-11-05T05:00:00Z"}"""

    private val drafts =
        HttpStatusCode.OK to """{"facts":[${fact(
            1,
            "MEDICATION",
        )},${fact(2, "SYMPTOM")},${fact(3, "DIAGNOSIS", kept = false)}],"patterns":[]}"""

    @Test
    fun `facts read, a rejection survives a reload, document sends kept and rejected ids and reads again`() {
        answers += drafts
        answers += drafts
        answers += HttpStatusCode.OK to """{"committedFacts":[${fact(
            1,
            "MEDICATION",
            "COMMITTED",
        )}],"createdTaskIds":[701],"patterns":[]}"""
        answers += HttpStatusCode.OK to """{"facts":[${fact(
            1,
            "MEDICATION",
            "COMMITTED",
        )},${fact(2, "SYMPTOM", "REJECTED")},${fact(3, "DIAGNOSIS", kept = false)}],"patterns":[]}"""
        val classToTest = VisitFactsViewModel(101, AiRepository { client }).also { created += it }
        classToTest.load()
        settle { classToTest.state.value.facts != null }
        assertThat(classToTest.state.value.drafts.map { it.id }).containsExactly(1L, 2L)
        assertThat(classToTest.state.value.dropped.map { it.id }).containsExactly(3L)

        classToTest.toggle(2)
        classToTest.load()
        settle { asked.size == 2 && !classToTest.state.value.loading }
        assertThat(classToTest.state.value.rejected).containsExactly(2L)

        classToTest.document()
        settle { classToTest.state.value.done != null && asked.size == 4 && !classToTest.state.value.loading }
        assertThat(
            asked[2],
        ).isEqualTo("POST ai/visit-facts/commit {\"appointmentId\":101,\"acceptFactIds\":[1],\"rejectFactIds\":[2]}")
        assertThat(classToTest.state.value.done).isEqualTo(1 to 1)
        assertThat(classToTest.state.value.drafts).isEmpty()
        assertThat(classToTest.state.value.committed).isEqualTo(1)
        assertThat(classToTest.state.value.rejected).isEmpty()
    }

    @Test
    fun `nothing kept can't be documented, a failed commit says so, a first failed read offers retry`() {
        answers += HttpStatusCode.InternalServerError to """{"status":500}"""
        answers += drafts
        answers += HttpStatusCode.InternalServerError to """{"status":500}"""
        val classToTest = VisitFactsViewModel(101, AiRepository { client }).also { created += it }
        classToTest.load()
        settle { classToTest.state.value.loadError != null }
        assertThat(classToTest.state.value.loadError!!.text).isEqualTo(Res.string.visitFactsLoadFailed)

        classToTest.load()
        settle { classToTest.state.value.facts != null }
        classToTest.toggle(1)
        classToTest.toggle(2)
        assertThat(classToTest.state.value.canDocument).isFalse()
        classToTest.document()
        assertThat(asked).hasSize(2)

        classToTest.toggle(2)
        classToTest.document()
        settle { classToTest.state.value.documentError != null }
        assertThat(classToTest.state.value.documentError!!.text).isEqualTo(Res.string.visitFactsDocumentFailed)
        assertThat(classToTest.state.value.rejected).containsExactly(1L)
    }

    @Test
    fun `notes are saved first, the documented text goes to the editor, a failed re-read keeps the drafts closed`() {
        answers += drafts
        answers += HttpStatusCode.OK to """{"committedFacts":[${fact(
            1,
            "MEDICATION",
            "COMMITTED",
        )},${fact(2, "SYMPTOM", "COMMITTED")}],"createdTaskIds":[],"patterns":[]}"""
        answers += HttpStatusCode.InternalServerError to """{"status":500}"""
        val classToTest = VisitFactsViewModel(101, AiRepository { client }).also { created += it }
        classToTest.load()
        settle { classToTest.state.value.facts != null }

        // Notes that can't be saved stop the commit (the backend would append to a note the editor then overwrites).
        classToTest.document(flushNotes = { false })
        settle { classToTest.state.value.documentError != null }
        assertThat(classToTest.state.value.documentError!!.text).isEqualTo(Res.string.notesSaveFailed)
        assertThat(asked).hasSize(1)

        var appended: String? = null
        classToTest.document(flushNotes = { true }) { appended = it }
        settle { asked.size == 3 && !classToTest.state.value.loading }
        assertThat(appended).isEqualTo("T1\nT2")
        assertThat(classToTest.state.value.drafts).isEmpty()
        assertThat(classToTest.state.value.canDocument).isFalse()
        assertThat(classToTest.state.value.committed).isEqualTo(2)
    }

    @Test
    fun `no facts at all draws nothing`() {
        answers += HttpStatusCode.OK to """{"facts":[],"patterns":[]}"""
        val classToTest = VisitFactsViewModel(101, AiRepository { client }).also { created += it }
        classToTest.load()
        settle { classToTest.state.value.facts != null }
        assertThat(classToTest.state.value.empty).isTrue()
    }

    @Test
    fun `the timeline keeps what it shows when a re-read fails`() {
        answers += HttpStatusCode.OK to """{"timeline":[${fact(1, "MEDICATION", "COMMITTED")}],"patterns":[]}"""
        answers += HttpStatusCode.InternalServerError to """{"status":500}"""
        val classToTest = ClinicalTimelineViewModel(11, AiRepository { client }).also { created += it }
        classToTest.load()
        settle { classToTest.state.value.timeline != null }
        classToTest.load()
        settle { asked.size == 2 }
        Thread.sleep(50)
        assertThat(classToTest.state.value.timeline!!.facts).hasSize(1)
        assertThat(classToTest.state.value.error).isNull()
        assertThat(asked.last()).isEqualTo("GET ai/visit-facts/patient/11/timeline")
    }

    @Test
    fun `documenting is tracked as ranges, a notes failure by its reason`() {
        answers += drafts
        answers += HttpStatusCode.OK to """{"committedFacts":[${fact(
            1,
            "MEDICATION",
            "COMMITTED",
        )}],"createdTaskIds":[9],"patterns":[]}"""
        answers += drafts
        val tracker = RecordingTracker()
        val classToTest = VisitFactsViewModel(101, AiRepository { client }, tracker).also { created += it }
        classToTest.load()
        settle { classToTest.state.value.facts != null }
        classToTest.toggle(2)
        classToTest.document(flushNotes = { false })
        settle { classToTest.state.value.documentError != null }
        classToTest.document()
        settle { classToTest.state.value.done != null }
        assertThat(tracker.events)
            .containsExactly(
                VisitEvents.VisitFactsDocumentFailed("notes_not_saved"),
                VisitEvents.VisitFactsDocumented("1", "1", "1"),
            ).inOrder()
    }
}
