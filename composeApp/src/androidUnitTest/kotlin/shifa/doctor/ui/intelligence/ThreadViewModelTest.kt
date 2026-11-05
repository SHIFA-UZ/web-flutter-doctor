@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.intelligence

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
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
import kotlinx.datetime.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.intelligence.ChatSessionStore
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiRateLimit
import shifa.doctor.generated.resources.aiUnavailable
import shifa.doctor.generated.resources.intelEmptyReply

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {
    private val answers = ArrayDeque<Pair<HttpStatusCode, String>>()
    private val bodies = mutableListOf<String>()
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                bodies += req.body.toByteArray().decodeToString()
                val (status, body) = answers.removeFirst()
                val type = if (status == HttpStatusCode.OK) "text/event-stream" else "application/json"
                respond(body, status, headersOf(HttpHeaders.ContentType, type))
            },
        )
    private val at = Instant.parse("2026-11-05T05:00:00Z")
    private val store = ChatSessionStore(MapSettings(), { "1" }, { at })
    private var changed = 0

    private fun vm(language: String = "UZ") =
        ThreadViewModel(store, AiRepository { client }, { language }, { at }, { changed++ })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `a typed question streams, the card shows, the turn is saved with the chat id and language`() {
        answers += HttpStatusCode.OK to "event:tool\ndata:{\"name\":\"get_today_schedule\",\"payload\":{\"cardType\":\"schedule\",\"count\":2}}\n\ndata:You have\n\ndata: two visits.\n\n"
        val classToTest = vm()
        classToTest.draft("Hey Shifa, what is my schedule today?")
        classToTest.send()
        settle { store.state.value.active!!.messages.size == 2 }
        val saved = store.state.value.active!!.messages
        assertThat(saved[0].content).isEqualTo("what is my schedule today?")
        assertThat(saved[1].content).isEqualTo("You have two visits.")
        assertThat(saved[1].toolName).isEqualTo("get_today_schedule")
        assertThat(classToTest.state.value.busy).isFalse()
        assertThat(classToTest.state.value.draft).isEmpty()
        // English words switch the reply language from the app's Uzbek; the chat id is the conversation id.
        val sent = apiJson.parseToJsonElement(bodies.single()).jsonObject
        assertThat(sent["language"]!!.jsonPrimitive.content).isEqualTo("EN")
        assertThat(sent["conversationId"]!!.jsonPrimitive.content).isEqualTo(store.state.value.activeId)
        assertThat(sent["messages"]!!.jsonArray).hasSize(1)
    }

    @Test
    fun `a booking says so and refreshes the calendar, the next turn sends the history`() {
        answers += HttpStatusCode.OK to "event:tool\ndata:{\"name\":\"manage_booking_workflow\",\"payload\":{\"cardType\":\"propose_appointment\",\"booked\":true,\"patientId\":7,\"statusMessage\":\"Booked Jasur Karimov at 10:00.\"}}\n\ndata:Done\n\n"
        answers += HttpStatusCode.OK to "data:Ok\n\n"
        val classToTest = vm()
        classToTest.send("Book Jasur tomorrow at 10")
        settle { store.state.value.active!!.messages.size == 2 }
        assertThat(store.state.value.active!!.messages[1].content).isEqualTo("Booked Jasur Karimov at 10:00.")
        settle { changed == 1 }
        assertThat(changed).isEqualTo(1)

        classToTest.send("thanks")
        settle { store.state.value.active!!.messages.size == 4 }
        val second = apiJson.parseToJsonElement(bodies[1]).jsonObject
        assertThat(second["messages"]!!.jsonArray).hasSize(3)
        // The proposal's patient is the focus.
        assertThat(second["patientId"]!!.jsonPrimitive.content).isEqualTo("7")
    }

    @Test
    fun `an empty reply and a rate limit are errors with retry, nothing is saved twice`() {
        answers += HttpStatusCode.OK to "data:\n\n"
        answers += HttpStatusCode.TooManyRequests to """{"status":429,"code":"RATE_LIMITED"}"""
        answers += HttpStatusCode.OK to "data:Here.\n\n"
        val classToTest = vm()
        classToTest.send("Find Ada")
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.error!!.text).isEqualTo(Res.string.intelEmptyReply)
        assertThat(classToTest.state.value.asking).isEqualTo("Find Ada")
        classToTest.retry()
        settle { classToTest.state.value.error?.text == Res.string.aiRateLimit }
        classToTest.retry()
        settle { store.state.value.active!!.messages.size == 2 }
        assertThat(
            store.state.value.active!!.messages.map { it.content },
        ).containsExactly("Find Ada", "Here.").inOrder()
        assertThat(classToTest.state.value.error).isNull()
    }

    @Test
    fun `the reply language belongs to each conversation, a finish for a chat no longer open is dropped`() {
        answers += HttpStatusCode.OK to "data:Хорошо\n\n"
        answers += HttpStatusCode.OK to "data:Ok\n\n"
        val classToTest = vm(language = "UZ")
        classToTest.send("Сколько приёмов сегодня?")
        settle { store.state.value.active!!.messages.size == 2 }
        val russian = store.state.value.activeId
        store.create()
        classToTest.send("8:30")
        settle { store.state.value.active!!.messages.size == 2 }
        // The new chat starts from the app's language, not the Russian chat's.
        assertThat(apiJson.parseToJsonElement(bodies[1]).jsonObject["language"]!!.jsonPrimitive.content).isEqualTo("UZ")
        assertThat(store.state.value.activeId).isNotEqualTo(russian)
    }

    @Test
    fun `the AI service down says so`() {
        answers += HttpStatusCode.OK to "event:error\ndata:{\"code\":\"AI_UNAVAILABLE\"}\n\n"
        val classToTest = vm()
        classToTest.send("Find Ada")
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.error!!.text).isEqualTo(Res.string.aiUnavailable)
    }

    @Test
    fun `stop drops the running turn, a blank or wake-only text sends nothing`() {
        val classToTest = vm()
        classToTest.send("   ")
        classToTest.send("Salom Shifa")
        assertThat(bodies).isEmpty()
        assertThat(classToTest.state.value.asking).isNull()
        classToTest.stop()
        assertThat(classToTest.state.value.busy).isFalse()
    }
}
