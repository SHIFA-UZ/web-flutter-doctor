import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart'
    show ValueListenable, debugPrint, kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollDirection;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/api/ai_message.dart';
import 'package:shifa_doc_app_v1/core/api/doctor_transcription_api.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_voice_recorder_session.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/widgets/submit_on_enter.dart';
import 'package:shifa_doc_app_v1/core/widgets/web_speech_conversation.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/shifa_ai_orb.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/visit_facts_panel.dart';
import 'package:shifa_doc_app_v1/features/intelligence/domain/intelligence_chat_models.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_action_cards.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_card_text.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_welcome.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';

/// Distinct "talking with Shifa" surface for the morning clinic OS demo.
class HomeVoiceSession extends ConsumerStatefulWidget {
  const HomeVoiceSession({
    super.key,
    this.selectedPatientId,
    this.selectedAppointmentId,
    this.conversationId,
    this.embedded = false,
    this.hideChrome = false,
    this.initialMessages,
    this.onMessagesChanged,
    this.recentSessions = const [],
    this.onOpenSession,
    this.onRenameSession,
    this.onDeleteSession,
    this.onWelcomeVisible,
  });

  final String? selectedPatientId;
  final String? selectedAppointmentId;
  final String? conversationId;

  /// Full-height intelligence mode (Cursor-like chat pane).
  final bool embedded;

  /// When true, omit the in-pane title (parent screen owns the header).
  final bool hideChrome;

  /// Restored chat history when opening a saved session.
  final List<IntelligenceChatMessage>? initialMessages;

  /// Persist turns after each completed exchange.
  final ValueChanged<List<IntelligenceChatMessage>>? onMessagesChanged;

  /// Recent chats for the empty-state welcome (intelligence mode).
  final List<IntelligenceChatSession> recentSessions;

  final ValueChanged<String>? onOpenSession;
  final void Function(String id, String title)? onRenameSession;
  final ValueChanged<String>? onDeleteSession;

  /// True while the empty welcome is showing. False once a thread or voice turn starts.
  final ValueChanged<bool>? onWelcomeVisible;

  @override
  ConsumerState<HomeVoiceSession> createState() => _HomeVoiceSessionState();
}

class _HomeVoiceSessionState extends ConsumerState<HomeVoiceSession> {
  final _session = DoctorVoiceRecorderSession();
  final _player = AudioPlayer();
  final _chatScroll = ScrollController();
  final List<_VoiceTurn> _turns = [];

  /// Latest normalized mic level for the orb. Updated without rebuilding the
  /// conversation list on every sample.
  final ValueNotifier<double> _visualLevel = ValueNotifier<double>(0.12);

  /// Bumped on barge-in / new play so stale complete events cannot end TTS early.
  int _ttsPlayGen = 0;

  WebSpeechConversation? _browserSpeech;

  /// True while a browser utterance is being committed to the AI.
  bool _browserFinalInFlight = false;

  VoiceSessionPhase _phase = VoiceSessionPhase.idle;
  String _liveCaption = '';
  String _streamingReply = '';
  String? _error;
  bool _busy = false;

  /// Hands-free loop: after Shifa speaks, listen again (Alexa / Siri style).
  bool _handsFreeVoice = false;

  /// Soft start failures after TTS (Chrome timing); reset on a successful listen.
  int _handsFreeStartFails = 0;

  /// Idle mic open only for the locale wake phrase (no active conversation).
  bool _wakeStandby = false;

  /// True only after browser speech actually started for wake standby.
  bool _wakeListening = false;
  bool? _reportedWelcome;
  Timer? _wakeStandbyTimer;

  /// Restarts browser speech if Chrome ends the session while UI still says Listening.
  Timer? _listenWatchdogTimer;
  DateTime? _lastSpeechActivityAt;
  DateTime? _lastListenStartAt;
  static const _listenRestartCooldown = Duration(milliseconds: 1400);

  /// Intelligence on web: continuous conversation via browser SpeechRecognition.
  bool get _alexaMode =>
      widget.embedded && kIsWeb && WebSpeechConversation.isSupported;

  /// Reply language for this turn (EN / RU / UZ). Starts from UI locale so
  /// Uzbek wake ("Salom Shifa") is not listened for as English "Hey Shifa".
  String _sessionLanguage = 'EN';

  /// Tap-to-record until tap-to-send. Ignores pauses (dictation / protocoling).
  bool _dictationHold = false;

  /// True while the dictation recording is being transcribed (before the LLM).
  bool _transcribingDictation = false;

  /// Bumped so a stalled morning stream cannot keep "Reviewing…" forever.
  int _askGen = 0;
  StreamSubscription<AiStreamEvent>? _aiSub;

  // End-of-speech (auto-send): after speech is heard, silence commits the turn.
  bool _heardSpeech = false;
  bool _autoFinishing = false;

  /// True once any mic amplitude event arrives (often false on Flutter web).
  bool _gotAmplitude = false;

  /// When levels never move, user must tap stop to send (don't discard).
  bool _manualEndpoint = false;
  DateTime? _listenStartedAt;
  DateTime? _speechStartedAt;
  DateTime? _silenceStartedAt;
  Timer? _maxListenTimer;
  Timer? _handsFreeResumeTimer;
  Timer? _amplitudeProbeTimer;
  Timer? _pulseTimer;

  /// Rolling peak for relative silence (web mic levels vary a lot).
  double _peakLevel = 0.12;

  // Web mic levels are often quiet — keep thresholds reachable.
  static const double _speechLevel = 0.12;
  static const double _silenceLevel = 0.09;
  // A doctor pauses mid-sentence to think; 1.1 s cut those sentences in half.
  // Wait long enough for a natural pause, and never commit a fragment shorter
  // than a few words.
  static const Duration _silenceToSend = Duration(milliseconds: 1900);
  static const Duration _minSpeechBeforeAutoSend = Duration(milliseconds: 700);
  static const Duration _maxListen = Duration(seconds: 45);
  static const Duration _noSpeechCancel = Duration(seconds: 20);

