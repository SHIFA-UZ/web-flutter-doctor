import 'dart:convert';

import 'api_client.dart';

/// Consultation note for an appointment (e.g. confirmed Shifa AI draft).
class ConsultationNoteDto {
  final int id;
  final String? body;
  final String? subjective;
  final String? assessment;
  final String? plan;
  final String source; // MANUAL | AI_DRAFT
  final String createdAt;

  const ConsultationNoteDto({
    required this.id,
    this.body,
    this.subjective,
    this.assessment,
    this.plan,
    required this.source,
    required this.createdAt,
  });

  factory ConsultationNoteDto.fromJson(Map<String, dynamic> json) {
    return ConsultationNoteDto(
      id: (json['id'] as num).toInt(),
      body: json['body'] as String?,
      subjective: json['subjective'] as String?,
      assessment: json['assessment'] as String?,
      plan: json['plan'] as String?,
      source: json['source'] as String? ?? 'MANUAL',
      createdAt: json['createdAt'] as String? ?? '',
    );
  }

  bool get isFromAi => source == 'AI_DRAFT';

  /// Display text: body if set, otherwise subjective/assessment/plan combined.
  /// Transcripts are omitted so appointment cards show the clinical summary.
  String get displayText {
    if (body != null && body!.trim().isNotEmpty) {
      return stripScribeTranscript(body!);
    }
    final parts = <String>[];
    if (subjective != null && subjective!.trim().isNotEmpty) parts.add(subjective!);
    if (assessment != null && assessment!.trim().isNotEmpty) parts.add(assessment!);
    if (plan != null && plan!.trim().isNotEmpty) parts.add(plan!);
    return parts.join('\n\n');
  }
}

/// Fetches consultation notes for an appointment (doctor-only).
Future<List<ConsultationNoteDto>> getConsultationNotesForAppointment({
  required ApiClient api,
  required String appointmentId,
}) async {
  final res = await api.get('/api/appointments/$appointmentId/consultation-notes');
  if (res.statusCode != 200) return [];
  final list = json.decode(utf8.decode(res.bodyBytes)) as List<dynamic>?;
  if (list == null) return [];
  return list
      .map((e) => ConsultationNoteDto.fromJson(e as Map<String, dynamic>))
      .toList();
}

/// Pending AI draft note for an appointment (e.g. AI Scribe). Shown with Confirm/Discard until saved as consultation note.
class DraftNoteDto {
  final String id;
  final String aiLabel;
  final String body;
  final String createdAt;
  final List<IcdSuggestionDto> icdSuggestions;

  const DraftNoteDto({
    required this.id,
    required this.aiLabel,
    required this.body,
    required this.createdAt,
    this.icdSuggestions = const [],
  });

  factory DraftNoteDto.fromJson(Map<String, dynamic> json) {
    final rawSuggestions = json['icdSuggestions'];
    final suggestions = <IcdSuggestionDto>[];
    if (rawSuggestions is List) {
      for (final item in rawSuggestions) {
        if (item is Map<String, dynamic>) {
          suggestions.add(IcdSuggestionDto.fromJson(item));
        } else if (item is Map) {
          suggestions.add(IcdSuggestionDto.fromJson(Map<String, dynamic>.from(item)));
        }
      }
    }
    return DraftNoteDto(
      id: json['id'] as String? ?? '',
      aiLabel: json['aiLabel'] as String? ?? 'Draft',
      body: json['body'] as String? ?? '',
      createdAt: json['createdAt'] as String? ?? '',
      icdSuggestions: suggestions,
    );
  }
}

class IcdSuggestionDto {
  final String code;
  final String title;
  final double? confidence;
  final bool isTop;

  const IcdSuggestionDto({
    required this.code,
    required this.title,
    this.confidence,
    this.isTop = false,
  });

  factory IcdSuggestionDto.fromJson(Map<String, dynamic> json) {
    return IcdSuggestionDto(
      code: (json['code'] as String?) ?? '',
      title: (json['title'] as String?) ?? '',
      confidence: (json['confidence'] as num?)?.toDouble(),
      isTop: json['isTop'] == true,
    );
  }
}

/// Fetches pending draft notes for an appointment (doctor-only).
Future<List<DraftNoteDto>> getDraftNotesForAppointment({
  required ApiClient api,
  required String appointmentId,
}) async {
  final res = await api.get('/api/appointments/$appointmentId/draft-notes');
  if (res.statusCode != 200) return [];
  final list = json.decode(utf8.decode(res.bodyBytes)) as List<dynamic>?;
  if (list == null) return [];
  return list
      .map((e) => DraftNoteDto.fromJson(e as Map<String, dynamic>))
      .toList();
}

