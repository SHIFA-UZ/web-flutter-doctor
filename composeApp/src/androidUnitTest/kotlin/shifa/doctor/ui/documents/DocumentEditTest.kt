@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.documents

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
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
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.DocumentsEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.documentDeleteFailed
import shifa.doctor.generated.resources.documentDeletedNamed
import shifa.doctor.generated.resources.documentUpdated
import shifa.doctor.stopAll

/** BE-21: the doctor's own upload renamed, recategorised and removed from the record's list. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentEditTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private var deleteStatus = HttpStatusCode.NoContent
    private val sent = mutableListOf<String>()
    private val engine =
        MockEngine { req ->
            val body = (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            sent += "${req.method.value} ${req.url.encodedPath}?${req.url.encodedQuery} $body".trim()
            when (req.method) {
                HttpMethod.Patch ->
                    respond(
                        """{"id":7,"title":"MRI knee","date":"2026-11-01","canView":true,"category":"MRI","isSharedWithTeam":true,"canEdit":true,"creatorType":"DOCTOR"}""",
                        HttpStatusCode.OK,
                        json,
                    )
                HttpMethod.Delete ->
                    if (deleteStatus == HttpStatusCode.NoContent) {
                        respond("", deleteStatus)
                    } else {
                        respond(
                            """{"status":${deleteStatus.value},"code":"X","requestId":"req-x"}""",
                            deleteStatus,
                            json,
                        )
                    }
                else ->
                    respond(
                        """[{"id":5,"title":"Blood test","date":"2026-10-22","canView":true,"category":"BLOOD_TEST","isSharedWithTeam":true},
                           {"id":7,"title":"Scan","date":"2026-11-01","canView":true,"category":"APPOINTMENT_NOTE","canEdit":true,"creatorType":"DOCTOR"},
                           {"id":8,"title":"Form 025-2 (2026-11-02)","date":"2026-11-02","canView":true,"category":"FORM_025_2","canEdit":true}]""",
                        HttpStatusCode.OK,
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
        PatientDocumentsViewModel(14, 3, DocumentsRepository { createHttpClient({ null }, engine) }, tracker).also {
            created += it
            it.load()
            settle { it.state.value.documents != null }
        }

    private fun PatientDocumentsViewModel.doc(id: Long) = state.value.documents!!.single { it.id == id }

    @Test
    fun `only the doctor's own uploads open the edit sheet, a form's PDF stays the form editor's`() {
        val classToTest = opened()
        classToTest.startEdit(classToTest.doc(5)) // someone else's
        assertThat(classToTest.state.value.edit).isNull()
        classToTest.startEdit(classToTest.doc(8)) // a 025-2 form's PDF
        assertThat(classToTest.state.value.edit).isNull()
        classToTest.startEdit(classToTest.doc(7))
        assertThat(classToTest.state.value.edit?.category).isEqualTo(DocumentCategory.APPOINTMENT_NOTE)
        assertThat(classToTest.state.value.edit?.changed).isFalse()
    }

    @Test
    fun `save sends the new title and type in the clinic's scope and replaces the row`() {
        val classToTest = opened()
        classToTest.startEdit(classToTest.doc(7))
        classToTest.editDraft { it.copy(title = "  MRI knee ", category = DocumentCategory.MRI) }
        classToTest.saveEdit()
        settle { classToTest.state.value.edit == null }
        assertThat(
            sent,
        ).contains("""PATCH /api/patients/14/documents/7?clinicId=3 {"title":"MRI knee","category":"MRI"}""")
        assertThat(classToTest.doc(7).title).isEqualTo("MRI knee")
        assertThat(classToTest.doc(7).isSharedWithTeam).isTrue()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.documentUpdated)
    }

    @Test
    fun `delete removes the row, already gone counts as done, a failure stays in the sheet`() {
        val classToTest = opened()
        deleteStatus = HttpStatusCode.InternalServerError
        classToTest.startEdit(classToTest.doc(7))
        classToTest.editDraft { it.copy(confirmDelete = true) }
        classToTest.delete()
        settle { classToTest.state.value.edit?.busy == false }
        assertThat(classToTest.state.value.edit?.error?.text).isEqualTo(Res.string.documentDeleteFailed)
        assertThat(classToTest.state.value.edit?.error?.requestId).isEqualTo("req-x")
        assertThat(classToTest.state.value.edit?.confirmDelete).isFalse() // back on the sheet, where the error shows
        assertThat(classToTest.state.value.documents!!.map { it.id }).contains(7L)

        deleteStatus = HttpStatusCode.NotFound
        classToTest.delete()
        settle { classToTest.state.value.edit == null }
        assertThat(classToTest.state.value.documents!!.map { it.id }).containsExactly(5L, 8L).inOrder()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.documentDeletedNamed)
        assertThat(sent.last()).isEqualTo("DELETE /api/patients/14/documents/7?clinicId=3")
    }

    @Test
    fun `edits and removals are tracked by their outcome, never the title`() {
        val classToTest = opened()
        classToTest.startEdit(classToTest.doc(7))
        classToTest.editDraft { it.copy(title = "MRI knee", category = DocumentCategory.MRI) }
        classToTest.saveEdit()
        settle { classToTest.state.value.edit == null }
        deleteStatus = HttpStatusCode.InternalServerError
        classToTest.startEdit(classToTest.doc(7))
        classToTest.delete()
        settle { classToTest.state.value.edit?.busy == false }
        deleteStatus = HttpStatusCode.NoContent
        classToTest.delete()
        settle { classToTest.state.value.edit == null }

        assertThat(tracker.events)
            .containsExactly(
                DocumentsEvents.DocumentEdited(DocumentCategory.MRI, true, true),
                DocumentsEvents.DocumentDeleteFailed("server"),
                DocumentsEvents.DocumentDeleted(DocumentCategory.MRI),
            ).inOrder()
    }
}
