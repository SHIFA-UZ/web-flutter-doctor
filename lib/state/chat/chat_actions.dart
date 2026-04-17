import 'dart:convert';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/features/chat/domain/chat_models.dart';

Future<List<ChatContact>> fetchConversationsWithClient({
  required ApiClient client,
}) async {
  final res = await client.get('/api/messages/conversations');
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final List<dynamic> json = jsonDecode(res.body) as List<dynamic>;
    return json.map((j) => ChatContact.fromJson(j as Map<String, dynamic>)).toList();
  }
  throw Exception('Failed to fetch conversations: ${res.statusCode} ${res.body}');
}

Future<ConversationWithMessages> getConversationWithMessagesWithClient({
  required ApiClient client,
  required String conversationId,
}) async {
  final res = await client.get('/api/messages/conversations/$conversationId');
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> json = jsonDecode(res.body) as Map<String, dynamic>;
    return ConversationWithMessages.fromJson(json);
  }
  throw Exception('Failed to fetch conversation: ${res.statusCode} ${res.body}');
}

/// Start or get a conversation with a recipient without sending any message.
Future<ChatContact> startConversationWithClient({
  required ApiClient client,
  String? recipientDoctorId,
  String? recipientPatientId,
}) async {
  if (recipientDoctorId == null && recipientPatientId == null) {
    throw Exception('Must provide recipientDoctorId or recipientPatientId');
  }
  final body = <String, dynamic>{
    if (recipientDoctorId != null) 'recipientDoctorId': int.parse(recipientDoctorId),
    if (recipientPatientId != null) 'recipientPatientId': int.parse(recipientPatientId),
  };
  final res = await client.post('/api/messages/conversations/start', body);
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> json = jsonDecode(res.body) as Map<String, dynamic>;
    return ChatContact.fromJson(json);
  }
  throw Exception('Failed to start conversation: ${res.statusCode} ${res.body}');
}

Future<ChatMessage> sendMessageWithClient({
  required ApiClient client,
  String? conversationId,
  String? recipientDoctorId,
  String? recipientPatientId,
  String? text,
  String? type, // "text", "image", "voice", "document"
  String? attachmentUrl,
  String? attachmentName,
  String? thumbnailUrl,
  int? fileSize,
  int? duration, // For voice messages in seconds
}) async {
  final body = <String, dynamic>{
    if (conversationId != null) 'conversationId': int.parse(conversationId),
    if (recipientDoctorId != null) 'recipientDoctorId': int.parse(recipientDoctorId),
    if (recipientPatientId != null) 'recipientPatientId': int.parse(recipientPatientId),
    if (text != null) 'text': text,
    if (type != null) 'type': type,
    if (attachmentUrl != null) 'attachmentUrl': attachmentUrl,
    if (attachmentName != null) 'attachmentName': attachmentName,
    if (thumbnailUrl != null) 'thumbnailUrl': thumbnailUrl,
    if (fileSize != null) 'fileSize': fileSize,
    if (duration != null) 'duration': duration,
  };

  final res = await client.post('/api/messages/send', body);
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> json = jsonDecode(res.body) as Map<String, dynamic>;
    return ChatMessage.fromJson(json);
  }
  throw Exception('Failed to send message: ${res.statusCode} ${res.body}');
}

Future<void> markConversationAsReadWithClient({
  required ApiClient client,
  required String conversationId,
}) async {
  final res = await client.post('/api/messages/conversations/$conversationId/read', {});
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw Exception('Failed to mark as read: ${res.statusCode} ${res.body}');
  }
}

Future<List<UserSearchResult>> searchUsersWithClient({
  required ApiClient client,
  required String query,
}) async {
  final res = await client.get('/api/messages/search', params: {'q': query});
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final List<dynamic> json = jsonDecode(res.body) as List<dynamic>;
    return json.map((j) => UserSearchResult.fromJson(j as Map<String, dynamic>)).toList();
  }
  throw Exception('Failed to search users: ${res.statusCode} ${res.body}');
}

Future<int> getUnreadCountWithClient({
  required ApiClient client,
}) async {
  final res = await client.get('/api/messages/unread-count');
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> json = jsonDecode(res.body) as Map<String, dynamic>;
    return (json['count'] as int?) ?? 0;
  }
  throw Exception('Failed to get unread count: ${res.statusCode} ${res.body}');
}

class ConversationWithMessages {
  final ChatContact conversation;
  final List<ChatMessage> messages;

  ConversationWithMessages({
    required this.conversation,
    required this.messages,
  });

  factory ConversationWithMessages.fromJson(Map<String, dynamic> json) {
    return ConversationWithMessages(
      conversation: ChatContact.fromJson(json['conversation'] as Map<String, dynamic>),
      messages: (json['messages'] as List<dynamic>?)
              ?.map((m) => ChatMessage.fromJson(m as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }
}
