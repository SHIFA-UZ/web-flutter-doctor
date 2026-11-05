package shifa.doctor.ui.notifications

import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.notifications.NotificationKind
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.documentAccessApprovedDetail
import shifa.doctor.generated.resources.documentAccessRejectedDetail
import shifa.doctor.generated.resources.documentAccessRequestDetail
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
import shifa.doctor.generated.resources.notificationMessageAppointmentReminder
import shifa.doctor.generated.resources.notificationMessageChat
import shifa.doctor.generated.resources.notificationMessagePartnershipAccepted
import shifa.doctor.generated.resources.notificationMessagePartnershipInvite
import shifa.doctor.generated.resources.notificationMessagePartnershipProgress
import shifa.doctor.generated.resources.notificationMessagePatientBookedAppointment
import shifa.doctor.generated.resources.notificationMessagePatientBookedAppointmentNoTime
import shifa.doctor.generated.resources.notificationMessagePatientCancelled
import shifa.doctor.generated.resources.notificationMessagePatientPaid
import shifa.doctor.generated.resources.notificationMessagePatientRescheduled
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

/** The board's colour roles (`DaInbox` `typeOf`): brand, info, error, warning, or plain secondary. */
enum class NotificationTone { Brand, Info, Error, Warning, Neutral }

data class NotificationLook(
    val label: StringResource,
    val icon: DrawableResource,
    val tone: NotificationTone,
)

/**
 * The card's label, 18 px icon and colour (board `DaInbox`, flow 07/08 catalogue): the board's icons (a lock for
 * document access, a clock for a reminder) over Flutter's Material ones; the flow's labels, plus "Appointment
 * reminder" (the board; Flutter called it "Appointment booked"), the AI briefing and summary and "Task update".
 */
fun NotificationKind.look(): NotificationLook =
    when (this) {
        NotificationKind.Booked ->
            NotificationLook(
                Res.string.notificationTypeAppointmentBooked,
                Res.drawable.ic_cal_plus_18,
                NotificationTone.Brand,
            )
        NotificationKind.Reminder ->
            NotificationLook(
                Res.string.notificationTypeAppointmentReminder,
                Res.drawable.ic_clock_18,
                NotificationTone.Info,
            )
        NotificationKind.Rescheduled ->
            NotificationLook(
                Res.string.notificationTypeAppointmentRescheduled,
                Res.drawable.ic_calendar_18,
                NotificationTone.Info,
            )
        NotificationKind.Paid ->
            NotificationLook(
                Res.string.notificationTypeAppointmentPaid,
                Res.drawable.ic_wallet_18,
                NotificationTone.Brand,
            )
        NotificationKind.Cancelled ->
            NotificationLook(
                Res.string.notificationTypeAppointmentCancelled,
                Res.drawable.ic_calendar_18,
                NotificationTone.Error,
            )
        NotificationKind.AppointmentUpdate ->
            NotificationLook(Res.string.notificationGeneric, Res.drawable.ic_calendar_18, NotificationTone.Neutral)
        NotificationKind.AiScribe ->
            NotificationLook(
                Res.string.notificationTypeAiScribeReady,
                Res.drawable.ic_sparkles_18,
                NotificationTone.Brand,
            )
        NotificationKind.AiSummary ->
            NotificationLook(
                Res.string.notificationTypeAiVisitSummaryReady,
                Res.drawable.ic_sparkles_18,
                NotificationTone.Brand,
            )
        NotificationKind.AiBriefing ->
            NotificationLook(
                Res.string.notificationTypeAiBriefingReady,
                Res.drawable.ic_sparkles_18,
                NotificationTone.Brand,
            )
        NotificationKind.TaskCompleted ->
            NotificationLook(Res.string.notificationTypeTaskCompleted, Res.drawable.ic_tasks_18, NotificationTone.Brand)
        NotificationKind.TaskAssigned ->
            NotificationLook(
                Res.string.notificationTypeTaskAssigned,
                Res.drawable.ic_tasks_18,
                NotificationTone.Warning,
            )
        NotificationKind.TaskUpdate ->
            NotificationLook(Res.string.notificationTypeTaskUpdate, Res.drawable.ic_tasks_18, NotificationTone.Warning)
        NotificationKind.AccessRequest ->
            NotificationLook(
                Res.string.notificationTypeDocumentAccessRequest,
                Res.drawable.ic_lock_18,
                NotificationTone.Info,
            )
        NotificationKind.AccessApproved ->
            NotificationLook(
                Res.string.notificationTypeDocumentAccessApproved,
                Res.drawable.ic_lock_18,
                NotificationTone.Brand,
            )
        NotificationKind.AccessRejected ->
            NotificationLook(
                Res.string.notificationTypeDocumentAccessRejected,
                Res.drawable.ic_lock_18,
                NotificationTone.Error,
            )
        NotificationKind.PartnershipInvite ->
            NotificationLook(
                Res.string.notificationTypePartnershipInvite,
                Res.drawable.ic_handshake_18,
                NotificationTone.Info,
            )
        NotificationKind.PartnershipAccepted ->
            NotificationLook(
                Res.string.notificationTypePartnershipAccepted,
                Res.drawable.ic_handshake_18,
                NotificationTone.Info,
            )
        NotificationKind.PartnershipProgress ->
            NotificationLook(
                Res.string.notificationTypePartnershipProgress,
                Res.drawable.ic_handshake_18,
                NotificationTone.Info,
            )
        NotificationKind.Chat ->
            NotificationLook(Res.string.notificationTypeChatMessage, Res.drawable.ic_chat_18, NotificationTone.Info)
        NotificationKind.Other ->
            NotificationLook(Res.string.notificationGeneric, Res.drawable.ic_bell_18, NotificationTone.Neutral)
    }

