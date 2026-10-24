import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../../../core/api/api_client.dart';
import '../../../core/api/ai_message.dart';

/// Bytes plus the server Content-Type (OpenAI MP3 or Gemini WAV).
class SpokenAudio {
  const SpokenAudio({required this.bytes, required this.mimeType});
  final List<int> bytes;
  final String mimeType;
}

/// Thrown when the AI stream fails with a structured error (validation, rate limit, safety block, unavailable).
class AiStreamException implements Exception {
  final String code;
  final String message;

  AiStreamException(this.code, this.message);

  @override
  String toString() => message;

  /// Human-readable body for banners/snackbars (maps known codes).
  String get userFacingMessage {
    if (code == 'RATE_LIMIT') {
      return "You've reached your daily AI usage limit. Try again tomorrow.";
    }
    return message;
  }

  /// User-friendly short label for the error type.
  String get displayLabel {
    switch (code) {
      case 'VALIDATION':
        return 'Invalid question';
      case 'RATE_LIMIT':
        return 'Rate limit';
      case 'SAFETY_BLOCK':
        return 'Safety';
      case 'AI_UNAVAILABLE':
        return 'AI unavailable';
      default:
        return 'Error';
    }
  }
}

/// Emitted when backend has created a draft note after stream completes (Save as Draft / Discard).
class AiDraftReady {
  final String draftId;
  final String draftLabel;
  final bool canSave;

  AiDraftReady({required this.draftId, required this.draftLabel, required this.canSave});
}

/// Stream event: either a content token or draft-ready metadata.
sealed class AiStreamEvent {}

class AiTokenEvent extends AiStreamEvent {
  final String token;
  AiTokenEvent(this.token);
}

class AiDraftReadyEvent extends AiStreamEvent {
  final AiDraftReady draft;
  AiDraftReadyEvent(this.draft);
}

/// Morning / Intelligence tool result for structured action cards.
class AiToolEvent extends AiStreamEvent {
  final String name;
  final Map<String, dynamic> payload;
  AiToolEvent({required this.name, required this.payload});
}

class AiApi {
  final ApiClient _api;

  AiApi(this._api);

  /// Spoken morning session stream (schedule + briefing tools). Tokens only — no draft event.
  Stream<AiStreamEvent> streamMorningAi({
    required List<AiMessage> messages,
    String? question,
    required String language,
    int? patientId,
    int? consultationId,
    String? conversationId,
  }) =>
      _streamAiAt(
        path: '/api/ai/morning/stream',
        messages: messages,
        question: question,
        language: language,
        patientId: patientId,
        consultationId: consultationId,
        conversationId: conversationId,
        expectDraft: false,
      );

  /// Patient-aware AI stream; yields [AiTokenEvent] then [AiDraftReadyEvent] when draft is created.
  /// Throws [AiStreamException] on structured errors.
  Stream<AiStreamEvent> streamAi({
    required List<AiMessage> messages,
    String? question,
    required String language,
    int? patientId,
    int? consultationId,
  }) =>
      _streamAiAt(
        path: '/api/ai/stream',
        messages: messages,
        question: question,
        language: language,
        patientId: patientId,
        consultationId: consultationId,
        expectDraft: true,
      );

