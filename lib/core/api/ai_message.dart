class AiMessage {
  final String role; // user | assistant | system
  final String content;

  const AiMessage({
    required this.role,
    required this.content,
  });

  Map<String, dynamic> toJson() => {
        'role': role,
        'content': content,
      };

  factory AiMessage.fromJson(Map<String, dynamic> json) {
    return AiMessage(
      role: (json['role'] ?? '').toString(),
      content: (json['content'] ?? '').toString(),
    );
  }
}
