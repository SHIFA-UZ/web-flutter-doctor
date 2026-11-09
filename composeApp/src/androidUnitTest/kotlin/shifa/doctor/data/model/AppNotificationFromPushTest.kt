package shifa.doctor.data.model

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test
import shifa.doctor.data.notifications.NotificationKind

class AppNotificationFromPushTest {
    private val now = Instant.parse("2026-11-09T10:00:00Z")

    @Test
    fun `a push's data is read as its inbox row`() {
        val n =
            AppNotification.fromPush(
                mapOf(
                    "type" to "appointment_booked_by_patient",
                    "notificationId" to "71",
                    "id" to "70",
                    "appointmentId" to "9",
                    "appointmentStartAt" to "2026-11-10T05:30:00Z",
                    "patientId" to "12",
                    "patientName" to " Anna ",
                    "createdAt" to "2026-11-09T09:59:00Z",
                ),
                now,
            )!!
        assertThat(n.id).isEqualTo(71)
        assertThat(n.kind).isEqualTo(NotificationKind.of("APPOINTMENT_BOOKED_BY_PATIENT"))
        assertThat(n.appointmentId).isEqualTo(9)
        assertThat(n.appointmentStartAt).isEqualTo(Instant.parse("2026-11-10T05:30:00Z"))
        assertThat(n.patientName).isEqualTo("Anna")
        assertThat(n.createdAt).isEqualTo(Instant.parse("2026-11-09T09:59:00Z"))
        assertThat(n.read).isFalse()
    }

    @Test
    fun `older key names still lead somewhere, entityId only on a chat push`() {
        val chat = AppNotification.fromPush(mapOf("type" to "CHAT_MESSAGE", "id" to "5", "entityId" to "42"), now)!!
        assertThat(chat.conversationId).isEqualTo(42)
        assertThat(chat.createdAt).isEqualTo(now) // no time in the push: now
        val task = AppNotification.fromPush(mapOf("type" to "TASK_COMPLETED", "id" to "6", "entityId" to "42"), now)!!
        assertThat(task.conversationId).isNull()
        val partnership =
            AppNotification.fromPush(
                mapOf("type" to "CARE_PARTNERSHIP_INVITE", "id" to "7", "partnershipId" to "3"),
                now,
            )!!
        assertThat(partnership.carePartnershipId).isEqualTo(3)
    }

    @Test
    fun `a push without its row's id is no row`() {
        assertThat(AppNotification.fromPush(mapOf("type" to "GENERAL", "notificationId" to "x"), now)).isNull()
    }
}
