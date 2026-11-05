package shifa.doctor.data.tasks

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import shifa.doctor.data.model.CreateTaskRequest
import shifa.doctor.data.model.MAX_TIMES_PER_DAY
import shifa.doctor.data.model.PatientOption
import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.model.TaskInputType
import shifa.doctor.data.model.TaskTemplate
import shifa.doctor.data.schedule.hhmm

enum class ScheduleMode { Even, Custom }

/** The interval choices (07 §3.3); a template's other value is snapped up to the next one. */
val TASK_INTERVALS = listOf(1, 2, 3, 4, 6, 8, 12, 24)

/** End dates and the start date picker reach a year ahead (07 §3.3). */
const val TASK_DAYS_AHEAD = 365

/** The create form (TASK-04, 07 §3.3, flow 07/13), as typed. */
data class TaskForm(
    val patient: PatientOption? = null,
    val name: String = "",
    val description: String = "",
    val category: TaskCategory = TaskCategory.Vital,
    val inputType: TaskInputType = TaskInputType.Numeric,
    val inputLabel: String = "",
    val mode: ScheduleMode = ScheduleMode.Even,
    val timesPerDay: Int = 1,
    val startTime: LocalTime = LocalTime(8, 0),
    val intervalHours: Int = 1,
    // Custom mode's slots, sorted and without repeats; kept while the doctor tries Even spacing.
    val customTimes: List<LocalTime> = emptyList(),
    val startDate: LocalDate,
    val useEndDate: Boolean = true,
    val endDate: LocalDate? = null,
    val durationText: String = "",
    val notesRequired: Boolean = false,
    val notesLabel: String = "",
) {
    /**
     * The day's slots as the backend will make them (`generateCheckIns`): custom ones; else the start, alone once a day
     * or every [intervalHours] up to [timesPerDay], before midnight.
     */
    val slots: List<LocalTime>
        get() =
            if (mode == ScheduleMode.Custom) {
                customTimes
            } else {
                (0 until timesPerDay)
                    .map { startTime.hour * MINUTES + startTime.minute + it * intervalHours * MINUTES }
                    .takeWhile { it < DAY_MINUTES }
                    .map { LocalTime(it / MINUTES, it % MINUTES) }
            }

    /** Even spacing's slots that don't fit before midnight (the preview's warning). */
    val clipped: Int get() = if (mode == ScheduleMode.Even) timesPerDay - slots.size else 0

    // Whole days as typed ("1.5", "-3" stay invalid; nothing is rewritten into another number).
    val duration: Int? get() = durationText.trim().toIntOrNull()

    /** What stops Create (flow §9), in the form's order. */
    val problems: List<TaskFormProblem>
        get() =
            buildList {
                if (patient?.id == null) add(TaskFormProblem.NoPatient)
                if (name.isBlank()) add(TaskFormProblem.NoName)
                if (mode == ScheduleMode.Custom && customTimes.isEmpty()) add(TaskFormProblem.NoSlots)
                // No end date would let the backend pick 31 days on its own: the doctor picks one.
                if (useEndDate && endDate == null) add(TaskFormProblem.NoEndDate)
                if (useEndDate && endDate != null && endDate < startDate) add(TaskFormProblem.EndBeforeStart)
                if (!useEndDate && (duration ?: 0) < 1) add(TaskFormProblem.BadDuration)
            }

    /**
     * `POST /tasks` (07 §3.3 payload rules): upper-case codes, "HH:mm", "yyyy-MM-dd"; custom mode sends its slots and
     * the first as the start; even mode sends the interval only with two or more a day, and the count that fits
     * (flow §8: Flutter sent 15 when 8 fit). The end date or the duration, never both; null when [problems] remain.
     */
    fun request(): CreateTaskRequest? {
        if (problems.isNotEmpty()) return null
        val custom = mode == ScheduleMode.Custom
        val count = slots.size.coerceIn(1, MAX_TIMES_PER_DAY)
        return CreateTaskRequest(
            patientId = patient?.id ?: return null,
            taskName = name.trim(),
            category = category.name.uppercase(),
            timesPerDay = count,
            startDate = startDate.toString(),
            inputType = inputType.name.uppercase(),
            notesRequired = notesRequired,
            description = description.trim().ifEmpty { null },
            startTime = hhmm(if (custom) customTimes.first() else startTime),
            intervalHours = intervalHours.takeIf { !custom && count >= 2 },
            customTimes = customTimes.map(::hhmm).takeIf { custom },
            endDate = if (useEndDate) endDate?.toString() else null,
            durationDays = if (useEndDate) null else duration,
            inputLabel = inputLabel.trim().ifEmpty { null },
            notesLabel = notesLabel.trim().ifEmpty { null },
        )
    }

    companion object {
        /** A blank form starting [today] (the practice day). */
        fun blank(
            today: LocalDate,
            patient: PatientOption? = null,
        ) = TaskForm(patient = patient, startDate = today)

        /**
         * A template's settings (07 §3.3 "Template prefill"): times per day 1–15, the interval snapped up to the next
         * choice ([intervalAdjusted] says so), custom times → custom mode; the patient and dates stay the doctor's.
         */
        fun from(
            t: TaskTemplate,
            today: LocalDate,
            patient: PatientOption? = null,
        ): TaskForm {
            val s = t.schedule
            val custom = s.customTimes.isNotEmpty()
            return TaskForm(
                patient = patient,
                name = t.name,
                description = t.description.orEmpty(),
                category = t.category,
                inputType = t.inputType,
                inputLabel = t.inputLabel.orEmpty(),
                mode = if (custom) ScheduleMode.Custom else ScheduleMode.Even,
                timesPerDay = s.timesPerDay,
                startTime = s.startTime ?: s.legacyTimes.firstOrNull() ?: LocalTime(8, 0),
                intervalHours = snapInterval(s.intervalHours),
                customTimes = s.customTimes,
                startDate = today,
                notesRequired = t.notesRequired,
                notesLabel = t.notesLabel.orEmpty(),
            )
        }

        /** The template's interval when it isn't a choice: the next one up (5 → 6), 24 at most; null → 1. */
        fun snapInterval(hours: Int?): Int =
            hours?.let { h -> TASK_INTERVALS.firstOrNull { it >= h } ?: TASK_INTERVALS.last() } ?: 1

        /** Whether [snapInterval] changed the template's interval (flow §6: say so). */
        fun intervalAdjusted(hours: Int?): Boolean = hours != null && snapInterval(hours) != hours

        /** End date default when the doctor first opens it: start + 30 days (07 §3.3). */
        fun defaultEnd(start: LocalDate): LocalDate = start.plus(DatePeriod(days = DEFAULT_DAYS))
    }
}

enum class TaskFormProblem { NoPatient, NoName, NoSlots, NoEndDate, EndBeforeStart, BadDuration }

/** Custom mode's list with [time] added: sorted, no repeats, at most 15; null when it's already there (flow §8). */
fun List<LocalTime>.withSlot(time: LocalTime): List<LocalTime>? =
    if (time in this) null else (this + time).sorted().take(MAX_TIMES_PER_DAY)

private const val MINUTES = 60
private const val DAY_MINUTES = 24 * 60
private const val DEFAULT_DAYS = 30
