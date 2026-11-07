@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.tasks

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Test
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.model.CheckInStatus
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.model.TaskCheckIn
import shifa.doctor.data.model.TaskDto
import shifa.doctor.data.model.TaskInputType
import shifa.doctor.data.model.TaskSchedule
import shifa.doctor.data.model.TaskStatus

class TaskModelsTest {
    // A dev row (2026-11-05), names replaced.
    private val devRow =
        """{"id":3,"patientId":14,"patientName":"Bekzod","patientTimeZone":"Asia/Tashkent","taskName":"Bug repro","description":null,"category":"VITAL","status":"CANCELLED","timesPerDay":3,"startTime":"05:00","intervalHours":null,"customTimes":["21:00","05:00","10:00"],"morningTime":null,"afternoonTime":null,"eveningTime":null,"startDate":"2026-11-01","endDate":null,"durationDays":1,"inputType":"NUMERIC","inputLabel":"Pressure","notesRequired":false,"notesLabel":null,"createdAt":"2026-11-01T21:35:50.484073Z","progress":{"totalCheckIns":6,"completedCheckIns":3,"pendingCheckIns":0,"missedCheckIns":3},"doctorId":2,"doctorName":"Dr","appointmentId":null}"""

    private fun task(json: String) = RemoteCareTask.from(apiJson.decodeFromString(TaskDto.serializer(), json))

    @Test
    fun `a dev row reads whole, custom times sorted`() {
        val t = task(devRow)!!
        assertThat(t.category).isEqualTo(TaskCategory.Vital)
        assertThat(t.status).isEqualTo(TaskStatus.Cancelled)
        assertThat(t.inputType).isEqualTo(TaskInputType.Numeric)
        assertThat(t.schedule.times).containsExactly(LocalTime(5, 0), LocalTime(10, 0), LocalTime(21, 0)).inOrder()
        assertThat(t.startDate).isEqualTo(LocalDate(2026, 11, 1))
        assertThat(t.progress!!.fraction).isEqualTo(0.5f)
        assertThat(t.patientTimeZone).isEqualTo("Asia/Tashkent")
    }

    @Test
    fun `unknown values fall back, a row without a start date is skipped`() {
        val t =
            task(
                """{"id":1,"patientId":2,"startDate":"2026-11-01","category":"YOGA","status":"PAUSED","inputType":"PHOTO"}""",
            )!!
        assertThat(t.category).isEqualTo(TaskCategory.Other)
        assertThat(t.status).isEqualTo(TaskStatus.Active)
        assertThat(t.inputType).isEqualTo(TaskInputType.Text)
        assertThat(task("""{"id":1,"patientId":2}""")).isNull()
    }

    @Test
    fun `the day's times are custom, evenly spaced until midnight, legacy, or the start alone`() {
        assertThat(TaskSchedule.of(4, "08:00", 6, null, emptyList()).times.map { it.toString() })
            .containsExactly("08:00", "14:00", "20:00").inOrder() // 02:00 would be tomorrow
        assertThat(
            TaskSchedule.of(1, null, null, null, listOf(null, null, "20:00")).times,
        ).containsExactly(LocalTime(20, 0))
        assertThat(TaskSchedule.of(1, "07:30", null, null, emptyList()).times).containsExactly(LocalTime(7, 30))
        assertThat(
            TaskSchedule.of(2, "bad", 12, listOf("x", "09:00", "09:00"), emptyList()).times,
        ).containsExactly(LocalTime(9, 0))
        assertThat(TaskSchedule.of(40, null, null, null, emptyList()).timesPerDay).isEqualTo(15)
        // The backend's order: a start wins over legacy times; no interval means every hour, still before midnight.
        assertThat(
            TaskSchedule.of(1, "08:00", null, null, listOf(null, null, "20:00")).times,
        ).containsExactly(LocalTime(8, 0))
        assertThat(
            TaskSchedule.of(3, "22:00", null, null, emptyList()).times,
        ).containsExactly(LocalTime(22, 0), LocalTime(23, 0)).inOrder()
    }

    @Test
    fun `a pending check-in is missed once its time passed for the patient, not the phone`() {
        val c =
            TaskCheckIn(
                1,
                LocalDate(
                    2026,
                    11,
                    5,
                ),
                LocalTime(13, 0), CheckInStatus.Pending, null, null, null, null, null,
            )
        val berlin = TimeZone.of("Europe/Berlin")
        // 08:30 UTC = 13:30 in Tashkent (past), 09:30 in Berlin (not yet).
        val now = Instant.parse("2026-11-05T08:30:00Z")
        assertThat(c.effectiveStatus(now, "Asia/Tashkent", berlin)).isEqualTo(CheckInStatus.Missed)
        assertThat(c.effectiveStatus(now, null, berlin)).isEqualTo(CheckInStatus.Pending)
        assertThat(c.effectiveStatus(now, "Not/AZone", berlin)).isEqualTo(CheckInStatus.Pending)
        // No patient zone and no practice zone: the server's status, never the phone's guess.
        assertThat(
            c.effectiveStatus(Instant.parse("2026-11-06T00:00:00Z"), null, null),
        ).isEqualTo(CheckInStatus.Pending)
        // Without a time, the whole day; a completed one stays completed.
        assertThat(c.copy(time = null).effectiveStatus(now, "Asia/Tashkent", berlin)).isEqualTo(CheckInStatus.Pending)
        assertThat(
            c.copy(status = CheckInStatus.Completed).effectiveStatus(now, "Asia/Tashkent", berlin),
        ).isEqualTo(CheckInStatus.Completed)
    }
}