  Stream<AiStreamEvent> _streamAiAt({
    required String path,
    required List<AiMessage> messages,
    String? question,
    required String language,
    int? patientId,
    int? consultationId,
    String? conversationId,
    required bool expectDraft,
  }) async* {
    final uri = Uri.parse('${_api.baseUrl}$path');

    final request = http.Request('POST', uri);
    request.headers.addAll(
      _api.buildHeaders(extra: {'Accept': 'text/event-stream'}),
    );

    request.body = jsonEncode({
      'question': question,
      'messages': messages.map((m) => m.toJson()).toList(),
      'language': language,
      'patientId': patientId,
      'consultationId': consultationId,
      'conversationId': conversationId,
    });

    final response = await request.send();

    if (response.statusCode != 200) {
      final body = await response.stream.bytesToString();
      String code = 'UNKNOWN';
      String message = 'AI stream failed: HTTP ${response.statusCode}';
      try {
        final json = jsonDecode(body) as Map<String, dynamic>?;
        if (json != null) {
          code = (json['code'] as String?) ?? code;
          final msg = json['message'] as String?;
          final errors = json['errors'] as Map<String, dynamic>?;
          if (msg != null) message = msg;
          if (errors != null && errors.isNotEmpty) {
            final first = errors.values.first;
            message = first is String ? first : message;
          }
        }
      } catch (_) {}
      throw AiStreamException(code, message);
    }

    String buffer = '';
    String currentEvent = '';
    final currentDataLines = <String>[];

    await for (final chunk in response.stream.transform(utf8.decoder)) {
      buffer += chunk;
      while (buffer.contains('\n')) {
        final idx = buffer.indexOf('\n');
        var line = buffer.substring(0, idx);
        buffer = buffer.substring(idx + 1);
        if (line.endsWith('\r')) {
          line = line.substring(0, line.length - 1);
        }

        if (line.isEmpty) {
          final eventName = currentEvent;
          final data = currentDataLines.join('\n');
          currentEvent = '';
          currentDataLines.clear();

          if (eventName == 'error' && data.trim().isNotEmpty) {
            try {
              final json = jsonDecode(data.trimLeft()) as Map<String, dynamic>?;
              final code = (json?['code'] as String?) ?? 'UNKNOWN';
              final message = (json?['message'] as String?) ?? 'Unknown error';
              throw AiStreamException(code, message);
            } catch (e) {
              if (e is AiStreamException) rethrow;
              throw AiStreamException('UNKNOWN', data);
            }
          }

          if (eventName == 'draft' && data.trim().isNotEmpty) {
            try {
              final json = jsonDecode(data.trimLeft()) as Map<String, dynamic>?;
              if (json != null) {
                yield AiDraftReadyEvent(AiDraftReady(
                  draftId: json['draftId'] as String? ?? '',
                  draftLabel: json['draftLabel'] as String? ?? 'Draft',
                  canSave: json['canSave'] as bool? ?? true,
                ));
              }
            } catch (_) {}
            continue;
          }

          if (eventName == 'tool' && data.trim().isNotEmpty) {
            try {
              final json = jsonDecode(data.trimLeft()) as Map<String, dynamic>?;
              if (json != null) {
                final name = (json['name'] as String?) ?? '';
                final rawPayload = json['payload'];
                final payload = rawPayload is Map
                    ? Map<String, dynamic>.from(rawPayload)
                    : <String, dynamic>{};
                if (name.isNotEmpty) {
                  yield AiToolEvent(name: name, payload: payload);
                }
              }
            } catch (_) {}
            continue;
          }

          if (data.isNotEmpty) {
            if (kDebugMode) {
              debugPrint("[TOKEN] -> '${data.replaceAll('\n', r'\n')}'");
            }
            yield AiTokenEvent(data);
          }
          continue;
        }

        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim();
          continue;
        }

        if (line.startsWith('data:')) {
          // Preserve token payload exactly; do not strip leading spaces.
          currentDataLines.add(line.substring(5));
        }
      }
    }

