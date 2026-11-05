@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.notifications

import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.notifications.NotificationFilter
import shifa.doctor.data.notifications.NotificationKind
import shifa.doctor.ui.theme.PreviewTheme
import shifa.doctor.utils.PracticeClock

private val now = Instant.parse("2026-11-05T09:12:00Z")
private val clock = PracticeClock("Asia/Tashkent", { now }, { TimeZone.of("Asia/Tashkent") })

private fun n(
    id: Long,
    type: String,
    at: String,
    read: Boolean = false,
    name: String? = null,
    appointment: Long? = null,
    start: String? = null,
    message: String = "",
    document: String? = null,
    doctor: String? = null,
) = AppNotification(
    id = id,
    type = type,
    kind = NotificationKind.of(type),
    title = "",
    message = message,
    createdAt = Instant.parse(at),
    read = read,
    appointmentId = appointment,
    appointmentStartAt = start?.let(Instant::parse),
    patientId = 11,
    patientName = name,
    documentTitle = document,
    requestingDoctorName = doctor,
)

private val inbox =
    listOf(
        n(
            1,
            "APPOINTMENT_BOOKED_BY_PATIENT",
            "2026-11-05T09:10:00Z",
            name = "Malika Tursunova",
            appointment = 101,
            start = "2026-11-06T05:00:00Z",
        ),
        n(2, "CHAT_MESSAGE", "2026-11-05T08:30:00Z"),
        n(3, "APPOINTMENT_CANCELLED_BY_PATIENT", "2026-11-05T05:10:00Z", name = "Jasur Karimov", appointment = 102),
        n(
            4,
            "DOCUMENT_ACCESS_REQUEST",
            "2026-11-04T12:00:00Z",
            name = "Dilnoza Rahimova",
            document = "Blood test results",
            doctor = "Dr. med. Sardor Nazarov",
        ),
        n(
            5,
            "TASK_COMPLETED",
            "2026-11-04T07:00:00Z",
            read = true,
            message = "Bekzod Aliyev completed \"Blood pressure diary\".",
        ),
        n(6, "AI_SCRIBE_READY", "2026-11-01T06:00:00Z", read = true, message = "AI scribe notes are ready for review."),
    )

// NOTIF-02: Today (unread: a booking with its actions, a message, a cancellation), Yesterday, an older read one.
@Composable
internal fun NotificationsPreview() =
    PreviewTheme {
        NotificationsContent(NotificationsUiState(inbox), 3, false, clock, NotificationsActions())
    }

// NOTIF-02: the first read.
@Composable
internal fun NotificationsLoadingPreview() =
    PreviewTheme {
        NotificationsContent(NotificationsUiState(), null, false, clock, NotificationsActions())
    }

// NOTIF-02: nothing yet; and the Tasks chip with nothing in it.
@Composable
internal fun NotificationsEmptyPreview() =
    PreviewTheme {
        NotificationsContent(NotificationsUiState(emptyList()), 0, false, clock, NotificationsActions())
    }

@Composable
internal fun NotificationsFilterEmptyPreview() =
    PreviewTheme {
        NotificationsContent(
            NotificationsUiState(inbox.take(3), filter = NotificationFilter.Tasks),
            3,
            false,
            clock,
            NotificationsActions(),
        )
    }

// NOTIF-02: clinic staff (no Tasks chip, no appointment actions), a booking being looked up.
@Composable
internal fun NotificationsStaffPreview() =
    PreviewTheme {
        NotificationsContent(NotificationsUiState(inbox, opening = 1), 120, true, clock, NotificationsActions())
    }
