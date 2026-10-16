/// Non-web stub — browser speech recognition is unavailable.
class WebSpeechConversation {
  WebSpeechConversation({
    required this.onInterim,
    required this.onFinal,
    required this.onError,
    required this.onEnd,
  });

  final void Function(String text) onInterim;
  final void Function(String text) onFinal;
  final void Function(String message) onError;
  final void Function() onEnd;

  static bool get isSupported => false;

  bool get isListening => false;

  Future<bool> start({
    String languageCode = 'en',
    bool continuous = false,
  }) async =>
      false;

  Future<void> stop() async {}

  void dispose() {}
}
