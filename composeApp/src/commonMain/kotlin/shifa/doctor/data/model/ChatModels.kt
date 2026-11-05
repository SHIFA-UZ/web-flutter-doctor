package shifa.doctor.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import shifa.doctor.data.api.absoluteFileUrl
import shifa.doctor.utils.PracticeClock

/** `ConversationDto` (07 §1.1): one row of `GET /messages/conversations`. */
@Serializable
data class ConversationDto(
    val id: Long? = null,
    val isDoctorParticipant: Boolean = false,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val participantId: Long? = null,
    val participantName: String? = null,
    val participantPhotoUrl: String? = null,
    val unreadCount: Int = 0,
)

/** A conversation with a patient or a colleague; [photoUrl] absolute. */
data class Conversation(
    val id: Long,
    val participantId: Long,
    val name: String,
    val photoUrl: String?,
    val isDoctor: Boolean,
    val lastMessage: String?,
    val lastActivity: Instant?,
    val unread: Int,
) {
    companion object {
        /** Null without an id or a participant (the row is skipped, not the list). */
        fun from(
            d: ConversationDto,
            apiBase: String,
        ): Conversation? =
            Conversation(
                id = d.id ?: return null,
                participantId = d.participantId ?: return null,
                name = d.participantName.orEmpty(),
                photoUrl = absoluteFileUrl(d.participantPhotoUrl, apiBase),
                isDoctor = d.isDoctorParticipant,
                lastMessage = d.lastMessage?.takeIf { it.isNotBlank() },
                lastActivity = PracticeClock.parseInstant(d.lastMessageAt),
                unread = d.unreadCount.coerceAtLeast(0),
            )
    }
}

@Serializable
data class MessageContentDto(
    val text: String? = null,
    val fileUrl: String? = null,
    val thumbnailUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val duration: Int? = null,
)

/** `MessageDto` (07 §1.3); the backend sends `type` and `status` in lower case. */
@Serializable
data class MessageDto(
    val id: Long? = null,
    val chatId: Long? = null,
    val senderId: Long? = null,
    val senderRole: String? = null,
    val type: String? = null,
    val content: MessageContentDto? = null,
    val status: String? = null,
    val isMine: Boolean = false,
    val isRead: Boolean = false,
    val createdAt: String? = null,
)

enum class MessageType { Text, Image, Voice, Document, System }

enum class MessageStatus { Sending, Sent, Delivered, Read }

/** One message; [fileUrl] absolute for every attachment type (Flutter skipped voice and documents, bug 16). */
data class Message(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val fromPatient: Boolean,
    val type: MessageType,
    val text: String?,
    val fileUrl: String?,
    val fileName: String?,
    val fileSize: Long?,
    val durationSeconds: Int?,
    val status: MessageStatus,
    val isMine: Boolean,
    val sentAt: Instant,
) {
    companion object {
        /**
         * Null without an id or a readable time. An unknown type with text reads as text, without as a document (the
         * backend's own fallback for an attachment); an unknown status as sent.
         */
        fun from(
            d: MessageDto,
            apiBase: String,
        ): Message? {
            val content = d.content ?: MessageContentDto()
            val fileUrl = absoluteFileUrl(content.fileUrl, apiBase) ?: absoluteFileUrl(content.thumbnailUrl, apiBase)
            val type =
                MessageType.entries.firstOrNull { it.name.equals(d.type, ignoreCase = true) }
                    ?: if (fileUrl == null) MessageType.Text else MessageType.Document
            return Message(
                id = d.id ?: return null,
                chatId = d.chatId ?: 0,
                senderId = d.senderId ?: 0,
                fromPatient = d.senderRole.equals("patient", ignoreCase = true),
                type = type,
                text = content.text,
                fileUrl = fileUrl,
                fileName = content.fileName,
                fileSize = content.fileSize,
                durationSeconds = content.duration,
                status =
                    MessageStatus.entries.firstOrNull { it.name.equals(d.status, ignoreCase = true) }
                        ?: if (d.isRead) MessageStatus.Read else MessageStatus.Sent,
                isMine = d.isMine,
                sentAt = PracticeClock.parseInstant(d.createdAt) ?: return null,
            )
        }
    }
}

/** `UserSearchResultDto` (07 §1.1): someone to start a conversation with. */
@Serializable
data class UserSearchResult(
    val id: Long = 0,
    val name: String = "",
    val isDoctor: Boolean = false,
    val email: String? = null,
    val phone: String? = null,
    val photoUrl: String? = null,
)

/** `POST /messages/send` (07 §1.4): text, or an uploaded attachment with its name, size and (voice) duration. */
@Serializable
data class SendMessageRequest(
    val conversationId: Long,
    val type: String,
    val text: String? = null,
    val attachmentUrl: String? = null,
    val attachmentName: String? = null,
    val fileSize: Long? = null,
    val duration: Int? = null,
)

@Serializable
data class StartConversationRequest(
    val recipientPatientId: Long? = null,
    val recipientDoctorId: Long? = null,
)
