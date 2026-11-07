package shifa.doctor.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.notifications.NotificationKind
import shifa.doctor.utils.PracticeClock

/**
 * `NotificationDto` (dev `/v3/api-docs`, 2026-11-05). `title` and `message` are the backend's English text (in-app rows
 * stay English, `DoctorPushI18n`); the app words a row from [type] and the structured fields instead (07 §2.3).
 */
@Serializable
data class NotificationDto(
    val id: Long? = null,
    val type: String? = null,
    val title: String? = null,
    val message: String? = null,
    val appointmentId: Long? = null,
    val appointmentStartAt: String? = null,
    val patientId: Long? = null,
    val patientName: String? = null,
    val documentId: Long? = null,
    val documentTitle: String? = null,
    val documentAccessRequestId: Long? = null,
    val documentAccessRequestStatus: String? = null,
    val requestingDoctorName: String? = null,
    val taskId: Long? = null,
    val treatmentPlanId: Long? = null,
    val patientFormId: Long? = null,
    // BE-06 (backend 2026-11-07): the conversation of a chat row and the partnership of a care-partnership row.
    val conversationId: Long? = null,
    val carePartnershipId: Long? = null,
    val createdAt: String? = null,
    val readAt: String? = null,
)

/** Spring's `Page` of `GET /notifications/page`: rows read one by one (one bad row doesn't drop the page). */
@Serializable
data class NotificationPageDto(
    val content: List<JsonElement>? = null,
    val last: Boolean = true,
    val number: Int = 0,
)

data class AppNotification(
    val id: Long,
    // The backend's type as sent (upper case), and what the app makes of it.
    val type: String,
    val kind: NotificationKind,
    val title: String,
    val message: String,
    val createdAt: Instant,
    val read: Boolean,
    val appointmentId: Long? = null,
    val appointmentStartAt: Instant? = null,
    val patientId: Long? = null,
    val patientName: String? = null,
    val documentId: Long? = null,
    val documentTitle: String? = null,
    val documentAccessRequestId: Long? = null,
    // The request's state, lower case: pending, approved or rejected (null on older rows: treated as pending).
    val accessStatus: String? = null,
    val requestingDoctorName: String? = null,
    val taskId: Long? = null,
    val conversationId: Long? = null,
    val carePartnershipId: Long? = null,
) {
    companion object {
        /** Null without an id or a time (the row is skipped, not the page). */
        fun from(d: NotificationDto): AppNotification? {
            val type = d.type?.trim()?.uppercase().orEmpty()
            return AppNotification(
                id = d.id ?: return null,
                type = type,
                kind = NotificationKind.of(type),
                title = d.title.orEmpty(),
                message = d.message.orEmpty(),
                createdAt = PracticeClock.parseInstant(d.createdAt) ?: return null,
                read = d.readAt != null,
                appointmentId = d.appointmentId,
                appointmentStartAt = PracticeClock.parseInstant(d.appointmentStartAt),
                patientId = d.patientId,
                patientName = d.patientName?.trim()?.takeIf { it.isNotEmpty() },
                documentId = d.documentId,
                documentTitle = d.documentTitle?.trim()?.takeIf { it.isNotEmpty() },
                documentAccessRequestId = d.documentAccessRequestId,
                accessStatus = d.documentAccessRequestStatus?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                requestingDoctorName = d.requestingDoctorName?.trim()?.takeIf { it.isNotEmpty() },
                taskId = d.taskId,
                conversationId = d.conversationId,
                carePartnershipId = d.carePartnershipId,
            )
        }
    }
}

/** One page, newest first; [last] when there's nothing older. */
data class NotificationPage(
    val items: List<AppNotification>,
    val page: Int,
    val last: Boolean,
)
