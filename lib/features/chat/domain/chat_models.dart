import 'package:shifa_doc_app_v1/core/utils/image_utils.dart';

class ChatContact {
  final String id;
  final String name;
  final String? photoUrl;
  final bool isDoctor;
  final String participantId;
  final String? lastMessage;
  final DateTime? lastActivity;
  final int unread;
  final List<ChatMessage> messages;

  ChatContact({
    required this.id,
    required this.name,
    this.photoUrl,
    required this.isDoctor,
    required this.participantId,
    this.lastMessage,
    this.lastActivity,
    this.unread = 0,
    required this.messages,
  });

  ChatContact copyWith({
    String? name,
    String? photoUrl,
    String? lastMessage,
    DateTime? lastActivity,
    int? unread,
    List<ChatMessage>? messages,
  }) {
    return ChatContact(
      id: id,
      name: name ?? this.name,
      photoUrl: photoUrl ?? this.photoUrl,
      isDoctor: isDoctor,
      participantId: participantId,
      lastMessage: lastMessage ?? this.lastMessage,
      lastActivity: lastActivity ?? this.lastActivity,
      unread: unread ?? this.unread,
      messages: messages ?? this.messages,
    );
  }

  factory ChatContact.fromJson(Map<String, dynamic> json) {
    final rawPhotoUrl = json['participantPhotoUrl'] as String?;
    return ChatContact(
      id: json['id'].toString(),
      name: json['participantName'] as String,
      photoUrl: normalizePhotoUrl(rawPhotoUrl),
      isDoctor: json['isDoctorParticipant'] as bool? ?? false,
      participantId: json['participantId'].toString(),
      lastMessage: json['lastMessage'] as String?,
      lastActivity: json['lastMessageAt'] != null
          ? DateTime.parse(json['lastMessageAt'] as String)
          : null,
      unread: (json['unreadCount'] as int?) ?? 0,
      messages: [],
    );
  }
}

enum MessageType {
  text,
  image,
  voice,
  document,
  system;

  static MessageType? fromString(String? value) {
    if (value == null) return null;
    switch (value.toLowerCase()) {
      case 'text':
        return MessageType.text;
      case 'image':
        return MessageType.image;
      case 'voice':
        return MessageType.voice;
      case 'document':
        return MessageType.document;
      case 'system':
        return MessageType.system;
      default:
        return MessageType.text;
    }
  }
}

enum MessageStatus {
  sending,
  sent,
  delivered,
  read;

  static MessageStatus? fromString(String? value) {
    if (value == null) return MessageStatus.sent;
    switch (value.toLowerCase()) {
      case 'sending':
        return MessageStatus.sending;
      case 'sent':
        return MessageStatus.sent;
      case 'delivered':
        return MessageStatus.delivered;
      case 'read':
        return MessageStatus.read;
      default:
        return MessageStatus.sent;
    }
  }
}

enum SenderRole {
  doctor,
  patient;

  static SenderRole? fromString(String? value) {
    if (value == null) return SenderRole.doctor;
    switch (value.toLowerCase()) {
      case 'doctor':
        return SenderRole.doctor;
      case 'patient':
        return SenderRole.patient;
      default:
        return SenderRole.doctor;
    }
  }
}

class MessageContent {
  final String? text;
  final String? fileUrl;
  final String? thumbnailUrl;
  final String? fileName;
  final int? fileSize;
  final int? duration; // For voice messages in seconds

  MessageContent({
    this.text,
    this.fileUrl,
    this.thumbnailUrl,
    this.fileName,
    this.fileSize,
    this.duration,
  });

  /// [imageUrlFallback] optional URL from message root (e.g. json['imageUrl']) when content has no fileUrl
  factory MessageContent.fromJson(Map<String, dynamic>? json, {String? imageUrlFallback}) {
    if (json == null) return MessageContent(fileUrl: imageUrlFallback);
    // Support both camelCase and snake_case from API
    final fileUrl = json['fileUrl'] as String? ?? json['file_url'] as String? ?? json['imageUrl'] as String? ?? imageUrlFallback;
    final thumbnailUrl = json['thumbnailUrl'] as String? ?? json['thumbnail_url'] as String?;
    return MessageContent(
      text: json['text'] as String?,
      fileUrl: fileUrl,
      thumbnailUrl: thumbnailUrl,
      fileName: json['fileName'] as String? ?? json['file_name'] as String?,
      fileSize: json['fileSize'] != null ? (json['fileSize'] as num).toInt() : (json['file_size'] != null ? (json['file_size'] as num).toInt() : null),
      duration: json['duration'] != null ? (json['duration'] as num).toInt() : null,
    );
  }
}