  @override
  void initState() {
    super.initState();
    _sessionLanguage = _uiFallbackLanguage();
    _hydrateFromInitial();
    if (_turns.isNotEmpty) _scrollChatToEnd(force: true);
    if (_alexaMode) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        unawaited(_ensureWakeStandby());
      });
    }
  }

  void _hydrateFromInitial() {
    _turns.clear();
    final msgs = widget.initialMessages ?? const <IntelligenceChatMessage>[];
    for (var i = 0; i < msgs.length;) {
      final m = msgs[i];
      if (m.role == 'user') {
        var assistant = '';
        String? toolName;
        Map<String, dynamic>? card;
        DateTime? assistantAt;
        if (i + 1 < msgs.length && msgs[i + 1].role == 'assistant') {
          final a = msgs[i + 1];
          assistant = a.content;
          toolName = a.toolName;
          card = a.cardPayload;
          assistantAt = a.at;
          i += 2;
        } else {
          i += 1;
        }
        _turns.add(
          _VoiceTurn(
            userText: m.content,
            assistantText: assistant,
            toolName: toolName,
            cardPayload: card,
            userAt: m.at,
            assistantAt: assistantAt,
          ),
        );
      } else {
        i += 1;
      }
    }
  }

  void _reportWelcome(bool visible) {
    if (_reportedWelcome == visible) return;
    _reportedWelcome = visible;
    final cb = widget.onWelcomeVisible;
    if (cb == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _reportedWelcome != visible) return;
      cb(visible);
    });
  }

  Future<void> _refreshAfterConfirmedWorkflow(
    Map<String, dynamic> payload,
  ) async {
    DateTime? appointmentDay;
    final preferred = payload['preferredSlot'];
    final startAt = preferred is Map ? preferred['startAt']?.toString() : null;
    if (startAt != null) {
      appointmentDay = DateTime.tryParse(startAt)?.toLocal();
    }
    ref.invalidate(doctorNotificationsProvider);
    ref.invalidate(doctorNotificationsUnreadCountProvider);
    await invalidateAppointmentRelatedProviders(
      ref,
      appointmentDay: appointmentDay,
    );
  }

  /// Latest patient the doctor already matched in this chat.
  /// Prefers search cards over propose cards so a wrong propose cannot stick.
  int? _focusPatientIdFromCards() {
    final fromSearch = _focusPatientIdFromSearchCards();
    if (fromSearch != null) return fromSearch;
    for (final turn in _turns.reversed) {
      final payload = turn.cardPayload;
      if (payload == null) continue;
      final cardType = (payload['cardType'] as String?) ?? turn.toolName ?? '';
      if (cardType == 'propose_appointment' ||
          cardType == 'propose_reschedule' ||
          cardType == 'patient_appointments' ||
          cardType == 'patient_documents' ||
          cardType == 'document_viewer' ||
          cardType == 'dental_chart' ||
          cardType == 'patient_brief' ||
          turn.toolName == 'propose_appointment' ||
          turn.toolName == 'propose_reschedule' ||
          turn.toolName == 'list_patient_documents' ||
          turn.toolName == 'open_patient_document' ||
          turn.toolName == 'open_dental_chart' ||
          turn.toolName == 'brief_patient') {
        final id = (payload['patientId'] as num?)?.toInt();
        if (id != null) return id;
      }
    }
    return null;
  }

  int? _focusPatientIdFromSearchCards() {
    for (final turn in _turns.reversed) {
      final payload = turn.cardPayload;
      if (payload == null) continue;
      final cardType = (payload['cardType'] as String?) ?? turn.toolName ?? '';
      if (cardType != 'patient_search' && turn.toolName != 'search_patients') {
        continue;
      }
      final matches = payload['matches'];
      if (matches is! List || matches.isEmpty) continue;
      if (matches.length == 1) {
        final row = matches.first;
        if (row is Map) return (row['patientId'] as num?)?.toInt();
        continue;
      }
      final recentUser = [
        ..._turns.reversed.take(4).map((t) => t.userText),
      ].join(' ').toLowerCase();
      for (final raw in matches) {
        if (raw is! Map) continue;
        final name = (raw['patientName'] as String?)?.toLowerCase() ?? '';
        if (name.isEmpty) continue;
        final tokens = name
            .split(RegExp(r'\s+'))
            .where((t) => t.length > 2)
            .toList();
        if (tokens.isNotEmpty && tokens.every(recentUser.contains)) {
          return (raw['patientId'] as num?)?.toInt();
        }
      }
    }
    return null;
  }

  void _emitMessages() {
    final cb = widget.onMessagesChanged;
    if (cb == null) return;
    final out = <IntelligenceChatMessage>[];
    for (final t in _turns) {
      out.add(
        IntelligenceChatMessage(
          role: 'user',
          content: t.userText,
          at: t.userAt,
        ),
      );
      if (t.assistantText.trim().isNotEmpty || t.cardPayload != null) {
        out.add(
          IntelligenceChatMessage(
            role: 'assistant',
            content: t.assistantText,
            at: t.assistantAt ?? t.userAt,
            toolName: t.toolName,
            cardPayload: t.cardPayload,
          ),
        );
      }
    }
    cb(out);
  }

  List<int> _appointmentIds(Map<String, dynamic>? payload) {
    if (payload == null) return const [];
    final ids = <int>[];
    void add(dynamic value) {
      if (value is num) ids.add(value.toInt());
    }

    add(payload['appointmentId']);
    final one = payload['appointment'];
    if (one is Map) add(one['appointmentId']);
    final many = payload['appointments'];
    if (many is List) {
      for (final row in many) {
        if (row is Map) add(row['appointmentId']);
      }
    }
    return ids;
  }

  /// Sends the prior structured query back to the server without putting it in speech.
  String _assistantContentForApi(_VoiceTurn turn) {
    final spoken = turn.assistantText.trim();
    final buffer = StringBuffer(spoken);
    final query = turn.cardPayload?['queryContext'];
    if (query != null) {
      buffer.write(
        '\n<appointment_query>${jsonEncode(query)}</appointment_query>',
      );
    }
    final listed = _appointmentIds(turn.cardPayload);
    if (listed.isNotEmpty) {
      buffer.write(
        '\n<appointment_list>${listed.join(',')}</appointment_list>',
      );
    }
    final action = turn.cardPayload?['actionContext'];
    if (action is Map) {
      final type = action['type']?.toString();
      if (type == 'CANCEL' && action['token'] != null) {
        buffer.write(
          '\n<appointment_action>${action['token']}</appointment_action>',
        );
      }
      if (type == 'FOCUS' && action['appointmentId'] != null) {
        buffer.write(
          '\n<appointment_focus>${action['appointmentId']}</appointment_focus>',
        );
      }
    }
    final text = buffer.toString().trim();
    return text;
  }

  /// Keep the latest message in view unless the doctor has scrolled up.
  bool _followLatest = true;
  int _followFrames = 0;
  bool _followQueued = false;

  bool _onChatScrollNotification(ScrollNotification notification) {
    if (notification is! UserScrollNotification) return false;
    if (notification.direction == ScrollDirection.forward) {
      _followLatest = false;
      _followFrames = 0;
      return false;
    }
    final gap =
        notification.metrics.maxScrollExtent - notification.metrics.pixels;
    if (notification.direction == ScrollDirection.idle && gap < 72) {
      _followLatest = true;
    }
    return false;
  }

  /// Jump to the latest content and keep correcting for a few frames.
  ///
  /// A single [ScrollController.animateTo] aims at the height from one frame.
  /// Streaming text and cards grow after that, so the view stops short of the
  /// end and later updates refuse to follow.
  void _scrollChatToEnd({bool force = false}) {
    if (force) _followLatest = true;
    if (!_followLatest) return;
    _followFrames = 10;
    _queueFollowLatest();
  }

  void _queueFollowLatest() {
    if (_followQueued || !_followLatest || _followFrames <= 0) return;
    _followQueued = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _followQueued = false;
      if (!mounted || !_followLatest) return;
      _followFrames--;
      if (!_chatScroll.hasClients) {
        if (_followFrames > 0) _queueFollowLatest();
        return;
      }
      final position = _chatScroll.position;
      final target = position.maxScrollExtent;
      if (target - position.pixels > 1) {
        position.jumpTo(target);
      }
      if (_followFrames > 0) _queueFollowLatest();
    });
  }

  @override
  void dispose() {
    _maxListenTimer?.cancel();
    _handsFreeResumeTimer?.cancel();
    _wakeStandbyTimer?.cancel();
    _listenWatchdogTimer?.cancel();
    _amplitudeProbeTimer?.cancel();
    _pulseTimer?.cancel();
    _visualLevel.dispose();
    _aiSub?.cancel();
    _browserSpeech?.dispose();
    _chatScroll.dispose();
    _player.dispose();
    unawaited(_session.stop(discard: true));
    super.dispose();
  }

  void _noteSpeechActivity() {
    _lastSpeechActivityAt = DateTime.now();
  }

  void _armListenWatchdog() {
    _listenWatchdogTimer?.cancel();
    if (!_alexaMode) return;
    _listenWatchdogTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (!mounted) return;
      if (!_handsFreeVoice || _busy || _browserFinalInFlight) return;
      if (_phase != VoiceSessionPhase.listening) return;
      final speech = _browserSpeech;
      // Chrome ended recognition but UI still shows Listening — reopen mic.
      if (speech != null && !speech.isListening) {
        _scheduleHandsFreeListen(delayMs: 400);
        return;
      }
      final last = _lastSpeechActivityAt ?? _listenStartedAt;
      // After big cards (dental chart) Chrome can sit "listening" with no audio.
      // Force a hard restart if nothing was heard for a short while.
      if (last != null &&
          DateTime.now().difference(last) > const Duration(seconds: 8) &&
          !_heardSpeech) {
        unawaited(_hardRestartListening());
      }
    });
  }

  /// Stop + delayed start — recovers zombie SpeechRecognition after TTS/cards.
  Future<void> _hardRestartListening() async {
    if (!mounted || !_handsFreeVoice || _busy || _browserFinalInFlight) return;
    final last = _lastListenStartAt;
    if (last != null &&
        DateTime.now().difference(last) < _listenRestartCooldown) {
      return;
    }
    _cancelListenWatchdog();
    try {
      await _browserSpeech?.stop();
    } catch (_) {}
    if (!mounted || !_handsFreeVoice) return;
    setState(() => _phase = VoiceSessionPhase.idle);
    _scheduleHandsFreeListen(delayMs: 500);
  }

  void _cancelListenWatchdog() {
    _listenWatchdogTimer?.cancel();
    _listenWatchdogTimer = null;
  }

  WebSpeechConversation _ensureBrowserSpeech() {
    return _browserSpeech ??= WebSpeechConversation(
      onInterim: (text) {
        if (!mounted) return;
        // Wake standby: show caption only when it looks like the wake phrase.
        if (_wakeStandby && !_handsFreeVoice) {
          if (_containsWakePhrase(text)) {
            _noteSpeechActivity();
            _visualLevel.value = (0.35 + (text.length % 7) * 0.06).clamp(
              0.0,
              1.0,
            );
            setState(() {
              _liveCaption = text;
              _phase = VoiceSessionPhase.listening;
              _heardSpeech = true;
            });
            _scrollChatToEnd();
          }
          return;
        }
        if (_phase != VoiceSessionPhase.listening) return;
        _noteSpeechActivity();
        // Uzbek live guesses are unstable. The caption appears once, from
        // the server transcript, after the doctor pauses.
        if (_recognizesUzbek) return;
        if (text.trim().isNotEmpty) {
          _pulseTimer?.cancel();
        }
        if (text.trim().isNotEmpty) {
          _visualLevel.value = (0.35 + (text.length % 7) * 0.06).clamp(
            0.0,
            1.0,
          );
        }
        setState(() {
          _liveCaption = text;
          _heardSpeech = text.trim().isNotEmpty;
        });
        _scrollChatToEnd();
      },
      onFinal: (text) {
        unawaited(_onBrowserFinalUtterance(text));
      },
      onError: (message) {
        if (!mounted) return;
        final fatal =
            message.toLowerCase().contains('permission') ||
            message.toLowerCase().contains('not-allowed') ||
            message.toLowerCase().contains('denied');
        setState(() {
          _phase = VoiceSessionPhase.idle;
          _busy = false;
          // Only kill loops on real mic permission failures.
          if (fatal) {
            _handsFreeVoice = false;
            _wakeStandby = false;
            _wakeListening = false;
            _surfaceError(message);
          }
        });
        if (fatal) return;
        if (_handsFreeVoice) {
          _scheduleHandsFreeListen(delayMs: 350);
        } else if (_wakeStandby || _alexaMode) {
          _scheduleWakeStandby();
        }
      },
      onEnd: () {
        if (!mounted || _browserFinalInFlight) return;
        // Wake-word mic dropped — reopen without requiring a tap.
        if (_wakeStandby && !_handsFreeVoice && !_busy) {
          _scheduleWakeStandby();
          return;
        }
        // Conversation: Chrome often ends after no-speech while UI still says
        // Listening — must re-arm (previously only restarted when phase==idle).
        if (!_handsFreeVoice || _busy) return;
        if (_phase == VoiceSessionPhase.thinking ||
            _phase == VoiceSessionPhase.speaking) {
          return;
        }
        if (_phase == VoiceSessionPhase.listening ||
            _phase == VoiceSessionPhase.idle) {
          _scheduleHandsFreeListen(delayMs: 280);
        }
      },
    );
  }

  Future<void> _onBrowserFinalUtterance(String text) async {
    final cleaned = text.trim();
    if (cleaned.isEmpty || _browserFinalInFlight) return;
    _browserFinalInFlight = true;
    _noteSpeechActivity();
    _cancelListenWatchdog();
    _handsFreeResumeTimer?.cancel();
    _wakeStandbyTimer?.cancel();
    if (!mounted) {
      _browserFinalInFlight = false;
      return;
    }

    try {
      // Idle wake: "Hey Shifa" / "Salom Shifa" (optionally with a command).
      if (_wakeStandby && !_handsFreeVoice) {
        var spokenRaw = cleaned;
        DoctorTranscriptionResult? alreadyTranscribed;
        if (!_containsWakePhrase(spokenRaw)) {
          alreadyTranscribed = await _tryGeminiWakeRecover(cleaned);
          final recovered = alreadyTranscribed?.text.trim() ?? '';
          if (!_containsWakePhrase(recovered)) {
            return;
          }
          spokenRaw = recovered;
        }
        _wakeStandby = false;
        _wakeListening = false;
        final chromeCommand = IntelligenceChatTitles.stripWakePhrase(spokenRaw);
        await _browserSpeech?.stop();
        if (chromeCommand.isEmpty) {
          // Wake only → enter active listening (no mic tap).
          setState(() {
            _error = null;
            _liveCaption = spokenRaw;
            _sessionLanguage = _uiFallbackLanguage();
          });
          await _beginListening();
          return;
        }
        // Wake + command — prefer Gemini STT so Uzbek is not mangled by Chrome.
        var spoken = chromeCommand;
        var lang = _detectLanguageFromText(spoken) ?? _uiFallbackLanguage();
        final server = alreadyTranscribed ?? await _transcribeCapturedAudio();
        if (server != null && server.text.isNotEmpty) {
          final fromServer = _stripShifaAddress(server.text);
          spoken = fromServer.isNotEmpty ? fromServer : server.text;
          lang = _resolveReplyLanguage(
            transcript: spoken,
            sttLanguage: server.language,
          );
        }
        setState(() {
          _busy = true;
          _handsFreeVoice = true;
          _phase = VoiceSessionPhase.thinking;
          _liveCaption = spoken;
          _sessionLanguage = lang;
          _error = null;
          _manualEndpoint = false;
        });
        await _askMorningAi(spoken, language: lang);
        return;
      }

      if (_recognizesUzbek) {
        await _commitUzbekUtterance();
        return;
      }

      var spoken = cleaned;
      var lang = _detectLanguageFromText(spoken) ?? _sessionLanguage;
      final server = await _transcribeCapturedAudio();
      if (server != null && server.text.isNotEmpty) {
        spoken = server.text;
        lang = _resolveReplyLanguage(
          transcript: spoken,
          sttLanguage: server.language,
        );
      }

      // Active conversation: goodbye phrases end the loop.
      if (_handsFreeVoice && _isConversationSignOff(spoken)) {
        await _endConversationFromSignOff(spoken, language: lang);
        return;
      }

      setState(() {
        _busy = true;
        _phase = VoiceSessionPhase.thinking;
        _liveCaption = spoken;
        _sessionLanguage = lang;
        _error = null;
        _manualEndpoint = false;
      });
      await _browserSpeech?.stop();
      await _askMorningAi(spoken, language: lang);
    } finally {
      _browserFinalInFlight = false;
    }
  }

  bool _containsWakePhrase(String text) =>
      IntelligenceChatTitles.containsWakePhrase(text);

  /// Uzbek UI, or a conversation already detected as Uzbek.
  bool get _recognizesUzbek =>
      _sessionLanguage.toUpperCase() == 'UZ' || _uiFallbackLanguage() == 'UZ';

  /// Chrome is not shown and not saved. One server transcript is committed.
  Future<void> _commitUzbekUtterance() async {
    await _browserSpeech?.stop();
    if (!mounted) return;
    setState(() {
      _busy = true;
      _phase = VoiceSessionPhase.thinking;
      _liveCaption = '';
      _error = null;
      _manualEndpoint = false;
    });
    final server = await _transcribeCapturedAudio();
    if (!mounted) return;
    final spoken = server?.text.trim() ?? '';
    if (spoken.isEmpty) {
      setState(() {
        _busy = false;
        _phase = VoiceSessionPhase.idle;
        _surfaceError(
          _tr(
            'intelNoSpeechShort',
            'No speech detected. Tap the mic and try again.',
          ),
        );
      });
      _scheduleHandsFreeListen();
      return;
    }
    final lang = _resolveReplyLanguage(
      transcript: spoken,
      sttLanguage: server?.language,
    );
    if (_handsFreeVoice && _isConversationSignOff(spoken)) {
      await _endConversationFromSignOff(spoken, language: lang);
      return;
    }
    setState(() {
      _busy = true;
      _phase = VoiceSessionPhase.thinking;
      _liveCaption = spoken;
      _sessionLanguage = lang;
      _error = null;
    });
    await _askMorningAi(spoken, language: lang);
  }

  /// Chrome's English recognizer often misses Uzbek "Salom Shifa". Check Gemini.
  Future<DoctorTranscriptionResult?> _tryGeminiWakeRecover(
    String chromeText,
  ) async {
    if (_uiFallbackLanguage() != 'UZ') return null;
    final words = chromeText
        .trim()
        .split(RegExp(r'\s+'))
        .where((w) => w.isNotEmpty);
    if (words.isEmpty || words.length > 8 || chromeText.trim().length > 120) {
      return null;
    }
    try {
      final result = await _transcribeCapturedAudio().timeout(
        const Duration(seconds: 15),
        onTimeout: () => null,
      );
      if (result == null || !_containsWakePhrase(result.text)) {
        _restartWakeAudioCapture();
        return null;
      }
      return result;
    } catch (_) {
      _restartWakeAudioCapture();
      return null;
    }
  }

  void _restartWakeAudioCapture() {
    if (!mounted || !_wakeStandby || _handsFreeVoice) return;
    unawaited(_session.start(_onMicLevel));
  }

  /// Doctor is closing the chat — stop hands-free listening.
  bool _isConversationSignOff(String text) {
    var t = text.trim().toLowerCase();
    t = t.replaceAll(RegExp(r'[!.?…]+$'), '').trim();
    if (t.isEmpty || t.length > 80) return false;
    // Still asking for clinical work — not a goodbye.
    if (RegExp(
      r'\b(open|show|view|book|schedule|appoint|patient|slot|document|'
      r'diagram|chart|how many|who is|when|reschedule|cancel|'
      r'открой|покажи|запис|пациент|документ|схем|'
      r'och|korsat|ko.rsat|bemor|hujjat)\b',
    ).hasMatch(t)) {
      return false;
    }
    // Strip soft lead-ins: "no that's it thank you" / "yeah thanks"
    t = t
        .replaceFirst(
          RegExp(r'^(no|nope|nah|yeah|yep|yes|ok|okay|alright|all right)[, ]+'),
          '',
        )
        .trim();
    const phrases = <String>[
      "that's it",
      'thats it',
      'that is it',
      "that'll be all",
      'that will be all',
      "we're done",
      'we are done',
      'nothing else',
      'stop listening',
      'goodbye',
      'good bye',
      'bye shifa',
      'bye',
      'thank you',
      'thanks',
      'thanks shifa',
      'thank you shifa',
      "ok that's it",
      "okay that's it",
      "alright that's it",
      'alright thank you',
      'all right thank you',
      'ok thank you',
      'okay thank you',
      "that's it thank you",
      'thats it thank you',
      "that's it thanks",
      'thank you that\'s all',
      'thank you that\'s it',
      'thanks that\'s all',
      'thanks that\'s it',
      'всё',
      'все',
      'всё спасибо',
      'все спасибо',
      'спасибо',
      'спасибо всё',
      'спасибо все',
      'хватит',
      'пока',
      'пока шифа',
      'до свидания',
      'bo\'ldi',
      'boldi',
      'yetadi',
      'rahmat',
      'rahmat shifa',
    ];
    if (phrases.contains(t)) return true;
    if (RegExp(
      r"^(ok|okay|alright|all right)[, ]+(that'?s it|that is it|thanks|thank you|"
      r"we'?re done|done)$",
    ).hasMatch(t)) {
      return true;
    }
    if (RegExp(r"^that'?s it[, ]+(thanks|thank you)$").hasMatch(t)) {
      return true;
    }
    if (RegExp(
      r"^(thanks|thank you)[, ]+(that'?s (it|all)|bye|goodbye|shifa)$",
    ).hasMatch(t)) {
      return true;
    }
    if (RegExp(r'^(bye|goodbye)([, ]+shifa)?$').hasMatch(t)) return true;
    return false;
  }

  Future<void> _endConversationFromSignOff(
    String text, {
    required String language,
  }) async {
    _handsFreeResumeTimer?.cancel();
    _wakeStandbyTimer?.cancel();
    _handsFreeVoice = false;
    _wakeStandby = false;
    _wakeListening = false;
    setState(() {
      _busy = true;
      _phase = VoiceSessionPhase.thinking;
      _liveCaption = text;
      _sessionLanguage = language;
      _error = null;
    });
    await _browserSpeech?.stop();
    final bye = _signOffReply(text, language);
    if (!mounted) return;
    final userAt = DateTime.now();
    setState(() {
      _turns.add(
        _VoiceTurn(
          userText: text,
          assistantText: bye,
          userAt: userAt,
          assistantAt: DateTime.now(),
        ),
      );
      _streamingReply = bye;
    });
    _emitMessages();
    _scrollChatToEnd(force: true);
    await _speakReply(bye, language);
    if (!mounted) return;
    setState(() {
      _busy = false;
      _phase = VoiceSessionPhase.idle;
      _liveCaption = '';
    });
    await _ensureWakeStandby();
  }

  String _signOffReply(String text, String language) {
    final t = text.toLowerCase();
    final lang = language.toUpperCase();
    if (lang == 'RU' || RegExp(r'[а-яё]', caseSensitive: false).hasMatch(t)) {
      if (t.contains('спасибо'))
        return 'Пожалуйста. Скажите «Привет, Шифа», когда понадоблюсь.';
      return 'Хорошо. Скажите «Привет, Шифа», когда понадоблюсь.';
    }
    if (lang == 'UZ') {
      return 'Mayli. Kerak bo‘lsa «Salom Shifa» deb chaqiring.';
    }
    if (t.contains('thank') || t.contains('thanks')) {
      return "You're welcome. Say Hey Shifa when you need me again.";
    }
    return 'Alright. Say Hey Shifa when you need me again.';
  }

  void _scheduleWakeStandby({int delayMs = 700}) {
    if (!mounted || !_alexaMode) return;
    _wakeStandbyTimer?.cancel();
    _wakeStandbyTimer = Timer(Duration(milliseconds: delayMs), () {
      unawaited(_ensureWakeStandby());
    });
  }

  /// Background mic for the locale wake phrase while conversation is closed.
  Future<void> _ensureWakeStandby() async {
    if (!mounted || !_alexaMode) return;
    if (_handsFreeVoice || _busy || _browserFinalInFlight) return;
    if (_phase == VoiceSessionPhase.listening ||
        _phase == VoiceSessionPhase.thinking ||
        _phase == VoiceSessionPhase.speaking) {
      return;
    }
    final canSpeak = ref.read(
      doctorFeatureProvider(DoctorFeature.speechToText),
    );
    if (!canSpeak) return;

    _wakeStandby = true;
    _ttsPlayGen++;
    try {
      await _player.stop();
    } catch (_) {}
    final speech = _ensureBrowserSpeech();
    final ok = await speech.start(
      languageCode: _uiFallbackLanguage(),
      continuous: true,
    );
    if (!mounted) return;
    if (!ok) {
      if (mounted) setState(() => _wakeListening = false);
      _scheduleWakeStandby(delayMs: 1200);
      return;
    }
    // Capture audio in parallel so Uzbek "Salom Shifa" is not lost if Chrome
    // mangles the wake phrase.
    unawaited(_session.start(_onMicLevel));
    if (mounted) {
      setState(() {
        _wakeListening = true;
        _phase = VoiceSessionPhase.idle;
        _busy = false;
      });
    }
  }

  String _tr(String key, String fallback) {
    final l10n = AppLocalizations.of(context);
    if (l10n == null) return fallback;
    return _t(l10n, key, fallback);
  }

  void _surfaceError(String message) {
    _error = message;
    if (!mounted) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final messenger = ScaffoldMessenger.maybeOf(context);
      messenger?.hideCurrentSnackBar();
      messenger?.showSnackBar(
        SnackBar(
          content: Text(message),
          behavior: SnackBarBehavior.floating,
          duration: const Duration(seconds: 4),
        ),
      );
    });
  }

  void _resetVad() {
    _heardSpeech = false;
    _autoFinishing = false;
    _gotAmplitude = false;
    _manualEndpoint = false;
    _listenStartedAt = null;
    _speechStartedAt = null;
    _silenceStartedAt = null;
    _peakLevel = 0.12;
    _maxListenTimer?.cancel();
    _maxListenTimer = null;
    _amplitudeProbeTimer?.cancel();
    _amplitudeProbeTimer = null;
    _pulseTimer?.cancel();
    _pulseTimer = null;
  }

  void _onMicLevel(double level) {
    if (!mounted || _phase != VoiceSessionPhase.listening || _autoFinishing) {
      return;
    }
    _gotAmplitude = true;
    final safe = level.isFinite ? level.clamp(0.0, 1.0) : 0.0;
    // Web often reports a flat noise floor (~0.08). Don't overwrite the
    // dictation pulse with that or the composer looks frozen.
    if (_dictationHold) {
      if (safe > 0.18) {
        _visualLevel.value = math.max(_visualLevel.value, safe);
      }
      return;
    }
    _visualLevel.value = safe;

    // Decay peak so silence is relative to recent speech, not absolute dB.
    _peakLevel = level > _peakLevel
        ? level
        : (_peakLevel * 0.92 + level * 0.08);
    // 0.42 treated soft trailing syllables as silence; keep the gate low so only
    // a real pause counts.
    final relativeSilenceGate = (_peakLevel * 0.30).clamp(_silenceLevel, 0.40);

    final now = DateTime.now();
    if (level >= _speechLevel) {
      final firstSpeech = !_heardSpeech;
      _heardSpeech = true;
      _speechStartedAt ??= now;
      _silenceStartedAt = null;
      if (firstSpeech) setState(() {});
      return;
    }

    // Dictation / Alexa: levels drive the orb only. Alexa commits on browser
    // utterance end; dictation commits when the doctor taps send.
    if (_dictationHold || _alexaMode) return;

    // Without usable levels, never auto-cancel — user taps stop to send.
    if (_manualEndpoint) return;

    if (!_heardSpeech) {
      final started = _listenStartedAt;
      if (started != null && now.difference(started) >= _noSpeechCancel) {
        unawaited(
          _cancelListening(
            reason:
                _tr(
                  'intelNoSpeech',
                  'No speech detected. Tap the mic, speak, then tap stop to send.',
                ),
          ),
        );
      }
      return;
    }

    if (level <= relativeSilenceGate) {
      _silenceStartedAt ??= now;
      final speechFor = _speechStartedAt == null
          ? Duration.zero
          : now.difference(_speechStartedAt!);
      final silentFor = now.difference(_silenceStartedAt!);
      if (speechFor >= _minSpeechBeforeAutoSend &&
          silentFor >= _silenceToSend) {
        unawaited(_autoSendFromSilence());
      }
    } else {
      _silenceStartedAt = null;
    }
  }

  void _enterManualEndpointMode() {
    if (!mounted || _phase != VoiceSessionPhase.listening) return;
    if (_manualEndpoint) return;
    setState(() => _manualEndpoint = true);
    _startListeningPulse(stopWhenHeard: false);
  }

  /// Soft wave while Alexa listening so the UI is not a frozen solid bar.
  void _startListeningPulse({bool stopWhenHeard = true}) {
    _pulseTimer?.cancel();
    _pulseTimer = Timer.periodic(const Duration(milliseconds: 140), (_) {
      if (!mounted || _phase != VoiceSessionPhase.listening) {
        _pulseTimer?.cancel();
        return;
      }
      if (stopWhenHeard && _heardSpeech) {
        _pulseTimer?.cancel();
        return;
      }
      if (!stopWhenHeard && _gotAmplitude && !_dictationHold) {
        _pulseTimer?.cancel();
        return;
      }
      final t = DateTime.now().millisecondsSinceEpoch / 180.0;
      final pulse =
          0.16 + 0.2 * (0.5 + 0.5 * (t % 2 < 1 ? (t % 1) : 1 - (t % 1)));
      if (_dictationHold) {
        // Keep bars moving even when the web mic reports a flat floor.
        final spoken = _visualLevel.value;
        _visualLevel.value = math.max(spoken * 0.82, pulse.clamp(0.18, 0.72));
      } else {
        _visualLevel.value = pulse.clamp(0.12, 0.5);
      }
    });
  }

  Future<void> _autoSendFromSilence() async {
    if (_autoFinishing || _phase != VoiceSessionPhase.listening) return;
    _autoFinishing = true;
    _maxListenTimer?.cancel();
    await _finishListening();
  }

  Future<void> _cancelListening({String? reason}) async {
    if (_phase != VoiceSessionPhase.listening) return;
    _maxListenTimer?.cancel();
    _resetVad();
    await _session.stop(discard: true);
    if (!mounted) return;
    setState(() {
      _phase = VoiceSessionPhase.idle;
      _busy = false;
      _dictationHold = false;
      _transcribingDictation = false;
      if (reason != null) {
        _handsFreeVoice = false;
        _surfaceError(reason);
      }
    });
  }

  String _uiFallbackLanguage() {
    final locale = ref.read(languageProvider).locale;
    switch (locale.languageCode) {
      case 'ru':
        return 'RU';
      case 'uz':
        return 'UZ';
      default:
        return 'EN';
    }
  }

  /// Prefer words in the transcript so Chrome STT "en-US" does not flip Uzbek.
  String _resolveReplyLanguage({
    required String transcript,
    String? sttLanguage,
  }) {
    return IntelligenceChatTitles.detectReplyLanguage(transcript) ??
        _normalizeLangCode(sttLanguage) ??
        _sessionLanguage;
  }

  String? _normalizeLangCode(String? raw) {
    final v = raw?.trim().toLowerCase();
    if (v == null || v.isEmpty) return null;
    if (v.startsWith('en')) return 'EN';
    if (v.startsWith('ru')) return 'RU';
    if (v.startsWith('uz') || v.startsWith('tr') || v.startsWith('az')) {
      return 'UZ';
    }
    return null;
  }

  String? _detectLanguageFromText(String text) {
    return IntelligenceChatTitles.detectReplyLanguage(text);
  }

  Future<void> _toggleListen() async {
    // Tap while Shifa is talking = barge-in (stop TTS, listen).
    if (_phase == VoiceSessionPhase.speaking) {
      _ttsPlayGen++;
      await _player.stop();
      _handsFreeResumeTimer?.cancel();
      setState(() {
        _phase = VoiceSessionPhase.idle;
        _busy = false;
      });
      // Fall through to start listening.
    } else if (_busy && _phase == VoiceSessionPhase.thinking) {
      _askGen++;
      await _aiSub?.cancel();
      if (mounted) {
        setState(() {
          _busy = false;
          _phase = VoiceSessionPhase.idle;
          _transcribingDictation = false;
        });
      }
    } else if (_busy && _phase != VoiceSessionPhase.listening) {
      return;
    }

    final canSpeak = ref.read(
      doctorFeatureProvider(DoctorFeature.speechToText),
    );
    if (!canSpeak) {
      setState(() {
        _surfaceError(
          _tr(
            'intelSpeechRequiresPro',
            'Speech requires Pro. Upgrade to talk with Shifa.',
          ),
        );
      });
      return;
    }

    // Second tap while listening:
    // - Dictation: send the recording (pauses were allowed)
    // - Alexa mode: end the conversation → wake-word standby
    // - Recorder mode: send what was captured
    if (_phase == VoiceSessionPhase.listening) {
      if (_dictationHold) {
        _dictationHold = false;
        await _finishListening();
        return;
      }
      if (_alexaMode) {
        _handsFreeVoice = false;
        _wakeStandby = false;
        _wakeListening = false;
        _handsFreeResumeTimer?.cancel();
        _wakeStandbyTimer?.cancel();
        _cancelListenWatchdog();
        await _browserSpeech?.stop();
        if (mounted) {
          setState(() {
            _phase = VoiceSessionPhase.idle;
            _busy = false;
            _liveCaption = '';
          });
        }
        await _ensureWakeStandby();
        return;
      }
      await _finishListening();
      return;
    }

    await _beginListening(fromUserTap: true);
  }

  /// Click to start recording, click the check to send. Pauses do not cut the turn.
  Future<void> _toggleDictation() async {
    final canSpeak = ref.read(
      doctorFeatureProvider(DoctorFeature.speechToText),
    );
    if (!canSpeak) {
      setState(() {
        _surfaceError(
          _tr(
            'intelSpeechRequiresPro',
            'Speech requires Pro. Upgrade to talk with Shifa.',
          ),
        );
      });
      return;
    }
    if (_phase == VoiceSessionPhase.speaking) {
      _ttsPlayGen++;
      await _player.stop();
      setState(() {
        _phase = VoiceSessionPhase.idle;
        _busy = false;
      });
    }
    if (_busy && _phase == VoiceSessionPhase.thinking) {
      _askGen++;
      await _aiSub?.cancel();
      setState(() {
        _busy = false;
        _phase = VoiceSessionPhase.idle;
        _transcribingDictation = false;
        _surfaceError(_tr('intelStopped', 'Stopped.'));
      });
      return;
    }
    if (_phase == VoiceSessionPhase.listening) {
      _dictationHold = false;
      _handsFreeVoice = false;
      await _browserSpeech?.stop();
      await _finishListening();
      return;
    }
    if (_busy) return;
    await _beginDictation();
  }

  Future<void> _beginDictation() async {
    _handsFreeResumeTimer?.cancel();
    _wakeStandbyTimer?.cancel();
    _cancelListenWatchdog();
    _wakeStandby = false;
    _wakeListening = false;
    _handsFreeVoice = false;
    _dictationHold = true;
    _transcribingDictation = false;
    _browserFinalInFlight = false;
    await _browserSpeech?.stop();
    try {
      await _session.stop(discard: true);
    } catch (_) {}
    await Future<void>.delayed(const Duration(milliseconds: 160));
    _resetVad();
    _listenStartedAt = DateTime.now();
    setState(() {
      _error = null;
      _busy = false;
      _phase = VoiceSessionPhase.listening;
      _liveCaption = '';
      _streamingReply = '';
      _manualEndpoint = true;
    });
    _visualLevel.value = 0.28;
    _startListeningPulse(stopWhenHeard: false);
    final err = await _session.start(_onMicLevel);
    if (err != null) {
      _dictationHold = false;
      if (mounted) {
        setState(() {
          _phase = VoiceSessionPhase.idle;
          _surfaceError(
            err == 'no_permission'
                ? _tr('intelMicPermission', 'Microphone permission is required.')
                : _tr(
                    'intelCouldNotStartRecording',
                    'Could not start recording.',
                  ),
          );
        });
      }
      return;
    }
    _maxListenTimer?.cancel();
    _maxListenTimer = Timer(const Duration(minutes: 10), () {
      if (!mounted || _phase != VoiceSessionPhase.listening) return;
      unawaited(_finishListening());
    });
  }

  Future<void> _beginListening({bool fromUserTap = false}) async {
    if (fromUserTap) {
      _handsFreeStartFails = 0;
      _handsFreeVoice = true;
    }
    final now = DateTime.now();
    final last = _lastListenStartAt;
    if (!fromUserTap &&
        last != null &&
        now.difference(last) < _listenRestartCooldown) {
      final wait = _listenRestartCooldown - now.difference(last);
      _scheduleHandsFreeListen(delayMs: wait.inMilliseconds.clamp(200, 2000));
      return;
    }
    _lastListenStartAt = now;

    _handsFreeResumeTimer?.cancel();
    _wakeStandbyTimer?.cancel();
    _wakeStandby = false;
    _wakeListening = false;
    _handsFreeVoice = true;
    _resetVad();
    _browserFinalInFlight = false;
    _listenStartedAt = DateTime.now();
    _lastSpeechActivityAt = DateTime.now();
    setState(() {
      _error = null;
      _busy = false;
      _phase = VoiceSessionPhase.listening;
      _liveCaption = '';
      _streamingReply = '';
    });
    _visualLevel.value = 0.12;
    _startListeningPulse();

    // Intelligence web: continuous conversation (pause ends the turn).
    if (_alexaMode) {
      // Release playback so the browser mic can reopen after TTS / chart cards.
      _ttsPlayGen++;
      try {
        await _player.stop();
      } catch (_) {}
      await Future<void>.delayed(const Duration(milliseconds: 200));
      if (!mounted || !_handsFreeVoice) return;
      // Start the file recorder first so Gemini STT hears the full utterance.
      // Chrome SpeechRecognition is English-biased and turns Uzbek into junk.
      await _session.start(_onMicLevel);
      if (!mounted || !_handsFreeVoice) {
        await _session.stop(discard: true);
        return;
      }
      final speech = _ensureBrowserSpeech();
      // Conversation turns: one utterance (continuous false). Wake uses continuous.
      final ok = await speech.start(
        languageCode: _sessionLanguage,
        continuous: false,
        interimResults: !_recognizesUzbek,
      );
      if (!ok && mounted) {
        await _session.stop(discard: true);
        _handsFreeStartFails++;
        _cancelListenWatchdog();
        _pulseTimer?.cancel();
        setState(() => _phase = VoiceSessionPhase.idle);
        if (_handsFreeStartFails >= 5) {
          _handsFreeVoice = false;
          _surfaceError(
            _tr(
              'intelCouldNotReopenMic',
              'Could not reopen the microphone. Say Hey Shifa or tap the mic.',
            ),
          );
          _scheduleWakeStandby(delayMs: 900);
        } else {
          // Do not clear _handsFreeVoice — retry shortly (Chrome timing).
          _scheduleHandsFreeListen(delayMs: 1100);
        }
      } else if (ok) {
        _handsFreeStartFails = 0;
        _armListenWatchdog();
      }
      return;
    }

    _maxListenTimer?.cancel();
    _maxListenTimer = Timer(_maxListen, () {
      if (!mounted || _phase != VoiceSessionPhase.listening) return;
      // Always try to send what we captured — don't silently discard.
      unawaited(_finishListening());
    });

    // Flutter web recorder often never emits amplitude; fall back to tap-to-send.
    _amplitudeProbeTimer?.cancel();
    _amplitudeProbeTimer = Timer(const Duration(milliseconds: 900), () {
      if (!mounted || _phase != VoiceSessionPhase.listening) return;
      if (!_gotAmplitude) {
        _enterManualEndpointMode();
      }
    });

    final err = await _session.start(_onMicLevel);
    if (err != null && mounted) {
      _resetVad();
      _handsFreeVoice = false;
      setState(() {
        _phase = VoiceSessionPhase.idle;
        _surfaceError(
          err == 'no_permission'
              ? _tr(
                  'intelMicPermissionRetry',
                  'Microphone permission is required. Allow mic access and try again.',
                )
              : _tr(
                  'intelCouldNotStartMic',
                  'Could not start the microphone. Check browser permissions.',
                ),
        );
      });
    }
  }

  void _scheduleHandsFreeListen({int delayMs = 650}) {
    if (!_handsFreeVoice || !mounted) return;
    // Allow re-arm even when UI still shows listening (dead Chrome session).
    final speechDead =
        _alexaMode &&
        _phase == VoiceSessionPhase.listening &&
        (_browserSpeech == null || !_browserSpeech!.isListening);
    if (_phase == VoiceSessionPhase.listening && !speechDead) return;
    _handsFreeResumeTimer?.cancel();
    _handsFreeResumeTimer = Timer(Duration(milliseconds: delayMs), () {
      if (!mounted || !_handsFreeVoice) return;
      if (_busy || _browserFinalInFlight) return;
      if (_phase == VoiceSessionPhase.thinking ||
          _phase == VoiceSessionPhase.speaking) {
        return;
      }
      final stillDead =
          _alexaMode &&
          (_browserSpeech == null || !_browserSpeech!.isListening);
      if (_phase == VoiceSessionPhase.listening && !stillDead) return;
      unawaited(_beginListening());
    });
  }

  Future<DoctorTranscriptionResult?> _transcribeCapturedAudio() async {
    final path = await _session
        .stop(discard: false)
        .timeout(
          const Duration(seconds: 8),
          onTimeout: () {
            throw TimeoutException('Could not finish the recording.');
          },
        );
    if (path == null || path.isEmpty) return null;
    final bytes = await readVoiceRecordingFileBytes(path).timeout(
      const Duration(seconds: 8),
      onTimeout: () {
        throw TimeoutException('Could not read the recording.');
      },
    );
    if (bytes.isEmpty) return null;
    debugPrint(
      'intelligence_stt stage=UPLOAD recordingId=${_session.recordingId} bytes=${bytes.length} durationSec=${_session.lastRecordingDurationSeconds}',
    );
    final name = voiceRecordingUploadFileName(path, bytes: bytes);
    final stt = await postDoctorTranscriptionDetailed(
      ref: ref,
      fileBytes: bytes,
      fileName: name,
      autoDetectLanguage: true,
      recordingId: _session.recordingId,
    );
    final cleaned = stt.text.trim();
    debugPrint(
      'intelligence_stt stage=TRANSCRIPT_READY recordingId=${_session.recordingId} transcriptChars=${cleaned.length} language=${stt.language ?? ''}',
    );
    if (cleaned.isEmpty || cleaned == kNoSpeechTranscriptPlaceholder) {
      return null;
    }
    return DoctorTranscriptionResult(text: cleaned, language: stt.language);
  }

  Future<void> _finishListening() async {
    _maxListenTimer?.cancel();
    final fromDictation = _dictationHold;
    _dictationHold = false;
    final gen = ++_askGen;
    setState(() {
      _busy = true;
      _phase = VoiceSessionPhase.thinking;
      _transcribingDictation = fromDictation;
    });
    _resetVad();
    try {
      final stt = await _transcribeCapturedAudio();
      if (!mounted || gen != _askGen) return;
      final cleaned = stt?.text.trim() ?? '';
      if (cleaned.isEmpty) {
        setState(() {
          _busy = false;
          _phase = VoiceSessionPhase.idle;
          _transcribingDictation = false;
          _surfaceError(
            fromDictation
                ? _tr(
                    'intelNoSpeechRecording',
                    'No speech in that recording. Tap the record button and try again.',
                  )
                : _tr(
                    'intelNoSpeechShort',
                    'No speech detected. Tap the mic and try again.',
                  ),
          );
        });
        _scheduleHandsFreeListen();
        return;
      }
      final lang = _resolveReplyLanguage(
        transcript: cleaned,
        sttLanguage: stt?.language,
      );
      setState(() {
        _liveCaption = cleaned;
        _sessionLanguage = lang;
        _transcribingDictation = false;
      });
      await _askMorningAi(cleaned, language: lang);
    } catch (e) {
      debugPrint('intelligence_stt_failed $e');
      if (!mounted) return;
      setState(() {
        _busy = false;
        _phase = VoiceSessionPhase.idle;
        _transcribingDictation = false;
        _handsFreeVoice = false;
        _surfaceError(
          e is TimeoutException
              ? _tr(
                  'intelTranscriptionTimeout',
                  'Transcription timed out. Try a shorter recording.',
                )
              : e.toString().contains('permission') ||
                    e.toString().contains('403')
              ? _tr(
                  'intelSpeechProOrDenied',
                  'Speech requires Pro, or mic permission was denied.',
                )
              : _tr(
                  'intelTranscriptionFailed',
                  'Transcription failed. Check your connection and try again.',
                ),
        );
      });
    }
  }

  bool _isDocumentEverythingCommand(String text) {
    final t = text.toLowerCase();
    return t.contains('document everything') ||
        t.contains('hujjatlashtir') ||
        t.contains('задокументир') ||
        t.contains('document all') ||
        t.contains('save everything');
  }

  /// Strip wake phrases so "Salom Shifa, free slots today" becomes the request.
  String _stripShifaAddress(String text) {
    final stripped = IntelligenceChatTitles.stripWakePhrase(text);
    return stripped.isEmpty ? text.trim() : stripped;
  }

  Future<void> _askMorningAi(String userText, {String? language}) async {
    final api = ref.read(aiApiProvider);
    userText = _stripShifaAddress(userText);
    final userAt = DateTime.now();
    final replyLang =
        _detectLanguageFromText(userText) ?? language ?? _sessionLanguage;
    _sessionLanguage = replyLang;
    final patientId = int.tryParse(widget.selectedPatientId ?? '');
    final appointmentId = int.tryParse(widget.selectedAppointmentId ?? '');

    // Intelligence mode uses list_draft_visit_facts + confirm card instead of auto-commit.
    if (!widget.embedded &&
        _isDocumentEverythingCommand(userText) &&
        appointmentId != null) {
      setState(() {
        _phase = VoiceSessionPhase.thinking;
        _error = null;
        _liveCaption = userText;
      });
      try {
        final result = await api.commitVisitFacts(appointmentId: appointmentId);
        ref.invalidate(visitFactsForAppointmentProvider(appointmentId));
        ref.invalidate(
          consultationNotesForAppointmentProvider('$appointmentId'),
        );
        if (patientId != null) {
          ref.invalidate(patientTimelineProvider(patientId));
        }
        final reply =
            'Documented ${result.committedFacts.length} facts'
            '${result.createdTaskIds.isEmpty ? '' : ' and drafted ${result.createdTaskIds.length} medication tasks'}'
            '. They are on the patient timeline.';
        if (!mounted) return;
        final userAt = DateTime.now();
        setState(() {
          _turns.add(
            _VoiceTurn(
              userText: userText,
              assistantText: reply,
              userAt: userAt,
              assistantAt: DateTime.now(),
            ),
          );
          _streamingReply = reply;
          _busy = false;
        });
        _emitMessages();
        _scrollChatToEnd();
        await _speakReply(reply, replyLang);
        if (mounted) setState(() => _phase = VoiceSessionPhase.idle);
      } catch (e) {
        if (!mounted) return;
        setState(() {
          _error = e.toString();
          _phase = VoiceSessionPhase.idle;
          _busy = false;
        });
      }
      return;
    }

    final history = <AiMessage>[
      ..._turns.expand(
        (t) => [
          AiMessage(role: 'user', content: t.userText),
          AiMessage(role: 'assistant', content: _assistantContentForApi(t)),
        ],
      ),
      AiMessage(role: 'user', content: userText),
    ];

    setState(() {
      _streamingReply = '';
      _phase = VoiceSessionPhase.thinking;
      _error = null;
      _transcribingDictation = false;
    });
    _scrollChatToEnd(force: true);

    final buffer = StringBuffer();
    String? toolName;
    Map<String, dynamic>? cardPayload;
    // Pin booking to the patient from the latest search/propose card — not a
    // hallucinated id, and not an unrelated clinic selection when Intelligence
    // already resolved someone in this chat.
    final focusPatientId = _focusPatientIdFromCards() ?? patientId;
    await _aiSub?.cancel();
    final askGen = ++_askGen;
    try {
      await for (final event
          in api
              .streamMorningAi(
                messages: history,
                language: replyLang,
                patientId: focusPatientId,
                consultationId: appointmentId,
                conversationId: widget.conversationId,
              )
              .timeout(const Duration(seconds: 75))) {
        if (!mounted || askGen != _askGen) return;
        if (event is AiToolEvent) {
          final name = event.name;
          toolName = name;
          final payload = Map<String, dynamic>.from(event.payload);
          if (payload['booked'] == true) {
            unawaited(_refreshAfterConfirmedWorkflow(payload));
          }
          cardPayload = payload;
          setState(() {});
          _scrollChatToEnd();
          continue;
        }
        if (event is AiTokenEvent) {
          buffer.write(event.token);
          setState(() {
            _streamingReply = buffer.toString();
            _phase = VoiceSessionPhase.thinking;
          });
          _scrollChatToEnd();
        }
      }
      final reply = buffer.toString().trim();
      if (!mounted) return;
      if (reply.isEmpty && cardPayload == null) {
        setState(() {
          _busy = false;
          _phase = VoiceSessionPhase.idle;
          _error =
              AppLocalizations.of(context)?.translate('intelEmptyReply') ??
              'Shifa had nothing to say. Try again.';
        });
        return;
      }
      // If we already booked from a named time, say that — don't ask to confirm again.
      final spoken = cardPayload?['booked'] == true
          ? (cardPayload?['statusMessage']?.toString() ?? reply)
          : reply.isEmpty
          ? (cardPayload?['spokenHint']?.toString() ?? '')
          : reply;
      setState(() {
        _turns.add(
          _VoiceTurn(
            userText: userText,
            assistantText: spoken,
            toolName: toolName,
            cardPayload: cardPayload,
            userAt: userAt,
            assistantAt: DateTime.now(),
          ),
        );
        _streamingReply = spoken;
      });
      _emitMessages();
      _scrollChatToEnd(force: true);
      if (spoken.isNotEmpty) {
        await _speakReply(spoken, replyLang);
      }
    } on AiStreamException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.userFacingMessage;
        _phase = VoiceSessionPhase.idle;
        _busy = false;
        _transcribingDictation = false;
      });
    } on TimeoutException {
      if (!mounted) return;
      setState(() {
        _error = _tr(
          'intelTookTooLong',
          'Shifa took too long to reply. Try again.',
        );
        _phase = VoiceSessionPhase.idle;
        _busy = false;
        _transcribingDictation = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _phase = VoiceSessionPhase.idle;
        _busy = false;
        _transcribingDictation = false;
      });
    } finally {
      // Always clear busy first, then re-arm mic — scheduling while busy
      // made the timer no-op and killed the Alexa loop after turn 1.
      if (mounted) {
        setState(() {
          _busy = false;
          if (_phase != VoiceSessionPhase.speaking) {
            _phase = VoiceSessionPhase.idle;
          }
        });
        if (_handsFreeVoice &&
            _phase != VoiceSessionPhase.listening &&
            _phase != VoiceSessionPhase.speaking) {
          // Longer after any reply (esp. dental chart / TTS) so mic can reopen.
          _scheduleHandsFreeListen(delayMs: _alexaMode ? 1100 : 450);
        }
      }
    }
  }

  Future<void> _speakReply(String text, String language) async {
    // Show speaking UI immediately — do not wait for OpenAI bytes.
    if (mounted) {
      setState(() {
        _phase = VoiceSessionPhase.speaking;
        _error = null;
      });
    }
    final playGen = ++_ttsPlayGen;
    try {
      final api = ref.read(aiApiProvider);
      final spoken = await api.speak(
        text,
        language: language,
        timeout: const Duration(seconds: 25),
      );
      if (!mounted || playGen != _ttsPlayGen) return;
      if (spoken.bytes.isEmpty) {
        setState(() {
          _phase = VoiceSessionPhase.idle;
        });
        return;
      }
      await _player.stop();
      // Let stop settle so a stale complete does not end the new play early.
      await Future<void>.delayed(const Duration(milliseconds: 60));
      if (!mounted || playGen != _ttsPlayGen) return;
      await _player.setVolume(1.0);
      final data = Uint8List.fromList(spoken.bytes);
      final mime = spoken.mimeType.isEmpty ? 'audio/mpeg' : spoken.mimeType;
      // Prefer data-URI UrlSource first (reliable on Flutter web).
      try {
        final dataUri = Uri.dataFromBytes(data, mimeType: mime);
        await _player.play(UrlSource(dataUri.toString()));
      } catch (_) {
        await _player.play(BytesSource(data));
      }
      if (!mounted || playGen != _ttsPlayGen) return;
      await _awaitTtsPlaybackDone(playGen, text);
      // Only stop if still playing (barge-in already stopped + bumped gen).
      if (playGen == _ttsPlayGen && _player.state == PlayerState.playing) {
        try {
          await _player.stop();
        } catch (_) {}
      }
      if (mounted && playGen == _ttsPlayGen) {
        setState(() => _phase = VoiceSessionPhase.idle);
      }
    } on TimeoutException {
      if (!mounted || playGen != _ttsPlayGen) return;
      setState(() {
        _phase = VoiceSessionPhase.idle;
        _surfaceError(
          _tr(
            'intelVoiceSlow',
            'Voice is slow — caption is above. Keep talking.',
          ),
        );
      });
    } catch (e) {
      if (!mounted || playGen != _ttsPlayGen) return;
      setState(() {
        _phase = VoiceSessionPhase.idle;
        final raw = e.toString();
        // Soft: caption already shows the reply; don't scare with long errors.
        if (raw.contains('502') ||
            raw.contains('Speech synthesis') ||
            raw.contains('TTS_FAILED') ||
            raw.contains('TimeoutException')) {
          _surfaceError(
            _tr('intelVoiceSkipped', 'Voice skipped — caption is above.'),
          );
        }
      });
    }
  }

  /// Wait until this play finishes. Do not trust a lone onPlayerComplete —
  /// on Flutter web it often fires early and a following stop() chops speech.
  Future<void> _awaitTtsPlaybackDone(int playGen, String spokenText) async {
    final estimatedMs = (spokenText.trim().length * 65).clamp(2000, 28000);
    var maxWait = Duration(milliseconds: estimatedMs + 900);
    try {
      final d = await _player.getDuration();
      if (d != null && d.inMilliseconds > 400) {
        maxWait = d + const Duration(milliseconds: 900);
        if (maxWait > const Duration(seconds: 30)) {
          maxWait = const Duration(seconds: 30);
        }
      }
    } catch (_) {}

    final done = Completer<void>();
    void markDone() {
      if (!done.isCompleted) done.complete();
    }

    final stateSub = _player.onPlayerStateChanged.listen((state) {
      if (playGen != _ttsPlayGen) return;
      if (state == PlayerState.completed) markDone();
    });
    final completeSub = _player.onPlayerComplete.listen((_) {
      if (playGen != _ttsPlayGen) return;
      markDone();
    });

    try {
      final deadline = DateTime.now().add(maxWait);
      while (!done.isCompleted && DateTime.now().isBefore(deadline)) {
        if (playGen != _ttsPlayGen) return;
        try {
          if (_player.state == PlayerState.completed) {
            markDone();
            break;
          }
          final dur = await _player.getDuration();
          final pos = await _player.getCurrentPosition();
          if (dur != null &&
              pos != null &&
              dur.inMilliseconds > 400 &&
              pos.inMilliseconds >= dur.inMilliseconds - 250) {
            markDone();
            break;
          }
        } catch (_) {}
        await Future.delayed(const Duration(milliseconds: 120));
      }
      // If complete fired early, keep waiting until position catches duration
      // or estimated speech time — never chop mid-phrase with stop().
      if (playGen != _ttsPlayGen) return;
      try {
        final dur = await _player.getDuration();
        final pos = await _player.getCurrentPosition();
        if (dur != null &&
            pos != null &&
            dur.inMilliseconds > 400 &&
            pos.inMilliseconds < dur.inMilliseconds - 250 &&
            _player.state == PlayerState.playing) {
          final remain = dur - pos + const Duration(milliseconds: 400);
          final cap = remain > const Duration(seconds: 20)
              ? const Duration(seconds: 20)
              : remain;
          final endAt = DateTime.now().add(cap);
          while (DateTime.now().isBefore(endAt) && playGen == _ttsPlayGen) {
            if (_player.state == PlayerState.completed) break;
            final p = await _player.getCurrentPosition();
            final d = await _player.getDuration();
            if (p != null &&
                d != null &&
                p.inMilliseconds >= d.inMilliseconds - 200) {
              break;
            }
            await Future.delayed(const Duration(milliseconds: 120));
          }
        }
      } catch (_) {}
    } finally {
      await stateSub.cancel();
      await completeSub.cancel();
    }
  }

  Future<void> _sendTyped(String text) async {
    final t = text.trim();
    if (t.isEmpty || _busy) return;
    // Typing exits hands-free voice loop; wake standby resumes after.
    _handsFreeVoice = false;
    _wakeStandby = false;
    _wakeListening = false;
    _handsFreeResumeTimer?.cancel();
    _wakeStandbyTimer?.cancel();
    await _browserSpeech?.stop();
    final lang = _detectLanguageFromText(t) ?? _sessionLanguage;
    setState(() {
      _busy = true;
      _liveCaption = t;
      _sessionLanguage = lang;
      _error = null;
    });
    _scrollChatToEnd(force: true);
    await _askMorningAi(t, language: lang);
    if (mounted && _alexaMode && !_handsFreeVoice) {
      _scheduleWakeStandby();
    }
  }

  Future<void> _retryLast() async {
    if (_busy) return;
    final draft = _liveCaption.trim();
    final text = draft.isNotEmpty
        ? draft
        : (_turns.isNotEmpty ? _turns.last.userText : '');
    if (text.isEmpty) return;
    if (draft.isEmpty && _turns.isNotEmpty) {
      _turns.removeLast();
    }
    setState(() {
      _busy = true;
      _liveCaption = text;
      _error = null;
      _streamingReply = '';
    });
    await _askMorningAi(text);
  }

  ({String status, String? subtitle}) _voiceCopy(AppLocalizations l10n) {
    switch (_phase) {
      case VoiceSessionPhase.listening:
        final status = _alexaMode
            ? (_heardSpeech
                  ? l10n.translate('intelBannerGotIt')
                  : l10n.translate('intelBannerListeningNatural'))
            : _manualEndpoint
            ? l10n.translate('intelBannerRecording')
            : _heardSpeech
            ? l10n.translate('intelBannerPauseSend')
            : l10n.translate('intelBannerSpeakNow');
        final subtitle = _alexaMode
            ? l10n.translate('intelBannerAlexaSubtitle')
            : _manualEndpoint
            ? l10n.translate('intelBannerManualSubtitle')
            : _handsFreeVoice
            ? l10n.translate('intelBannerHandsFreeSubtitle')
            : null;
        return (status: status, subtitle: subtitle);
      case VoiceSessionPhase.thinking:
        return (
          status: _transcribingDictation
              ? l10n.translate('transcribing')
              : l10n.translate('intelReviewing'),
          subtitle: _transcribingDictation
              ? l10n.translate('intelSendingRecording')
              : _t(
                  l10n,
                  'voiceThinkingSubtitle',
                  'Shifa is reviewing your request',
                ),
        );
      case VoiceSessionPhase.speaking:
        return (
          status: _t(l10n, 'voiceSpeaking', 'Shifa is speaking…'),
          subtitle: _t(
            l10n,
            'voiceSpeakingSubtitle',
            'Tap the microphone to interrupt',
          ),
        );
      case VoiceSessionPhase.idle:
        return (
          status: _t(l10n, 'talkWithShifa', 'Talk with Shifa'),
          subtitle: null,
        );
    }
  }

  Widget _embeddedVoicePanel(AppLocalizations l10n) {
    final copy = _voiceCopy(l10n);
    final listening = _phase == VoiceSessionPhase.listening;
    return ShifaVoiceOrbPanel(
      phase: _phase,
      intensity: _visualLevel,
      status: copy.status,
      subtitle: copy.subtitle,
      caption: listening && _alexaMode && _liveCaption.trim().isNotEmpty
          ? _liveCaption
          : null,
      onStop: listening ? () => unawaited(_toggleListen()) : null,
      stopLabel: _t(l10n, 'intelVoiceStop', 'Stop'),
      stopSemanticsLabel: l10n.translate('intelStopListening'),
    );
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(languageProvider);
    ref.listen(languageProvider, (previous, next) {
      if (!_alexaMode || _handsFreeVoice || _busy) return;
      if (_wakeStandby || _phase == VoiceSessionPhase.idle) {
        _sessionLanguage = _uiFallbackLanguage();
        _scheduleWakeStandby(delayMs: 250);
      }
    });
    final canUse = ref.watch(doctorFeatureProvider(DoctorFeature.askShifaAi));
    if (!canUse) return const SizedBox.shrink();

    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final phaseLabel = switch (_phase) {
      VoiceSessionPhase.listening => _t(l10n, 'voiceListening', 'Listening…'),
      VoiceSessionPhase.thinking => _t(l10n, 'voiceThinking', 'Thinking…'),
      VoiceSessionPhase.speaking => _t(l10n, 'voiceSpeaking', 'Speaking…'),
      VoiceSessionPhase.idle => _t(l10n, 'talkWithShifa', 'Talk with Shifa'),
    };

    final header = Row(
      children: [
        _PulseOrb(phase: _phase, brand: brand),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                widget.embedded
                    ? _t(l10n, 'shifaIntelligence', 'Shifa Intelligence')
                    : _t(l10n, 'shifaClinicOs', 'Shifa — clinic OS'),
                style: AppDesignSystem.h2(context),
              ),
              const SizedBox(height: 2),
              Text(
                phaseLabel,
                style: AppDesignSystem.body2(
                  context,
                ).copyWith(color: brand, fontWeight: FontWeight.w600),
              ),
            ],
          ),
        ),
      ],
    );

    final legacyControls = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : 280.0;
            final size = (width * 0.55).clamp(160.0, 260.0);
            return Center(
              child: ShifaAiOrb(
                phase: _phase,
                intensity: _visualLevel,
                size: size,
                reduceMotion: MediaQuery.disableAnimationsOf(context),
              ),
            );
          },
        ),
        if (_error != null) ...[
          const SizedBox(height: 8),
          Text(
            _error!,
            style: AppDesignSystem.caption(
              context,
            ).copyWith(color: Colors.red.shade700),
          ),
        ],
        const SizedBox(height: 12),
        _HoldToTalkButton(
          phase: _phase,
          busy: _busy,
          brand: brand,
          onPressed: _toggleListen,
          labelListening: _t(
            l10n,
            'listeningCancel',
            'Listening… pause to send',
          ),
          labelIdle: _t(l10n, 'holdToTalk', 'Tap once, then speak'),
        ),
        if (_phase == VoiceSessionPhase.listening) ...[
          const SizedBox(height: 6),
          Text(
            _heardSpeech
                ? l10n.translate('intelPauseToSend')
                : l10n.translate('intelSpeakNow'),
            textAlign: TextAlign.center,
            style: AppDesignSystem.caption(context).copyWith(color: brand),
          ),
        ],
        const SizedBox(height: 10),
        _TypedFallback(
          enabled: !_busy && _phase != VoiceSessionPhase.listening,
          onSend: _sendTyped,
          hint: _t(l10n, 'orTypeHere', 'Or type: How many appointments today?'),
        ),
        if (widget.selectedAppointmentId != null) ...[
          const SizedBox(height: 16),
          VisitFactsPanel(
            appointmentId: int.tryParse(widget.selectedAppointmentId!),
            patientId: int.tryParse(widget.selectedPatientId ?? ''),
          ),
        ],
      ],
    );

    if (widget.embedded) {
      final showVoiceOrb =
          _phase == VoiceSessionPhase.listening ||
          _phase == VoiceSessionPhase.speaking ||
          (_phase == VoiceSessionPhase.thinking && _handsFreeVoice);
      final inVoiceUi =
          _phase == VoiceSessionPhase.listening ||
          _phase == VoiceSessionPhase.speaking ||
          _phase == VoiceSessionPhase.thinking;
      final showWelcome =
          _turns.isEmpty &&
          _liveCaption.isEmpty &&
          _streamingReply.isEmpty &&
          !_busy &&
          !inVoiceUi;
      _reportWelcome(showWelcome);

      return ColoredBox(
        color: AppDesignSystem.backgroundSecondary,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (!widget.hideChrome) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 16, 24, 10),
                child: header,
              ),
              const Divider(height: 1),
            ],
            Expanded(
              child: Align(
                alignment: Alignment.topCenter,
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    maxWidth: showWelcome ? 720 : 880,
                  ),
                  child: SizedBox(
                    width: double.infinity,
                    height: double.infinity,
                    child: showWelcome
                        ? IntelligenceWelcome(
                            recentSessions: widget.recentSessions,
                            onSendPrompt: _sendTyped,
                            onOpenSession: widget.onOpenSession ?? (_) {},
                            onRenameSession:
                                widget.onRenameSession ?? (_, __) {},
                            onDeleteSession: widget.onDeleteSession ?? (_) {},
                          )
                        : NotificationListener<ScrollNotification>(
                            onNotification: _onChatScrollNotification,
                            child: ListView(
                              controller: _chatScroll,
                              padding: const EdgeInsets.fromLTRB(
                                24,
                                20,
                                24,
                                16,
                              ),
                              children: [
                                for (final turn in _turns) ...[
                                  _UserBubble(
                                    text: turn.userText,
                                    brand: brand,
                                    at: turn.userAt,
                                  ),
                                  const SizedBox(height: 12),
                                  if (turn.assistantText.trim().isNotEmpty ||
                                      turn.cardPayload != null)
                                    _AssistantBlock(
                                      text: turn.assistantText,
                                      toolName: turn.toolName,
                                      cardPayload: turn.cardPayload,
                                      at: turn.assistantAt,
                                    ),
                                  const SizedBox(height: 18),
                                ],
                                if ((_busy ||
                                        _phase ==
                                            VoiceSessionPhase.listening) &&
                                    _liveCaption.isNotEmpty &&
                                    (_turns.isEmpty ||
                                        _turns.last.userText !=
                                            _liveCaption)) ...[
                                  _UserBubble(
                                    text: _liveCaption,
                                    brand: brand,
                                    at: DateTime.now(),
                                  ),
                                  const SizedBox(height: 12),
                                ],
                                if (_phase == VoiceSessionPhase.thinking &&
                                    _streamingReply.isEmpty &&
                                    !showVoiceOrb)
                                  _AssistantBlock(
                                    text: _transcribingDictation
                                        ? l10n.translate('transcribing')
                                        : _t(
                                            l10n,
                                            'intelReviewing',
                                            'Reviewing…',
                                          ),
                                    toolName: null,
                                    cardPayload: null,
                                    pending: true,
                                  ),
                                if (_streamingReply.isNotEmpty &&
                                    (_turns.isEmpty ||
                                        _turns.last.assistantText !=
                                            _streamingReply))
                                  _AssistantBlock(
                                    text: _streamingReply,
                                    toolName: null,
                                    cardPayload: null,
                                  ),
                                if (showVoiceOrb)
                                  Padding(
                                    padding: const EdgeInsets.symmetric(
                                      vertical: 4,
                                    ),
                                    child: _embeddedVoicePanel(l10n),
                                  ),
                                if (_error != null) ...[
                                  const SizedBox(height: 8),
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          _error!,
                                          style:
                                              AppDesignSystem.caption(
                                                context,
                                              ).copyWith(
                                                color: Colors.red.shade700,
                                              ),
                                        ),
                                      ),
                                      TextButton(
                                        onPressed: _busy ? null : _retryLast,
                                        child: Text(l10n.translate('retry')),
                                      ),
                                    ],
                                  ),
                                ],
                              ],
                            ),
                          ),
                  ),
                ),
              ),
            ),
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 14),
              child: Align(
                alignment: Alignment.topCenter,
                heightFactor: 1,
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    maxWidth: showWelcome ? 720 : 880,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (!showWelcome) _PatientContextLine(turns: _turns),
                      _IntelligenceComposer(
                        phase: _phase,
                        busy: _busy,
                        brand: brand,
                        heardSpeech: _heardSpeech,
                        error: _error,
                        handsFree: _handsFreeVoice,
                        wakeStandby: _wakeListening,
                        manualEndpoint: _manualEndpoint,
                        alexaMode: _alexaMode,
                        dictationHold: _dictationHold,
                        transcribing: _transcribingDictation,
                        dictationLevel: _visualLevel,
                        onMic: () => unawaited(_toggleListen()),
                        onDictation: () => unawaited(_toggleDictation()),
                        onSend: _sendTyped,
                        hint: _t(l10n, 'intelComposerHint', 'Ask Shifa…'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Align(
                alignment: Alignment.topCenter,
                heightFactor: 1,
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    maxWidth: showWelcome ? 720 : 880,
                  ),
                  child: Text(
                    _t(
                      l10n,
                      'intelDisclaimer',
                      'AI-generated responses may require review.',
                    ),
                    textAlign: TextAlign.center,
                    style: AppDesignSystem.caption(
                      context,
                    ).copyWith(color: AppDesignSystem.textTertiary),
                  ),
                ),
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppDesignSystem.cardRadius),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            brand.withValues(alpha: 0.12),
            AppDesignSystem.primaryAi.withValues(alpha: 0.18),
            Colors.white,
          ],
        ),
        border: Border.all(
          color: AppDesignSystem.primaryAi.withValues(alpha: 0.35),
          width: 1.4,
        ),
        boxShadow: [
          BoxShadow(
            color: AppDesignSystem.primaryAi.withValues(alpha: 0.12),
            blurRadius: 24,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(AppDesignSystem.cardPadding),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            header,
            const SizedBox(height: 16),
            if (_liveCaption.isNotEmpty) ...[
              _CaptionBubble(
                label: _t(l10n, 'youSaid', 'You said'),
                text: _liveCaption,
                alignEnd: true,
                brand: brand,
              ),
              const SizedBox(height: 8),
            ],
            if (_streamingReply.isNotEmpty || _turns.isNotEmpty) ...[
              _CaptionBubble(
                label: _t(l10n, 'shifaSaid', 'Shifa'),
                text: localizeIntelligenceHint(
                  l10n,
                  _streamingReply.isNotEmpty
                      ? _streamingReply
                      : _turns.last.assistantText,
                ),
                alignEnd: false,
                brand: AppDesignSystem.primaryAi,
              ),
              const SizedBox(height: 8),
            ],
            legacyControls,
          ],
        ),
      ),
    );
  }
}

