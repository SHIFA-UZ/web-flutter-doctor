@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.tasks

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import shifa.doctor.data.model.PatientOption
import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.model.TaskInputType
import shifa.doctor.data.model.TaskSchedule
import shifa.doctor.data.model.TaskTemplate

class TaskFormTest {
    private val today = LocalDate(2026, 11, 5)
    private val ok =
        TaskForm.blank(
            today,
            PatientOption(14, "Bekzod", null),
        ).copy(name = " Blood pressure ", endDate = LocalDate(2026, 11, 30))

    @Test
    fun `even spacing makes the backend's slots, clipped at midnight`() {
        val f = ok.copy(timesPerDay = 15, intervalHours = 2, startTime = LocalTime(8, 0))
        assertThat(
            f.slots.map {
                it.toString()
            },
        ).containsExactly("08:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00", "22:00").inOrder()
        assertThat(f.clipped).isEqualTo(7)
        assertThat(ok.slots).containsExactly(LocalTime(8, 0))
        assertThat(ok.clipped).isEqualTo(0)
    }

    @Test
    fun `the payload follows the spec, with the count that fits`() {
        val even =
            ok.copy(
                timesPerDay = 15,
                intervalHours = 2,
                endDate = LocalDate(2026, 11, 30),
                description = "  ",
                inputLabel = " mmHg ",
            ).request()!!
        assertThat(even.taskName).isEqualTo("Blood pressure")
        assertThat(even.category).isEqualTo("VITAL")
        assertThat(even.inputType).isEqualTo("NUMERIC")
        assertThat(even.timesPerDay).isEqualTo(8)
        assertThat(even.startTime).isEqualTo("08:00")
        assertThat(even.intervalHours).isEqualTo(2)
        assertThat(even.customTimes).isNull()
        assertThat(even.startDate).isEqualTo("2026-11-05")
        assertThat(even.endDate).isEqualTo("2026-11-30")
        assertThat(even.durationDays).isNull()
        assertThat(even.description).isNull()
        assertThat(even.inputLabel).isEqualTo("mmHg")
        // Once a day: no interval; duration instead of the end date.
        val once = ok.copy(useEndDate = false, durationText = "14", endDate = LocalDate(2026, 11, 30)).request()!!
        assertThat(once.intervalHours).isNull()
        assertThat(once.endDate).isNull()
        assertThat(once.durationDays).isEqualTo(14)
        val custom =
            ok.copy(
                mode = ScheduleMode.Custom,
                customTimes = listOf(LocalTime(7, 30), LocalTime(21, 0)),
            ).request()!!
        assertThat(custom.customTimes).containsExactly("07:30", "21:00").inOrder()
        assertThat(custom.startTime).isEqualTo("07:30")
        assertThat(custom.timesPerDay).isEqualTo(2)
        assertThat(custom.intervalHours).isNull()
    }

    @Test
    fun `problems stop the payload, in the form's order`() {
        val blank = TaskForm.blank(today)
        assertThat(
            blank.problems,
        ).containsExactly(TaskFormProblem.NoPatient, TaskFormProblem.NoName, TaskFormProblem.NoEndDate).inOrder()
        // An unpicked end date would let the backend choose 31 days.
        assertThat(ok.copy(endDate = null).problems).containsExactly(TaskFormProblem.NoEndDate)
        assertThat(ok.copy(endDate = null, useEndDate = false, durationText = "14").problems).isEmpty()
        assertThat(blank.request()).isNull()
        assertThat(ok.copy(mode = ScheduleMode.Custom).problems).containsExactly(TaskFormProblem.NoSlots)
        assertThat(ok.copy(endDate = LocalDate(2026, 11, 1)).problems).containsExactly(TaskFormProblem.EndBeforeStart)
        assertThat(
            ok.copy(useEndDate = false, durationText = "0").problems,
        ).containsExactly(TaskFormProblem.BadDuration)
        assertThat(ok.copy(useEndDate = false, durationText = "").problems).containsExactly(TaskFormProblem.BadDuration)
        assertThat(
            ok.copy(useEndDate = false, durationText = "1.5").problems,
        ).containsExactly(TaskFormProblem.BadDuration)
        assertThat(
            ok.copy(useEndDate = false, durationText = "-3").problems,
        ).containsExactly(TaskFormProblem.BadDuration)
        assertThat(ok.problems).isEmpty()
    }

    @Test
    fun `a template fills the form, its odd interval snapped up and said`() {
        val t =
            TaskTemplate(
                "BP",
                "Twice",
                TaskCategory.Vital,
                TaskSchedule.of(3, "07:00", 5, null, emptyList()),
                TaskInputType.Numeric,
                "mmHg",
                true,
                "Feeling?",
            )
        val f = TaskForm.from(t, today)
        assertThat(f.intervalHours).isEqualTo(6)
        assertThat(TaskForm.intervalAdjusted(5)).isTrue()
        assertThat(TaskForm.intervalAdjusted(6)).isFalse()
        assertThat(TaskForm.snapInterval(30)).isEqualTo(24)
        assertThat(f.mode).isEqualTo(ScheduleMode.Even)
        assertThat(f.patient).isNull()
        assertThat(f.notesRequired).isTrue()
        val custom =
            TaskForm.from(
                t.copy(schedule = TaskSchedule.of(2, null, null, listOf("20:00", "08:00"), emptyList())),
                today,
            )
        assertThat(custom.mode).isEqualTo(ScheduleMode.Custom)
        assertThat(custom.customTimes).containsExactly(LocalTime(8, 0), LocalTime(20, 0)).inOrder()
    }

    @Test
    fun `custom slots stay sorted without repeats, 15 at most`() {
        val list = listOf(LocalTime(8, 0))
        assertThat(list.withSlot(LocalTime(7, 0))).containsExactly(LocalTime(7, 0), LocalTime(8, 0)).inOrder()
        assertThat(list.withSlot(LocalTime(8, 0))).isNull()
    }
}
