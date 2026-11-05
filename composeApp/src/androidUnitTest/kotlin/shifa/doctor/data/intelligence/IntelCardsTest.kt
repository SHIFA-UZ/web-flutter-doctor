@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.intelligence

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import shifa.doctor.data.api.apiJson

// JSON fixtures shaped like the backend's tool payloads (DoctorMorningToolService), one per read-only card (INTEL-05).
class IntelCardsTest {
    private fun card(
        tool: String?,
        json: String,
    ) = IntelCard.of(tool, apiJson.parseToJsonElement(json).jsonObject)

    @Test
    fun `schedule - type from the tool name, the count, eight rows and the rest counted`() {
        val rows =
            (1..10).joinToString(",") {
                """{"appointmentId":$it,"patientId":$it,"patientName":"P$it","date":"2026-11-05","startLocal":"0$it:00","status":"CONFIRMED"}"""
            }
        val c =
            card(
                "get_today_schedule",
                """{"date":"2026-11-05","count":10,"appointments":[$rows]}""",
            ) as IntelCard.Schedule
        assertThat(c.count).isEqualTo(10)
        assertThat(c.visits).hasSize(IntelCard.SCHEDULE_ROWS)
        assertThat(c.more).isEqualTo(2)
        assertThat(c.date).isEqualTo(LocalDate(2026, 11, 5))
        assertThat(c.visits.first().appointmentId).isEqualTo(1)
        val empty = card(null, """{"cardType":"schedule","count":0,"appointments":[]}""") as IntelCard.Schedule
        assertThat(empty.visits).isEmpty()
        assertThat(empty.more).isEqualTo(0)
    }

    @Test
    fun `patient appointments, free slots, search, most visited, patterns and brief read their rows`() {
        val pa =
            card(
                null,
                """{"cardType":"patient_appointments","patientName":"Jasur Karimov","rangeLabel":"Upcoming","appointments":[{"appointmentId":7,"date":"2026-11-06","startLocal":"10:00","status":"CANCELLED"}]}""",
            ) as IntelCard.PatientAppointments
        assertThat(pa.count).isEqualTo(1)
        assertThat(pa.visits.single().date).isEqualTo(LocalDate(2026, 11, 6))
        assertThat(pa.rangeLabel).isEqualTo("Upcoming")

        val slots =
            (0 until 30).joinToString(
                ",",
            ) { """{"startLocal":"${8 + it / 4}:${(it % 4) * 15}","slotMinutes":15}""" }
        val fs = card("list_free_slots", """{"dateLabel":"Thu 6 Nov","slots":[$slots]}""") as IntelCard.FreeSlots
        assertThat(fs.slots).hasSize(IntelCard.SLOT_CHIPS)
        assertThat(fs.more).isEqualTo(6)
        assertThat(fs.date).isNull()
        assertThat(fs.dateLabel).isEqualTo("Thu 6 Nov")
        val none =
            card(
                "list_free_slots",
                """{"slots":[],"spokenHint":"No free slots on Thursday."}""",
            ) as IntelCard.FreeSlots
        assertThat(none.hint).isEqualTo("No free slots on Thursday.")

        val search =
            card(
                "search_patients",
                """{"fuzzy":true,"query":"Karimv","matches":[{"patientId":7,"patientName":"Jasur Karimov","phone":"+998 93 555 12 34"},{"patientName":null}]}""",
            ) as IntelCard.PatientSearch
        assertThat(search.fuzzy).isTrue()
        assertThat(search.matches.map { it.name }).containsExactly("Jasur Karimov", null).inOrder()

        val mv =
            card(
                null,
                """{"cardType":"most_visited","patients":[{"patientName":"Ada","visitCount":12},{"label":"Bob","count":3.0}]}""",
            ) as IntelCard.MostVisited
        assertThat(mv.patients).containsExactly("Ada" to 12L, "Bob" to 3L).inOrder()

        val pt =
            card(
                "get_clinic_patterns",
                """{"patterns":[{"key":"medication:ibuprofen","label":"Spoken medication","factType":"MEDICATION","count":14}]}""",
            ) as IntelCard.Patterns
        assertThat(pt.patterns.single().count).isEqualTo(14)

        val brief =
            card(
                "brief_patient",
                """{"briefing":"## Snapshot\n- Allergy: penicillin","spokenHint":"Here is the brief."}""",
            ) as IntelCard.Brief
        assertThat(brief.text).startsWith("## Snapshot")
    }