String _t(AppLocalizations l10n, String key, String fallback) {
  final v = l10n.translate(key);
  return v == key ? fallback : v;
}

class _VoiceTurn {
  _VoiceTurn({
    required this.userText,
    required this.assistantText,
    required this.userAt,
    this.assistantAt,
    this.toolName,
    this.cardPayload,
  });
  final String userText;
  final String assistantText;
  final DateTime userAt;
  final DateTime? assistantAt;
  final String? toolName;
  final Map<String, dynamic>? cardPayload;
}

String _formatChatTimestamp(DateTime at, AppLocalizations l10n) {
  return intelligenceChatTimestamp(l10n, at);
}

class _UserBubble extends StatelessWidget {
  const _UserBubble({required this.text, required this.brand, this.at});
  final String text;
  final Color brand;
  final DateTime? at;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final maxWidth = constraints.maxWidth.isFinite
            ? constraints.maxWidth * 0.78
            : 560.0;
        return Align(
          alignment: Alignment.centerRight,
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: maxWidth),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                if (at != null) ...[
                  Text(
                    _formatChatTimestamp(at!, AppLocalizations.of(context)!),
                    style: AppDesignSystem.caption(context).copyWith(
                      color: AppDesignSystem.textTertiary,
                      fontSize: 11,
                    ),
                  ),
                  const SizedBox(height: 4),
                ],
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 10,
                  ),
                  decoration: BoxDecoration(
                    color: brand.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Text(text, style: AppDesignSystem.body2(context)),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _AssistantBlock extends StatelessWidget {
  const _AssistantBlock({
    required this.text,
    required this.toolName,
    required this.cardPayload,
    this.at,
    this.pending = false,
  });

  final String text;
  final String? toolName;
  final Map<String, dynamic>? cardPayload;
  final DateTime? at;
  final bool pending;

  String _assistantLine(AppLocalizations l10n) {
    final localized = localizeIntelligenceHint(l10n, text);
    if (!intelligenceToolLineStillEnglish(localized)) return localized;
    final payload = cardPayload;
    if (payload == null) return localized;
    final status = payload['statusMessage']?.toString() ?? '';
    if (status.isNotEmpty) {
      final fromStatus = localizeIntelligenceHint(l10n, status);
      if (!intelligenceToolLineStillEnglish(fromStatus)) return fromStatus;
    }
    final hint = payload['spokenHint']?.toString() ?? '';
    if (hint.isNotEmpty && hint != text) {
      final fromHint = localizeIntelligenceHint(l10n, hint);
      if (!intelligenceToolLineStillEnglish(fromHint)) return fromHint;
    }
    final name = payload['patientName']?.toString() ?? '';
    final slot = payload['preferredSlot'];
    final time = slot is Map ? slot['startLocal']?.toString() ?? '' : '';
    final when =
        payload['speakableDateLabel']?.toString() ??
        payload['dateLabel']?.toString() ??
        '';
    if (name.isEmpty || time.isEmpty) return localized;
    final cardType = (payload['cardType'] as String?) ?? toolName ?? '';
    final onWhen = when.isEmpty ? '' : ' on $when';
    final canonical = cardType == 'propose_reschedule'
        ? 'Moved $name to $time$onWhen.'
        : 'Booked $name at $time$onWhen.';
    final rebuilt = localizeIntelligenceHint(l10n, canonical);
    return intelligenceToolLineStillEnglish(rebuilt) ? localized : rebuilt;
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final maxWidth = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : 880.0;
        return Align(
          alignment: Alignment.centerLeft,
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: maxWidth),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (at != null && !pending) ...[
                  Text(
                    _formatChatTimestamp(at!, AppLocalizations.of(context)!),
                    style: AppDesignSystem.caption(context).copyWith(
                      color: AppDesignSystem.textTertiary,
                      fontSize: 11,
                    ),
                  ),
                  const SizedBox(height: 4),
                ],
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      Icons.auto_awesome,
                      size: 16,
                      color: AppDesignSystem.primaryAi.withValues(alpha: 0.9),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _assistantLine(
                          AppLocalizations.of(context)!,
                        ),
                        style: AppDesignSystem.body2(context).copyWith(
                          height: 1.45,
                          fontStyle: pending
                              ? FontStyle.italic
                              : FontStyle.normal,
                          color: pending
                              ? AppDesignSystem.textSecondary
                              : AppDesignSystem.textPrimary,
                        ),
                      ),
                    ),
                  ],
                ),
                if (cardPayload != null && toolName != null)
                  IntelligenceActionCard(
                    toolName: toolName!,
                    payload: cardPayload!,
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _IntelligenceComposer extends StatefulWidget {
  const _IntelligenceComposer({
    required this.phase,
    required this.busy,
    required this.brand,
    required this.heardSpeech,
    required this.onMic,
    required this.onDictation,
    required this.onSend,
    required this.hint,
    this.error,
    this.handsFree = false,
    this.wakeStandby = false,
    this.manualEndpoint = false,
    this.alexaMode = false,
    this.dictationHold = false,
    this.transcribing = false,
    this.dictationLevel,
  });

  final VoiceSessionPhase phase;
  final bool busy;
  final Color brand;
  final bool heardSpeech;
  final VoidCallback onMic;
  final VoidCallback onDictation;
  final ValueChanged<String> onSend;
  final String hint;
  final String? error;
  final bool handsFree;
  final bool wakeStandby;
  final bool manualEndpoint;
  final bool alexaMode;
  final bool dictationHold;
  final bool transcribing;
  final ValueListenable<double>? dictationLevel;

  @override
  State<_IntelligenceComposer> createState() => _IntelligenceComposerState();
}

class _IntelligenceComposerState extends State<_IntelligenceComposer> {
  final _controller = TextEditingController();
  final _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onDraftChanged);
  }

  void _onDraftChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _controller.removeListener(_onDraftChanged);
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _submit() {
    if (widget.busy || widget.phase == VoiceSessionPhase.listening) return;
    final t = _controller.text.trim();
    if (t.isEmpty) return;
    _controller.clear();
    widget.onSend(t);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final listening = widget.phase == VoiceSessionPhase.listening;
    final speaking = widget.phase == VoiceSessionPhase.speaking;
    final wake =
        widget.alexaMode &&
        widget.wakeStandby &&
        widget.phase == VoiceSessionPhase.idle;
    final canSend =
        _controller.text.trim().isNotEmpty &&
        !widget.busy &&
        !listening &&
        !speaking;
    // Allow mic while idle, listening (stop), or speaking (barge-in).
    final micEnabled = !widget.busy || listening || speaking;
    final borderColor = listening
        ? widget.brand.withValues(alpha: 0.7)
        : wake
        ? widget.brand.withValues(alpha: 0.45)
        : AppDesignSystem.border;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.error != null && widget.phase == VoiceSessionPhase.idle) ...[
          Text(
            widget.error!,
            style: AppDesignSystem.caption(
              context,
            ).copyWith(color: Colors.red.shade700),
          ),
          const SizedBox(height: 8),
        ],
        if (widget.transcribing) ...[
          Text(
            l10n.translate('intelTranscribingRecording'),
            textAlign: TextAlign.center,
            style: AppDesignSystem.caption(
              context,
            ).copyWith(color: widget.brand, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
        ] else if (listening && widget.dictationHold) ...[
          Text(
            l10n.translate('intelRecordingHold'),
            textAlign: TextAlign.center,
            style: AppDesignSystem.caption(
              context,
            ).copyWith(color: widget.brand, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
        ],
        Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: widget.dictationHold ? widget.brand : borderColor,
              width: listening || wake || widget.dictationHold ? 1.6 : 1,
            ),
          ),
          padding: const EdgeInsets.fromLTRB(12, 4, 4, 4),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: widget.dictationHold
                    ? Padding(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        child: _ComposerDictationWave(
                          brand: widget.brand,
                          level: widget.dictationLevel,
                        ),
                      )
                    : TextField(
                        controller: _controller,
                        focusNode: _focus,
                        enabled: !widget.busy && !listening && !speaking,
                        minLines: 1,
                        maxLines: 4,
                        textInputAction: TextInputAction.send,
                        onSubmitted: submitOnEnter(canSend ? _submit : null),
                        decoration: InputDecoration(
                          hintText: listening
                              ? l10n.translate('intelComposerListening')
                              : speaking
                              ? l10n.translate('intelComposerSpeaking')
                              : widget.transcribing
                              ? l10n.translate('transcribing')
                              : widget.hint,
                          border: InputBorder.none,
                          isDense: true,
                        ),
                      ),
              ),
              IconButton(
                tooltip: widget.dictationHold
                    ? l10n.translate('intelTapToSendRecording')
                    : l10n.translate('intelDictateHint'),
                style: IconButton.styleFrom(minimumSize: const Size(40, 40)),
                onPressed: (!widget.busy || listening || widget.transcribing)
                    ? widget.onDictation
                    : null,
                icon: widget.transcribing
                    ? SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: widget.brand,
                        ),
                      )
                    : Icon(
                        widget.dictationHold
                            ? Icons.check_circle
                            : Icons.fiber_manual_record_outlined,
                        color: widget.dictationHold
                            ? Colors.green.shade700
                            : widget.brand,
                        size: widget.dictationHold ? 26 : 22,
                      ),
              ),
              IconButton(
                tooltip: listening
                    ? l10n.translate('intelStopListening')
                    : speaking
                    ? l10n.translate('intelInterruptSpeak')
                    : wake
                    ? l10n.translate('intelMicWake')
                    : l10n.translate('intelMicTalk'),
                style: IconButton.styleFrom(minimumSize: const Size(40, 40)),
                onPressed: micEnabled ? widget.onMic : null,
                icon: Icon(
                  listening
                      ? Icons.stop_circle_outlined
                      : speaking
                      ? Icons.mic
                      : wake
                      ? Icons.hearing
                      : Icons.mic_none,
                  color: listening ? Colors.red.shade600 : widget.brand,
                  size: listening || speaking ? 26 : 22,
                ),
              ),
              IconButton(
                tooltip: l10n.translate('send'),
                style: IconButton.styleFrom(minimumSize: const Size(40, 40)),
                onPressed: canSend ? _submit : null,
                icon: Icon(
                  Icons.send_rounded,
                  color: canSend
                      ? widget.brand
                      : widget.brand.withValues(alpha: 0.35),
                ),
              ),
            ],
          ),
        ),
        if (widget.handsFree && widget.phase == VoiceSessionPhase.idle) ...[
          const SizedBox(height: 6),
          Text(
            l10n.translate('intelHandsFreeOn'),
            textAlign: TextAlign.center,
            style: AppDesignSystem.caption(
              context,
            ).copyWith(color: AppDesignSystem.textSecondary),
          ),
        ] else if (wake) ...[
          const SizedBox(height: 6),
          Text(
            l10n.translate('intelListeningForWake'),
            textAlign: TextAlign.center,
            style: AppDesignSystem.caption(
              context,
            ).copyWith(color: widget.brand, fontWeight: FontWeight.w600),
          ),
        ],
      ],
    );
  }
}

