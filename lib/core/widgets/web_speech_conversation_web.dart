// ignore_for_file: avoid_web_libraries_in_flutter

import 'dart:async';
import 'dart:js_interop';
import 'dart:js_interop_unsafe';

import 'package:web/web.dart' as web;

/// Browser SpeechRecognition for Alexa-style turn-taking on Flutter web.
///
/// Each utterance ends after a natural pause; [onFinal] fires with the transcript.
/// Pass [continuous] for wake-word standby so "Hey Shifa" / "Salom Shifa" is not missed between turns.
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

  web.SpeechRecognition? _recognition;
  bool _listening = false;
  bool _stopping = false;
  /// Ignore onend until this time so Chrome's immediate end-after-start does not
  /// thrash into a restart loop (common after TTS / heavy UI turns).
  DateTime? _ignoreEndUntil;

  static bool get isSupported {
    try {
      return web.window.has('SpeechRecognition') ||
          web.window.has('webkitSpeechRecognition');
    } catch (_) {
      return false;
    }
  }

  bool get isListening => _listening;

  web.SpeechRecognition? _createRecognition() {
    try {
      final ctor = web.window.getProperty('SpeechRecognition'.toJS) ??
          web.window.getProperty('webkitSpeechRecognition'.toJS);
      if (ctor == null) return null;
      return (ctor as JSFunction).callAsConstructor<web.SpeechRecognition>();
    } catch (_) {
      try {
        return web.SpeechRecognition();
      } catch (_) {
        return null;
      }
    }
  }

  String _toBcp47(String languageCode) {
    switch (languageCode.toUpperCase()) {
      case 'RU':
        return 'ru-RU';
      case 'UZ':
        return 'uz-UZ';
      case 'EN':
      default:
        return 'en-US';
    }
  }

  Future<bool> start({
    String languageCode = 'en',
    bool continuous = false,
    bool interimResults = true,
  }) async {
    if (!isSupported) {
      onError('Voice conversation needs Chrome or Edge with mic access.');
      return false;
    }
    await stop();
    // Longer gap after TTS / heavy cards — mic + AudioContext need recovery.
    await Future<void>.delayed(const Duration(milliseconds: 350));
    _stopping = false;

    // Retry a few times: mic/AudioContext often needs a beat after speaking.
    for (var attempt = 0; attempt < 4; attempt++) {
      if (attempt > 0) {
        await Future<void>.delayed(Duration(milliseconds: 280 * attempt));
      }
      final rec = _createRecognition();
      if (rec == null) {
        onError('Speech recognition is not available in this browser.');
        return false;
      }
      _recognition = rec;

      // continuous=false: one utterance per turn (pause commits). Avoids Chrome
      // zombie sessions that report listening but capture nothing after big UI.
      rec.continuous = continuous;
      // Uzbek turns pass false. Chrome's uz-UZ guesses rewrite the same
      // sentence while the doctor is still speaking, then the server
      // transcript replaces that line. Chrome is only the pause detector.
      rec.interimResults = interimResults;
      rec.maxAlternatives = 1;
      rec.lang = _toBcp47(languageCode);

      rec.onstart = ((web.Event _) {
        _listening = true;
        _ignoreEndUntil = DateTime.now().add(const Duration(milliseconds: 900));
      }).toJS;

      rec.onresult = ((web.Event event) {
        try {
          final e = event as web.SpeechRecognitionEvent;
          final results = e.results;
          if (results.length == 0) return;
          final index = e.resultIndex.clamp(0, results.length - 1);
          final result = results.item(index);
          if (result.length == 0) return;
          final transcript = result.item(0).transcript.trim();
          if (transcript.isEmpty) return;
          if (result.isFinal) {
            onFinal(transcript);
          } else {
            onInterim(transcript);
          }
        } catch (_) {}
      }).toJS;

      rec.onerror = ((web.Event event) {
        String code = 'error';
        try {
          code = (event as web.SpeechRecognitionErrorEvent).error;
        } catch (_) {}
        // Transient / expected — do not surface; onend will re-arm.
        if (code == 'aborted' ||
            code == 'no-speech' ||
            code == 'audio-capture') {
          return;
        }
        if (code == 'not-allowed') {
          onError(
            'Microphone permission denied. Allow mic access and try again.',
          );
        } else if (code == 'network') {
          onError('Speech recognition network error. Check your connection.');
        } else {
          onError('Speech recognition error: $code');
        }
      }).toJS;

      rec.onend = ((web.Event _) {
        _listening = false;
        if (_stopping) return;
        final until = _ignoreEndUntil;
        if (until != null && DateTime.now().isBefore(until)) {
          // Chrome often fires end immediately after start (esp. after TTS /
          // heavy cards). Swallow it; host watchdog re-arms if still dead.
          return;
        }
        onEnd();
      }).toJS;

      try {
        // Grace: suppress thrash from instant onend after start.
        _ignoreEndUntil = DateTime.now().add(const Duration(milliseconds: 900));
        rec.start();
        _listening = true;
        return true;
      } catch (_) {
        _listening = false;
        _recognition = null;
        // Retry — often InvalidStateError right after TTS.
      }
    }
    // Soft failure: let the caller retry without killing the loop.
    return false;
  }

  Future<void> stop() async {
    _stopping = true;
    _listening = false;
    _ignoreEndUntil = null;
    final rec = _recognition;
    _recognition = null;
    if (rec == null) return;
    try {
      rec.onstart = null;
      rec.onresult = null;
      rec.onerror = null;
      rec.onend = null;
    } catch (_) {}
    try {
      rec.stop();
    } catch (_) {}
    try {
      rec.abort();
    } catch (_) {}
  }

  void dispose() {
    unawaited(stop());
  }
}
