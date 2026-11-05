@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.notifications

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.notifications.NotificationFilter
import shifa.doctor.data.notifications.NotificationsRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appointmentGone
import shifa.doctor.generated.resources.notificationsMarkFailed
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PracticeClock

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val asked = mutableListOf<String>()
    private var readStatus = HttpStatusCode.OK
    private var pageFails = false
    private var holdPageOne: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    private fun row(
        id: Long,
        type: String,
        extra: String = "",
        read: Boolean = false,
    ) =
        """{"id":$id,"type":"$type","title":"T","message":"M","createdAt":"2026-11-05T0${id % 9}:00:00Z","readAt":${if (read) "\"2026-11-05T09:00:00Z\"" else "null"}$extra}"""

    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.substringAfter("/api/")
            asked += req.method.value + " " + path + (
                req.url.parameters["page"]?.let {
                    "?page=$it"
                } ?: ""
            ) + (req.url.parameters["day"]?.let { "?day=$it" } ?: "")
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                path == "notifications/page" && pageFails ->
                    respond(
                        """{"status":500,"requestId":"r-1"}""",
                        HttpStatusCode.InternalServerError,
                        json,
                    )
                path == "notifications/page" && req.url.parameters["page"] == "0" ->
                    respond(
                        """{"content":[${row(
                            1,
                            "APPOINTMENT_BOOKED_BY_PATIENT",
                            ""","appointmentId":101,"appointmentStartAt":"2026-11-06T05:00:00Z"""",
                        )},${row(
                            2,
                            "APPOINTMENT_CANCELLED_BY_PATIENT",
                            ""","appointmentId":102""",
                        )},${row(3, "TASK_COMPLETED", ""","taskId":1""")}],"number":0,"last":false}""",
                        HttpStatusCode.OK,
                        json,
                    )
                // Page 1 repeats row 3 (a new row shifted the pages) and brings two more.
                path == "notifications/page" && holdPageOne != null -> {
                    holdPageOne!!.await()
                    respond("""{"content":[${row(9, "GENERAL")}],"number":1,"last":true}""", HttpStatusCode.OK, json)
                }
                path == "notifications/page" ->
                    respond(
                        """{"content":[${row(
                            3,
                            "TASK_COMPLETED",
                        )},${row(
                            4,
                            "DOCUMENT_ACCESS_APPROVED",
                            ""","patientId":11,"documentId":7,"documentTitle":"Blood test"""",
                        )},${row(5, "CHAT_MESSAGE", read = true)}],"number":1,"last":true}""",
                        HttpStatusCode.OK,
                        json,
                    )
                path.endsWith("/read") || path.endsWith("read-all") -> respond("", readStatus, json)
                // 101 is on its day; 102 is gone (404).
                path == "calendar" ->
                    respond(
                        """[{"type":"APPOINTMENT","appointmentId":101,"startAt":"2026-11-06T05:00:00Z","endAt":"2026-11-06T05:30:00Z","patientId":13,"patientName":"Malika","status":"CONFIRMED"}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                path == "appointments/102" ->
                    respond(
                        """{"status":404,"code":"NOT_FOUND"}""",
                        HttpStatusCode.NotFound,
                        json,
                    )
                else -> respond("{}", HttpStatusCode.OK, json)
            }
        }

    private val client = createHttpClient({ null }, engine)
    private val clock =
        PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-05T09:12:00Z") }, { TimeZone.of("Asia/Tashkent") })

    private fun vm() =
        NotificationsViewModel(
            NotificationsRepository({ client }),
            NotificationOpener(AppointmentsRepository({ client }), CalendarRepository({ client })),
        )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ShellNotice.pending.value = null
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun loaded() = vm().also { it.load().also { _ -> settle { it.state.value.items != null } } }

    @Test
    fun `pages newest first, the next one without repeats, chips filter on the phone`() {
        val classToTest = loaded()
        assertThat(classToTest.state.value.items!!.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
        classToTest.loadMore()
        settle { classToTest.state.value.last }
        assertThat(classToTest.state.value.items!!.map { it.id }).containsExactly(1L, 2L, 3L, 4L, 5L).inOrder()
        classToTest.loadMore() // the last page: nothing more is asked
        assertThat(asked.count { it.startsWith("GET notifications/page") }).isEqualTo(2)
        classToTest.select(NotificationFilter.Messages)
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(4L, 5L)
        classToTest.select(NotificationFilter.Appointments)
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `a booking is read and opens its panel from the calendar day, once per tap`() {
        val classToTest = loaded()
        val booked = classToTest.state.value.items!!.first()
        classToTest.open(booked, staff = false, clock)
        classToTest.open(booked, staff = false, clock) // a second tap while it's looked up does nothing
        settle { classToTest.state.value.go != null }
        val go = classToTest.state.value.go as NotificationGo.Appointment
        assertThat(go.entry.appointmentId).isEqualTo(101L)
        assertThat(asked).containsAtLeast("PUT notifications/1/read", "GET calendar?day=2026-11-06")
        assertThat(asked.count { it == "PUT notifications/1/read" }).isEqualTo(1)
        assertThat(classToTest.state.value.items!!.first().read).isTrue()
        classToTest.opened()
        assertThat(classToTest.state.value.go).isNull()
    }

    @Test
    fun `an appointment that's gone says so and goes nowhere`() {
        val classToTest = loaded()
        classToTest.open(classToTest.state.value.items!![1], staff = false, clock)
        settle { ShellNotice.pending.value != null }
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.appointmentGone)
        assertThat(classToTest.state.value.go).isNull()
        assertThat(classToTest.state.value.opening).isNull()
    }

    @Test
    fun `access approved opens the document, staff only reach chat, a refused read comes back unread`() {
        val classToTest = loaded()
        classToTest.loadMore()
        settle { classToTest.state.value.last }
        val approved = classToTest.state.value.items!!.first { it.id == 4L }
        classToTest.open(approved, staff = false, clock)
        assertThat(classToTest.state.value.go).isEqualTo(NotificationGo.Document(11, 7, "Blood test"))
        classToTest.opened()
        val chat = classToTest.state.value.items!!.first { it.id == 5L }
        classToTest.open(chat, staff = true, clock)
        assertThat(classToTest.state.value.go).isEqualTo(NotificationGo.Chat)
        classToTest.opened()
        // A completed task opens its details (TASK-03); for staff, a booking: read, nothing opens.
        classToTest.open(classToTest.state.value.items!!.first { it.id == 3L }, staff = false, clock)
        assertThat(classToTest.state.value.go).isEqualTo(NotificationGo.Task(1))
        classToTest.opened()
        readStatus = HttpStatusCode.InternalServerError
        classToTest.open(classToTest.state.value.items!!.first(), staff = true, clock)
        settle { ShellNotice.pending.value != null }
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.notificationsMarkFailed)
        assertThat(classToTest.state.value.items!!.first().read).isFalse()
        assertThat(classToTest.state.value.go).isNull()
    }

    @Test
    fun `mark all reads every row, a failed refresh keeps the list under a banner`() {
        val classToTest = loaded()
        classToTest.markAll()
        settle { classToTest.state.value.items!!.all { it.read } }
        assertThat(classToTest.state.value.items!!.all { it.read }).isTrue()
        assertThat(asked).contains("PUT notifications/read-all")
        pageFails = true
        classToTest.refresh()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.error?.requestId).isEqualTo("r-1")
        assertThat(classToTest.state.value.items).hasSize(3)
    }

    @Test
    fun `a refresh drops a page asked for before it, and asks the shell for the count again`() {
        val classToTest = loaded()
        val counts = mutableListOf<Unit>()
        val listening =
            kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launch {
                shifa.doctor.data.notifications.NotificationReads.changed
                    .collect { counts += it }
            }
        holdPageOne = kotlinx.coroutines.CompletableDeferred()
        classToTest.loadMore()
        classToTest.refresh()
        settle { !classToTest.state.value.refreshing }
        holdPageOne!!.complete(Unit)
        Thread.sleep(100)
        assertThat(classToTest.state.value.items!!.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(classToTest.state.value.page).isEqualTo(0)
        assertThat(counts).isNotEmpty()
        listening.cancel()
    }

    @Test
    fun `mark all runs even when the rows read so far are all read`() {
        val classToTest = loaded()
        classToTest.markAll()
        settle { !classToTest.state.value.markingAll }
        classToTest.markAll()
        settle { asked.count { it == "PUT notifications/read-all" } == 2 }
        assertThat(asked.count { it == "PUT notifications/read-all" }).isEqualTo(2)
    }

    @Test
    fun `reads stand when a page asked for earlier answers later`() {
        val classToTest = loaded()
        // Mark all while the next page is out: that page is dropped, nothing comes back unread.
        holdPageOne = kotlinx.coroutines.CompletableDeferred()
        classToTest.loadMore()
        classToTest.markAll()
        settle { !classToTest.state.value.markingAll }
        holdPageOne!!.complete(Unit)
        Thread.sleep(100)
        assertThat(classToTest.state.value.items!!.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(classToTest.state.value.items!!.all { it.read }).isTrue()
        assertThat(classToTest.state.value.loadingMore).isFalse()
        // A row read here stays read when page 0 (still unread on the server's old answer) comes back.
        holdPageOne = null
        val fresh = loaded()
        fresh.open(fresh.state.value.items!![2], staff = false, clock)
        fresh.refresh()
        settle { !fresh.state.value.refreshing }
        assertThat(fresh.state.value.items!!.first { it.id == 3L }.read).isTrue()
    }
}
