import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../../../core/api/api_client.dart';
import '../../../core/api/ai_message.dart';

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

class AiApi {
  final ApiClient _api;

  AiApi(this._api);

  /// Patient-aware AI stream; yields [AiTokenEvent] then [AiDraftReadyEvent] when draft is created.
  /// Throws [AiStreamException] on structured errors.
  Stream<AiStreamEvent> streamAi({
    required List<AiMessage> messages,
    String? question,
    required String language,
    int? patientId,
    int? consultationId,
  }) async* {
    final uri = Uri.parse('${_api.baseUrl}/api/ai/stream');

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