class _PatientContextLine extends StatelessWidget {
  const _PatientContextLine({required this.turns});

  final List<_VoiceTurn> turns;

  @override
  Widget build(BuildContext context) {
    final mark = IntelligenceChatContext.summarize(
      turns.map((t) => (payload: t.cardPayload, toolName: t.toolName)),
    );
    if (!mark.linked) return const SizedBox.shrink();
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final name = mark.name;
    final label = name == null
        ? l10n.translate('intelPatient')
        : l10n.translate('intelContextPatient').replaceAll('{name}', name);
    return Semantics(
      label: label,
      child: Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(
          children: [
            Icon(Icons.person_outline, size: 16, color: brand),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: AppDesignSystem.caption(context).copyWith(
                  color: AppDesignSystem.textSecondary,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PulseOrb extends StatelessWidget {
  const _PulseOrb({required this.phase, required this.brand});
  final VoiceSessionPhase phase;
  final Color brand;

  @override
  Widget build(BuildContext context) {
    final active = phase != VoiceSessionPhase.idle;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 280),
      width: 52,
      height: 52,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: LinearGradient(
          colors: active
              ? [brand, AppDesignSystem.primaryAi]
              : [
                  brand.withValues(alpha: 0.7),
                  AppDesignSystem.primaryAi.withValues(alpha: 0.7),
                ],
        ),
        boxShadow: active
            ? [
                BoxShadow(
                  color: brand.withValues(alpha: 0.45),
                  blurRadius: 18,
                  spreadRadius: 2,
                ),
              ]
            : null,
      ),
      child: Icon(switch (phase) {
        VoiceSessionPhase.listening => Icons.mic,
        VoiceSessionPhase.thinking => Icons.auto_awesome,
        VoiceSessionPhase.speaking => Icons.graphic_eq,
        VoiceSessionPhase.idle => Icons.record_voice_over,
      }, color: Colors.white),
    );
  }
}

class _CaptionBubble extends StatelessWidget {
  const _CaptionBubble({
    required this.label,
    required this.text,
    required this.alignEnd,
    required this.brand,
  });

  final String label;
  final String text;
  final bool alignEnd;
  final Color brand;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: alignEnd ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * 0.85,
        ),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            color: alignEnd
                ? brand.withValues(alpha: 0.1)
                : Colors.white.withValues(alpha: 0.92),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: brand.withValues(alpha: 0.25)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label.toUpperCase(),
                style: AppDesignSystem.caption(context).copyWith(
                  color: brand,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.6,
                ),
              ),
              const SizedBox(height: 4),
              Text(text, style: AppDesignSystem.body1(context)),
            ],
          ),
        ),
      ),
    );
  }
}

