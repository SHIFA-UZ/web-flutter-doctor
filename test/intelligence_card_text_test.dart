import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_card_text.dart';

void main() {
  final uz = AppLocalizations(const Locale('uz'));
  final en = AppLocalizations(const Locale('en'));

  test('patient appointment card labels follow the app language', () {
    expect(intelligenceRangeLabel(uz, 'this week'), 'Shu hafta');
    expect(
      intelligenceDateLabel(uz, isoDate: '2026-10-20', fallback: '20 Oct 2026'),
      '20 Okt 2026',
    );
    expect(intelligenceStatusLabel(uz, 'COMPLETED'), 'Yakunlangan');
    expect(intelligenceStatusLabel(uz, 'CONFIRMED'), 'Tasdiqlangan');
    expect(intelligenceStatusLabel(uz, 'CANCELLED'), 'Bekor qilingan');
    expect(intelligenceRangeLabel(en, 'this week'), 'This week');
    expect(intelligenceDateLabel(en, fallback: '20 Oct 2026'), '20 Oct 2026');
  });

  test('cancellation cards use the app language', () {
    expect(
      localizeIntelligenceHint(
        uz,
        'Which visits should I cancel? Tell me the day, or say all for a period such as next week.',
      ),
      'Qaysi qabullarni bekor qilishim kerak? Kunini ayting yoki kelasi hafta kabi ma’lum bir davr uchun hammasi deng.',
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'I can cancel 1 upcoming visit: Botir 2 Muzrabov Friday 23 October at 16:00. Say yes to cancel them. Nothing has been cancelled yet.',
      ),
      'Men yaqinlashib kelayotgan 1 ta qabulni bekor qila olaman: Botir 2 Muzrabov Juma 23-oktabr soat 16:00 da. Ularni bekor qilish uchun «ha» deng. Hali hech narsa bekor qilinmadi.',
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'Nothing was cancelled. Say yes if you want me to cancel the visits I just listed.',
      ),
      'Hech narsa bekor qilinmadi. Agar hozirgina sanab o‘tgan qabullarimni bekor qilishimni xohlasangiz, «ha» deng.',
    );
  });

  test('reschedule cards follow the app language', () {
    expect(
      intelligenceDateTimeLabel(uz, '20 Oct 2026 02:00'),
      '20 Okt 2026 02:00',
    );
    expect(
      intelligenceDateTimeLabel(en, '20 Oct 2026 02:00'),
      '20 Oct 2026 02:00',
    );
    final moved = localizeIntelligenceHint(
      uz,
      'Botir 2 Muzrabov is on Tuesday, 20 October at 02:00. Which day should I move that visit to?',
    );
    expect(moved, contains('Seshanba'));
    expect(moved, contains('oktabr'));
    expect(moved, contains('Botir 2 Muzrabov'));
    expect(moved, contains('02:00'));
    expect(moved, isNot(contains('Tuesday')));
    expect(moved, isNot(contains('October')));
    expect(
      localizeIntelligenceHint(
        en,
        'Botir 2 Muzrabov is on Tuesday, 20 October at 02:00. Which day should I move that visit to?',
      ),
      'Botir 2 Muzrabov is on Tuesday, 20 October at 02:00. Which day should I move that visit to?',
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'I could not tell which visit to move. Say the patient and the day.',
      ),
      'Qaysi tashrifni ko‘chirishni bilmayman. Bemor va kunni ayting.',
    );
    expect(intelligencePatientLabel(uz, 'Patient'), 'Bemor');
    expect(intelligencePatientLabel(uz, null), 'Bemor');
    expect(
      intelligencePatientLabel(uz, 'Botir 2 Muzrabov'),
      'Botir 2 Muzrabov',
    );
    expect(intelligenceDocumentLabel(uz, 'Document'), 'Hujjat');
    expect(intelligenceDocumentLabel(uz, 'Chest X-ray'), 'Chest X-ray');
  });

  test('screenshot search and booking sentences are Uzbek', () {
    const noMatchEn = 'No patients matched "Bruno nol" in your roster.';
    const bookedEn = 'Booked Botir 2 Muzrabov at 11:00 on Friday, 23 October.';
    expect(
      localizeIntelligenceHint(uz, noMatchEn),
      'Ro‘yxatingizda “Bruno nol” bo‘yicha bemor topilmadi.',
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'No patients matched “Bruno nol” in your roster.',
      ),
      'Ro‘yxatingizda “Bruno nol” bo‘yicha bemor topilmadi.',
    );
    expect(localizeIntelligenceHint(en, noMatchEn), noMatchEn);
    final booked = localizeIntelligenceHint(uz, bookedEn);
    expect(
      booked,
      'Botir 2 Muzrabov 23-oktabr, Juma kuni, soat 11:00 ga band qilindi.',
    );
    expect(booked, isNot(contains('Booked')));
    expect(booked, isNot(contains('Friday')));
    expect(booked, isNot(contains('October')));
    expect(localizeIntelligenceHint(en, bookedEn), bookedEn);
    final booking = localizeIntelligenceHint(
      uz,
      'Booking Botir 2 Muzrabov at 11:00 on Friday, 23 October.',
    );
    expect(booking, isNot(contains('Booking')));
    expect(booking, isNot(contains('Friday')));
    expect(booking, isNot(contains('October')));
    expect(booking, contains('Juma'));
    expect(booking, contains('oktabr'));
    expect(
      localizeIntelligenceHint(
        uz,
        'Please confirm booking Botir Muzrabov at 09:00 on 22-oktabr, Payshanba.',
      ),
      'Iltimos, Botir Muzrabovning 22-oktabr, Payshanba kuni soat 09:00 dagi qabulini tasdiqlang.',
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'Please confirm booking Botir Muzrabov at 09:00 on Thursday, 22 October.',
      ),
      'Iltimos, Botir Muzrabovning 22-oktabr, Payshanba kuni soat 09:00 dagi qabulini tasdiqlang.',
    );
    expect(
      localizeIntelligenceHint(
        en,
        'Please confirm booking Botir Muzrabov at 09:00 on Thursday, 22 October.',
      ),
      'Please confirm booking Botir Muzrabov at 09:00 on Thursday, 22 October.',
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'Booked Botir Muzrabov at 09:00 on 2026-10-22.',
      ),
      'Botir Muzrabov 22-oktabr, Payshanba kuni, soat 09:00 ga band qilindi.',
    );
  });

  test('other tool hints follow the app language', () {
    expect(
      localizeIntelligenceHint(
        uz,
        'You have 3 appointments today. The first is with Patient in about 5 minutes.',
      ),
      'Bugun 3 ta uchrashuv bor. Birinchisi Bemor bilan taxminan 5 daqiqadan so‘ng.',
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'No patients matched "Botil" in your roster.',
      ),
      'Ro‘yxatingizda “Botil” bo‘yicha bemor topilmadi.',
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'No exact match for "Botil". Did you mean Botir 2 Muzrabov?',
      ),
      contains('Botir 2 Muzrabov'),
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'You have 2 appointments scheduled last week.',
      ),
      contains('2'),
    );
    expect(
      localizeIntelligenceHint(
        uz,
        'You have 2 appointments scheduled last week.',
      ),
      isNot(contains('scheduled')),
    );
  });

  test('today timestamp follows the app language', () {
    final at = DateTime.now();
    expect(intelligenceChatTimestamp(uz, at), startsWith('Bugun · '));
    expect(intelligenceChatTimestamp(en, at), startsWith('Today · '));
  });
}
