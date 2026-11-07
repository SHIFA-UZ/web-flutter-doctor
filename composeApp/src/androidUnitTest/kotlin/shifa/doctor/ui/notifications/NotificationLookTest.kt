package shifa.doctor.ui.notifications

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.notifications.NotificationKind
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.documentAccessRequestDetail
import shifa.doctor.generated.resources.notificationMessagePatientBookedAppointment
import shifa.doctor.generated.resources.notificationMessagePatientBookedAppointmentNoTime
import shifa.doctor.generated.resources.notificationMessagePatientCancelled
import shifa.doctor.generated.resources.notificationTypeAppointmentReminder

class NotificationLookTest {
    private val at = Instant.parse("2026-11-06T05:00:00Z")

    private fun n(
        type: String,
        name: String? = null,
        start: Instant? = null,
        document: String? = null,
        doctor: String? = null,
        message: String = "Server text",
    ) = AppNotification(
        id = 1,
        type = type,
        kind = NotificationKind.of(type),
        title = "Title",
        message = message,
        createdAt = at,
        read = false,
        appointmentStartAt = start,
        patientName = name,
        documentTitle = document,
        requestingDoctorName = doctor,
    )

    @Test
    fun `bodies are templates from the fields, never parsed English`() {
        assertThat(n("APPOINTMENT_BOOKED_BY_PATIENT", "Malika", at).body("Patient")).isEqualTo(
            NotificationBody.Template(Res.string.notificationMessagePatientBookedAppointment, listOf("Malika"), at),
        )
        assertThat(n("APPOINTMENT_BOOKED_BY_PATIENT").body("Patient")).isEqualTo(
            NotificationBody.Template(Res.string.notificationMessagePatientBookedAppointmentNoTime, listOf("Patient")),
        )
        assertThat(n("APPOINTMENT_CANCELLED", "Jasur").body("Patient")).isEqualTo(
            NotificationBody.Template(Res.string.notificationMessagePatientCancelled, listOf("Jasur")),
        )
        assertThat(n("DOCUMENT_ACCESS_REQUEST", "Dilnoza", document = "Blood test", doctor = "Dr. N").body("Patient"))
            .isEqualTo(
                NotificationBody.Template(
                    Res.string.documentAccessRequestDetail,
                    listOf("Dr. N", "Blood test", "Dilnoza"),
                ),
            )
        // Without the fields a template needs, or for a type without one: the backend's text, else its title.
        assertThat(
            n("DOCUMENT_ACCESS_REQUEST", "Dilnoza").body("Patient"),
        ).isEqualTo(NotificationBody.Server("Server text"))
        assertThat(n("TASK_COMPLETED").body("Patient")).isEqualTo(NotificationBody.Server("Server text"))
        assertThat(n("GENERAL", message = " ").body("Patient")).isEqualTo(NotificationBody.Server("Title"))
    }

    @Test
    fun `every kind has a look, a reminder is not called a booking`() {
        NotificationKind.entries.forEach { it.look() }
        assertThat(NotificationKind.Reminder.look().label).isEqualTo(Res.string.notificationTypeAppointmentReminder)
        assertThat(NotificationKind.Cancelled.look().tone).isEqualTo(NotificationTone.Error)
    }
}