class _HoldToTalkButton extends StatelessWidget {
  const _HoldToTalkButton({
    required this.phase,
    required this.busy,
    required this.brand,
    required this.onPressed,
    required this.labelListening,
    required this.labelIdle,
  });

  final VoiceSessionPhase phase;
  final bool busy;
  final Color brand;
  final VoidCallback onPressed;
  final String labelListening;
  final String labelIdle;

  @override
  Widget build(BuildContext context) {
    final listening = phase == VoiceSessionPhase.listening;
    return SizedBox(
      height: 52,
      child: ElevatedButton.icon(
        onPressed: busy && !listening ? null : onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: listening ? Colors.red.shade600 : brand,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(28),
          ),
          elevation: listening ? 6 : 2,
        ),
        icon: Icon(listening ? Icons.stop_circle_outlined : Icons.mic),
        label: Text(
          listening ? labelListening : labelIdle,
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
    );
  }
}

class _TypedFallback extends StatefulWidget {
  const _TypedFallback({
    required this.enabled,
    required this.onSend,
    required this.hint,
  });

  final bool enabled;
  final ValueChanged<String> onSend;
  final String hint;

  @override
  State<_TypedFallback> createState() => _TypedFallbackState();
}

class _TypedFallbackState extends State<_TypedFallback> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: TextField(
            controller: _controller,
            enabled: widget.enabled,
            decoration: InputDecoration(
              hintText: widget.hint,
              isDense: true,
              filled: true,
              fillColor: Colors.white.withValues(alpha: 0.85),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
                borderSide: BorderSide(
                  color: AppDesignSystem.border.withValues(alpha: 0.6),
                ),
              ),
            ),
            onSubmitted: (v) {
              widget.onSend(v);
              _controller.clear();
            },
          ),
        ),
        const SizedBox(width: 8),
        ShifaSecondaryButton(
          label: 'Send',
          onPressed: !widget.enabled
              ? null
              : () {
                  final t = _controller.text;
                  _controller.clear();
                  widget.onSend(t);
                },
        ),
      ],
    );
  }
}

