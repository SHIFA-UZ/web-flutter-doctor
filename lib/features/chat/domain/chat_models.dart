class ChatContact {
  final String id;
  final String name;
  final String lastMessage;
  final DateTime lastActivity;
  final int unread;
  final List<ChatMessage> messages;

  ChatContact({
    required this.id,
    required this.name,
    required this.lastMessage,
    required this.lastActivity,
    this.unread = 0,
    required this.messages,
  });

  ChatContact copyWith({
    String? lastMessage,
    DateTime? lastActivity,
    int? unread,
    List<ChatMessage>? messages,
  }) {
    return ChatContact(
      id: id,
      name: name,
      lastMessage: lastMessage ?? this.lastMessage,
      lastActivity: lastActivity ?? this.lastActivity,
      unread: unread ?? this.unread,
      messages: messages ?? this.messages,
    );
  }
}

class ChatMessage {
  final String id;
  final String text;
  final DateTime sentAt;
  final bool isMine;

  ChatMessage({
    required this.id,
    required this.text,
    required this.sentAt,
    required this.isMine,
  });
}