    @Test
    fun `a known card that failed says why under its own title, never an empty list`() {
        // DoctorMorningToolService: an unknown patient, a briefing that couldn't be made.
        assertThat(
            card(
                "list_patient_appointments",
                """{"cardType":"patient_appointments","error":"Patient not found","spokenHint":"I could not find that patient.","appointments":[]}""",
            ),
        )
            .isEqualTo(IntelCard.Failed("patient_appointments", "I could not find that patient."))
        assertThat(
            card("brief_patient", """{"cardType":"patient_brief","error":"No data"}"""),
        ).isEqualTo(IntelCard.Failed("patient_brief", "No data"))
    }

    @Test
    fun `a card the app doesn't draw yet shows its spoken line, else its error`() {
        assertThat(
            card("open_dental_chart", """{"cardType":"dental_chart","spokenHint":"Here is the chart."}"""),
        )
            .isEqualTo(IntelCard.Other("dental_chart", "Here is the chart."))
        assertThat(
            (card(null, """{"cardType":"something_new","error":"Not available."}""") as IntelCard.Other).line,
        ).isEqualTo("Not available.")
        assertThat((card(null, "{}") as IntelCard.Other).line).isNull()
    }

    // INTEL-06: payloads as DoctorMorningToolService / IntelligenceBookingWorkflowService / AppointmentActionService send them.
    @Test
    fun `a proposal reads its slots, the preferred one, the workflow and the app's own outcome`() {
        val slots =
            """"slots":[{"startAt":"2026-11-06T05:00:00Z","startLocal":"10:00","slotMinutes":30,"locationId":3,"locationLabel":"Chilonzor"},{"startLocal":"11:00"},{"startAt":"x"}]"""
        val book =
            card(
                "propose_appointment",
                """{"patientId":12,"patientName":"Jasur Karimov","date":"2026-11-06","dateLabel":"6 Nov 2026","isVideo":false,"reason":null,"preferredSlot":{"startAt":"2026-11-06T05:00:00Z","startLocal":"10:00","slotMinutes":30},$slots,"spokenHint":"Booking Jasur Karimov at 10:00 on Friday, 6 November."}""",
            ) as IntelCard.Proposal
        assertThat(book.move).isFalse()
        assertThat(book.slots.map { it.time }).containsExactly("10:00", "11:00").inOrder()
        assertThat(book.slots[0]).isEqualTo(CardSlot("2026-11-06T05:00:00Z", "10:00", 30, 3, "Chilonzor"))
        assertThat(book.preferred!!.time).isEqualTo("10:00")
        assertThat(book.time).isEqualTo("10:00")
        assertThat(book.workflow).isFalse()
        assertThat(book.done).isFalse()
        assertThat(book.reason).isNull()
        // The app's own booking and the doctor's Cancel, saved into the card.
        val saved =
            card(
                "propose_appointment",
                """{"patientId":12,$slots,"booked":true,"dismissed":true,"spokenHint":"Booking …"}""",
            ) as IntelCard.Proposal
        assertThat(saved.done).isTrue()
        assertThat(saved.dismissed).isTrue()
        assertThat(saved.status).isNull()
        assertThat(
            (
                card(
                    "propose_appointment",
                    """{"preferredSlot":{"startLocal":"10:00"},$slots,"booked":true,"bookedTime":"11:00"}""",
                ) as IntelCard.Proposal
            ).time,
        )
            .isEqualTo("11:00")

        val move =
            card(
                null,
                """{"cardType":"propose_reschedule","workflowId":"w2","appointmentId":102,"currentDateLabel":"5 Nov 2026","currentStartLocal":"10:30","date":"2026-11-06",$slots}""",
            ) as IntelCard.Proposal
        assertThat(move.move).isTrue()
        assertThat(move.workflow).isTrue()
        assertThat(move.current).isEqualTo("5 Nov 2026 10:30")
        assertThat(move.time).isNull()
    }

