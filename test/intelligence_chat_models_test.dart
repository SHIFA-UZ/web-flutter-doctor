import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/features/intelligence/domain/intelligence_chat_models.dart';

IntelligenceChatMessage _msg(
  String role,
  String content, {
  String? toolName,
  Map<String, dynamic>? card,
}) {
  return IntelligenceChatMessage(
    role: role,
    content: content,
    at: DateTime.utc(2026, 10, 18, 12),
    toolName: toolName,
    cardPayload: card,
  );
}

void main() {
  test('Uzbek Salom Shifa is a wake phrase and strips like Hey Shifa', () {
    expect(IntelligenceChatTitles.containsWakePhrase('Salom Shifa'), isTrue);
    expect(IntelligenceChatTitles.containsWakePhrase('salom shifa'), isTrue);
    expect(
      IntelligenceChatTitles.containsWakePhrase('Assalomu alaykum, Shifa'),
      isTrue,
    );
    expect(IntelligenceChatTitles.containsWakePhrase('Салом Шифа'), isTrue);
    expect(IntelligenceChatTitles.containsWakePhrase('Hey Shifa'), isTrue);
    expect(IntelligenceChatTitles.containsWakePhrase('bugun bemorlar'), isFalse);
    expect(IntelligenceChatTitles.stripWakePhrase('Salom Shifa'), isEmpty);
    expect(
      IntelligenceChatTitles.stripWakePhrase(
        'Salom Shifa, bugun bo\'sh slotlar',
      ),
      "bugun bo'sh slotlar",
    );
  });

  test('starter prompts and wake phrases become short titles', () {
    expect(
      IntelligenceChatTitles.shortTitle('Summarize my schedule for today.'),
      "Today's schedule",
    );
    expect(
      IntelligenceChatTitles.shortTitle(
        'Hey Shifa, summarize my schedule for today',
      ),
      "Today's schedule",
    );
    expect(
      IntelligenceChatTitles.shortTitle(
        'Salom Shifa, summarize my schedule for today',
      ),
      "Today's schedule",
    );
    expect(
      IntelligenceChatTitles.shortTitle('What is my schedule today?'),
      "Today's schedule",
    );
    expect(
      IntelligenceChatTitles.shortTitle('Help me find a patient.'),
      'Find a patient',
    );
    expect(
      IntelligenceChatTitles.shortTitle(
        'Help me manage an appointment — find a patient and propose free slots.',
      ),
      'Manage appointments',
    );
    expect(
      IntelligenceChatTitles.shortTitle('Помогите найти пациента.'),
      'Найти пациента',
    );
  });

  test('long titles trim on a word and previews use the latest message', () {
    const long =
        'Please help me find an alternative time for the consultation next week in the afternoon';
    final title = IntelligenceChatTitles.shortTitle(long);
    expect(title.endsWith('…'), isTrue);
    expect(title.length, lessThan(long.length));

    final session = IntelligenceChatSession(
      id: '1',
      title: IntelligenceChatSession.untitled,
      createdAt: DateTime.utc(2026, 10, 18),
      updatedAt: DateTime.utc(2026, 10, 18, 12),
      messages: [
        _msg('user', 'Summarize my schedule for today.'),
        _msg('assistant', 'You have three visits this afternoon.'),
      ],
    );
    expect(session.displayTitle, "Today's schedule");
    expect(session.preview, 'You have three visits this afternoon.');
    expect(session.preview, isNot(session.displayTitle));
  });

  test('a renamed title is kept when messages change', () {
    final session = IntelligenceChatSession(
      id: '1',
      title: 'Appointment rescheduling',
      titleCustom: true,
      createdAt: DateTime.utc(2026, 10, 18),
      updatedAt: DateTime.utc(2026, 10, 18),
      messages: [_msg('user', 'Hey Shifa how are you')],
    );
    final restored = IntelligenceChatSession.fromJson(session.toJson());
    expect(restored.titleCustom, isTrue);
    expect(restored.displayTitle, 'Appointment rescheduling');

    final updated = IntelligenceChatSession(
      id: restored.id,
      title: restored.title,
      titleCustom: restored.titleCustom,
      createdAt: restored.createdAt,
      updatedAt: DateTime.utc(2026, 10, 18, 13),
      messages: [
        ...restored.messages,
        _msg('assistant', 'I can help with that visit.'),
      ],
    );
    expect(updated.displayTitle, 'Appointment rescheduling');
    expect(updated.preview, 'I can help with that visit.');
  });

  test('patient link comes from a patient card, not the day schedule', () {
    final schedule = IntelligenceChatSession(
      id: 's',
      title: IntelligenceChatSession.untitled,
      createdAt: DateTime.utc(2026, 10, 18),
      updatedAt: DateTime.utc(2026, 10, 18),
      messages: [
        _msg('user', 'Summarize my schedule for today.'),
        _msg(
          'assistant',
          'Three visits today.',
          toolName: 'get_today_schedule',
          card: {
            'cardType': 'schedule',
            'appointments': [
              {'patientId': 4, 'patientName': 'Ada Lovelace'},
            ],
          },
        ),
      ],
    );
    expect(schedule.patientLinked, isFalse);

    final search = IntelligenceChatSession(
      id: 'p',
      title: IntelligenceChatSession.untitled,
      createdAt: DateTime.utc(2026, 10, 18),
      updatedAt: DateTime.utc(2026, 10, 18),
      messages: [
        _msg('user', 'Help me find a patient.'),
        _msg(
          'assistant',
          'Found Ada Lovelace.',
          toolName: 'search_patients',
          card: {
            'cardType': 'patient_search',
            'matches': [
              {'patientId': 9, 'patientName': 'Ada Lovelace'},
            ],
          },
        ),
      ],
    );
    expect(search.patientLinked, isTrue);
    expect(search.patientMark.name, 'Ada Lovelace');
    expect(search.matchesQuery('lovelace'), isTrue);
    expect(search.matchesQuery('schedule'), isFalse);

    final placeholder = IntelligenceChatContext.patientName({
      'cardType': 'propose_appointment',
      'patientId': 3,
      'patientName': 'Patient',
    }, 'propose_appointment');
    expect(
      IntelligenceChatContext.isPatientLinked({
        'cardType': 'propose_appointment',
        'patientId': 3,
        'patientName': 'Patient',
      }, 'propose_appointment'),
      isTrue,
    );
    expect(placeholder, isNull);
  });

  test('older chats without titleCustom still decode', () {
    final session = IntelligenceChatSession.fromJson({
      'id': 'old',
      'title': 'hey Shifa',
      'createdAt': '2026-10-01T00:00:00.000Z',
      'updatedAt': '2026-10-01T00:00:00.000Z',
      'messages': [
        {
          'role': 'user',
          'content': 'how are you',
          'at': '2026-10-01T00:00:00.000Z',
        },
      ],
    });
    expect(session.titleCustom, isFalse);
    expect(session.displayTitle, 'how are you');
  });
}