/// Live bars in the composer while hold-to-dictate is recording.
class _ComposerDictationWave extends StatefulWidget {
  const _ComposerDictationWave({required this.brand, required this.level});

  final Color brand;
  final ValueListenable<double>? level;

  @override
  State<_ComposerDictationWave> createState() => _ComposerDictationWaveState();
}

class _ComposerDictationWaveState extends State<_ComposerDictationWave> {
  static const _bars = 22;
  late List<double> _levels;

  @override
  void initState() {
    super.initState();
    _levels = List<double>.filled(_bars, 0.22);
    widget.level?.addListener(_onLevel);
  }

  @override
  void didUpdateWidget(covariant _ComposerDictationWave oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.level != widget.level) {
      oldWidget.level?.removeListener(_onLevel);
      widget.level?.addListener(_onLevel);
    }
  }

  void _onLevel() {
    if (!mounted) return;
    final v = (widget.level?.value ?? 0.22).clamp(0.12, 1.0);
    setState(() {
      _levels = List<double>.from(_levels.skip(1))..add(v);
    });
  }

  @override
  void dispose() {
    widget.level?.removeListener(_onLevel);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 28,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (var i = 0; i < _levels.length; i++)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 0.6),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 80),
                  curve: Curves.easeOut,
                  height: 4 + _levels[i] * 24,
                  decoration: BoxDecoration(
                    color: Color.lerp(
                      widget.brand.withValues(alpha: 0.25),
                      widget.brand,
                      _levels[i],
                    ),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
