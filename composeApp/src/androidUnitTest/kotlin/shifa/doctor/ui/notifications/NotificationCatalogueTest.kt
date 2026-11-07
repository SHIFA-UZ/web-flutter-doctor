@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.notifications

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.junit.Test
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.notifications.NotificationFilter
import shifa.doctor.data.notifications.NotificationKind
import shifa.doctor.data.notifications.NotificationTarget
import shifa.doctor.data.notifications.target
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_bell_18
import shifa.doctor.generated.resources.ic_cal_plus_18
import shifa.doctor.generated.resources.ic_calendar_18
import shifa.doctor.generated.resources.ic_chat_18
import shifa.doctor.generated.resources.ic_clock_18
import shifa.doctor.generated.resources.ic_handshake_18
import shifa.doctor.generated.resources.ic_lock_18
import shifa.doctor.generated.resources.ic_sparkles_18
import shifa.doctor.generated.resources.ic_tasks_18
import shifa.doctor.generated.resources.ic_wallet_18
import shifa.doctor.generated.resources.notificationGeneric
import shifa.doctor.generated.resources.notificationTypeAiBriefingReady
import shifa.doctor.generated.resources.notificationTypeAiScribeReady
import shifa.doctor.generated.resources.notificationTypeAiVisitSummaryReady
import shifa.doctor.generated.resources.notificationTypeAppointmentBooked
import shifa.doctor.generated.resources.notificationTypeAppointmentCancelled
import shifa.doctor.generated.resources.notificationTypeAppointmentPaid
import shifa.doctor.generated.resources.notificationTypeAppointmentReminder
import shifa.doctor.generated.resources.notificationTypeAppointmentRescheduled
import shifa.doctor.generated.resources.notificationTypeChatMessage
import shifa.doctor.generated.resources.notificationTypeDocumentAccessApproved
import shifa.doctor.generated.resources.notificationTypeDocumentAccessRejected
import shifa.doctor.generated.resources.notificationTypeDocumentAccessRequest
import shifa.doctor.generated.resources.notificationTypePartnershipAccepted
import shifa.doctor.generated.resources.notificationTypePartnershipInvite
import shifa.doctor.generated.resources.notificationTypePartnershipProgress
import shifa.doctor.generated.resources.notificationTypeTaskAssigned
import shifa.doctor.generated.resources.notificationTypeTaskCompleted
import shifa.doctor.generated.resources.notificationTypeTaskUpdate

/**
 * NOTIF-01 "Done when": every backend `Notification.Type` (2026-11-05) → label, icon, colour, chip and tap target, for a
 * row carrying every id (appointment 101, patient 11, document 7, task 1), as a doctor.
 */
class NotificationCatalogueTest {
    private data class Row(
        val label: StringResource,
        val icon: DrawableResource,
        val tone: NotificationTone,
        val filter: NotificationFilter,
        val target: NotificationTarget,
    )

    private val visit = NotificationTarget.Appointment(101, null)
    private val generic =
        Row(
            Res.string.notificationGeneric,
            Res.drawable.ic_bell_18,
            NotificationTone.Neutral,
            NotificationFilter.All,
            visit,
        )

    private fun appointment(
        label: StringResource,
        icon: DrawableResource,
        tone: NotificationTone,
    ) = Row(label, icon, tone, NotificationFilter.Appointments, visit)