    if (currentDataLines.isNotEmpty) {
      final data = currentDataLines.join('\n');
      if (data.isNotEmpty) {
        if (kDebugMode) {
          debugPrint("[TOKEN] -> '${data.replaceAll('\n', r'\n')}'");
        }
        yield AiTokenEvent(data);
      }
    }
  }

  /// Confirm draft and save as consultation note. Optional [patientId] / [appointmentId] when draft had none.
  Future<void> confirmDraft(String draftId, {int? patientId, int? appointmentId}) async {
    final uri = Uri.parse('${_api.baseUrl}/api/ai/draft/$draftId/confirm');
    final body = <String, dynamic>{};
    if (patientId != null) body['patientId'] = patientId;
    if (appointmentId != null) body['appointmentId'] = appointmentId;
    final response = await http.post(
      uri,
      headers: _api.buildHeaders(),
      body: jsonEncode(body.isEmpty ? {} : body),
    );
    if (response.statusCode != 200) throw Exception('Confirm failed: ${response.statusCode}');
  }

  /// Discard draft (status = DISCARDED).
  Future<void> discardDraft(String draftId) async {
    final uri = Uri.parse('${_api.baseUrl}/api/ai/draft/$draftId/discard');
    final response = await http.post(uri, headers: _api.buildHeaders());
    if (response.statusCode != 200) throw Exception('Discard failed: ${response.statusCode}');
  }

  /// Result of patient briefing API.
  static PatientBriefingResult parseBriefingResponse(Map<String, dynamic> json) {
    return PatientBriefingResult(
      briefing: json['briefing'] as String? ?? '',
      documentCount: json['documentCount'] as int? ?? 0,
      appointmentCount: json['appointmentCount'] as int? ?? 0,
    );
  }

  /// Server TTS for short spoken morning replies.
  /// Uzbek is Gemini WAV; English/Russian stay OpenAI MP3. Use [SpokenAudio.mimeType].
  Future<SpokenAudio> speak(
    String text, {
    String language = 'UZ',
    Duration timeout = const Duration(seconds: 25),
  }) async {
    final uri = Uri.parse('${_api.baseUrl}/api/ai/speak');
    final spoken = _truncateForSpeech(text);
    final response = await http
        .post(
          uri,
          headers: _api.buildHeaders(),
          body: jsonEncode({'text': spoken, 'language': language}),
        )
        .timeout(timeout);
    if (response.statusCode != 200) {
      throw AiStreamException(
        'TTS_FAILED',
        'Speech synthesis failed: HTTP ${response.statusCode}',
      );
    }
    final mime = response.headers['content-type']?.split(';').first.trim();
    return SpokenAudio(
      bytes: response.bodyBytes,
      mimeType: (mime == null || mime.isEmpty) ? 'audio/mpeg' : mime,
    );
  }

  static String _truncateForSpeech(String text, {int maxChars = 560}) {
    final t = text.trim();
    if (t.length <= maxChars) return t;
    final cut = t.substring(0, maxChars);
    final ends = <int>[
      cut.lastIndexOf('.'),
      cut.lastIndexOf('!'),
      cut.lastIndexOf('?'),
    ].where((i) => i >= 40);
    if (ends.isNotEmpty) {
      return cut.substring(0, ends.reduce((a, b) => a > b ? a : b) + 1).trim();
    }
    final space = cut.lastIndexOf(' ');
    return (space > 40 ? cut.substring(0, space) : cut).trim();
  }

  Future<VisitFactsBundle> fetchVisitFactsForAppointment(int appointmentId) async {
    final uri = Uri.parse(
      '${_api.baseUrl}/api/ai/visit-facts/appointment/$appointmentId',
    );
    final response = await http.get(uri, headers: _api.buildHeaders());
    if (response.statusCode != 200) {
      throw Exception('Visit facts failed: ${response.statusCode}');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>? ?? {};
    return VisitFactsBundle.fromJson(json);
  }

  Future<VisitFactsBundle> fetchPatientTimeline(int patientId) async {
    final uri = Uri.parse(
      '${_api.baseUrl}/api/ai/visit-facts/patient/$patientId/timeline',
    );
    final response = await http.get(uri, headers: _api.buildHeaders());
    if (response.statusCode != 200) {
      throw Exception('Timeline failed: ${response.statusCode}');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>? ?? {};
    return VisitFactsBundle(
      facts: (json['timeline'] as List? ?? [])
          .whereType<Map>()
          .map((e) => VisitFactDto.fromJson(Map<String, dynamic>.from(e)))
          .toList(),
      patterns: (json['patterns'] as List? ?? [])
          .whereType<Map>()
          .map((e) => ClinicPatternDto.fromJson(Map<String, dynamic>.from(e)))
          .toList(),
    );
  }

  Future<VisitFactsCommitResult> commitVisitFacts({
    required int appointmentId,
    List<int>? acceptFactIds,
    List<int>? rejectFactIds,
  }) async {
    final uri = Uri.parse('${_api.baseUrl}/api/ai/visit-facts/commit');
    final response = await http.post(
      uri,
      headers: _api.buildHeaders(),
      body: jsonEncode({
        'appointmentId': appointmentId,
        if (acceptFactIds != null) 'acceptFactIds': acceptFactIds,
        if (rejectFactIds != null) 'rejectFactIds': rejectFactIds,
      }),
    );
    if (response.statusCode != 200) {
      throw Exception('Commit visit facts failed: ${response.statusCode}');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>? ?? {};
    return VisitFactsCommitResult.fromJson(json);
  }

  /// Generate AI patient briefing from documents the doctor can access.
  /// [patientId] as string (e.g. from Patient.id). [language] e.g. 'en', 'uz', 'ru'.
  Future<PatientBriefingResult> fetchPatientBriefing(
    String patientId, {
    String language = 'en',
  }) async {
    final uri = Uri.parse('${_api.baseUrl}/api/ai/patient-briefing/$patientId');
    final response = await http.post(
      uri,
      headers: _api.buildHeaders(),
      body: jsonEncode({'language': language}),
    );
    final sc = response.statusCode;
    if (sc == 200) {
      final json = jsonDecode(response.body) as Map<String, dynamic>? ?? {};
      return AiApi.parseBriefingResponse(json);
    }
    if (sc == 400 || sc == 404 || sc == 403) {
      final body = response.body;
      try {
        final json = jsonDecode(body) as Map<String, dynamic>?;
        final message = json?['message'] as String? ?? body;
        throw AiStreamException('BRIEFING_ERROR', message);
      } catch (e) {
        if (e is AiStreamException) rethrow;
        throw AiStreamException('BRIEFING_ERROR', response.body);
      }
    }
    if (sc == 429) {
      var message = 'Briefing failed: HTTP 429';
      try {
        final json = jsonDecode(response.body) as Map<String, dynamic>?;
        message = json?['message'] as String? ?? message;
      } catch (_) {}
      throw AiStreamException('RATE_LIMIT', message);
    }
    throw AiStreamException(
      'AI_UNAVAILABLE',
      'Briefing failed: HTTP $sc',
    );
  }
}

/// Result of patient briefing generation.
class PatientBriefingResult {
  final String briefing;
  final int documentCount;
  final int appointmentCount;

  PatientBriefingResult({
    required this.briefing,
    required this.documentCount,
    this.appointmentCount = 0,
  });
}

class VisitFactDto {
  final int id;
  final int? appointmentId;
  final int patientId;
  final String factType;
  final String text;
  final String? quote;
  final String? scheduleHint;
  final String status;
  final bool reflectionKept;
  final String? dropReason;
  final int? remoteTaskId;
  final String createdAt;
  final String? committedAt;

  VisitFactDto({
    required this.id,
    this.appointmentId,
    required this.patientId,
    required this.factType,
    required this.text,
    this.quote,
    this.scheduleHint,
    required this.status,
    required this.reflectionKept,
    this.dropReason,
    this.remoteTaskId,
    required this.createdAt,
    this.committedAt,
  });

  factory VisitFactDto.fromJson(Map<String, dynamic> json) => VisitFactDto(
        id: (json['id'] as num).toInt(),
        appointmentId: (json['appointmentId'] as num?)?.toInt(),
        patientId: (json['patientId'] as num).toInt(),
        factType: json['factType'] as String? ?? '',
        text: json['text'] as String? ?? '',
        quote: json['quote'] as String?,
        scheduleHint: json['scheduleHint'] as String?,
        status: json['status'] as String? ?? '',
        reflectionKept: json['reflectionKept'] as bool? ?? true,
        dropReason: json['dropReason'] as String?,
        remoteTaskId: (json['remoteTaskId'] as num?)?.toInt(),
        createdAt: json['createdAt'] as String? ?? '',
        committedAt: json['committedAt'] as String?,
      );

  bool get isDraft => status == 'DRAFT';
  bool get isDropped => status == 'DROPPED' || !reflectionKept;
  bool get isCommitted => status == 'COMMITTED';
}

class ClinicPatternDto {
  final String key;
  final String label;
  final String factType;
  final int count;

  ClinicPatternDto({
    required this.key,
    required this.label,
    required this.factType,
    required this.count,
  });

  factory ClinicPatternDto.fromJson(Map<String, dynamic> json) =>
      ClinicPatternDto(
        key: json['key'] as String? ?? '',
        label: json['label'] as String? ?? '',
        factType: json['factType'] as String? ?? '',
        count: (json['count'] as num?)?.toInt() ?? 0,
      );
}

class VisitFactsBundle {
  final List<VisitFactDto> facts;
  final List<ClinicPatternDto> patterns;

  VisitFactsBundle({required this.facts, required this.patterns});

  factory VisitFactsBundle.fromJson(Map<String, dynamic> json) =>
      VisitFactsBundle(
        facts: (json['facts'] as List? ?? [])
            .whereType<Map>()
            .map((e) => VisitFactDto.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
        patterns: (json['patterns'] as List? ?? [])
            .whereType<Map>()
            .map((e) => ClinicPatternDto.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
      );

  List<VisitFactDto> get keptDrafts =>
      facts.where((f) => f.isDraft && f.reflectionKept).toList();

  List<VisitFactDto> get dropped => facts.where((f) => f.isDropped).toList();
}

class VisitFactsCommitResult {
  final List<VisitFactDto> committedFacts;
  final List<int> createdTaskIds;
  final List<ClinicPatternDto> patterns;

  VisitFactsCommitResult({
    required this.committedFacts,
    required this.createdTaskIds,
    required this.patterns,
  });

  factory VisitFactsCommitResult.fromJson(Map<String, dynamic> json) =>
      VisitFactsCommitResult(
        committedFacts: (json['committedFacts'] as List? ?? [])
            .whereType<Map>()
            .map((e) => VisitFactDto.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
        createdTaskIds: (json['createdTaskIds'] as List? ?? [])
            .map((e) => (e as num).toInt())
            .toList(),
        patterns: (json['patterns'] as List? ?? [])
            .whereType<Map>()
            .map((e) => ClinicPatternDto.fromJson(Map<String, dynamic>.from(e)))
            .toList(),
      );
}
