package shifa.doctor.data.notifications

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.model.NotificationDto
import shifa.doctor.data.model.NotificationPage
import shifa.doctor.data.model.NotificationPageDto

/**
 * The inbox (NOTIF-01, 07 §2): one page at a time (Flutter read the whole history, flow 07/08 §12), read one or all.
 * The unread count is the shell's (`BadgesRepository.notificationsUnread`).
 */
class NotificationsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** `GET /notifications/page?page&size`: newest first (the backend's order). */
    suspend fun page(
        page: Int,
        size: Int = PAGE_SIZE,
    ): Result<NotificationPage> =
        apiCall<NotificationPageDto> {
            client().get(ApiConfig.Endpoints.NOTIFICATIONS_PAGE) {
                parameter("page", page)
                parameter("size", size)
            }
        }.map { p ->
            NotificationPage(
                p.content.orEmpty().rows(NotificationDto.serializer(), "notifications", AppNotification::from),
                p.number,
                p.last,
            )
        }

    /** `PUT /notifications/{id}/read`; on success the shell's count is read again ([NotificationReads]). */
    suspend fun markRead(id: Long): Result<Unit> =
        apiCall<Unit> {
            client().put(ApiConfig.Endpoints.notificationRead(id))
        }.onSuccess { NotificationReads.changed.tryEmit(Unit) }

    /** `PUT /notifications/read-all`; then as [markRead]. */
    suspend fun markAllRead(): Result<Unit> =
        apiCall<Unit> {
            client().put(ApiConfig.Endpoints.NOTIFICATIONS_READ_ALL)
        }.onSuccess { NotificationReads.changed.tryEmit(Unit) }

    companion object {
        const val PAGE_SIZE = 20
    }
}

/** Notifications were read on the server: the shell reads its count again (the count is not polled, 07 §2.4). */
object NotificationReads {
    val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}