/** A card's text: a template in the app's language, or the backend's (English) text when there's nothing to fill. */
sealed interface NotificationBody {
    /** [args] in the template's order; with [at], the appointment's date and time (practice zone) follow them. */
    data class Template(
        val text: StringResource,
        val args: List<String> = emptyList(),
        val at: Instant? = null,
    ) : NotificationBody

    data class Server(
        val text: String,
    ) : NotificationBody
}

/**
 * The body from the type and the structured fields (07 §2.3; spec §8 #13: never parse the English text). [patient] is
 * the localised "Patient" for a row without a name.
 */
fun AppNotification.body(patient: String): NotificationBody {
    val name = patientName ?: patient
    val document = documentTitle
    val doctor = requestingDoctorName
    return when (kind) {
        NotificationKind.Booked ->
            appointmentStartAt?.let {
                NotificationBody.Template(Res.string.notificationMessagePatientBookedAppointment, listOf(name), it)
            } ?: NotificationBody.Template(Res.string.notificationMessagePatientBookedAppointmentNoTime, listOf(name))
        NotificationKind.Reminder -> NotificationBody.Template(Res.string.notificationMessageAppointmentReminder)
        NotificationKind.Rescheduled ->
            NotificationBody.Template(Res.string.notificationMessagePatientRescheduled, listOf(name))
        NotificationKind.Paid -> NotificationBody.Template(Res.string.notificationMessagePatientPaid, listOf(name))
        NotificationKind.Cancelled ->
            NotificationBody.Template(
                Res.string.notificationMessagePatientCancelled,
                listOf(name),
            )
        NotificationKind.Chat -> NotificationBody.Template(Res.string.notificationMessageChat)
        NotificationKind.PartnershipInvite -> NotificationBody.Template(Res.string.notificationMessagePartnershipInvite)
        NotificationKind.PartnershipAccepted ->
            NotificationBody.Template(
                Res.string.notificationMessagePartnershipAccepted,
            )
        NotificationKind.PartnershipProgress ->
            NotificationBody.Template(
                Res.string.notificationMessagePartnershipProgress,
            )
        NotificationKind.AccessRequest ->
            if (document != null && doctor != null) {
                NotificationBody.Template(Res.string.documentAccessRequestDetail, listOf(doctor, document, name))
            } else {
                server()
            }
        NotificationKind.AccessApproved ->
            document?.let {
                NotificationBody.Template(Res.string.documentAccessApprovedDetail, listOf(it, name))
            } ?: server()
        NotificationKind.AccessRejected ->
            document?.let {
                NotificationBody.Template(Res.string.documentAccessRejectedDetail, listOf(it, name))
            } ?: server()
        else -> server()
    }
}

private fun AppNotification.server() = NotificationBody.Server(message.ifBlank { title })
