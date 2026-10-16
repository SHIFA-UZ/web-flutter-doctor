import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/api/ai_message.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/api/doctor_transcription_api.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_voice_recorder_session.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/widgets/submit_on_enter.dart';
import 'package:shifa_doc_app_v1/core/widgets/web_speech_conversation.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/visit_facts_panel.dart';
import 'package:shifa_doc_app_v1/features/intelligence/domain/intelligence_chat_models.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_action_cards.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_welcome.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';

enum VoiceSessionPhase { idle, listening, thinking, speaking }

/// Distinct "talking with Shifa" surface for the morning clinic OS demo.
class HomeVoiceSession extends ConsumerStatefulWidget {
  const HomeVoiceSession({
    super.key,
    this.selectedPatientId,
    this.selectedAppointmentId,
    this.embedded = false,
    this.hideChrome = false,
    this.initialMessages,
    this.onMessagesChanged,
    this.recentSessions = const [],
    this.onOpenSession,
  });

  final String? selectedPatientId;
  final String? selectedAppointmentId;

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

  @override
  ConsumerState<HomeVoiceSession> createState() => _HomeVoiceSessionState();
}

class _HomeVoiceSessionState extends ConsumerState<HomeVoiceSession> {
  final _session = DoctorVoiceRecorderSession();
  final _player = AudioPlayer();
  final _chatScroll = ScrollController();
  final List<_VoiceTurn> _turns = [];
  // Growable — List.filled is fixed-length and removeAt throws on Flutter web.
  final List<double> _levels = List<double>.generate(
    DoctorVoiceRecorderSession.waveBarCount,
    (_) => 0.12,
  );

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
  /// Idle mic open only for "Hey Shifa" (no active conversation).
  bool _wakeStandby = false;
  Timer? _wakeStandbyTimer;
  /// Restarts browser speech if Chrome ends the session while UI still says Listening.
  Timer? _listenWatchdogTimer;
  DateTime? _lastSpeechActivityAt;
  DateTime? _lastListenStartAt;
  static const _listenRestartCooldown = Duration(milliseconds: 1400);

  /// Intelligence on web: continuous conversation via browser SpeechRecognition.
  bool get _alexaMode =>
      widget.embedded && kIsWeb && WebSpeechConversation.isSupported;
  /// Reply language for this turn (EN / RU / UZ), from speech — not UI locale.
  String _sessionLanguage = 'EN';
  StreamSubscription<AiStreamEvent>? _aiSub;
  /// startAt values already booked in this chat (avoid double-book on "yes").
  final Set<String> _bookedStartAts = {};

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
  static const Duration _silenceToSend = Duration(milliseconds: 1100);
  static const Duration _minSpeechBeforeAutoSend = Duration(milliseconds: 280);
  static const Duration _maxListen = Duration(seconds: 45);
  static const Duration _noSpeechCancel = Duration(seconds: 20);

