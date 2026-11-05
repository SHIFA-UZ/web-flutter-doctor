package shifa.doctor.data.notifications

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test
import shifa.doctor.data.model.AppNotification

class NotificationRulesTest {
    private fun n(
        type: String,
        appointment: Long? = null,
        patient: Long? = null,
        task: Long? = null,
        document: Long? = null,
    ) = AppNotification(
        id = 1,
        type = type,
        kind = NotificationKind.of(type),
        title = "",
        message = "",
        createdAt = Instant.parse("2026-11-05T05:00:00Z"),
        read = false,
        appointmentId = appointment,
        patientId = patient,
        taskId = task,
        documentId = document,
    )

    // Every value of the backend's `Notification.Type` (2026-11-05) and what it becomes.
    private val backend =
        mapOf(
            "CHAT_MESSAGE" to NotificationKind.Chat,
            "APPOINTMENT_CANCELLED" to NotificationKind.Cancelled,
            "APPOINTMENT_CHANGED" to NotificationKind.Rescheduled,
            "APPOINTMENT_REMINDER" to NotificationKind.Reminder,
            "APPOINTMENT_BOOKED_BY_PATIENT" to NotificationKind.Booked,
            "APPOINTMENT_CANCELLED_BY_PATIENT" to NotificationKind.Cancelled,
            "APPOINTMENT_RESCHEDULED_BY_PATIENT" to NotificationKind.Rescheduled,
            "APPOINTMENT_ASSIGNED" to NotificationKind.AppointmentUpdate,
            "APPOINTMENT_PAID_BY_PATIENT" to NotificationKind.Paid,
            "SIGNATURE_REQUESTED" to NotificationKind.Other,
            "TASK_REMINDER" to NotificationKind.TaskUpdate,
            "TASK_COMPLETED" to NotificationKind.TaskCompleted,
            "DOCUMENT_ACCESS_REQUEST" to NotificationKind.AccessRequest,
            "DOCUMENT_ACCESS_APPROVED" to NotificationKind.AccessApproved,
            "DOCUMENT_ACCESS_REJECTED" to NotificationKind.AccessRejected,
            "TASK_ASSIGNED" to NotificationKind.TaskAssigned,
            "TASK_CANCELLED" to NotificationKind.TaskUpdate,
            "GENERAL" to NotificationKind.Other,
            "AI_SCRIBE_READY" to NotificationKind.AiScribe,
            "AI_VISIT_SUMMARY_READY" to NotificationKind.AiSummary,
            "CONSULTATION_PAYMENT_REMINDER" to NotificationKind.Other,
            "CONSULTATION_PAYMENT_DUE_24H" to NotificationKind.Other,
            "TREATMENT_PLAN_PAYMENT_REMINDER" to NotificationKind.Other,
            "PROPHYLAXIS_REMINDER" to NotificationKind.Other,
            "INSTALLMENT_OVERDUE" to NotificationKind.Other,
            "APPOINTMENT_BRIEFING_READY" to NotificationKind.AiBriefing,
            "CARE_PARTNERSHIP_INVITE" to NotificationKind.PartnershipInvite,
            "CARE_PARTNERSHIP_ACCEPTED" to NotificationKind.PartnershipAccepted,
            "CARE_PARTNERSHIP_PROGRESS" to NotificationKind.PartnershipProgress,
            "NEW_MESSAGE" to NotificationKind.Chat,
            "APPOINTMENT_SOMETHING_NEW" to NotificationKind.AppointmentUpdate,
            "TASK_SOMETHING_NEW" to NotificationKind.TaskUpdate,
            "" to NotificationKind.Other,
        )

    @Test
    fun `every backend type has a kind, new ones fall under their prefix`() {
        assertThat(backend.keys.associateWith { NotificationKind.of(it) }).isEqualTo(backend)
    }

    @Test
    fun `chips show their kinds, AI and unknown only under All`() {
        assertThat(NotificationFilter.Appointments.shows(NotificationKind.Cancelled)).isTrue()
        assertThat(NotificationFilter.Tasks.shows(NotificationKind.TaskUpdate)).isTrue()
        assertThat(NotificationFilter.Messages.shows(NotificationKind.AccessRequest)).isTrue()
        assertThat(NotificationFilter.Messages.shows(NotificationKind.PartnershipProgress)).isTrue()
        assertThat(NotificationFilter.Appointments.shows(NotificationKind.AiScribe)).isFalse()
        assertThat(NotificationFilter.Messages.shows(NotificationKind.Other)).isFalse()
        assertThat(NotificationKind.entries.all { NotificationFilter.All.shows(it) }).isTrue()
    }

    @Test
    fun `a tap leads by Flutter's order, clinic staff only to chat`() {
        assertThat(n("DOCUMENT_ACCESS_APPROVED", appointment = 4, patient = 11, document = 7).target(false))
            .isEqualTo(NotificationTarget.Patient(11, 7, openDocument = true))
        assertThat(n("DOCUMENT_ACCESS_REQUEST", patient = 11, document = 7).target(false))
            .isEqualTo(NotificationTarget.Patient(11, 7, openDocument = false))
        assertThat(n("DOCUMENT_ACCESS_REQUEST").target(false)).isEqualTo(NotificationTarget.Inbox)
        assertThat(n("CHAT_MESSAGE", patient = 11).target(false)).isEqualTo(NotificationTarget.Chat)
        assertThat(n("CARE_PARTNERSHIP_INVITE", patient = 12).target(false)).isEqualTo(NotificationTarget.Partnerships)
        assertThat(n("AI_SCRIBE_READY", appointment = 101, patient = 11).target(false))
            .isEqualTo(NotificationTarget.Appointment(101, null))
        assertThat(n("TASK_COMPLETED", patient = 14, task = 1).target(false)).isEqualTo(NotificationTarget.Task(1))
        assertThat(n("TASK_ASSIGNED", patient = 14, task = 1).target(false)).isEqualTo(NotificationTarget.Patient(14))
        assertThat(n("GENERAL").target(false)).isEqualTo(NotificationTarget.Inbox)
        // Clinic staff have no Calendar, Patients or Tasks.
        assertThat(n("TASK_COMPLETED", patient = 14, task = 1).target(true)).isEqualTo(NotificationTarget.Inbox)
        assertThat(
            n("APPOINTMENT_BOOKED_BY_PATIENT", appointment = 101).target(true),
        ).isEqualTo(NotificationTarget.Inbox)
        assertThat(n("CHAT_MESSAGE").target(true)).isEqualTo(NotificationTarget.Chat)
    }
}
