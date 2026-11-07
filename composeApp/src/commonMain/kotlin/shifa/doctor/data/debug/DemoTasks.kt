@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.debug

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import shifa.doctor.data.api.ApiConfig.Endpoints
import shifa.doctor.data.api.apiJson
import shifa.doctor.utils.currentInstant

/**
 * The demo remote-care tasks (TASK-01), in memory: three tasks (blood pressure three times a day with done, missed and
 * coming check-ins; an evening pill; a finished walk), two templates, create and cancel, answered as the backend does
 * (upper-case enums, "HH:mm", Tashkent patients).
 */
internal class DemoTasks(
    private val today: () -> LocalDate = { currentInstant().toLocalDateTime(TimeZone.of("Asia/Tashkent")).date },
) {
    private val tasks: MutableList<JsonObject> = seed()
    private var lastId = 600L

    /** The answer to a tasks request, or null when [path] isn't one. */
    fun handle(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String,
    ): JsonElement? {
        val id = Regex("tasks/(\\d+)(/.*)?").matchEntire(path)?.groupValues?.get(1)?.toLongOrNull()
        return when {
            method == "GET" && path == Endpoints.TASKS ->
                JsonArray(
                    tasks.filter { t ->
                        (query["patientId"]?.let { t.long("patientId") == it.toLongOrNull() } ?: true) &&
                            (query["status"]?.let { t.str("status") == it.uppercase() } ?: true)
                    },
                )
            method == "GET" && path == Endpoints.TASK_TEMPLATES ->
                JsonArray(
                    TEMPLATES.map { apiJson.parseToJsonElement(it) },
                )
            method == "POST" && path == Endpoints.TASKS -> create(body)
            id == null -> null
            method == "GET" && path == Endpoints.task(id) -> find(id)
            method == "GET" && path == Endpoints.taskCheckIns(id) -> find(id)?.let { checkIns(id) }
            method == "PATCH" && path == Endpoints.taskCancel(id) ->
                find(id)?.let { t ->
                    val cancelled = JsonObject(t + ("status" to JsonPrimitive("CANCELLED")))
                    tasks[tasks.indexOf(t)] = cancelled
                    cancelled
                }
            else -> null
        }
    }

    private fun find(id: Long) = tasks.firstOrNull { it.long("id") == id }

    private fun create(body: String): JsonObject {
        val req = apiJson.parseToJsonElement(body).jsonObject
        val patient = req["patientId"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
        val task =
            JsonObject(
                req +
                    mapOf(
                        "id" to JsonPrimitive(++lastId),
                        "status" to JsonPrimitive("ACTIVE"),
                        "patientName" to JsonPrimitive(DemoChat.DEMO_NAMES[patient] ?: "#$patient"),
                        "patientTimeZone" to JsonPrimitive("Asia/Tashkent"),
                        "createdAt" to JsonPrimitive(currentInstant().toString()),
                        "progress" to progress(0, 0, 0, 0),
                    ),
            )
        tasks.add(0, task)
        return task
    }

    /** Task 501: yesterday's three readings (one missed), today's 08:00 done, 13:00 and 20:00 still pending. */
    private fun checkIns(id: Long): JsonArray {
        if (id != 501L) return JsonArray(emptyList())
        val d = today()
        val y = d.minus(DatePeriod(days = 1))

        fun c(
            n: Long,
            day: LocalDate,
            time: String,
            status: String,
            value: Double? = null,
            notes: String? = null,
        ) = buildJsonObject {
            put("id", n)
            put("scheduledDate", day.toString())
            put("scheduledTime", time)
            put("status", status)
            put("numericValue", value?.let(::JsonPrimitive) ?: JsonNull)
            put("notes", notes?.let(::JsonPrimitive) ?: JsonNull)
            put("completedAt", if (value != null) JsonPrimitive("${day}T$time:00+05:00") else JsonNull)
        }
        return JsonArray(
            listOf(
                c(1, y, "08:00", "COMPLETED", 128.0),
                c(2, y, "13:00", "MISSED"),
                c(3, y, "20:00", "COMPLETED", 135.0, "Felt dizzy after dinner"),
                c(4, d, "08:00", "COMPLETED", 124.0),
                c(5, d, "13:00", "PENDING"),
                c(6, d, "20:00", "PENDING"),
            ),
        )
    }

    private fun seed(): MutableList<JsonObject> {
        val d = today()
        return mutableListOf(
            task(
                501,
                14,
                "Blood pressure diary",
                "VITAL",
                "ACTIVE",
                d.minus(DatePeriod(days = 1)),
                "\"timesPerDay\":3,\"customTimes\":[\"08:00\",\"13:00\",\"20:00\"],\"startTime\":\"08:00\",\"endDate\":\"${d.plus(
                    DatePeriod(days = 12),
                )}\"," +
                    "\"inputType\":\"NUMERIC\",\"inputLabel\":\"Systolic (mmHg)\",\"notesRequired\":true,\"notesLabel\":\"How do you feel?\"",
                progress(6, 3, 2, 1),
            ),
            task(
                502,
                11,
                "Evening metformin",
                "MEDICATION",
                "ACTIVE",
                d.minus(DatePeriod(days = 5)),
                "\"timesPerDay\":1,\"eveningTime\":\"20:00\",\"durationDays\":30,\"inputType\":\"BOOLEAN\"",
                progress(5, 4, 0, 1),
            ),
            task(
                503,
                12,
                "Daily 20-minute walk",
                "EXERCISE",
                "COMPLETED",
                d.minus(DatePeriod(days = 20)),
                "\"timesPerDay\":1,\"startTime\":\"18:00\",\"endDate\":\"${d.minus(
                    DatePeriod(days = 6),
                )}\",\"inputType\":\"TEXT\",\"inputLabel\":\"Minutes walked\"",
                progress(14, 14, 0, 0),
            ),
        )
    }

    private fun task(
        id: Long,
        patient: Long,
        name: String,
        category: String,
        status: String,
        start: LocalDate,
        schedule: String,
        progress: JsonObject,
    ): JsonObject =
        JsonObject(
            apiJson
                .parseToJsonElement(
                    "{\"id\":$id,\"patientId\":$patient,\"taskName\":\"$name\",\"category\":\"$category\",\"status\":\"$status\"," +
                        "\"startDate\":\"$start\",$schedule,\"patientTimeZone\":\"Asia/Tashkent\",\"doctorName\":\"Dr. med. Aziza Karimova\"," +
                        "\"createdAt\":\"${start}T04:00:00Z\"}",
                ).jsonObject +
                mapOf(
                    "patientName" to JsonPrimitive(DemoChat.DEMO_NAMES.getValue(patient)),
                    "progress" to progress,
                ),
        )

    private fun progress(
        total: Int,
        completed: Int,
        pending: Int,
        missed: Int,
    ) = buildJsonObject {
        put("totalCheckIns", total)
        put("completedCheckIns", completed)
        put("pendingCheckIns", pending)
        put("missedCheckIns", missed)
    }

    private fun JsonObject.long(key: String) = this[key]?.jsonPrimitive?.content?.toLongOrNull()

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

    private companion object {
        val TEMPLATES =
            listOf(
                """{"taskName":"Blood pressure diary","category":"VITAL","timesPerDay":3,"customTimes":["08:00","13:00","20:00"],"inputType":"NUMERIC","inputLabel":"Systolic (mmHg)","notesRequired":true,"notesLabel":"How do you feel?"}""",
                """{"taskName":"Blood sugar before breakfast","category":"VITAL","timesPerDay":2,"startTime":"07:00","intervalHours":12,"inputType":"NUMERIC","inputLabel":"mmol/L","notesRequired":false}""",
            )
    }
}
