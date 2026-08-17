import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/core/api/consultation_notes_api.dart';

void main() {
  test('composeVisitDocumentationText puts scribe before doctor notes', () {
    const scribe = ConsultationNoteDto(
      id: 1,
      body: 'AI protocol and transcript',
      source: 'AI_DRAFT',
      createdAt: '',
    );
    const doctor = ConsultationNoteDto(
      id: 2,
      body: 'Follow up next week',
      source: 'MANUAL',
      createdAt: '',
    );
    final text = composeVisitDocumentationText(
      scribeHeading: 'From Shifa AI',
      doctorHeading: 'Doctor notes',
      notes: const [doctor, scribe],
    );
    expect(text.indexOf('AI protocol'), lessThan(text.indexOf('Follow up next week')));
    expect(text, contains('From Shifa AI'));
    expect(text, contains('Doctor notes'));
  });
}
