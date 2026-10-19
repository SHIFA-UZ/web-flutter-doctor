import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/core/api/consultation_notes_api.dart';

void main() {
  test('manual Intelligence notes join for the appointment notes field', () {
    const notes = [
      ConsultationNoteDto(
        id: 1,
        body: 'Scribe summary',
        source: 'AI_DRAFT',
        createdAt: '2026-10-19T10:00:00Z',
      ),
      ConsultationNoteDto(
        id: 2,
        body: 'Patient reports mild pain.',
        source: 'MANUAL',
        createdAt: '2026-10-19T10:05:00Z',
      ),
      ConsultationNoteDto(
        id: 3,
        body: 'Added ibuprofen.',
        source: 'MANUAL',
        createdAt: '2026-10-19T10:06:00Z',
      ),
    ];
    expect(
      joinedManualConsultationNotes(notes),
      'Patient reports mild pain.\n\nAdded ibuprofen.',
    );
  });
}