    @Test
    fun `the workflow's finished booking has its line, a turn without a proposal is its line only`() {
        val done =
            card(
                "manage_booking_workflow",
                """{"cardType":"propose_appointment","workflowId":"w1","workflowStatus":"COMPLETED","booked":true,"patientName":"Jasur Karimov","date":"2026-11-06","requestedTime":"10:30","spokenHint":"Booked Jasur Karimov at 10:30 on 2026-11-06."}""",
            ) as IntelCard.Proposal
        assertThat(done.done).isTrue()
        assertThat(done.time).isEqualTo("10:30")
        assertThat(done.status).isEqualTo("Booked Jasur Karimov at 10:30 on 2026-11-06.")
        val moved =
            card(
                "propose_reschedule",
                """{"cardType":"propose_reschedule","workflowStatus":"COMPLETED","moved":true,"spokenHint":"Moved the appointment to 10:30 on 2026-11-06."}""",
            ) as IntelCard.Proposal
        assertThat(moved.done).isTrue()
        assertThat(moved.move).isTrue()
        // "Moved the appointment to…" says no more than the app's own "Appointment moved." (and reads half English).
        assertThat(moved.status).isNull()

        assertThat(
            card(
                "manage_booking_workflow",
                """{"cardType":"propose_appointment","workflowStatus":"NONE","error":"No active booking workflow","spokenHint":"There is no booking waiting for confirmation."}""",
            ),
        ).isEqualTo(IntelCard.Failed("propose_appointment", "There is no booking waiting for confirmation."))
        assertThat(
            card(
                "manage_booking_workflow",
                """{"cardType":"propose_appointment","workflowStatus":"COLLECTING_DETAILS","spokenHint":"Okay. Which patient should I book instead?"}""",
            ),
        )
            .isInstanceOf(IntelCard.Failed::class.java)
    }

    @Test
    fun `a cancellation waits only with its token, a result lists what was and wasn't cancelled`() {
        val rows = """"appointments":[{"appointmentId":5,"patientName":"Jasur Karimov","whenLabel":"Friday 6 November at 10:30","status":"CONFIRMED"}]"""
        val preview =
            card(
                "cancel_appointments",
                """{"cardType":"cancel_preview","status":"AWAITING_CONFIRMATION","actionContext":{"type":"CANCEL","token":"t"},$rows,"skipped":[{"appointmentId":6}],"count":1}""",
            ) as IntelCard.Cancel
        assertThat(preview.awaiting).isTrue()
        assertThat(preview.count).isEqualTo(1)
        assertThat(preview.skipped).isEqualTo(1)
        assertThat(
            preview.rows.single(),
        ).isEqualTo(CancelRow("Jasur Karimov", "Friday 6 November at 10:30", "CONFIRMED"))
        val clarify =
            card(
                null,
                """{"cardType":"cancel_preview","status":"AWAITING_CLARIFICATION",$rows}""",
            ) as IntelCard.Cancel
        assertThat(clarify.awaiting).isFalse()
        val result =
            card(
                null,
                """{"cardType":"cancel_result","status":"PARTIAL_SUCCESS","cancelled":[{"patientName":"A","status":"CANCELLED"}],"failed":[{"patientName":"B","status":"IN_PROGRESS"}]}""",
            ) as IntelCard.Cancel
        assertThat(result.result).isTrue()
        assertThat(result.awaiting).isFalse()
        assertThat(result.rows.map { it.status }).containsExactly("CANCELLED", "IN_PROGRESS").inOrder()
    }
}
