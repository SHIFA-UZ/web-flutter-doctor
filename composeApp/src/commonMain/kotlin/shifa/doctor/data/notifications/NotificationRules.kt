package shifa.doctor.data.notifications

import kotlinx.datetime.Instant
import shifa.doctor.data.model.AppNotification

/** The inbox's chips (flow 07/08 §3): client-side, by kind. AI and unknown kinds show only under [All]. */
enum class NotificationFilter { All, Appointments, Tasks, Messages }

/**
 * What a backend type means to the doctor (07 §2.3, flow 07/08 "Type catalogue", board `DaInbox`): its look, chip and
 * tap target follow from the kind. Unknown `APPOINTMENT_*` / `TASK_*` types still sort under their chip.
 */
enum class NotificationKind(
    val filter: NotificationFilter,
) {
    Booked(NotificationFilter.Appointments),
    Reminder(NotificationFilter.Appointments),
    Rescheduled(NotificationFilter.Appointments),
    Paid(NotificationFilter.Appointments),
    Cancelled(NotificationFilter.Appointments),
    AppointmentUpdate(NotificationFilter.Appointments),
    AiScribe(NotificationFilter.All),
    AiSummary(NotificationFilter.All),

    // An `APPOINTMENT_*` type: under Appointments like the rest (flow 07/08 §3).
    AiBriefing(NotificationFilter.Appointments),
    TaskCompleted(NotificationFilter.Tasks),
    TaskAssigned(NotificationFilter.Tasks),
    TaskUpdate(NotificationFilter.Tasks),
    AccessRequest(NotificationFilter.Messages),
    AccessApproved(NotificationFilter.Messages),
    AccessRejected(NotificationFilter.Messages),
    PartnershipInvite(NotificationFilter.Messages),
    PartnershipAccepted(NotificationFilter.Messages),
    PartnershipProgress(NotificationFilter.Messages),
    Chat(NotificationFilter.Messages),
    Other(NotificationFilter.All),
    ;

    val isAccess: Boolean get() = this == AccessRequest || this == AccessApproved || this == AccessRejected
    val isPartnership: Boolean
        get() = this == PartnershipInvite || this == PartnershipAccepted || this == PartnershipProgress

    companion object {
        /** [type] as the backend sends it (`Notification.Type`, upper case). */
        fun of(type: String): NotificationKind =
            when (type) {
                "APPOINTMENT_BOOKED_BY_PATIENT" -> Booked
                "APPOINTMENT_REMINDER" -> Reminder
                "APPOINTMENT_RESCHEDULED_BY_PATIENT", "APPOINTMENT_CHANGED" -> Rescheduled
                "APPOINTMENT_PAID_BY_PATIENT" -> Paid
                "APPOINTMENT_CANCELLED_BY_PATIENT", "APPOINTMENT_CANCELLED" -> Cancelled
                "AI_SCRIBE_READY" -> AiScribe
                "AI_VISIT_SUMMARY_READY" -> AiSummary
                "APPOINTMENT_BRIEFING_READY" -> AiBriefing
                "TASK_COMPLETED" -> TaskCompleted
                "TASK_ASSIGNED" -> TaskAssigned
                "DOCUMENT_ACCESS_REQUEST" -> AccessRequest
                "DOCUMENT_ACCESS_APPROVED" -> AccessApproved
                "DOCUMENT_ACCESS_REJECTED" -> AccessRejected
                "CARE_PARTNERSHIP_INVITE" -> PartnershipInvite
                "CARE_PARTNERSHIP_ACCEPTED" -> PartnershipAccepted
                "CARE_PARTNERSHIP_PROGRESS" -> PartnershipProgress
                "CHAT_MESSAGE", "NEW_MESSAGE" -> Chat
                else ->
                    when {
                        type.startsWith("APPOINTMENT_") -> AppointmentUpdate
                        type.startsWith("TASK_") -> TaskUpdate
                        else -> Other
                    }
            }
    }
}

fun NotificationFilter.shows(kind: NotificationKind): Boolean = this == NotificationFilter.All || kind.filter == this

/** Where a tap leads (flow 07/08 §10, 07/10). */
sealed interface NotificationTarget {
    /** The Calendar on the appointment's practice day, the appointment open. */
    data class Appointment(
        val id: Long,
        val startAt: Instant?,
    ) : NotificationTarget

    /** The patient's record; [documentId] selected, its viewer open when [openDocument]. */
    data class Patient(
        val id: Long,
        val documentId: Long? = null,
        val openDocument: Boolean = false,
    ) : NotificationTarget

    data class Task(
        val id: Long,
    ) : NotificationTarget

    /** The Chat tab: the row carries no conversation id (BE-06), so the list, not a thread. */
    data object Chat : NotificationTarget

    data object Partnerships : NotificationTarget

    /** Nothing to open: the tap only marks it read. */
    data object Inbox : NotificationTarget
}

/**
 * Flutter's order (`app.dart:284-440`): document access, chat, partnership, appointment, a completed task, the patient,
 * else the inbox. Clinic staff have no Calendar, Patients or Tasks (flow 01/18), so only chat leads anywhere for them.
 */
fun AppNotification.target(staff: Boolean): NotificationTarget {
    val patient = patientId
    val appointment = appointmentId
    val task = taskId
    return when {
        kind == NotificationKind.Chat -> NotificationTarget.Chat
        staff -> NotificationTarget.Inbox
        kind.isAccess && patient != null ->
            NotificationTarget.Patient(patient, documentId, openDocument = kind == NotificationKind.AccessApproved)
        kind.isAccess -> NotificationTarget.Inbox
        kind.isPartnership -> NotificationTarget.Partnerships
        appointment != null -> NotificationTarget.Appointment(appointment, appointmentStartAt)
        kind == NotificationKind.TaskCompleted && task != null -> NotificationTarget.Task(task)
        patient != null -> NotificationTarget.Patient(patient)
        else -> NotificationTarget.Inbox
    }
}
