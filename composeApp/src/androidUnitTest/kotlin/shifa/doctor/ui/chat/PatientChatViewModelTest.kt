@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.chat

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
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
import shifa.doctor.data.chat.ChatRepository
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.ChatEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.failedToStartConversation
import shifa.doctor.stopAll
import shifa.doctor.ui.navigation.ShellNotice

@OptIn(ExperimentalCoroutinesApi::class)
class PatientChatViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var status = HttpStatusCode.OK

    private fun body(req: HttpRequestData) = (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()

    private val repo =
        ChatRepository({
            createHttpClient(
                { null },
                MockEngine { req ->
                    asked += req.method.value + " " + req.url.encodedPath.substringAfter("/api/") + " " + body(req)
                    respond(
                        if (status == HttpStatusCode.OK) {
                            """{"id":801,"participantId":11,"participantName":"Dilnoza Rahimova","isDoctorParticipant":false}"""
                        } else {
                            """{"status":400,"code":"VALIDATION_ERROR","requestId":"req-7"}"""
                        },
                        status,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            )
        }, { "https://api.shifa.uz/api/" })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
        ShellNotice.pending.value = null
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the backend finds or starts the conversation with the patient in one call`() {
        val classToTest = PatientChatViewModel(11, repo).also { created += it }
        settle { classToTest.state.value.conversation != null }
        assertThat(classToTest.state.value.conversation?.id).isEqualTo(801L)
        assertThat(asked).containsExactly("POST messages/conversations/start {\"recipientPatientId\":11}")
    }

    @Test
    fun `a refused start says so and gives up`() {
        status = HttpStatusCode.BadRequest
        val tracker = RecordingTracker()
        val classToTest = PatientChatViewModel(11, repo, tracker).also { created += it }
        settle { classToTest.state.value.failed != null }
        assertThat(classToTest.state.value.failed?.text).isEqualTo(Res.string.failedToStartConversation)
        assertThat(classToTest.state.value.failed?.requestId).isEqualTo("req-7")
        assertThat(classToTest.state.value.conversation).isNull()
        // The screen says it, only while it's still there.
        assertThat(ShellNotice.pending.value).isNull()
        assertThat(tracker.events).containsExactly(ChatEvents.ChatConversationStartFailed("validation", "patient"))
    }
}
