package shifa.doctor.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import shifa.doctor.data.schedule.time
import shifa.doctor.utils.PracticeClock

/** `TaskDto` (dev `/v3/api-docs`, 2026-11-05): enums upper case; the legacy morning/afternoon/evening times still occur. */
@Serializable
data class TaskDto(
    val id: Long? = null,
    val patientId: Long? = null,
    val patientName: String? = null,
    val patientTimeZone: String? = null,
    val taskName: String? = null,
    val description: String? = null,
    val category: String? = null,
    val status: String? = null,
    val timesPerDay: Int? = null,
    val startTime: String? = null,
    val intervalHours: Int? = null,
    val customTimes: List<String?>? = null,
    val morningTime: String? = null,
    val afternoonTime: String? = null,
    val eveningTime: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val durationDays: Int? = null,
    val inputType: String? = null,
    val inputLabel: String? = null,
    val notesRequired: Boolean = false,
    val notesLabel: String? = null,
    val createdAt: String? = null,
    val progress: TaskProgressDto? = null,
    val doctorName: String? = null,
    val appointmentId: Long? = null,
)

@Serializable
data class TaskProgressDto(
    val totalCheckIns: Int = 0,
    val completedCheckIns: Int = 0,
    val pendingCheckIns: Int = 0,
    val missedCheckIns: Int = 0,
)

@Serializable
data class CheckInDto(
    val id: Long? = null,
    val scheduledDate: String? = null,
    val scheduledTime: String? = null,
    val status: String? = null,
    val numericValue: Double? = null,
    val textValue: String? = null,
    val booleanValue: Boolean? = null,
    val notes: String? = null,
    val completedAt: String? = null,
)

/** `TemplateDto`: a task's settings without patient or dates (the doctor's earlier tasks). */
@Serializable
data class TaskTemplateDto(
    val taskName: String? = null,
    val description: String? = null,
    val category: String? = null,
    val timesPerDay: Int? = null,
    val startTime: String? = null,
    val intervalHours: Int? = null,
    val customTimes: List<String?>? = null,
    val morningTime: String? = null,
    val afternoonTime: String? = null,
    val eveningTime: String? = null,
    val inputType: String? = null,
    val inputLabel: String? = null,
    val notesRequired: Boolean = false,
    val notesLabel: String? = null,
)

/** `POST /tasks` (07 §3.3 payload rules): enums upper case, times "HH:mm", dates "yyyy-MM-dd". */
@Serializable
data class CreateTaskRequest(
    val patientId: Long,
    val taskName: String,
    val category: String,
    val timesPerDay: Int,
    val startDate: String,
    val inputType: String,
    val notesRequired: Boolean,
    val description: String? = null,
    val startTime: String? = null,
    val intervalHours: Int? = null,
    val customTimes: List<String>? = null,
    val endDate: String? = null,
    val durationDays: Int? = null,
    val inputLabel: String? = null,
    val notesLabel: String? = null,
    val appointmentId: Long? = null,
)

enum class TaskCategory { Vital, Exercise, Medication, Other }

enum class TaskStatus { Draft, Active, Completed, Expired, Cancelled }

enum class TaskInputType { Numeric, Text, Boolean }

enum class CheckInStatus { Pending, Completed, Missed }

/** The enum whose name matches [raw] ignoring case, else [fallback] (07 §3.5: unknown category → other, …). */
inline fun <reified E : Enum<E>> enumOf(
    raw: String?,
    fallback: E,
): E = enumValues<E>().firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: fallback

/** "08:00" or "08:00:00" → 08:00; anything else null (Flutter's `int.parse` threw, 07 §3.5). */
fun parseTaskTime(raw: String?): LocalTime? = raw?.trim()?.let(::time)

fun parseTaskDate(raw: String?): LocalDate? =
    raw?.trim()?.take(DATE_LENGTH)?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

private const val DATE_LENGTH = 10

data class TaskProgress(
    val total: Int,
    val completed: Int,
    val pending: Int,
    val missed: Int,
) {
    /** 0–1; 0 without check-ins. */
    val fraction: Float get() = if (total > 0) completed.toFloat() / total else 0f
}

/** The schedule a task or template carries, read as the backend sends it. */
data class TaskSchedule(
    val timesPerDay: Int,
    val startTime: LocalTime?,
    val intervalHours: Int?,
    val customTimes: List<LocalTime>,
    // Older tasks name up to three fixed times instead.
    val legacyTimes: List<LocalTime>,
) {
    /**
     * The day's times, in the backend's order (`generateCheckIns`): custom times; else the start, alone once a day or
     * every [intervalHours] (1 when unset) up to the count and before midnight; else the legacy morning / afternoon /
     * evening times.
     */
    val times: List<LocalTime>
        get() {
            if (customTimes.isNotEmpty()) return customTimes
            val start = startTime ?: return legacyTimes
            if (timesPerDay <= 1) return listOf(start)
            val step = (intervalHours ?: 1).coerceIn(1, HOURS_PER_DAY) * MINUTES
            return (0 until timesPerDay)
                .map { start.hour * MINUTES + start.minute + it * step }
                .takeWhile { it < DAY_MINUTES }
                .map { LocalTime(it / MINUTES, it % MINUTES) }
        }

    companion object {
        fun of(
            timesPerDay: Int?,
            startTime: String?,
            intervalHours: Int?,
            customTimes: List<String?>?,
            legacy: List<String?>,
        ) = TaskSchedule(
            timesPerDay = (timesPerDay ?: 1).coerceIn(1, MAX_TIMES_PER_DAY),
            startTime = parseTaskTime(startTime),
            intervalHours = intervalHours,
            customTimes = customTimes.orEmpty().mapNotNull(::parseTaskTime).distinct().sorted(),
            legacyTimes = legacy.mapNotNull(::parseTaskTime),
        )
    }
}

