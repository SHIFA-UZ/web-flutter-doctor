@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.home

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.notifications.NotificationsRepository
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.ui.notifications.NotificationGo
import shifa.doctor.ui.notifications.NotificationOpener
import shifa.doctor.utils.PracticeClock

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAttentionViewModelTest {
    private val asked = mutableListOf<String>()
    private var pageFails = false
    private var readFails = false
    private var allReadFirstPage = false

    private fun row(
        id: Long,
        read: Boolean = false,
    ) =
        """{"id":$id,"type":"CHAT_MESSAGE","title":"T","message":"M","createdAt":"2026-11-05T0${id % 9}:00:00Z","readAt":${if (read) "\"2026-11-05T09:00:00Z\"" else "null"}}"""

    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath.substringAfter("/api/")
                asked += req.method.value + " " + path + (req.url.parameters["status"]?.let { "?status=$it" } ?: "")
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    path == "notifications/page" && pageFails -> respond("{}", HttpStatusCode.InternalServerError, json)
                    // Page 0 all read, the unread ones on page 1; a row without ids for the inbox fallback.
                    path == "notifications/page" && allReadFirstPage && req.url.parameters["page"] == "0" ->
                        respond(
                            """{"content":[${(1L..20L).joinToString(",") { row(it, read = true) }}],"last":false}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    path == "notifications/page" && allReadFirstPage ->
                        respond(
                            """{"content":[{"id":30,"type":"GENERAL","title":"T","message":"M","createdAt":"2026-11-01T00:00:00Z","readAt":null}],"last":true}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    // 6 unread and 4 read: 10 rows, newest first.
                    path == "notifications/page" ->
                        respond(
                            """{"content":[${(1L..10L).joinToString(",") { row(it, read = it > 6) }}],"last":true}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    path.endsWith(
                        "/read",
                    ) -> respond("", if (readFails) HttpStatusCode.InternalServerError else HttpStatusCode.OK, json)
                    path == "tasks" ->
                        respond(
                            (1..5).joinToString(",", "[", "]") {
                                """{"id":$it,"patientId":14,"taskName":"T$it","status":"ACTIVE","startDate":"2026-11-01"}"""
                            },
                            HttpStatusCode.OK,
                            json,
                        )
                    else -> respond("{}", HttpStatusCode.OK, json)
                }
            },
        )

    private val clock =
        PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-05T09:12:00Z") }, { TimeZone.of("Asia/Tashkent") })

    private fun vm() =
        HomeAttentionViewModel(
            NotificationsRepository({
                client
            }),
            TasksRepository({ client }),
            NotificationOpener(),
        )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `4 unread then the tasks, 6 in all, and the 8 newest for the feed`() {
        val classToTest = vm()
        classToTest.refresh(withTasks = true)
        settle { classToTest.state.value.tasks != null && classToTest.state.value.notifications != null }
        val items = classToTest.state.value.attention
        assertThat(items).hasSize(6)
        assertThat(items.take(4).all { it is AttentionItem.Unread }).isTrue()
        assertThat(items.drop(4).all { it is AttentionItem.Task }).isTrue()
        assertThat(
            classToTest.state.value.activity.map { it.id },
        ).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L).inOrder()
        assertThat(asked).contains("GET tasks?status=ACTIVE")
    }

    @Test
    fun `without the plan no tasks are asked, a failed first read says so`() {
        pageFails = true
        val classToTest = vm()
        classToTest.refresh(withTasks = false)
        settle { classToTest.state.value.failed }
        assertThat(classToTest.state.value.failed).isTrue()
        assertThat(asked.none { it.startsWith("GET tasks") }).isTrue()
    }

    @Test
    fun `mark as read leaves at once and comes back if refused, the feed opens without reading`() {
        val classToTest = vm()
        classToTest.refresh(withTasks = false)
        settle { classToTest.state.value.notifications != null }
        val first = classToTest.state.value.notifications!!.first()
        readFails = true
        classToTest.markRead(first)
        settle {
            asked.contains(
                "PUT notifications/1/read",
            ) && classToTest.state.value.notifications!!.first().read.not()
        }
        assertThat(classToTest.state.value.notifications!!.first().read).isFalse()
        classToTest.open(first, staff = false, clock, read = false)
        settle { classToTest.state.value.go != null }
        assertThat(classToTest.state.value.go).isEqualTo(NotificationGo.Chat)
        assertThat(asked.count { it == "PUT notifications/1/read" }).isEqualTo(1)
    }

    @Test
    fun `older unread come from the next pages, a row with nothing to open goes to the inbox`() {
        allReadFirstPage = true
        val classToTest = vm()
        classToTest.refresh(withTasks = false)
        settle { classToTest.state.value.notifications != null }
        val unread = classToTest.state.value.attention.filterIsInstance<AttentionItem.Unread>().map { it.notification.id }
        assertThat(unread).containsExactly(30L)
        classToTest.open(classToTest.state.value.notifications!!.first { it.id == 30L }, staff = false, clock)
        settle { classToTest.state.value.go != null }
        assertThat(classToTest.state.value.go).isEqualTo(NotificationGo.Inbox)
    }

    @Test
    fun `one read a minute unless forced, and a read row stays read in a later answer`() {
        val classToTest = vm()
        classToTest.refresh(withTasks = false)
        settle { classToTest.state.value.notifications != null }
        classToTest.refresh(withTasks = false)
        Thread.sleep(100)
        assertThat(asked.count { it == "GET notifications/page" }).isEqualTo(1)
        // The plan arriving reads at once (tasks), and a pull forces it.
        classToTest.refresh(withTasks = true)
        settle { classToTest.state.value.tasks != null }
        assertThat(asked.count { it == "GET tasks?status=ACTIVE" }).isEqualTo(1)
        classToTest.markRead(classToTest.state.value.notifications!!.first())
        classToTest.refresh(withTasks = true, force = true)
        settle { asked.count { it == "GET notifications/page" } == 3 }
        Thread.sleep(100)
        assertThat(classToTest.state.value.notifications!!.first().read).isTrue()
    }

    @Test
    fun `the plan arriving during a read still reads the tasks, a downgrade drops them`() {
        val classToTest = vm()
        classToTest.refresh(withTasks = false)
        classToTest.refresh(withTasks = true)
        settle { classToTest.state.value.tasks != null }
        assertThat(classToTest.state.value.tasks).hasSize(5)
        classToTest.refresh(withTasks = false)
        settle { classToTest.state.value.tasks == null }
        assertThat(classToTest.state.value.tasks).isNull()
    }
}