class CompleteAppointmentResult {
  final bool scribePending;
  final bool hasDocumentation;
  final bool hasScribeNote;

  const CompleteAppointmentResult({
    this.scribePending = false,
    this.hasDocumentation = false,
    this.hasScribeNote = false,
  });

  factory CompleteAppointmentResult.fromJson(Map<String, dynamic> json) {
    return CompleteAppointmentResult(
      scribePending: json['scribePending'] == true,
      hasDocumentation: json['hasDocumentation'] == true,
      hasScribeNote: json['hasScribeNote'] == true,
    );
  }
}

class ScribeStatusDto {
  final String status;
  final bool hasDocumentation;
  final bool hasScribeNote;

  const ScribeStatusDto({
    required this.status,
    this.hasDocumentation = false,
    this.hasScribeNote = false,
  });

  factory ScribeStatusDto.fromJson(Map<String, dynamic> json) {
    return ScribeStatusDto(
      status: json['status'] as String? ?? 'none',
      hasDocumentation: json['hasDocumentation'] == true,
      hasScribeNote: json['hasScribeNote'] == true,
    );
  }
}

Future<CompleteAppointmentResult> completeAppointmentVisit({
  required ApiClient api,
  required String appointmentId,
  int? clinicId,
  String? doctorNotes,
  bool awaitingScribe = false,
}) async {
  final payload = <String, dynamic>{
    if (clinicId != null) 'clinicId': clinicId,
    if (doctorNotes != null && doctorNotes.trim().isNotEmpty)
      'doctorNotes': doctorNotes.trim(),
    if (awaitingScribe) 'awaitingScribe': true,
  };
  final res = await api.put(
    '/api/appointments/$appointmentId/complete',
    payload,
  );
  if (res.statusCode < 200 || res.statusCode >= 300) {
    throw Exception('Failed to complete appointment: ${res.statusCode}');
  }
  try {
    final map = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return CompleteAppointmentResult.fromJson(map);
  } catch (_) {
    return const CompleteAppointmentResult();
  }
}

Future<ScribeStatusDto> getAppointmentScribeStatus({
  required ApiClient api,
  required String appointmentId,
}) async {
  final res = await api.get('/api/appointments/$appointmentId/scribe-status');
  if (res.statusCode != 200) {
    return const ScribeStatusDto(status: 'none');
  }
  try {
    final map = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return ScribeStatusDto.fromJson(map);
  } catch (_) {
    return const ScribeStatusDto(status: 'none');
  }
}

Future<ScribeStatusDto> waitForScribeReady({
  required ApiClient api,
  required String appointmentId,
  Duration timeout = const Duration(seconds: 120),
  Duration interval = const Duration(seconds: 3),
}) async {
  final deadline = DateTime.now().add(timeout);
  var last = await getAppointmentScribeStatus(
    api: api,
    appointmentId: appointmentId,
  );
  while (!last.hasScribeNote && DateTime.now().isBefore(deadline)) {
    await Future<void>.delayed(interval);
    last = await getAppointmentScribeStatus(
      api: api,
      appointmentId: appointmentId,
    );
  }
  return last;
}

/// SOAP / protocol only — drop the raw visit transcript from appointment-card display.
String stripScribeTranscript(String text) {
  final match = RegExp(
    r'^TRANSCRIPT:\s*',
    caseSensitive: false,
    multiLine: true,
  ).firstMatch(text);
  if (match == null) return text.trim();
  return text.substring(0, match.start).trim();
}

/// Scribe / AI notes first, then the doctor's own notes.
String composeVisitDocumentationText({
  required String scribeHeading,
  required String doctorHeading,
  required List<ConsultationNoteDto> notes,
  List<DraftNoteDto> drafts = const [],
  String extraDoctorNotes = '',
}) {
  final scribeParts = <String>[
    ...notes.where((n) => n.isFromAi).map((n) => n.displayText.trim()),
    ...drafts.map((d) => d.body.trim()),
  ].where((t) => t.isNotEmpty).toList();
  final doctorParts = <String>[
    ...notes.where((n) => !n.isFromAi).map((n) => n.displayText.trim()),
    extraDoctorNotes.trim(),
  ].where((t) => t.isNotEmpty).toList();

  final blocks = <String>[];
  if (scribeParts.isNotEmpty) {
    blocks.add('$scribeHeading\n\n${scribeParts.join('\n\n')}');
  }
  if (doctorParts.isNotEmpty) {
    blocks.add('$doctorHeading\n\n${doctorParts.join('\n\n')}');
  }
  return blocks.join('\n\n').trim();
}
