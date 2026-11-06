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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.intelligence.CardSlot
import shifa.doctor.data.intelligence.ChatSessionStore
import shifa.doctor.data.model.VisitKind
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiRateLimit
import shifa.doctor.generated.resources.aiUnavailable
import shifa.doctor.generated.resources.intelBookingFailed
import shifa.doctor.generated.resources.intelEmptyReply
import shifa.doctor.generated.resources.intelMissingPatientOrSlot
import shifa.doctor.generated.resources.intelSlotConflict
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val answers = ArrayDeque<Pair<HttpStatusCode, String>>()
    private val bodies = java.util.concurrent.CopyOnWriteArrayList<String>()

    // Booking calls (INTEL-06), apart from the streams: "PUT appointments/102/change" → its body; the next status.
    private val calls = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>()
    private val callAnswers = ArrayDeque<HttpStatusCode>()

    // Holds booking calls until completed, to look at the state while one runs.
    private var gate: CompletableDeferred<Unit>? = null
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath.removePrefix("/api/")
                if (req.method.value == "GET" && path.matches(Regex("appointments/\\d+"))) {
                    val video = path.endsWith("/103")
                    return@MockEngine respond(
                        """{"id":${path.substringAfter('/')},"isVideoConsultation":$video}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                if (!path.startsWith("ai/")) {
                    calls += "${req.method.value} $path" to req.body.toByteArray().decodeToString()
                    gate?.await()
                    return@MockEngine respond("", callAnswers.removeFirstOrNull() ?: HttpStatusCode.OK)
                }
                if (path == "ai/visit-facts/commit") {
                    calls += "${req.method.value} $path" to req.body.toByteArray().decodeToString()
                    gate?.await()
                    val status = callAnswers.removeFirstOrNull() ?: HttpStatusCode.OK
                    val json = if (status == HttpStatusCode.OK) """{"committedFacts":[$COMMITTED_1021,$COMMITTED_1022],"createdTaskIds":[5]}""" else "{}"
                    return@MockEngine respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                bodies += req.body.toByteArray().decodeToString()
                val (status, body) = answers.removeFirst()
                val type = if (status == HttpStatusCode.OK) "text/event-stream" else "application/json"
                respond(body, status, headersOf(HttpHeaders.ContentType, type))
            },
        )
    private val at = Instant.parse("2026-11-05T05:00:00Z")
    private val store = ChatSessionStore(MapSettings(), { "1" }, { at })
    private var changed = 0
    private val touched = mutableListOf<Pair<List<LocalDate>, List<Instant>>>()

    private fun vm(language: String = "UZ") =
        ThreadViewModel(
            store,
            AiRepository { client },
            { language },
            { at },
            { dates, starts ->
                touched += dates to starts
                changed++
            },
            AppointmentsRepository { client },
        ).also { created += it }

    // One answered turn whose card is [payload] (the second saved message).
    private fun withCard(
        classToTest: ThreadViewModel,
        payload: String,
    ) {
        answers += HttpStatusCode.OK to "event:tool\ndata:{\"name\":\"propose_appointment\",\"payload\":$payload}\n\ndata:Here.\n\n"
        classToTest.send("Book Jasur tomorrow")
        settle { store.state.value.active!!.messages.size == 2 }
    }

    private fun savedCard() = store.state.value.active!!.messages[1].cardPayload!!

    private val slots =
        """"slots":[{"startAt":"2026-11-06T05:00:00Z","startLocal":"10:00","slotMinutes":45,"locationId":3,"locationLabel":"Chilonzor"},""" +
            """{"startAt":"2026-11-06T09:00:00Z","startLocal":"14:00"}]"""
    private val slot = CardSlot("2026-11-06T05:00:00Z", "10:00", 45, 3, "Chilonzor")

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    // Up to 10 s: streams are read on the engine's threads, slow while the other test forks run (returns at once when met).
    private fun settle(check: () -> Boolean) {
        repeat(1000) {
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

    @Test
    fun `a card's Confirm books the slot, saves the card booked and refreshes the calendar`() {
        val classToTest = vm()
        withCard(
            classToTest,
            """{"cardType":"propose_appointment","patientId":12,"patientName":"Jasur Karimov","reason":"Check-up",$slots}""",
        )
        classToTest.book(1, slot)
        settle { changed == 1 }
        val (call, body) = calls.single()
        assertThat(call).isEqualTo("POST schedule/book")
        val sent = apiJson.parseToJsonElement(body).jsonObject
        assertThat(sent["startAt"]!!.jsonPrimitive.content).isEqualTo("2026-11-06T05:00:00Z")
        assertThat(sent["slotMinutes"]!!.jsonPrimitive.content).isEqualTo("45")
        assertThat(sent["patientId"]!!.jsonPrimitive.content).isEqualTo("12")
        assertThat(sent["locationId"]!!.jsonPrimitive.content).isEqualTo("3")
        assertThat(sent["location"]!!.jsonPrimitive.content).isEqualTo("Chilonzor")
        assertThat(sent["reason"]!!.jsonPrimitive.content).isEqualTo("Check-up")
        assertThat(savedCard()["booked"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(savedCard()["bookedTime"]!!.jsonPrimitive.content).isEqualTo("10:00")
        assertThat(classToTest.state.value.cards[1]).isEqualTo(CardUi())
        assertThat(touched.single()).isEqualTo(emptyList<LocalDate>() to listOf(Instant.parse("2026-11-06T05:00:00Z")))
        // Booked: a second tap books nothing.
        classToTest.book(1, slot)
        assertThat(calls).hasSize(1)
    }

    @Test
    fun `a video booking sends no location, a move sends the new slot's location`() {
        val classToTest = vm()
        withCard(classToTest, """{"cardType":"propose_appointment","patientId":12,"isVideo":true,$slots}""")
        classToTest.book(1, slot)
        settle { calls.size == 1 && !classToTest.state.value.booking }
        val video = apiJson.parseToJsonElement(calls[0].second).jsonObject
        assertThat(video["isVideo"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(video.keys).containsNoneOf("location", "locationId", "reason")

        classToTest.stop()
        answers += HttpStatusCode.OK to "event:tool\ndata:{\"name\":\"propose_reschedule\",\"payload\":{\"cardType\":\"propose_reschedule\",\"appointmentId\":102,$slots}}\n\n"
        classToTest.send("Move Jasur")
        // Booking waits for Shifa's reply to end and the first booking to settle (both refuse a tap meanwhile).
        settle {
            store.state.value.active!!.messages.size == 4 &&
                !classToTest.state.value.busy &&
                !classToTest.state.value.booking
        }
        classToTest.book(3, slot)
        settle { calls.size == 2 }
        assertThat(calls[1].first).isEqualTo("PUT appointments/102/change")
        assertThat(
            apiJson.parseToJsonElement(calls[1].second).jsonObject["locationId"]!!.jsonPrimitive.content,
        ).isEqualTo("3")
        settle { store.state.value.active!!.messages[3].cardPayload!!["moved"] != null }
        assertThat(
            store.state.value.active!!.messages[3].cardPayload!!["moved"]!!.jsonPrimitive.content,
        ).isEqualTo("true")
        // The video booking was the first change; the move the second.
        settle { touched.size == 2 }
        assertThat(touched.last().second).containsExactly(Instant.parse("2026-11-06T05:00:00Z"))
    }

    @Test
    fun `a taken slot, a failure and no connection each say so, nothing is saved`() {
        callAnswers += HttpStatusCode.Conflict
        callAnswers += HttpStatusCode.BadRequest
        val classToTest = vm()
        withCard(classToTest, """{"cardType":"propose_appointment","patientId":12,$slots}""")
        classToTest.book(1, slot)
        settle { classToTest.state.value.cards[1]?.error != null }
        assertThat(classToTest.state.value.cards[1]!!.error!!.text).isEqualTo(Res.string.intelSlotConflict)
        assertThat(classToTest.state.value.cards[1]!!.busy).isFalse()
        classToTest.book(1, slot)
        settle { classToTest.state.value.cards[1]?.error?.text == Res.string.intelBookingFailed }
        assertThat(classToTest.state.value.cards[1]!!.error!!.args).containsExactly("400")
        assertThat(savedCard()["booked"]).isNull()
        assertThat(changed).isEqualTo(0)
        // A slot without its instant can't be booked.
        classToTest.book(1, slot.copy(startAt = null))
        assertThat(classToTest.state.value.cards[1]!!.error!!.text).isEqualTo(Res.string.intelMissingPatientOrSlot)
        assertThat(calls).hasSize(2)
    }

    @Test
    fun `the backend's workflow card books nothing from the app, Cancel hides the card for good`() {
        val classToTest = vm()
        withCard(classToTest, """{"cardType":"propose_appointment","workflowId":"w1","patientId":12,$slots}""")
        classToTest.book(1, slot)
        assertThat(calls).isEmpty()
        classToTest.toggleOtherTimes(1)
        assertThat(classToTest.state.value.cards[1]!!.showOther).isTrue()
        classToTest.dismiss(1)
        assertThat(savedCard()["dismissed"]!!.jsonPrimitive.content).isEqualTo("true")
        // The next turn is added onto the chat as it is now: the card stays dismissed.
        answers += HttpStatusCode.OK to "data:Ok\n\n"
        classToTest.send("thanks")
        settle { store.state.value.active!!.messages.size == 4 }
        assertThat(savedCard()["dismissed"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `the cancellation buttons answer in the chat's language, a cancellation done refreshes the calendar`() {
        val classToTest = vm("RU")
        answers += HttpStatusCode.OK to "data:Могу\n\n"
        classToTest.send("Отмени пятницу")
        settle { store.state.value.active!!.messages.size == 2 }
        answers += HttpStatusCode.OK to "event:tool\ndata:{\"name\":\"cancel_appointments\",\"payload\":{\"cardType\":\"cancel_result\",\"cancelled\":[{\"appointmentId\":5}],\"failed\":[]}}\n\ndata:Отменено\n\n"
        classToTest.answerCancel(true)
        settle { store.state.value.active!!.messages.size == 4 }
        assertThat(store.state.value.active!!.messages[2].content).isEqualTo("да")
        settle { changed == 1 }
        assertThat(changed).isEqualTo(1)

        val uz = vm("UZ")
        store.create()
        answers += HttpStatusCode.OK to "data:Hech narsa\n\n"
        uz.answerCancel(false)
        settle { store.state.value.active!!.messages.size == 2 }
        assertThat(store.state.value.active!!.messages[0].content).isEqualTo("yo'q")
    }

    @Test
    fun `while a card books, leaving and coming back neither books again nor lets a question through`() {
        val classToTest = vm()
        withCard(
            classToTest,
            """{"cardType":"propose_reschedule","appointmentId":102,"currentDateLabel":"5 Nov 2026","currentStartLocal":"10:30",$slots}""",
        )
        gate = CompletableDeferred()
        classToTest.book(1, slot)
        settle { calls.size == 1 }
        assertThat(classToTest.state.value.booking).isTrue()
        classToTest.stop()
        assertThat(classToTest.state.value.cards[1]!!.busy).isTrue()
        classToTest.book(1, slot.copy(startAt = "2026-11-06T09:00:00Z", time = "14:00"))
        classToTest.draft("use 14:00 instead")
        assertThat(classToTest.state.value.canSend).isFalse()
        classToTest.send()
        assertThat(calls).hasSize(1)
        assertThat(bodies).hasSize(1)

        gate!!.complete(Unit)
        settle { touched.size == 1 }
        assertThat(classToTest.state.value.booking).isFalse()
        // The visit's old day and the new slot's start are both read again.
        assertThat(
            touched.single(),
        ).isEqualTo(listOf(LocalDate(2026, 11, 5)) to listOf(Instant.parse("2026-11-06T05:00:00Z")))
    }

    @Test
    fun `a cancellation's result reads the cancelled visits' days again`() {
        answers += HttpStatusCode.OK to "event:tool\ndata:{\"name\":\"cancel_appointments\",\"payload\":{\"cardType\":\"cancel_result\",\"cancelled\":[{\"startAt\":\"2026-11-07T05:00:00Z\"}]}}\n\ndata:Cancelled 1.\n\n"
        vm().send("yes")
        settle { touched.size == 1 }
        assertThat(touched.single().second).containsExactly(Instant.parse("2026-11-07T05:00:00Z"))
    }

    @Test
    fun `Document everything commits every listed fact once and saves the count, a failure says so`() {
        val classToTest = vm()
        withCard(
            classToTest,
            """{"cardType":"draft_visit_facts","appointmentId":102,"facts":[{"factId":1021,"factType":"MEDICATION","text":"Ibuprofen"},{"factId":1022,"factType":"SYMPTOM","text":"Pain"}]}""",
        )
        callAnswers += HttpStatusCode.InternalServerError
        classToTest.documentFacts(1)
        settle { classToTest.state.value.cards[1]?.error != null }
        assertThat(classToTest.state.value.cards[1]!!.error!!.text).isNotNull()
        assertThat(savedCard()["documented"]).isNull()

        classToTest.documentFacts(1)
        settle { savedCard()["documented"] != null }
        assertThat(savedCard()["documented"]!!.jsonPrimitive.content).isEqualTo("2")
        val sent = apiJson.parseToJsonElement(calls.last().second).jsonObject
        assertThat(sent["appointmentId"]!!.jsonPrimitive.content).isEqualTo("102")
        assertThat(sent["acceptFactIds"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly("1021", "1022")
        // Documented: nothing is sent again.
        classToTest.documentFacts(1)
        assertThat(calls).hasSize(2)
    }

    @Test
    fun `a commit still running after leaving and coming back is not sent again`() {
        val classToTest = vm()
        withCard(
            classToTest,
            """{"cardType":"draft_visit_facts","appointmentId":102,"facts":[{"factId":1021,"factType":"MEDICATION","text":"Ibuprofen"}]}""",
        )
        gate = CompletableDeferred()
        classToTest.documentFacts(1)
        settle { calls.size == 1 }
        classToTest.stop()
        assertThat(classToTest.state.value.cards[1]!!.busy).isTrue()
        classToTest.documentFacts(1)
        gate!!.complete(Unit)
        settle { savedCard()["documented"] != null }
        assertThat(calls).hasSize(1)
    }

    @Test
    fun `Review on the visit opens a video visit as video, others in the clinic`() {
        val classToTest = vm()
        val kinds = mutableListOf<VisitKind>()
        classToTest.openVisit(103) { kinds += it }
        settle { kinds.size == 1 }
        classToTest.openVisit(102) { kinds += it }
        settle { kinds.size == 2 }
        assertThat(kinds).containsExactly(VisitKind.Video, VisitKind.InPerson).inOrder()
    }

    @Test
    fun `a live answer that opens a visit asks the screen to open it once, a closed or already opened visit does not`() {
        val classToTest = vm()
        val visit =
            """{"cardType":"open_appointment","appointmentId":102,"route":"inPerson","appointment":{"appointmentId":102,"status":"CONFIRMED","day":"2026-11-06","startLocal":"10:30","endLocal":"11:00","route":"inPerson"}}"""
        withCard(classToTest, visit)
        assertThat(classToTest.state.value.autoOpen!!.appointmentId).isEqualTo(102)
        classToTest.autoOpened()
        assertThat(classToTest.state.value.autoOpen).isNull()
        answers += HttpStatusCode.OK to "event:tool\ndata:{\"name\":\"open_appointment\",\"payload\":${visit.replace(
            "CONFIRMED",
            "CANCELLED",
        )}}\n\ndata:Cancelled.\n\n"
        classToTest.send("Open it again")
        settle { store.state.value.active!!.messages.size == 4 }
        assertThat(classToTest.state.value.autoOpen).isNull()
        // The same visit asked for again in this run doesn't open again.
        answers += HttpStatusCode.OK to "event:tool\ndata:{\"name\":\"open_appointment\",\"payload\":$visit}\n\ndata:Again.\n\n"
        classToTest.send("Open my 10:30 again")
        settle { store.state.value.active!!.messages.size == 6 }
        assertThat(classToTest.state.value.autoOpen).isNull()
    }
}

private const val COMMITTED_1021 = """{"id":1021,"patientId":12,"factType":"MEDICATION","text":"Ibuprofen","status":"COMMITTED"}"""
private const val COMMITTED_1022 = """{"id":1022,"patientId":12,"factType":"SYMPTOM","text":"Pain","status":"COMMITTED"}"""
