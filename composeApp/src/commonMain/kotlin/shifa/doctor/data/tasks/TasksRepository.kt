package shifa.doctor.data.tasks

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.api.usable
import shifa.doctor.data.model.CheckInDto
import shifa.doctor.data.model.CreateTaskRequest
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.model.TaskCheckIn
import shifa.doctor.data.model.TaskDto
import shifa.doctor.data.model.TaskStatus
import shifa.doctor.data.model.TaskTemplate
import shifa.doctor.data.model.TaskTemplateDto

/**
 * Remote-care tasks (TASK-01, 07 §3, 15 §2.10): PREMIUM only (`DoctorFeature.RemoteCareTasks`; the shell keeps others
 * out, a 403 still comes back as `ApiException.Forbidden`). Lists are read row by row; a bad row is skipped.
 */
class TasksRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** `GET /tasks[?patientId=&status=]`. */
    suspend fun tasks(
        patientId: Long? = null,
        status: TaskStatus? = null,
    ): Result<List<RemoteCareTask>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.TASKS) {
                patientId?.let { parameter("patientId", it) }
                status?.let { parameter("status", it.name.uppercase()) }
            }
        }.map { it.rows(TaskDto.serializer(), "tasks", RemoteCareTask::from) }

    /** `GET /tasks/{id}`; one that can't be used is a typed failure, not null. */
    suspend fun task(id: Long): Result<RemoteCareTask> =
        apiCall<TaskDto> { client().get(ApiConfig.Endpoints.task(id)) }
            .usable("task", RemoteCareTask::from)

    /** `GET /tasks/{id}/check-ins`. */
    suspend fun checkIns(id: Long): Result<List<TaskCheckIn>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.taskCheckIns(id)) }
            .map { it.rows(CheckInDto.serializer(), "check-ins", TaskCheckIn::from) }

    /** `GET /tasks/templates` (the doctor's earlier tasks' settings). */
    suspend fun templates(): Result<List<TaskTemplate>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.TASK_TEMPLATES) }
            .map { it.rows(TaskTemplateDto.serializer(), "templates", TaskTemplate::from) }

    /** `POST /tasks`: the task as created. */
    suspend fun create(request: CreateTaskRequest): Result<RemoteCareTask> =
        apiCall<TaskDto> {
            client().post(ApiConfig.Endpoints.TASKS) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.usable("created task", RemoteCareTask::from)

    /** `PATCH /tasks/{id}/cancel` (no body; Flutter sent `{}`). */
    suspend fun cancel(id: Long): Result<RemoteCareTask> =
        apiCall<TaskDto> { client().patch(ApiConfig.Endpoints.taskCancel(id)) }
            .usable("cancelled task", RemoteCareTask::from)
}
