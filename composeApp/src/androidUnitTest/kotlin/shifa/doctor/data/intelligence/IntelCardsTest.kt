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
            card("propose_appointment", """{"cardType":"propose_appointment","spokenHint":"Shall I book 10:00?"}"""),
        )
            .isEqualTo(IntelCard.Other("propose_appointment", "Shall I book 10:00?"))
        assertThat(
            (card(null, """{"cardType":"something_new","error":"Not available."}""") as IntelCard.Other).line,
        ).isEqualTo("Not available.")
        assertThat((card(null, "{}") as IntelCard.Other).line).isNull()
    }
}