  @override
  void initState() {
    super.initState();
    _hydrateFromInitial();
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
        _turns.add(_VoiceTurn(
          userText: m.content,
          assistantText: assistant,
          toolName: toolName,
          cardPayload: card,
          userAt: m.at,
          assistantAt: assistantAt,
        ));
      } else {
        i += 1;
      }
    }
  }

  bool _isAffirmativeUtterance(String text) {
    final t = text.trim().toLowerCase();
    if (t.isEmpty || t.length > 48) return false;
    if (RegExp(r"\b(no|not|don't|dont|cancel|another|different)\b").hasMatch(t)) {
      return false;
    }
    return RegExp(
      r'\b(yes|yeah|yep|yup|ok|okay|sure|confirm|please|book it|do it|'
      r'да|хорошо|ha|mayli|tasdiq)\b',
    ).hasMatch(t);
  }

  String? _extractClockTime(String text) {
    final t = text.toLowerCase();
    final ampm = RegExp(
      r'\b([01]?\d|2[0-3])(?::([0-5]\d))?\s*(a\.?m\.?|p\.?m\.?)\b',
    ).firstMatch(t);
    if (ampm != null) {
      var hour = int.parse(ampm.group(1)!);
      final minute = (ampm.group(2) ?? '00').padLeft(2, '0');
      final isPm = ampm.group(3)!.startsWith('p');
      if (hour == 12) hour = 0;
      if (isPm) hour += 12;
      return '${hour.toString().padLeft(2, '0')}:$minute';
    }
    final clock = RegExp(r'\b([01]?\d|2[0-3]):([0-5]\d)\b').firstMatch(t);
    if (clock != null) {
      return '${clock.group(1)!.padLeft(2, '0')}:${clock.group(2)}';
    }
    final oclock = RegExp(r"\b([01]?\d|2[0-3])\s*o['’]?clock\b").firstMatch(t);
    if (oclock != null) {
      return '${oclock.group(1)!.padLeft(2, '0')}:00';
    }
    return null;
  }

  Map<String, dynamic>? _latestProposePayload() {
    for (final turn in _turns.reversed) {
      final payload = turn.cardPayload;
      if (payload == null) continue;
      final type = (payload['cardType'] as String?) ?? turn.toolName ?? '';
      if (type == 'propose_appointment' ||
          type == 'propose_reschedule' ||
          turn.toolName == 'propose_appointment' ||
          turn.toolName == 'propose_reschedule') {
        return payload;
      }
    }
    return null;
  }

  Map<String, dynamic>? _resolveBookableSlot(String userText) {
    final payload = _latestProposePayload();
    if (payload == null || payload['booked'] == true) return null;
    final preferred = payload['preferredSlot'];
    if (preferred is Map) {
      final slot = Map<String, dynamic>.from(preferred);
      final startAt = slot['startAt']?.toString();
      if (startAt != null && !_bookedStartAts.contains(startAt)) {
        if (_isAffirmativeUtterance(userText) ||
            _extractClockTime(userText) == slot['startLocal']?.toString()) {
          return {
            'payload': payload,
            'slot': slot,
            'reschedule': (payload['cardType'] == 'propose_reschedule') ||
                false,
          };
        }
      }
    }
    final wanted = _extractClockTime(userText);
    if (wanted == null) return null;
    final slots = (payload['slots'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e));
    for (final slot in slots) {
      if (slot['startLocal']?.toString() == wanted) {
        final startAt = slot['startAt']?.toString();
        if (startAt != null && !_bookedStartAts.contains(startAt)) {
          return {
            'payload': payload,
            'slot': slot,
            'reschedule': payload['cardType'] == 'propose_reschedule',
          };
        }
      }
    }
    return null;
  }

  Future<bool> _tryCompletePendingBooking(String userText) async {
    final target = _resolveBookableSlot(userText);
    if (target == null) return false;
    final payload = target['payload'] as Map<String, dynamic>;
    final slot = target['slot'] as Map<String, dynamic>;
    final reschedule = target['reschedule'] == true;
    final startAt = slot['startAt']?.toString();
    if (startAt == null) return false;

    setState(() {
      _busy = true;
      _phase = VoiceSessionPhase.thinking;
      _liveCaption = userText;
      _error = null;
    });

    final ok = reschedule
        ? await _executeReschedule(payload, slot)
        : await _executeBook(payload, slot);
    if (!mounted) return true;
    final timeLabel = slot['startLocal']?.toString() ?? '';
    final patientName = payload['patientName']?.toString() ?? 'the patient';
    // Prefer full month names for TTS (avoid "Oct" → O-C-T).
    final dateLabel = payload['speakableDateLabel']?.toString() ??
        payload['dateLabel']?.toString() ??
        '';
    final reply = ok
        ? (reschedule
            ? 'Moved $patientName to $timeLabel${dateLabel.isEmpty ? '' : ' on $dateLabel'}.'
            : 'Booked $patientName at $timeLabel${dateLabel.isEmpty ? '' : ' on $dateLabel'}.')
        : 'That slot could not be booked. Pick another time on the card.';
    final updatedPayload = Map<String, dynamic>.from(payload)
      ..['preferredSlot'] = slot
      ..['booked'] = ok
      ..['statusMessage'] = reply
      ..['autoConfirm'] = false;
    final userAt = DateTime.now();
    setState(() {
      _turns.add(
        _VoiceTurn(
          userText: userText,
          assistantText: reply,
          toolName: reschedule ? 'propose_reschedule' : 'propose_appointment',
          cardPayload: updatedPayload,
          userAt: userAt,
          assistantAt: DateTime.now(),
        ),
      );
      _streamingReply = reply;
      _busy = false;
      _phase = VoiceSessionPhase.idle;
    });
    if (ok) _bookedStartAts.add(startAt);
    _emitMessages();
    _scrollChatToEnd(force: true);
    if (ok) {
      await _speakReply(reply, _sessionLanguage);
    }
    if (mounted) {
      setState(() {
        _busy = false;
        _phase = VoiceSessionPhase.idle;
      });
      if (_handsFreeVoice) {
        _scheduleHandsFreeListen(delayMs: _alexaMode ? 750 : 450);
      }
    }
    return true;
  }

  /// Book immediately when the doctor named a free time (or backend set autoConfirm).
  Future<Map<String, dynamic>> _maybeAutoBookFromTool(
    Map<String, dynamic> payload, {
    required String userText,
    required String toolName,
  }) async {
    final next = Map<String, dynamic>.from(payload);
    Map<String, dynamic>? slot;
    final preferred = next['preferredSlot'];
    if (preferred is Map) {
      slot = Map<String, dynamic>.from(preferred);
    } else {
      final wanted = _extractClockTime(userText);
      if (wanted != null) {
        for (final raw in (next['slots'] as List? ?? const [])) {
          if (raw is Map && raw['startLocal']?.toString() == wanted) {
            slot = Map<String, dynamic>.from(raw);
            next['preferredSlot'] = slot;
            break;
          }
        }
      }
    }
    if (slot == null) return next;
    final namedTime = _extractClockTime(userText);
    final shouldBook =
        next['autoConfirm'] == true ||
        (namedTime != null && namedTime == slot['startLocal']?.toString());
    if (!shouldBook) return next;
    final startAt = slot['startAt']?.toString();
    if (startAt == null || _bookedStartAts.contains(startAt)) {
      if (startAt != null && _bookedStartAts.contains(startAt)) {
        next['booked'] = true;
        next['statusMessage'] =
            next['statusMessage'] ?? 'Appointment booked.';
      }
      return next;
    }
    final reschedule = toolName == 'propose_reschedule' ||
        next['cardType'] == 'propose_reschedule';
    final ok = reschedule
        ? await _executeReschedule(next, slot)
        : await _executeBook(next, slot);
    final timeLabel = slot['startLocal']?.toString() ?? '';
    final name = next['patientName']?.toString() ?? 'patient';
    final dateLabel = next['speakableDateLabel']?.toString() ??
        next['dateLabel']?.toString() ??
        '';
    if (ok) {
      _bookedStartAts.add(startAt);
      next['booked'] = true;
      next['autoConfirm'] = false;
      next['statusMessage'] = reschedule
          ? 'Moved $name to $timeLabel${dateLabel.isEmpty ? '' : ' on $dateLabel'}.'
          : 'Booked $name at $timeLabel${dateLabel.isEmpty ? '' : ' on $dateLabel'}.';
    }
    return next;
  }

  Future<bool> _executeBook(
    Map<String, dynamic> payload,
    Map<String, dynamic> slot,
  ) async {
    final patientId = (payload['patientId'] as num?)?.toInt();
    final startAt = slot['startAt']?.toString();
    final slotMinutes = (slot['slotMinutes'] as num?)?.toInt() ?? 30;
    if (patientId == null || startAt == null) return false;
    try {
      final client = ref.read(apiClientProvider);
      final body = <String, dynamic>{
        'startAt': startAt,
        'slotMinutes': slotMinutes,
        'patientId': patientId,
        'location': (payload['isVideo'] == true)
            ? 'Video Consultation'
            : (slot['locationLabel']?.toString() ?? 'Clinic'),
        'reason': payload['reason'],
        'isVideo': payload['isVideo'] == true,
      };
      final locId = (slot['locationId'] as num?)?.toInt();
      if (locId != null && payload['isVideo'] != true) {
        body['locationId'] = locId;
      }
      final res = await client.post('/api/schedule/book', body);
      final ok = res.statusCode == 200 || res.statusCode == 201;
      if (ok) unawaited(_refreshHomeAfterScheduleChange(startAt));
      return ok;
    } catch (_) {
      return false;
    }
  }

  Future<bool> _executeReschedule(
    Map<String, dynamic> payload,
    Map<String, dynamic> slot,
  ) async {
    final appointmentId = (payload['appointmentId'] as num?)?.toInt();
    final startAt = slot['startAt']?.toString();
    final slotMinutes = (slot['slotMinutes'] as num?)?.toInt() ?? 30;
    if (appointmentId == null || startAt == null) return false;
    try {
      final client = ref.read(apiClientProvider);
      final res = await client.put(
        '/api/appointments/$appointmentId/change',
        {'startAt': startAt, 'slotMinutes': slotMinutes},
      );
      final ok = res.statusCode == 200 || res.statusCode == 204;
      if (ok) unawaited(_refreshHomeAfterScheduleChange(startAt));
      return ok;
    } catch (_) {
      return false;
    }
  }

  Future<void> _refreshHomeAfterScheduleChange(String? startAtIso) async {
    DateTime? appointmentDay;
    if (startAtIso != null && startAtIso.isNotEmpty) {
      try {
        appointmentDay = DateTime.parse(startAtIso).toLocal();
      } catch (_) {}
    }
    try {
      ref.invalidate(doctorNotificationsProvider);
      ref.invalidate(doctorNotificationsUnreadCountProvider);
    } catch (_) {}
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
      final cardType =
          (payload['cardType'] as String?) ?? turn.toolName ?? '';
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
      final cardType =
          (payload['cardType'] as String?) ?? turn.toolName ?? '';
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
        final tokens =
            name.split(RegExp(r'\s+')).where((t) => t.length > 2).toList();
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
      out.add(IntelligenceChatMessage(
        role: 'user',
        content: t.userText,
        at: t.userAt,
      ));
      if (t.assistantText.trim().isNotEmpty || t.cardPayload != null) {
        out.add(IntelligenceChatMessage(
          role: 'assistant',
          content: t.assistantText,
          at: t.assistantAt ?? t.userAt,
          toolName: t.toolName,
          cardPayload: t.cardPayload,
        ));
      }
    }
    cb(out);
  }

  /// Sends the prior structured query back to the server without putting it in speech.
  String _assistantContentForApi(_VoiceTurn turn) {
    final spoken = turn.assistantText.trim();
    final context = turn.cardPayload?['queryContext'];
    if (context == null) return spoken;
    final encoded = jsonEncode(context);
    if (spoken.isEmpty) return '<appointment_query>$encoded</appointment_query>';
    return '$spoken\n<appointment_query>$encoded</appointment_query>';
  }

  bool _isNearBottom() {
    if (!_chatScroll.hasClients) return true;
    final pos = _chatScroll.position;
    return pos.maxScrollExtent - pos.pixels < 120;
  }

  void _scrollChatToEnd({bool force = false}) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_chatScroll.hasClients) return;
      if (!force && !_isNearBottom()) return;
      _chatScroll.animateTo(
        _chatScroll.position.maxScrollExtent,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
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
    if (last != null && DateTime.now().difference(last) < _listenRestartCooldown) {
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
        // Wake standby: show caption only when it looks like "Hey Shifa…"
        if (_wakeStandby && !_handsFreeVoice) {
          if (_containsWakePhrase(text)) {
            _noteSpeechActivity();
            setState(() {
              _liveCaption = text;
              _phase = VoiceSessionPhase.listening;
              _heardSpeech = true;
              _levels.removeAt(0);
              _levels.add(0.35 + (text.length % 7) * 0.06);
            });
          }
          return;
        }
        if (_phase != VoiceSessionPhase.listening) return;
        _noteSpeechActivity();
        if (text.trim().isNotEmpty) {
          _pulseTimer?.cancel();
        }
        setState(() {
          _liveCaption = text;
          _heardSpeech = text.trim().isNotEmpty;
          // Animate bars while browser hears speech.
          if (_heardSpeech) {
            _levels.removeAt(0);
            _levels.add(0.35 + (text.length % 7) * 0.06);
          }
        });
      },
      onFinal: (text) {
        unawaited(_onBrowserFinalUtterance(text));
      },
      onError: (message) {
        if (!mounted) return;
        final fatal = message.toLowerCase().contains('permission') ||
            message.toLowerCase().contains('not-allowed') ||
            message.toLowerCase().contains('denied');
        setState(() {
          _phase = VoiceSessionPhase.idle;
          _busy = false;
          // Only kill loops on real mic permission failures.
          if (fatal) {
            _handsFreeVoice = false;
            _wakeStandby = false;
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

    final lang = _detectLanguageFromText(cleaned) ?? _sessionLanguage;

    try {
      // Idle wake standby: only "Hey Shifa" (optionally with a command) starts.
      if (_wakeStandby && !_handsFreeVoice) {
        if (!_containsWakePhrase(cleaned)) {
          return;
        }
        _wakeStandby = false;
        final command = _stripShifaAddress(cleaned);
        await _browserSpeech?.stop();
        if (command.isEmpty) {
          // Wake only → enter active listening (no mic tap).
          setState(() {
            _sessionLanguage = lang;
            _error = null;
            _liveCaption = cleaned;
          });
          await _beginListening();
          return;
        }
        // "Hey Shifa, …" → run the request immediately.
        setState(() {
          _busy = true;
          _handsFreeVoice = true;
          _phase = VoiceSessionPhase.thinking;
          _liveCaption = cleaned;
          _sessionLanguage = lang;
          _error = null;
          _manualEndpoint = false;
        });
        await _askMorningAi(command, language: lang);
        return;
      }

      // Active conversation: goodbye phrases end the loop.
      if (_handsFreeVoice && _isConversationSignOff(cleaned)) {
        await _endConversationFromSignOff(cleaned, language: lang);
        return;
      }

      setState(() {
        _busy = true;
        _phase = VoiceSessionPhase.thinking;
        _liveCaption = cleaned;
        _sessionLanguage = lang;
        _error = null;
        _manualEndpoint = false;
      });
      await _browserSpeech?.stop();
      await _askMorningAi(cleaned, language: lang);
    } finally {
      _browserFinalInFlight = false;
    }
  }

  bool _containsWakePhrase(String text) {
    final t = text.toLowerCase();
    return RegExp(
          r"\b((hey|hi|hello|ok|okay)\s+)?shifa\b",
          caseSensitive: false,
        ).hasMatch(t) ||
        RegExp(
          r"\b((эй|привет|здравствуй)\s+)?шифа\b",
          caseSensitive: false,
        ).hasMatch(t);
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
    if (RegExp(
      r"^that'?s it[, ]+(thanks|thank you)$",
    ).hasMatch(t)) {
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
      if (t.contains('спасибо')) return 'Пожалуйста. Скажите «Привет, Шифа», когда понадоблюсь.';
      return 'Хорошо. Скажите «Привет, Шифа», когда понадоблюсь.';
    }
    if (lang == 'UZ') {
      return 'Mayli. Kerak bo‘lsa «Hey Shifa» deb chaqiring.';
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

  /// Background mic for "Hey Shifa" while conversation is closed.
  Future<void> _ensureWakeStandby() async {
    if (!mounted || !_alexaMode) return;
    if (_handsFreeVoice || _busy || _browserFinalInFlight) return;
    if (_phase == VoiceSessionPhase.listening ||
        _phase == VoiceSessionPhase.thinking ||
        _phase == VoiceSessionPhase.speaking) {
      return;
    }
    final canSpeak =
        ref.read(doctorFeatureProvider(DoctorFeature.speechToText));
    if (!canSpeak) return;

    _wakeStandby = true;
    _ttsPlayGen++;
    try {
      await _player.stop();
    } catch (_) {}
    final speech = _ensureBrowserSpeech();
    final ok = await speech.start(
      languageCode: _sessionLanguage,
      continuous: true,
    );
    if (!mounted) return;
    if (!ok) {
      _scheduleWakeStandby(delayMs: 1200);
      return;
    }
    if (mounted) {
      setState(() {
        _phase = VoiceSessionPhase.idle;
        _busy = false;
      });
    }
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
    if (_manualEndpoint && level >= _speechLevel) {
      // Levels started working after we entered manual mode — keep manual send.
    }
    setState(() {
      _levels.removeAt(0);
      _levels.add(level);
    });

    // Decay peak so silence is relative to recent speech, not absolute dB.
    _peakLevel = level > _peakLevel ? level : (_peakLevel * 0.92 + level * 0.08);
    final relativeSilenceGate =
        (_peakLevel * 0.42).clamp(_silenceLevel, 0.55);

    final now = DateTime.now();
    if (level >= _speechLevel) {
      _heardSpeech = true;
      _speechStartedAt ??= now;
      _silenceStartedAt = null;
      return;
    }

    // Without usable levels, never auto-cancel — user taps stop to send.
    if (_manualEndpoint) return;

    if (!_heardSpeech) {
      final started = _listenStartedAt;
      if (started != null && now.difference(started) >= _noSpeechCancel) {
        unawaited(_cancelListening(
          reason:
              'No speech detected. Tap the mic, speak, then tap stop to send.',
        ));
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
      if (!stopWhenHeard && _gotAmplitude) {
        _pulseTimer?.cancel();
        return;
      }
      final t = DateTime.now().millisecondsSinceEpoch / 180.0;
      final pulse =
          0.16 + 0.2 * (0.5 + 0.5 * (t % 2 < 1 ? (t % 1) : 1 - (t % 1)));
      setState(() {
        _levels.removeAt(0);
        _levels.add(pulse.clamp(0.12, 0.5));
      });
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

  /// Prefer STT-detected language, then heuristics from the transcript text.
  String _resolveReplyLanguage({
    required String transcript,
    String? sttLanguage,
  }) {
    final fromStt = _normalizeLangCode(sttLanguage);
    if (fromStt != null) return fromStt;
    return _detectLanguageFromText(transcript) ?? _uiFallbackLanguage();
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
    final t = text.trim();
    if (t.isEmpty) return null;
    if (RegExp(r'[А-Яа-яЁё]').hasMatch(t)) return 'RU';
    final lower = t.toLowerCase();
    const uzMarkers = [
      'qabul',
      'nechta',
      'bugun',
      'kecha',
      'bemor',
      'shifokor',
      'hozir',
      'iltimos',
      'salom',
      'rahmat',
      "o'",
      'gʻ',
      'qʻ',
    ];
    for (final m in uzMarkers) {
      if (lower.contains(m)) return 'UZ';
    }
    if (RegExp(r'[A-Za-z]').hasMatch(t)) return 'EN';
    return null;
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
    } else if (_busy && _phase != VoiceSessionPhase.listening) {
      return;
    }

    final canSpeak = ref.read(doctorFeatureProvider(DoctorFeature.speechToText));
    if (!canSpeak) {
      setState(() {
        _surfaceError('Speech requires Pro. Upgrade to talk with Shifa.');
      });
      return;
    }

    // Second tap while listening:
    // - Alexa mode: end the conversation → wake-word standby
    // - Recorder mode: send what was captured
    if (_phase == VoiceSessionPhase.listening) {
      if (_alexaMode) {
        _handsFreeVoice = false;
        _wakeStandby = false;
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

    await _beginListening();
  }

  Future<void> _beginListening() async {
    final now = DateTime.now();
    final last = _lastListenStartAt;
    if (last != null && now.difference(last) < _listenRestartCooldown) {
      final wait = _listenRestartCooldown - now.difference(last);
      _scheduleHandsFreeListen(
        delayMs: wait.inMilliseconds.clamp(200, 2000),
      );
      return;
    }
    _lastListenStartAt = now;

    _handsFreeResumeTimer?.cancel();
    _wakeStandbyTimer?.cancel();
    _wakeStandby = false;
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
      for (var i = 0; i < _levels.length; i++) {
        _levels[i] = 0.12;
      }
    });
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
      final speech = _ensureBrowserSpeech();
      // Conversation turns: one utterance (continuous false). Wake uses continuous.
      final ok = await speech.start(
        languageCode: _sessionLanguage,
        continuous: false,
      );
      if (!ok && mounted) {
        _handsFreeStartFails++;
        _cancelListenWatchdog();
        _pulseTimer?.cancel();
        setState(() => _phase = VoiceSessionPhase.idle);
        if (_handsFreeStartFails >= 5) {
          _handsFreeVoice = false;
          _surfaceError(
            'Could not reopen the microphone. Say Hey Shifa or tap the mic.',
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
              ? 'Microphone permission is required. Allow mic access and try again.'
              : 'Could not start the microphone. Check browser permissions.',
        );
      });
    }
  }

  void _scheduleHandsFreeListen({int delayMs = 650}) {
    if (!_handsFreeVoice || !mounted) return;
    // Allow re-arm even when UI still shows listening (dead Chrome session).
    final speechDead = _alexaMode &&
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
      final stillDead = _alexaMode &&
          (_browserSpeech == null || !_browserSpeech!.isListening);
      if (_phase == VoiceSessionPhase.listening && !stillDead) return;
      unawaited(_beginListening());
    });
  }

  Future<void> _finishListening() async {
    _maxListenTimer?.cancel();
    setState(() {
      _busy = true;
      _phase = VoiceSessionPhase.thinking;
    });
    final path = await _session.stop(discard: false);
    _resetVad();
    if (path == null || path.isEmpty) {
      if (mounted) {
        setState(() {
          _busy = false;
          _phase = VoiceSessionPhase.idle;
          _error = 'No recording captured.';
        });
      }
      return;
    }

    try {
      final bytes = await readVoiceRecordingFileBytes(path);
      final name = voiceRecordingUploadFileName(path, bytes: bytes);
      // Auto-detect: do not force UI locale (was turning English speech into Uzbek).
      final stt = await postDoctorTranscriptionDetailed(
        ref: ref,
        fileBytes: bytes,
        fileName: name,
        autoDetectLanguage: true,
      );
      if (!mounted) return;
      final cleaned = stt.text.trim();
      if (cleaned.isEmpty || cleaned == kNoSpeechTranscriptPlaceholder) {
        setState(() {
          _busy = false;
          _phase = VoiceSessionPhase.idle;
          _surfaceError('No speech detected. Tap the mic and try again.');
        });
        _scheduleHandsFreeListen();
        return;
      }
      final lang = _resolveReplyLanguage(
        transcript: cleaned,
        sttLanguage: stt.language,
      );
      setState(() {
        _liveCaption = cleaned;
        _sessionLanguage = lang;
      });
      await _askMorningAi(cleaned, language: lang);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _phase = VoiceSessionPhase.idle;
        _handsFreeVoice = false;
        _surfaceError(
          e.toString().contains('permission') || e.toString().contains('403')
              ? 'Speech requires Pro, or mic permission was denied.'
              : 'Transcription failed. Check your connection and try again.',
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

  /// Strip wake phrases so "Hey Shifa, free slots today" becomes the request.
  String _stripShifaAddress(String text) {
    var t = text.trim();
    t = t.replaceFirst(
      RegExp(
        r"^(hey|hi|hello|ok|okay)?\s*shifa[,!.]?\s*",
        caseSensitive: false,
      ),
      '',
    );
    t = t.replaceFirst(
      RegExp(
        r"^(эй|привет|здравствуй)?\s*шифа[,!.]?\s*",
        caseSensitive: false,
      ),
      '',
    );
    return t.trim().isEmpty ? text.trim() : t.trim();
  }

  Future<void> _askMorningAi(
    String userText, {
    String? language,
  }) async {
    final api = ref.read(aiApiProvider);
    userText = _stripShifaAddress(userText);
    final userAt = DateTime.now();
    final replyLang = language ??
        _detectLanguageFromText(userText) ??
        _sessionLanguage;
    _sessionLanguage = replyLang;
    final patientId = int.tryParse(widget.selectedPatientId ?? '');
    final appointmentId = int.tryParse(widget.selectedAppointmentId ?? '');

    // If the doctor named a free time (or said yes after one was locked), book
    // immediately — do not loop through another propose card.
    if (widget.embedded && await _tryCompletePendingBooking(userText)) {
      return;
    }

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
    });

    final buffer = StringBuffer();
    String? toolName;
    Map<String, dynamic>? cardPayload;
    // Pin booking to the patient from the latest search/propose card — not a
    // hallucinated id, and not an unrelated clinic selection when Intelligence
    // already resolved someone in this chat.
    final focusPatientId = _focusPatientIdFromCards() ?? patientId;
    await _aiSub?.cancel();
    try {
      await for (final event in api.streamMorningAi(
        messages: history,
        language: replyLang,
        patientId: focusPatientId,
        consultationId: appointmentId,
      )) {
        if (!mounted) return;
        if (event is AiToolEvent) {
          final name = event.name;
          toolName = name;
          var payload = Map<String, dynamic>.from(event.payload);
          if (widget.embedded &&
              (name == 'propose_appointment' || name == 'propose_reschedule')) {
            payload = await _maybeAutoBookFromTool(
              payload,
              userText: userText,
              toolName: name,
            );
          }
          cardPayload = payload;
          setState(() {});
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
          _error = 'Shifa had nothing to say. Try again.';
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
        _turns.add(_VoiceTurn(
          userText: userText,
          assistantText: spoken,
          toolName: toolName,
          cardPayload: cardPayload,
          userAt: userAt,
          assistantAt: DateTime.now(),
        ));
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
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _phase = VoiceSessionPhase.idle;
        _busy = false;
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
      final bytes = await api.speak(
        text,
        language: language,
        timeout: const Duration(seconds: 8),
      );
      if (!mounted || playGen != _ttsPlayGen) return;
      if (bytes.isEmpty) {
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
      final data = Uint8List.fromList(bytes);
      // Prefer data-URI UrlSource first (reliable on Flutter web).
      try {
        final dataUri = Uri.dataFromBytes(data, mimeType: 'audio/mpeg');
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
        _surfaceError('Voice is slow — caption is above. Keep talking.');
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
          _surfaceError('Voice skipped — caption is above.');
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

  @override
  Widget build(BuildContext context) {
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
                style: AppDesignSystem.body2(context).copyWith(
                  color: brand,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ],
    );

    final legacyControls = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _WaveStrip(
          levels: _levels,
          brand: brand,
          active: _phase == VoiceSessionPhase.listening,
        ),
        if (_error != null) ...[
          const SizedBox(height: 8),
          Text(
            _error!,
            style: AppDesignSystem.caption(context).copyWith(
              color: Colors.red.shade700,
            ),
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
          labelIdle: _t(
            l10n,
            'holdToTalk',
            'Tap once, then speak',
          ),
        ),
        if (_phase == VoiceSessionPhase.listening) ...[
          const SizedBox(height: 6),
          Text(
            _heardSpeech
                ? 'Pause when finished — sending automatically'
                : 'Speak now…',
            textAlign: TextAlign.center,
            style: AppDesignSystem.caption(context).copyWith(color: brand),
          ),
        ],
        const SizedBox(height: 10),
        _TypedFallback(
          enabled: !_busy && _phase != VoiceSessionPhase.listening,
          onSend: _sendTyped,
          hint: _t(
            l10n,
            'orTypeHere',
            'Or type: How many appointments today?',
          ),
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
      final inVoiceUi = _phase == VoiceSessionPhase.listening ||
          _phase == VoiceSessionPhase.speaking ||
          _phase == VoiceSessionPhase.thinking;
      final showWelcome = _turns.isEmpty &&
          _liveCaption.isEmpty &&
          _streamingReply.isEmpty &&
          !_busy &&
          !inVoiceUi;

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
              child: showWelcome
                  ? IntelligenceWelcome(
                      recentSessions: widget.recentSessions,
                      onSendPrompt: _sendTyped,
                      onOpenSession: widget.onOpenSession ?? (_) {},
                    )
                  : ListView(
                      controller: _chatScroll,
                      padding: const EdgeInsets.fromLTRB(24, 20, 24, 16),
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
                                _phase == VoiceSessionPhase.listening) &&
                            _liveCaption.isNotEmpty &&
                            (_turns.isEmpty ||
                                _turns.last.userText != _liveCaption)) ...[
                          _UserBubble(
                            text: _liveCaption,
                            brand: brand,
                            at: DateTime.now(),
                          ),
                          const SizedBox(height: 12),
                        ],
                        if (_phase == VoiceSessionPhase.listening)
                          _ListeningBanner(
                            brand: brand,
                            levels: _levels,
                            heardSpeech: _heardSpeech,
                            handsFree: _handsFreeVoice,
                            manualEndpoint: _manualEndpoint,
                            alexaMode: _alexaMode,
                            liveCaption: _liveCaption,
                          ),
                        if (_phase == VoiceSessionPhase.speaking)
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            child: Row(
                              children: [
                                Icon(Icons.graphic_eq, color: brand),
                                const SizedBox(width: 10),
                                Text(
                                  _t(l10n, 'voiceSpeaking', 'Speaking…'),
                                  style: AppDesignSystem.body2(context)
                                      .copyWith(
                                    color: brand,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        if (_phase == VoiceSessionPhase.thinking &&
                            _streamingReply.isEmpty)
                          _AssistantBlock(
                            text: _t(l10n, 'intelReviewing', 'Reviewing…'),
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
                        if (_error != null) ...[
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  _error!,
                                  style: AppDesignSystem.caption(context)
                                      .copyWith(color: Colors.red.shade700),
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
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 14),
              child: _IntelligenceComposer(
                phase: _phase,
                busy: _busy,
                brand: brand,
                levels: _levels,
                heardSpeech: _heardSpeech,
                error: _error,
                handsFree: _handsFreeVoice,
                wakeStandby: _wakeStandby,
                manualEndpoint: _manualEndpoint,
                alexaMode: _alexaMode,
                onMic: () => unawaited(_toggleListen()),
                onSend: _sendTyped,
                hint: _t(
                  l10n,
                  'intelComposerHint',
                  'Ask Shifa about patients, appointments, or clinical tasks…',
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Text(
                _t(
                  l10n,
                  'intelDisclaimer',
                  'AI-generated responses may require review.',
                ),
                textAlign: TextAlign.center,
                style: AppDesignSystem.caption(context).copyWith(
                  color: AppDesignSystem.textTertiary,
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
                text: _streamingReply.isNotEmpty
                    ? _streamingReply
                    : _turns.last.assistantText,
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

String _formatChatTimestamp(DateTime at) {
  final local = at.toLocal();
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(local.year, local.month, local.day);
  final hh = local.hour.toString().padLeft(2, '0');
  final mm = local.minute.toString().padLeft(2, '0');
  final time = '$hh:$mm';
  if (day == today) return 'Today · $time';
  final yesterday = today.subtract(const Duration(days: 1));
  if (day == yesterday) return 'Yesterday · $time';
  const months = <String>[
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];
  final date = '${local.day} ${months[local.month - 1]} ${local.year}';
  return '$date · $time';
}

class _UserBubble extends StatelessWidget {
  const _UserBubble({
    required this.text,
    required this.brand,
    this.at,
  });
  final String text;
  final Color brand;
  final DateTime? at;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerRight,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * 0.72,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            if (at != null) ...[
              Text(
                _formatChatTimestamp(at!),
                style: AppDesignSystem.caption(context).copyWith(
                  color: AppDesignSystem.textTertiary,
                  fontSize: 11,
                ),
              ),
              const SizedBox(height: 4),
            ],
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
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

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * 0.85,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (at != null && !pending) ...[
              Text(
                _formatChatTimestamp(at!),
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
                    text,
                    style: AppDesignSystem.body2(context).copyWith(
                      height: 1.45,
                      fontStyle: pending ? FontStyle.italic : FontStyle.normal,
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
  }
}

class _ListeningBanner extends StatelessWidget {
  const _ListeningBanner({
    required this.brand,
    required this.levels,
    required this.heardSpeech,
    required this.handsFree,
    required this.manualEndpoint,
    required this.alexaMode,
    required this.liveCaption,
  });

  final Color brand;
  final List<double> levels;
  final bool heardSpeech;
  final bool handsFree;
  final bool manualEndpoint;
  final bool alexaMode;
  final String liveCaption;

  @override
  Widget build(BuildContext context) {
    final title = alexaMode
        ? (heardSpeech ? 'Got it… keep talking or pause' : 'Listening… speak naturally')
        : manualEndpoint
            ? 'Recording… speak, then tap stop'
            : heardSpeech
                ? 'Listening… pause to send'
                : 'Listening… speak now';
    final subtitle = alexaMode
        ? 'Pause when finished — Shifa answers, then listens again. Say “that’s it” or “thank you” to stop, or tap stop.'
        : manualEndpoint
            ? 'Tap the red stop button when you finish speaking'
            : handsFree
                ? 'Hands-free — tap stop when done to send'
                : null;

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 12),
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
      decoration: BoxDecoration(
        color: brand.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: brand.withValues(alpha: 0.28)),
      ),
      child: Column(
        children: [
          Icon(Icons.mic, color: brand, size: 36),
          const SizedBox(height: 8),
          Text(
            title,
            textAlign: TextAlign.center,
            style: AppDesignSystem.body1(context).copyWith(
              color: brand,
              fontWeight: FontWeight.w700,
            ),
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 4),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              style: AppDesignSystem.caption(context).copyWith(
                color: AppDesignSystem.textSecondary,
              ),
            ),
          ],
          if (alexaMode && liveCaption.trim().isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(
              liveCaption,
              textAlign: TextAlign.center,
              style: AppDesignSystem.body2(context),
            ),
          ],
          const SizedBox(height: 12),
          SizedBox(
            height: 36,
            child: _WaveStrip(levels: levels, brand: brand, active: true),
          ),
        ],
      ),
    );
  }
}

class _IntelligenceComposer extends StatefulWidget {
  const _IntelligenceComposer({
    required this.phase,
    required this.busy,
    required this.brand,
    required this.levels,
    required this.heardSpeech,
    required this.onMic,
    required this.onSend,
    required this.hint,
    this.error,
    this.handsFree = false,
    this.wakeStandby = false,
    this.manualEndpoint = false,
    this.alexaMode = false,
  });

  final VoiceSessionPhase phase;
  final bool busy;
  final Color brand;
  final List<double> levels;
  final bool heardSpeech;
  final VoidCallback onMic;
  final ValueChanged<String> onSend;
  final String hint;
  final String? error;
  final bool handsFree;
  final bool wakeStandby;
  final bool manualEndpoint;
  final bool alexaMode;

  @override
  State<_IntelligenceComposer> createState() => _IntelligenceComposerState();
}

class _IntelligenceComposerState extends State<_IntelligenceComposer> {
  final _controller = TextEditingController();
  final _focus = FocusNode();

  @override
  void dispose() {
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
    final listening = widget.phase == VoiceSessionPhase.listening;
    final speaking = widget.phase == VoiceSessionPhase.speaking;
    // Allow mic while idle, listening (stop), or speaking (barge-in).
    final micEnabled = !widget.busy || listening || speaking;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.error != null &&
            widget.phase == VoiceSessionPhase.idle) ...[
          Text(
            widget.error!,
            style: AppDesignSystem.caption(context).copyWith(
              color: Colors.red.shade700,
            ),
          ),
          const SizedBox(height: 8),
        ],
        if (listening) ...[
          Text(
            widget.alexaMode
                ? (widget.heardSpeech
                    ? 'Pause to send — then keep talking after the reply'
                    : 'Speak naturally — say “that’s it” when you’re done')
                : widget.manualEndpoint
                    ? 'Speak, then tap the red stop to send'
                    : widget.heardSpeech
                        ? 'Pause when finished — sending automatically'
                        : 'Speak now — I am listening',
            textAlign: TextAlign.center,
            style: AppDesignSystem.caption(context).copyWith(
              color: widget.brand,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
        ],
        Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: listening
                  ? widget.brand.withValues(alpha: 0.55)
                  : AppDesignSystem.border,
              width: listening ? 1.6 : 1,
            ),
          ),
          padding: const EdgeInsets.fromLTRB(12, 4, 6, 4),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: TextField(
                  controller: _controller,
                  focusNode: _focus,
                  enabled: !widget.busy && !listening && !speaking,
                  minLines: 1,
                  maxLines: 4,
                  textInputAction: TextInputAction.send,
                  onSubmitted: submitOnEnter(
                    widget.busy || listening || speaking ? null : _submit,
                  ),
                  decoration: InputDecoration(
                    hintText: listening
                        ? 'Listening…'
                        : speaking
                            ? 'Shifa is speaking… tap mic to interrupt'
                            : widget.alexaMode && widget.wakeStandby
                                ? 'Say “Hey Shifa” — or type here…'
                                : widget.hint,
                    border: InputBorder.none,
                    isDense: true,
                  ),
                ),
              ),
              IconButton(
                tooltip: listening
                    ? 'Stop listening'
                    : speaking
                        ? 'Interrupt and speak'
                        : widget.wakeStandby
                            ? 'Wake standby on — or tap to talk'
                            : 'Talk with Shifa',
                onPressed: micEnabled ? widget.onMic : null,
                icon: Icon(
                  listening
                      ? Icons.stop_circle_outlined
                      : speaking
                          ? Icons.mic
                          : widget.wakeStandby
                              ? Icons.hearing
                              : Icons.mic_none,
                  color: listening
                      ? Colors.red.shade600
                      : widget.brand,
                  size: listening || speaking ? 28 : 24,
                ),
              ),
              IconButton(
                tooltip: 'Send',
                onPressed:
                    widget.busy || listening || speaking ? null : _submit,
                icon: Icon(Icons.send_rounded, color: widget.brand),
              ),
            ],
          ),
        ),
        if (widget.handsFree && widget.phase == VoiceSessionPhase.idle) ...[
          const SizedBox(height: 6),
          Text(
            'Hands-free on — mic will open after each reply',
            textAlign: TextAlign.center,
            style: AppDesignSystem.caption(context).copyWith(
              color: AppDesignSystem.textSecondary,
            ),
          ),
        ] else if (widget.alexaMode &&
            widget.wakeStandby &&
            widget.phase == VoiceSessionPhase.idle) ...[
          const SizedBox(height: 6),
          Text(
            'Say “Hey Shifa” to start — no need to touch the mic',
            textAlign: TextAlign.center,
            style: AppDesignSystem.caption(context).copyWith(
              color: AppDesignSystem.textSecondary,
            ),
          ),
        ],
      ],
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
              : [brand.withValues(alpha: 0.7), AppDesignSystem.primaryAi.withValues(alpha: 0.7)],
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
      child: Icon(
        switch (phase) {
          VoiceSessionPhase.listening => Icons.mic,
          VoiceSessionPhase.thinking => Icons.auto_awesome,
          VoiceSessionPhase.speaking => Icons.graphic_eq,
          VoiceSessionPhase.idle => Icons.record_voice_over,
        },
        color: Colors.white,
      ),
    );
  }
}

class _WaveStrip extends StatelessWidget {
  const _WaveStrip({
    required this.levels,
    required this.brand,
    required this.active,
  });

  final List<double> levels;
  final Color brand;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 44,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          for (final level in levels)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 1),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 80),
                  height: 8 + level * 36,
                  decoration: BoxDecoration(
                    color: active
                        ? brand.withValues(alpha: 0.35 + level * 0.55)
                        : brand.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
              ),
            ),
        ],
      ),
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