const val MAX_TIMES_PER_DAY = 15
private const val MINUTES = 60
private const val HOURS_PER_DAY = 24
private const val DAY_MINUTES = HOURS_PER_DAY * 60

data class RemoteCareTask(
    val id: Long,
    val patientId: Long,
    val patientName: String,
    // The patient's IANA zone: check-in times are theirs (07 §3.2); null → the practice zone.
    val patientTimeZone: String?,
    val name: String,
    val description: String?,
    val category: TaskCategory,
    val status: TaskStatus,
    val schedule: TaskSchedule,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val durationDays: Int?,
    val inputType: TaskInputType,
    val inputLabel: String?,
    val notesRequired: Boolean,
    val notesLabel: String?,
    val createdAt: Instant?,
    val progress: TaskProgress?,
    val doctorName: String?,
    val appointmentId: Long?,
) {
    companion object {
        /** Null without an id, a patient or a start date (the row is skipped, not the list). */
        fun from(d: TaskDto): RemoteCareTask? =
            RemoteCareTask(
                id = d.id ?: return null,
                patientId = d.patientId ?: return null,
                patientName = d.patientName?.trim().orEmpty(),
                patientTimeZone = d.patientTimeZone?.trim()?.takeIf { it.isNotEmpty() },
                name = d.taskName?.trim().orEmpty(),
                description = d.description?.trim()?.takeIf { it.isNotEmpty() },
                category = enumOf(d.category, TaskCategory.Other),
                status = enumOf(d.status, TaskStatus.Active),
                schedule =
                    TaskSchedule.of(
                        d.timesPerDay,
                        d.startTime,
                        d.intervalHours,
                        d.customTimes,
                        listOf(d.morningTime, d.afternoonTime, d.eveningTime),
                    ),
                startDate = parseTaskDate(d.startDate) ?: return null,
                endDate = parseTaskDate(d.endDate),
                durationDays = d.durationDays?.takeIf { it > 0 },
                inputType = enumOf(d.inputType, TaskInputType.Text),
                inputLabel = d.inputLabel?.trim()?.takeIf { it.isNotEmpty() },
                notesRequired = d.notesRequired,
                notesLabel = d.notesLabel?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = PracticeClock.parseInstant(d.createdAt),
                progress =
                    d.progress?.let {
                        TaskProgress(it.totalCheckIns, it.completedCheckIns, it.pendingCheckIns, it.missedCheckIns)
                    },
                doctorName = d.doctorName?.trim()?.takeIf { it.isNotEmpty() },
                appointmentId = d.appointmentId,
            )
    }
}

data class TaskTemplate(
    val name: String,
    val description: String?,
    val category: TaskCategory,
    val schedule: TaskSchedule,
    val inputType: TaskInputType,
    val inputLabel: String?,
    val notesRequired: Boolean,
    val notesLabel: String?,
) {
    companion object {
        fun from(d: TaskTemplateDto): TaskTemplate? =
            TaskTemplate(
                name = d.taskName?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
                description = d.description?.trim()?.takeIf { it.isNotEmpty() },
                category = enumOf(d.category, TaskCategory.Other),
                schedule =
                    TaskSchedule.of(
                        d.timesPerDay,
                        d.startTime,
                        d.intervalHours,
                        d.customTimes,
                        listOf(d.morningTime, d.afternoonTime, d.eveningTime),
                    ),
                inputType = enumOf(d.inputType, TaskInputType.Text),
                inputLabel = d.inputLabel?.trim()?.takeIf { it.isNotEmpty() },
                notesRequired = d.notesRequired,
                notesLabel = d.notesLabel?.trim()?.takeIf { it.isNotEmpty() },
            )
    }
}

data class TaskCheckIn(
    val id: Long,
    val date: LocalDate,
    val time: LocalTime?,
    val status: CheckInStatus,
    val numericValue: Double?,
    val textValue: String?,
    val booleanValue: Boolean?,
    val notes: String?,
    val completedAt: Instant?,
) {
    companion object {
        fun from(d: CheckInDto): TaskCheckIn? =
            TaskCheckIn(
                id = d.id ?: return null,
                date = parseTaskDate(d.scheduledDate) ?: return null,
                time = parseTaskTime(d.scheduledTime),
                status = enumOf(d.status, CheckInStatus.Pending),
                numericValue = d.numericValue,
                textValue = d.textValue?.takeIf { it.isNotBlank() },
                booleanValue = d.booleanValue,
                notes = d.notes?.trim()?.takeIf { it.isNotEmpty() },
                completedAt = PracticeClock.parseInstant(d.completedAt),
            )
    }
}