    private val table =
        mapOf(
            "APPOINTMENT_BOOKED_BY_PATIENT" to
                appointment(
                    Res.string.notificationTypeAppointmentBooked,
                    Res.drawable.ic_cal_plus_18,
                    NotificationTone.Brand,
                ),
            "APPOINTMENT_REMINDER" to
                appointment(
                    Res.string.notificationTypeAppointmentReminder,
                    Res.drawable.ic_clock_18,
                    NotificationTone.Info,
                ),
            "APPOINTMENT_RESCHEDULED_BY_PATIENT" to
                appointment(
                    Res.string.notificationTypeAppointmentRescheduled,
                    Res.drawable.ic_calendar_18,
                    NotificationTone.Info,
                ),
            "APPOINTMENT_CHANGED" to
                appointment(
                    Res.string.notificationTypeAppointmentRescheduled,
                    Res.drawable.ic_calendar_18,
                    NotificationTone.Info,
                ),
            "APPOINTMENT_PAID_BY_PATIENT" to
                appointment(
                    Res.string.notificationTypeAppointmentPaid,
                    Res.drawable.ic_wallet_18,
                    NotificationTone.Brand,
                ),
            "APPOINTMENT_CANCELLED_BY_PATIENT" to
                appointment(
                    Res.string.notificationTypeAppointmentCancelled,
                    Res.drawable.ic_calendar_18,
                    NotificationTone.Error,
                ),
            "APPOINTMENT_CANCELLED" to
                appointment(
                    Res.string.notificationTypeAppointmentCancelled,
                    Res.drawable.ic_calendar_18,
                    NotificationTone.Error,
                ),
            "APPOINTMENT_ASSIGNED" to
                appointment(
                    Res.string.notificationGeneric,
                    Res.drawable.ic_calendar_18,
                    NotificationTone.Neutral,
                ),
            "APPOINTMENT_BRIEFING_READY" to
                appointment(
                    Res.string.notificationTypeAiBriefingReady,
                    Res.drawable.ic_sparkles_18,
                    NotificationTone.Brand,
                ),
            "AI_SCRIBE_READY" to
                Row(
                    Res.string.notificationTypeAiScribeReady,
                    Res.drawable.ic_sparkles_18,
                    NotificationTone.Brand,
                    NotificationFilter.All,
                    visit,
                ),
            "AI_VISIT_SUMMARY_READY" to
                Row(
                    Res.string.notificationTypeAiVisitSummaryReady,
                    Res.drawable.ic_sparkles_18,
                    NotificationTone.Brand,
                    NotificationFilter.All,
                    visit,
                ),
            "TASK_COMPLETED" to
                Row(
                    Res.string.notificationTypeTaskCompleted,
                    Res.drawable.ic_tasks_18,
                    NotificationTone.Brand,
                    NotificationFilter.Tasks,
                    visit,
                ),
            "TASK_ASSIGNED" to
                Row(
                    Res.string.notificationTypeTaskAssigned,
                    Res.drawable.ic_tasks_18,
                    NotificationTone.Warning,
                    NotificationFilter.Tasks,
                    visit,
                ),
            "TASK_REMINDER" to
                Row(
                    Res.string.notificationTypeTaskUpdate,
                    Res.drawable.ic_tasks_18,
                    NotificationTone.Warning,
                    NotificationFilter.Tasks,
                    visit,
                ),
            "TASK_CANCELLED" to
                Row(
                    Res.string.notificationTypeTaskUpdate,
                    Res.drawable.ic_tasks_18,
                    NotificationTone.Warning,
                    NotificationFilter.Tasks,
                    visit,
                ),
            "DOCUMENT_ACCESS_REQUEST" to
                Row(
                    Res.string.notificationTypeDocumentAccessRequest,
                    Res.drawable.ic_lock_18,
                    NotificationTone.Info,
                    NotificationFilter.Messages,
                    NotificationTarget.Patient(11, 7, false),
                ),
            "DOCUMENT_ACCESS_APPROVED" to
                Row(
                    Res.string.notificationTypeDocumentAccessApproved,
                    Res.drawable.ic_lock_18,
                    NotificationTone.Brand,
                    NotificationFilter.Messages,
                    NotificationTarget.Patient(11, 7, true),
                ),
            "DOCUMENT_ACCESS_REJECTED" to
                Row(
                    Res.string.notificationTypeDocumentAccessRejected,
                    Res.drawable.ic_lock_18,
                    NotificationTone.Error,
                    NotificationFilter.Messages,
                    NotificationTarget.Patient(11, 7, false),
                ),
            "CARE_PARTNERSHIP_INVITE" to
                Row(
                    Res.string.notificationTypePartnershipInvite,
                    Res.drawable.ic_handshake_18,
                    NotificationTone.Info,
                    NotificationFilter.Messages,
                    NotificationTarget.Partnerships,
                ),
            "CARE_PARTNERSHIP_ACCEPTED" to
                Row(
                    Res.string.notificationTypePartnershipAccepted,
                    Res.drawable.ic_handshake_18,
                    NotificationTone.Info,
                    NotificationFilter.Messages,
                    NotificationTarget.Partnerships,
                ),
            "CARE_PARTNERSHIP_PROGRESS" to
                Row(
                    Res.string.notificationTypePartnershipProgress,
                    Res.drawable.ic_handshake_18,
                    NotificationTone.Info,
                    NotificationFilter.Messages,
                    NotificationTarget.Partnerships,
                ),
            "CHAT_MESSAGE" to
                Row(
                    Res.string.notificationTypeChatMessage,
                    Res.drawable.ic_chat_18,
                    NotificationTone.Info,
                    NotificationFilter.Messages,
                    NotificationTarget.Chat,
                ),
            "NEW_MESSAGE" to
                Row(
                    Res.string.notificationTypeChatMessage,
                    Res.drawable.ic_chat_18,
                    NotificationTone.Info,
                    NotificationFilter.Messages,
                    NotificationTarget.Chat,
                ),
            "SIGNATURE_REQUESTED" to generic,
            "GENERAL" to generic,
            "CONSULTATION_PAYMENT_REMINDER" to generic,
            "CONSULTATION_PAYMENT_DUE_24H" to generic,
            "CONSULTATION_PAYMENT_DUE_6H" to generic,
            "CONSULTATION_PAYMENT_DUE_1H" to generic,
            "TREATMENT_PLAN_PAYMENT_REMINDER" to generic,
            "TREATMENT_PLAN_UPDATED" to generic,
            "PROPHYLAXIS_REMINDER" to generic,
            "INSTALLMENT_DUE_SOON" to generic,
            "INSTALLMENT_DUE_TODAY" to generic,
            "INSTALLMENT_OVERDUE" to generic,
            "INSTALLMENT_SCHEDULE_CREATED" to generic,
        )

    private fun row(type: String) =
        AppNotification(
            id = 1,
            type = type,
            kind = NotificationKind.of(type),
            title = "",
            message = "",
            createdAt = Instant.parse("2026-11-05T05:00:00Z"),
            read = false,
            appointmentId = 101,
            patientId = 11,
            documentId = 7,
            taskId = 1,
        )

    @Test
    fun `every backend type has its label, icon, colour, chip and target`() {
        val actual =
            table.keys.associateWith { type ->
                val n = row(type)
                val look = n.kind.look()
                Row(look.label, look.icon, look.tone, n.kind.filter, n.target(staff = false))
            }
        assertThat(actual).isEqualTo(table)
    }

    @Test
    fun `the table covers every kind`() {
        assertThat(
            table.keys.map { NotificationKind.of(it) }.toSet(),
        ).containsExactlyElementsIn(NotificationKind.entries)
    }
}
