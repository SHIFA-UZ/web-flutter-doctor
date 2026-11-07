package shifa.doctor.data.notifications

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import shifa.doctor.data.model.AppNotification
import shifa.doctor.utils.PracticeClock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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

    /** The Chat tab, for a row without its conversation (older backends). */
    data object Chat : NotificationTarget

    /** The row's conversation (BE-06). */
    data class Conversation(
        val id: Long,
    ) : NotificationTarget

    /** The partnership list, for a row without its partnership (older backends). */
    data object Partnerships : NotificationTarget

    /** The row's partnership (BE-06). */
    data class Partnership(
        val id: Long,
    ) : NotificationTarget

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
        kind == NotificationKind.Chat ->
            conversationId?.let {
                NotificationTarget.Conversation(
                    it,
                )
            } ?: NotificationTarget.Chat
        staff -> NotificationTarget.Inbox
        kind.isAccess && patient != null ->
            NotificationTarget.Patient(patient, documentId, openDocument = kind == NotificationKind.AccessApproved)
        kind.isAccess -> NotificationTarget.Inbox
        kind.isPartnership ->
            carePartnershipId?.let {
                NotificationTarget.Partnership(
                    it,
                )
            } ?: NotificationTarget.Partnerships
        appointment != null -> NotificationTarget.Appointment(appointment, appointmentStartAt)
        kind == NotificationKind.TaskCompleted && task != null -> NotificationTarget.Task(task)
        patient != null -> NotificationTarget.Patient(patient)
        else -> NotificationTarget.Inbox
    }
}

/**
 * A row's time (flow 07/08 §4) in the practice zone (Flutter used the phone's): under a minute "Just now", under an
 * hour "{n} min ago", today "14:32", yesterday "Yesterday 09:10", else the day and the time. A time ahead of the clock
 * (a skewed phone) counts as just now.
 */
sealed interface NotificationTime {
    data object JustNow : NotificationTime

    data class MinutesAgo(
        val minutes: Int,
    ) : NotificationTime

    data class Today(
        val time: String,
    ) : NotificationTime

    data class Yesterday(
        val time: String,
    ) : NotificationTime

    data class Day(
        val date: LocalDate,
        val time: String,
    ) : NotificationTime
}

fun notificationTime(
    at: Instant,
    clock: PracticeClock,
): NotificationTime {
    val now = clock.nowInstant()
    val ago = now - at
    val local = clock.local(at)
    val today = clock.local(now).date
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return when {
        ago < 1.minutes -> NotificationTime.JustNow
        ago < 1.hours -> NotificationTime.MinutesAgo(ago.inWholeMinutes.toInt())
        local.date == today -> NotificationTime.Today(time)
        local.date == today.minus(DatePeriod(days = 1)) -> NotificationTime.Yesterday(time)
        else -> NotificationTime.Day(local.date, time)
    }
}

/** The list's sections (flow §3): rows by practice day, in the order they came (newest first). */
fun notificationDays(
    items: List<AppNotification>,
    clock: PracticeClock,
): List<Pair<LocalDate, List<AppNotification>>> = items.groupBy { clock.local(it.createdAt).date }.toList()
