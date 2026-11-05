@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.tasks

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CreateTaskRequest
import shifa.doctor.data.model.TaskStatus

class TasksRepositoryTest {
    private val asked = mutableListOf<String>()
    private var forbidden = false
    private val task = """{"id":7,"patientId":14,"patientName":"B","taskName":"T","category":"VITAL","status":"ACTIVE","startDate":"2026-11-01","inputType":"NUMERIC","timesPerDay":1}"""
    private val classToTest =
        TasksRepository({
            createHttpClient(
                { null },
                MockEngine { req ->
                    val body = (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
                    asked += req.method.value + " " +
                        req.url.encodedPath.substringAfter(
                            "/api/",
                        ) + (
                            req.url.encodedQuery.takeIf {
                                it.isNotEmpty()
                            }?.let { "?$it" } ?: ""
                        ) + (body?.let { " $it" } ?: "")
                    val path = req.url.encodedPath.substringAfter("/api/")
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    when {
                        path == "tasks/99" -> respond("{}", HttpStatusCode.OK, json)
                        forbidden ->
                            respond(
                                """{"status":403,"code":"FEATURE_NOT_AVAILABLE"}""",
                                HttpStatusCode.Forbidden,
                                json,
                            )
                        path == "tasks" && req.method.value == "GET" ->
                            respond(
                                """[$task,{"id":8},{"id":9,"patientId":1,"startDate":"2026-11-02"}]""",
                                HttpStatusCode.OK,
                                json,
                            )
                        path.endsWith(
                            "check-ins",
                        ) ->
                            respond(
                                """[{"id":1,"scheduledDate":"2026-11-05","scheduledTime":"08:00","status":"COMPLETED","numericValue":120},{"id":2}]""",
                                HttpStatusCode.OK,
                                json,
                            )
                        path == "tasks/templates" ->
                            respond(
                                """[{"taskName":"BP","category":"VITAL","timesPerDay":2},{"taskName":""}]""",
                                HttpStatusCode.OK,
                                json,
                            )
                        else -> respond(task, HttpStatusCode.OK, json)
                    }
                },
            )
        })

    @Test
    fun `lists filter on the server, bad rows are skipped`() =
        runTest {
            assertThat(
                classToTest.tasks(patientId = 14, status = TaskStatus.Active).getOrThrow().map {
                    it.id
                },
            ).containsExactly(7L, 9L)
            assertThat(classToTest.checkIns(7).getOrThrow().map { it.id }).containsExactly(1L)
            assertThat(classToTest.templates().getOrThrow().map { it.name }).containsExactly("BP")
            assertThat(
                asked,
            ).containsExactly(
                "GET tasks?patientId=14&status=ACTIVE",
                "GET tasks/7/check-ins",
                "GET tasks/templates",
            ).inOrder()
        }

    @Test
    fun `create sends the payload, cancel patches without a body, a 403 is typed`() =
        runTest {
            classToTest.create(
                CreateTaskRequest(14, "T", "VITAL", 1, "2026-11-05", "NUMERIC", false, startTime = "08:00"),
            ).getOrThrow()
            classToTest.cancel(7).getOrThrow()
            assertThat(asked.first()).isEqualTo(
                """POST tasks {"patientId":14,"taskName":"T","category":"VITAL","timesPerDay":1,"startDate":"2026-11-05","inputType":"NUMERIC","notesRequired":false,"startTime":"08:00"}""",
            )
            assertThat(asked.last()).isEqualTo("PATCH tasks/7/cancel")
            // A 200 that can't be used is a typed server error, not an exception from nowhere.
            assertThat(classToTest.task(99).exceptionOrNull()).isInstanceOf(ApiException.ServerError::class.java)
            forbidden = true
            assertThat(classToTest.tasks().exceptionOrNull()).isInstanceOf(ApiException.Forbidden::class.java)
        }
}
