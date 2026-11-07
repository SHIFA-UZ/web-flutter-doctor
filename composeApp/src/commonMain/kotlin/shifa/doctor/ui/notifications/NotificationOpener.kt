package shifa.doctor.ui.notifications

import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.notifications.NotificationTarget
import shifa.doctor.data.notifications.target
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appointmentGone
import shifa.doctor.generated.resources.couldNotLoad
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PracticeClock

/**
 * Where a notification leads (flow 07/08 §10, 02/03 §5), shared by the inbox and Home's attention centre. An
 * appointment is found on its practice day in the calendar (its start from the row, else `GET /appointments/{id}`);
 * gone → "This appointment no longer exists", a failed read says so. Null: nothing to open (the banner says why when
 * something went wrong).
 */
class NotificationOpener(
    private val appointments: AppointmentsRepository = AppointmentsRepository(),
    private val calendar: CalendarRepository = calendarRepository,
) {
    /** Whether finding the target means a read first (the row shows a spinner meanwhile). */
    fun slow(
        n: AppNotification,
        staff: Boolean,
    ) = n.target(staff) is NotificationTarget.Appointment

    suspend fun go(
        n: AppNotification,
        staff: Boolean,
        clock: PracticeClock,
    ): NotificationGo? =
        when (val target = n.target(staff)) {
            is NotificationTarget.Appointment -> appointment(target, clock)
            is NotificationTarget.Patient ->
                if (target.openDocument && target.documentId != null) {
                    NotificationGo.Document(target.id, target.documentId, n.documentTitle)
                } else {
                    NotificationGo.Patient(target.id)
                }
            NotificationTarget.Chat -> NotificationGo.Chat
            is NotificationTarget.Conversation -> NotificationGo.Conversation(target.id)
            is NotificationTarget.Task -> NotificationGo.Task(target.id)
            // Without the partnership id (older backends): the list, the newest change on top of its group.
            NotificationTarget.Partnerships -> NotificationGo.Partnerships
            is NotificationTarget.Partnership -> NotificationGo.Partnership(target.id)
            NotificationTarget.Inbox -> null
        }

    private suspend fun appointment(
        target: NotificationTarget.Appointment,
        clock: PracticeClock,
    ): NotificationGo? {
        val found =
            runCatching {
                val start =
                    target.startAt
                        ?: appointments.detail(target.id).getOrThrow().startAt.let(PracticeClock::parseInstant)
                start?.let { at ->
                    calendar
                        .day(DayKey(clock.local(at).date))
                        .getOrThrow()
                        .filterIsInstance<CalendarEntry.Appointment>()
                        .firstOrNull { it.appointmentId == target.id }
                }
            }
        return found.fold(
            { entry ->
                entry?.let { NotificationGo.Appointment(it) }
                    ?: null.also { ShellNotice.pending.value = Notice(Res.string.appointmentGone) }
            },
            { e ->
                // A 404 from the appointment means it's gone; anything else is a failed read.
                ShellNotice.pending.value =
                    if (e is ApiException.NotFound) {
                        Notice(Res.string.appointmentGone)
                    } else {
                        Notice(e.toUserMessage(Res.string.couldNotLoad), requestId = e.requestId)
                    }
                null
            },
        )
    }
}