class ChatMessage {
  final String id;
  final String chatId;
  final String senderId;
  final SenderRole senderRole;
  final MessageType type;
  final MessageContent content;
  final MessageStatus status;
  final DateTime sentAt;
  final bool isMine;
  final bool isRead;

  ChatMessage({
    required this.id,
    required this.chatId,
    required this.senderId,
    required this.senderRole,
    required this.type,
    required this.content,
    required this.status,
    required this.sentAt,
    required this.isMine,
    this.isRead = false,
  });

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    // Support both old and new JSON formats for backward compatibility
    final hasNewFormat = json['content'] != null && json['type'] != null;
    
    if (hasNewFormat) {
      // Fallback: some APIs send imageUrl/attachmentUrl at message root
      final messageLevelUrl = json['imageUrl'] as String? ?? json['attachmentUrl'] as String?;
      return ChatMessage(
        id: json['id'].toString(),
        chatId: json['chatId'].toString(),
        senderId: json['senderId'].toString(),
        senderRole: SenderRole.fromString(json['senderRole'] as String?) ?? SenderRole.doctor,
        type: MessageType.fromString(json['type'] as String?) ?? MessageType.text,
        content: MessageContent.fromJson(
          json['content'] as Map<String, dynamic>?,
          imageUrlFallback: messageLevelUrl,
        ),
        status: MessageStatus.fromString(json['status'] as String?) ?? MessageStatus.sent,
        sentAt: DateTime.parse(json['createdAt'] as String),
        isMine: json['isMine'] as bool? ?? false,
        isRead: json['isRead'] as bool? ?? false,
      );
    } else {
      // Legacy format - convert to new structure
      final attachmentUrl = json['attachmentUrl'] as String?;
      final attachmentName = json['attachmentName'] as String?;
      final text = json['text'] as String?;
      
      // Infer type from attachment
      final messageType = attachmentUrl != null
          ? (attachmentName?.toLowerCase().endsWith('.pdf') == true ||
             attachmentName?.toLowerCase().endsWith('.doc') == true ||
             attachmentName?.toLowerCase().endsWith('.docx') == true
              ? MessageType.document
              : MessageType.image)
          : MessageType.text;

      return ChatMessage(
        id: json['id'].toString(),
        chatId: json['conversationId']?.toString() ?? '0',
        senderId: json['senderId']?.toString() ?? '0',
        senderRole: SenderRole.doctor, // Legacy: assume doctor
        type: messageType,
        content: MessageContent(
          text: text,
          fileUrl: attachmentUrl,
          fileName: attachmentName,
        ),
        status: json['isRead'] == true ? MessageStatus.read : MessageStatus.sent,
        sentAt: DateTime.parse(json['createdAt'] as String),
        isMine: json['isMine'] as bool? ?? false,
        isRead: json['isRead'] as bool? ?? false,
      );
    }
  }
}

class UserSearchResult {
  final String id;
  final String name;
  final String? photoUrl;
  final bool isDoctor;
  final String? email;
  final String? phone;

  UserSearchResult({
    required this.id,
    required this.name,
    this.photoUrl,
    required this.isDoctor,
    this.email,
    this.phone,
  });

  factory UserSearchResult.fromJson(Map<String, dynamic> json) {
    final rawPhotoUrl = json['photoUrl'] as String?;
    return UserSearchResult(
      id: json['id'].toString(),
      name: json['name'] as String,
      photoUrl: normalizePhotoUrl(rawPhotoUrl),
      isDoctor: json['isDoctor'] as bool? ?? false,
      email: json['email'] as String?,
      phone: json['phone'] as String?,
    );
  }
}
