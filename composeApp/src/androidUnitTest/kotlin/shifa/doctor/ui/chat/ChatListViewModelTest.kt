@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.chat

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.chat.ChatRepository
import shifa.doctor.data.chat.ConversationSort
import shifa.doctor.data.model.UserSearchResult
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.ChatEvents
import shifa.doctor.stopAll
import kotlin.time.Duration.Companion.milliseconds

class ChatListViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()
    private val tracker = RecordingTracker()

    private val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.substringAfter("/api/")
            asked += req.method.value + " " + path + (req.url.parameters["q"]?.let { "?q=$it" } ?: "")
            val body =
                when (path) {
                    "messages/conversations" ->
                        """[{"id":7,"participantId":11,"participantName":"Dilnoza","lastMessageAt":"2026-11-05T05:00:00Z","unreadCount":2},{"id":8,"participantId":12,"participantName":"Jasur","lastMessageAt":"2026-11-05T06:00:00Z"}]"""
                    "messages/search" -> """[{"id":11,"name":"Dilnoza","isDoctor":false},{"id":13,"name":"Malika","isDoctor":false}]"""
                    "messages/conversations/start" -> """{"id":9,"participantId":13,"participantName":"Malika"}"""
                    else -> "{}"
                }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

    // Real time: the polling and the search debounce wait with delay().
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

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
        ChatListViewModel(
            ChatRepository({
                createHttpClient({
                    null
                }, engine)
            }, { "https://api.shifa.uz/api/" }),
            every = 50.milliseconds,
            debounce = 30.milliseconds,
            tracker = tracker,
        ).also { created += it }

    @Test
    fun `in front it reads now and again, behind it nothing`() {
        val classToTest = opened()
        assertThat(asked).isEmpty()
        classToTest.foreground(true)
        settle { asked.count { it == "GET messages/conversations" } >= 2 }
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(8L, 7L).inOrder()
        classToTest.foreground(false)
        Thread.sleep(80)
        val reads = asked.size
        Thread.sleep(150)
        assertThat(asked.size).isEqualTo(reads)
    }

    @Test
    fun `typing searches after it stops, a known person opens their conversation, a new one starts`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.conversations != null }
        classToTest.type("D")
        classToTest.type("Di")
        classToTest.type("Dil")
        settle { classToTest.state.value.results != null }
        assertThat(asked.filter { it.startsWith("GET messages/search") }).containsExactly("GET messages/search?q=Dil")
        classToTest.startWith(UserSearchResult(11, "Dilnoza"))
        settle { classToTest.state.value.open != null }
        assertThat(classToTest.state.value.open?.id).isEqualTo(7L)
        assertThat(classToTest.state.value.typed).isEmpty()
        // Opening reads it on the server and drops its pill at once.
        assertThat(classToTest.state.value.conversations!!.first { it.id == 7L }.unread).isEqualTo(0)
        settle { "POST messages/conversations/7/read" in asked }
        assertThat(asked).doesNotContain("POST messages/conversations/start")
        classToTest.opened()
        classToTest.startWith(UserSearchResult(13, "Malika"))
        settle { classToTest.state.value.open?.id == 9L }
        assertThat(asked).contains("POST messages/conversations/start")
        assertThat(classToTest.state.value.conversations!!.map { it.id }).contains(9L)
    }

    @Test
    fun `search, a started conversation and the order are tracked, never the text`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.conversations != null }
        classToTest.type("Mal")
        settle { classToTest.state.value.results != null }
        classToTest.startWith(UserSearchResult(13, "Malika"))
        settle { classToTest.state.value.open?.id == 9L }
        classToTest.sort(ConversationSort.Oldest)
        classToTest.sort(ConversationSort.Oldest)
        assertThat(tracker.events)
            .containsExactly(
                ChatEvents.ChatUsersSearched("2-5"),
                ChatEvents.ChatConversationStarted(isDoctor = false),
                ChatEvents.ChatListSorted(ConversationSort.Oldest),
            ).inOrder()
    }

    @Test
    fun `the unread filter keeps only unread`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.conversations != null }
        classToTest.sort(ConversationSort.UnreadNewest)
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(7L)
    }

    @Test
    fun `a slow read is waited for, never cancelled by the next poll`() {
        val slow =
            MockEngine { req ->
                asked += req.url.encodedPath
                kotlinx.coroutines.delay(150)
                respond(
                    """[{"id":7,"participantId":11,"participantName":"Dilnoza"}]""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val classToTest =
            ChatListViewModel(
                ChatRepository({
                    createHttpClient({ null }, slow)
                }, { "https://api.shifa.uz/api/" }),
                every = 20.milliseconds,
            ).also { created += it }
        classToTest.foreground(true)
        settle { classToTest.state.value.conversations != null }
        assertThat(classToTest.state.value.conversations!!.map { it.id }).containsExactly(7L)
        classToTest.foreground(false)
    }

    @Test
    fun `a failed search says so and Retry asks again`() {
        var fail = true
        val flaky =
            MockEngine { req ->
                asked += req.url.encodedPath
                if (fail) {
                    respond(
                        """{"status":500}""",
                        HttpStatusCode.InternalServerError,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(
                        """[{"id":11,"name":"Dilnoza"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        val classToTest =
            ChatListViewModel(
                ChatRepository({
                    createHttpClient({ null }, flaky)
                }, { "https://api.shifa.uz/api/" }),
                debounce = 10.milliseconds,
            ).also { created += it }
        classToTest.type("Dil")
        settle { classToTest.state.value.searchFailed }
        assertThat(classToTest.state.value.searchFailed).isTrue()
        fail = false
        classToTest.retrySearch()
        settle { classToTest.state.value.results?.isNotEmpty() == true }
        assertThat(classToTest.state.value.searchFailed).isFalse()
        assertThat(classToTest.state.value.results!!.single().id).isEqualTo(11L)
    }
}
