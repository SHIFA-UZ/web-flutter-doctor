import 'dart:convert';

class IntelligenceChatMessage {
  const IntelligenceChatMessage({
    required this.role,
    required this.content,
    required this.at,
    this.toolName,
    this.cardPayload,
  });

  final String role; // user | assistant
  final String content;
  final DateTime at;

  /// Morning tool name when this assistant turn has a structured card.
  final String? toolName;

  /// Structured tool payload for restoring action cards.
  final Map<String, dynamic>? cardPayload;

  Map<String, dynamic> toJson() => {
        'role': role,
        'content': content,
        'at': at.toIso8601String(),
        if (toolName != null) 'toolName': toolName,
        if (cardPayload != null) 'cardPayload': cardPayload,
      };

  factory IntelligenceChatMessage.fromJson(Map<String, dynamic> json) {
    Map<String, dynamic>? card;
    final raw = json['cardPayload'];
    if (raw is Map) {
      card = Map<String, dynamic>.from(raw);
    }
    return IntelligenceChatMessage(
      role: (json['role'] as String?) ?? 'user',
      content: (json['content'] as String?) ?? '',
      at: DateTime.tryParse((json['at'] as String?) ?? '') ?? DateTime.now(),
      toolName: json['toolName'] as String?,
      cardPayload: card,
    );
  }
}

class IntelligenceChatSession {
  IntelligenceChatSession({
    required this.id,
    required this.title,
    required this.createdAt,
    required this.updatedAt,
    required this.messages,
  });

  final String id;
  String title;
  final DateTime createdAt;
  DateTime updatedAt;
  List<IntelligenceChatMessage> messages;

  /// Full first user message — used for recent/history list labels.
  String get displayTitle {
    for (final m in messages) {
      if (m.role == 'user' && m.content.trim().isNotEmpty) {
        return m.content.trim();
      }
    }
    final t = title.trim();
    return t.isEmpty ? 'New chat' : t;
  }

  String get preview => displayTitle;

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
        'messages': messages.map((m) => m.toJson()).toList(),
      };

  factory IntelligenceChatSession.fromJson(Map<String, dynamic> json) {
    final rawMessages = (json['messages'] as List? ?? const [])
        .whereType<Map>()
        .map((e) =>
            IntelligenceChatMessage.fromJson(Map<String, dynamic>.from(e)))
        .toList();
    return IntelligenceChatSession(
      id: (json['id'] as String?) ??
          DateTime.now().millisecondsSinceEpoch.toString(),
      title: (json['title'] as String?) ?? 'New chat',
      createdAt: DateTime.tryParse((json['createdAt'] as String?) ?? '') ??
          DateTime.now(),
      updatedAt: DateTime.tryParse((json['updatedAt'] as String?) ?? '') ??
          DateTime.now(),
      messages: rawMessages,
    );
  }

  static String encodeList(List<IntelligenceChatSession> sessions) =>
      jsonEncode(sessions.map((s) => s.toJson()).toList());

  static List<IntelligenceChatSession> decodeList(String raw) {
    try {
      final list = jsonDecode(raw);
      if (list is! List) return [];
      return list
          .whereType<Map>()
          .map((e) => IntelligenceChatSession.fromJson(
              Map<String, dynamic>.from(e)))
          .toList()
        ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    } catch (_) {
      return [];
    }
  }
}
