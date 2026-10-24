import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

/// Turns English tool-card labels into the app language.
///
/// Appointment cards store period names, dates, and statuses in English.
/// The chat already answers in the doctor's language; these helpers keep
/// the card itself in the same language as the rest of the app.
String intelligenceStatusLabel(AppLocalizations l10n, String status) {
  switch (status.trim().toUpperCase()) {
    case 'COMPLETED':
      return l10n.translate('appointmentStatusCompleted');
    case 'CONFIRMED':
      return l10n.translate('appointmentStatusConfirmed');
    case 'CANCELLED':
    case 'CANCELED':
      return l10n.translate('appointmentStatusCancelled');
    case 'REQUESTED':
      return l10n.translate('appointmentStatusRequested');
    case 'IN_PROGRESS':
    case 'STARTED':
      return l10n.translate('appointmentStatusInProgress');
    case 'PENDING':
      return l10n.translate('pending');
    case 'SCHEDULED':
      return l10n.translate('scheduled');
    case 'NO_SHOW':
      return l10n.translate('appointmentStatusNoShow');
    default:
      return status;
  }
}

String intelligenceDateLabel(
  AppLocalizations l10n, {
  String? isoDate,
  String? fallback,
}) {
  final iso = _parseIsoDate(isoDate);
  if (iso != null) {
    return '${iso.day} ${l10n.monthShort(iso.month)} ${iso.year}';
  }
  final parsed = _parseEnglishDate(fallback);
  if (parsed != null) {
    return '${parsed.day} ${l10n.monthShort(parsed.month)} ${parsed.year}';
  }
  return fallback ?? isoDate ?? '';
}

/// A card field that may be an ISO date, an English date, or a period name.
String intelligenceFieldLabel(
  AppLocalizations l10n, {
  String? isoDate,
  String? fallback,
}) {
  final raw = (fallback ?? '').trim();
  final dated = intelligenceDateLabel(
    l10n,
    isoDate: isoDate,
    fallback: raw.isEmpty ? null : raw,
  );
  if (dated.isEmpty) return raw;
  if (dated != raw) return dated;
  return intelligenceRangeLabel(l10n, dated);
}

/// "Patient" / "The patient" are placeholders, not a person's name.
String intelligencePatientLabel(AppLocalizations l10n, String? raw) {
  final name = (raw ?? '').trim();
  switch (name.toLowerCase()) {
    case '':
    case 'patient':
    case 'the patient':
    case 'this patient':
    case 'your next patient':
      return l10n.translate('intelPatient');
    default:
      return name;
  }
}

/// "Document" is a placeholder title, not the file's real name.
String intelligenceDocumentLabel(AppLocalizations l10n, String? raw) {
  final title = (raw ?? '').trim();
  if (title.isEmpty || title.toLowerCase() == 'document') {
    return l10n.translate('intelDocument');
  }
  return title;
}

/// Draft-fact category stored as an English enum.
String intelligenceFactTypeLabel(AppLocalizations l10n, String raw) {
  switch (raw.trim().toUpperCase()) {
    case 'SYMPTOM':
      return _line(l10n, en: 'Symptom', uz: 'Simptom', ru: 'Симптом');
    case 'DIAGNOSIS':
      return _line(l10n, en: 'Diagnosis', uz: 'Tashxis', ru: 'Диагноз');
    case 'THERAPY':
      return _line(l10n, en: 'Therapy', uz: 'Terapiya', ru: 'Терапия');
    case 'MEDICATION':
      return _line(l10n, en: 'Medication', uz: 'Dori', ru: 'Лекарство');
    default:
      return raw;
  }
}

/// "20 Oct 2026" or "20 Oct 2026 02:00".
String intelligenceDateTimeLabel(AppLocalizations l10n, String raw) {
  final text = raw.trim();
  final match = RegExp(
    r'^(\d{1,2}\s+[A-Za-z]{3,9}(?:\s+\d{4})?)(?:\s+(\d{2}:\d{2}))?$',
  ).firstMatch(text);
  if (match == null) return intelligenceFieldLabel(l10n, fallback: text);
  final date = intelligenceDateLabel(l10n, fallback: match.group(1));
  final time = match.group(2);
  if (time == null || date == match.group(1)) {
    return time == null ? date : '$date $time';
  }
  return '$date $time';
}

String intelligenceRangeLabel(AppLocalizations l10n, String raw) {
  final text = raw.trim();
  final lower = text.toLowerCase();
  switch (lower) {
    case 'this week':
      return l10n.translate('intelRangeThisWeek');
    case 'next week':
      return l10n.translate('intelRangeNextWeek');
    case 'today':
      return l10n.translate('today');
    case 'tomorrow':
      return l10n.translate('intelTomorrow');
    case 'yesterday':
      return l10n.translate('yesterday');
    case 'upcoming':
      return l10n.translate('upcomingAppointments');
    case 'last week':
    case 'this month':
    case 'last month':
    case 'next month':
    case 'this quarter':
    case 'last quarter':
    case 'next quarter':
    case 'this year':
    case 'last year':
    case 'next year':
    case 'coming up':
    case 'from tomorrow onwards':
    case 'friday of next week':
    case 'the upcoming year':
      return _namedPeriod(l10n, lower);
  }
  final rolling = RegExp(
    r'^the (next|last) (\d+) (day|days|week|weeks|month|months)$',
  ).firstMatch(lower);
  if (rolling != null) {
    return _rollingPeriod(
      l10n,
      rolling.group(1)!,
      rolling.group(2)!,
      rolling.group(3)!,
    );
  }
  final bounded = RegExp(
    r'^(on|since|before|after) (\d{1,2} [A-Za-z]+ \d{4})$',
  ).firstMatch(text);
  if (bounded != null) {
    return _boundedDate(l10n, bounded.group(1)!, bounded.group(2)!);
  }
  final between = RegExp(
    r'^from (\d{1,2} [A-Za-z]+ \d{4}) until (\d{1,2} [A-Za-z]+ \d{4})$',
  ).firstMatch(text);
  if (between != null) {
    return _betweenDates(l10n, between.group(1)!, between.group(2)!);
  }
  final span = RegExp(
    r'^(this week|next week)\s*\((.+)\)\s*$',
    caseSensitive: false,
  ).firstMatch(text);
  if (span != null) {
    final label = span.group(1)!.toLowerCase() == 'next week'
        ? l10n.translate('intelRangeNextWeek')
        : l10n.translate('intelRangeThisWeek');
    return '$label (${_localizeEnglishDates(l10n, span.group(2)!)})';
  }
  final upcoming = RegExp(
    r'^upcoming\s*\(from\s+(.+)\)\s*$',
    caseSensitive: false,
  ).firstMatch(text);
  if (upcoming != null) {
    return l10n
        .translate('intelRangeUpcomingFrom')
        .replaceAll('{date}', _localizeEnglishDates(l10n, upcoming.group(1)!));
  }
  return _localizeEnglishDates(l10n, text);
}

/// Localizes known English cancellation hints. Other text is left as sent.
bool intelligenceToolLineStillEnglish(String text) {
  return RegExp(
    r'\b(Booked|Booking|Moved|Moving|No patients matched|Please confirm)\b',
  ).hasMatch(text);
}

String localizeIntelligenceHint(AppLocalizations l10n, String hint) {
  final text = _normalizeToolHint(hint);
  if (text.isEmpty) return text;
  const exact = <String, String>{
    'Which visits should I cancel? Tell me the day, or say all for a period such as next week.':
        'intelCancelAskWhich',
    'Nothing was cancelled. Say yes if you want me to cancel the visits I just listed.':
        'intelCancelNothing',
    'No visits in that period can be cancelled. Nothing was changed.':
        'intelCancelNoneEligible',
    "I don't have a cancellation waiting for confirmation. Tell me which visits to cancel.":
        'intelCancelNotWaiting',
    'I could not find that visit.': 'intelCancelNotFound',
  };
  final key = exact[text];
  if (key != null) return l10n.translate(key);

  final tooMany = RegExp(
    r'^That matches more than (\d+) visits\. Narrow the period before I cancel anything\.$',
  ).firstMatch(text);
  if (tooMany != null) {
    return l10n
        .translate('intelCancelTooMany')
        .replaceAll('{count}', tooMany.group(1)!);
  }

  final preview = RegExp(
    r'^I can cancel (\d+) upcoming visit(?:s)?: (.+?)\.(?: (\d+) cannot be cancelled and will be left as they are\.)? Say yes to cancel them\. Nothing has been cancelled yet\.$',
  ).firstMatch(text);
  if (preview != null) {
    final count = preview.group(1)!;
    final names = _localizeVisitList(l10n, preview.group(2)!);
    final visitWord = count == '1'
        ? l10n.translate('intelCancelVisit')
        : l10n.translate('intelCancelVisits');
    var sentence = l10n
        .translate('intelCancelPreview')
        .replaceAll('{count}', count)
        .replaceAll('{visitWord}', visitWord)
        .replaceAll('{names}', names);
    final skipped = preview.group(3);
    if (skipped != null) {
      sentence =
          '$sentence ${l10n.translate('intelCancelSkipped').replaceAll('{count}', skipped)}';
    }
    return sentence;
  }

  if (l10n.locale.languageCode == 'en') return text;
  final templated = _matchToolSentence(l10n, text);
  if (templated != null) return templated;
  final parts = text.split(RegExp(r'(?<=\.)\s+(?=[A-ZА-ЯЁ])'));
  if (parts.length > 1) {
    final joined = parts
        .map((part) => localizeIntelligenceHint(l10n, part.trim()))
        .join(' ');
    if (joined != text) return joined;
  }
  final localized = _localizeEmbeddedCalendar(l10n, text);
  return localized == text ? text : localized;
}

String intelligenceChatTimestamp(AppLocalizations l10n, DateTime at) {
  final local = at.toLocal();
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(local.year, local.month, local.day);
  final time =
      '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  if (day == today) return '${l10n.translate('today')} · $time';
  final yesterday = today.subtract(const Duration(days: 1));
  if (day == yesterday) return '${l10n.translate('yesterday')} · $time';
  return '${local.day} ${l10n.monthShort(local.month)} ${local.year} · $time';
}

String? _matchToolSentence(AppLocalizations l10n, String text) {
  final exact = _exactSentences(l10n)[text];
  if (exact != null) return exact;

  final moveAsk = RegExp(
    r'^(.+?) is on ([A-Za-z]+), (\d{1,2}) ([A-Za-z]+) at (\d{2}:\d{2})\. Which day should I move that visit to\?$',
  ).firstMatch(text);
  if (moveAsk != null) {
    return _fill(
      _line(
        l10n,
        en: '{name} is on {when} at {time}. Which day should I move that visit to?',
        uz: '{name} {when} kuni, soat {time} da. Ushbu tashrifni qaysi kunga o‘tkazishim kerak?',
        ru: '{name} записан на {when}, {time}. На какой день перенести этот визит?',
      ),
      {
        'name': intelligencePatientLabel(l10n, moveAsk.group(1)),
        'when': _speakableDay(
          l10n,
          moveAsk.group(2)!,
          moveAsk.group(3)!,
          moveAsk.group(4)!,
        ),
        'time': moveAsk.group(5)!,
      },
    );
  }

  final passed = RegExp(
    r'^([A-Za-z]+, \d{1,2} [A-Za-z]+) has already passed\. Name a future day for (.+)\.$',
  ).firstMatch(text);
  if (passed != null) {
    return _fill(
      _line(
        l10n,
        en: '{when} has already passed. Name a future day for {name}.',
        uz: '{when} o‘tib ketgan. {name} uchun kelajakdagi kunni ayting.',
        ru: '{when} уже прошло. Назовите будущий день для {name}.',
      ),
      {
        'when': _localizeEmbeddedCalendar(l10n, passed.group(1)!),
        'name': intelligencePatientLabel(l10n, passed.group(2)),
      },
    );
  }

  final patterns = <RegExp, String Function(RegExpMatch)>{
    RegExp(
      r'^No free slots on (.+) to move (.+)\. Please choose another day\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'No free slots on {when} to move {name}. Please choose another day.',
        uz: '{when} kuni {name}ni ko‘chirish uchun bo‘sh vaqt yo‘q. Boshqa kunni ayting.',
        ru: 'Нет свободного времени {when}, чтобы перенести {name}. Назовите другой день.',
      ),
      {
        'when': _localizeEmbeddedCalendar(l10n, m.group(1)!),
        'name': intelligencePatientLabel(l10n, m.group(2)),
      },
    ),
    RegExp(
      r'^(\d{2}:\d{2}) is not free on (.+)\. Here are other open times\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: '{time} is not free on {when}. Here are other open times.',
        uz: '{when} kuni soat {time} band. Mana boshqa bo‘sh vaqtlar.',
        ru: '{time} {when} занято. Вот другие свободные окна.',
      ),
      {
        'time': m.group(1)!,
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
      },
    ),
    RegExp(r'^Moving (.+) to (\d{2}:\d{2}) on (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Moving {name} to {time} on {when}.',
        uz: '{name}ni {when} kuni soat {time} ga ko‘chiryapman.',
        ru: 'Переношу {name} на {when}, {time}.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'time': m.group(2)!,
        'when': _localizeEmbeddedCalendar(l10n, m.group(3)!),
      },
    ),
    RegExp(r'^Moving (.+) to (.+)\. Which time\?$'): (m) => _fill(
      _line(
        l10n,
        en: 'Moving {name} to {when}. Which time?',
        uz: '{name}ni {when} ga ko‘chiryapman. Qaysi vaqt?',
        ru: 'Переношу {name} на {when}. Какое время?',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
      },
    ),
    RegExp(
      r'^No free slots on (.+) for (.+)\. Please choose another day\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'No free slots on {when} for {name}. Please choose another day.',
        uz: '{when} kuni {name} uchun bo‘sh vaqt yo‘q. Boshqa kunni ayting.',
        ru: '{when} нет свободного времени для {name}. Назовите другой день.',
      ),
      {
        'when': _localizeEmbeddedCalendar(l10n, m.group(1)!),
        'name': intelligencePatientLabel(l10n, m.group(2)),
      },
    ),
    RegExp(
      r'^(\d{2}:\d{2}) is not free for (.+) on (.+)\. Here are other open times\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: '{time} is not free for {name} on {when}. Here are other open times.',
        uz: '{name} uchun {when} kuni soat {time} band. Mana boshqa bo‘sh vaqtlar.',
        ru: '{time} занято для {name} {when}. Вот другие свободные окна.',
      ),
      {
        'time': m.group(1)!,
        'name': intelligencePatientLabel(l10n, m.group(2)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(3)!),
      },
    ),
    RegExp(r'^Booking (.+) at (\d{2}:\d{2}) on (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Booking {name} at {time} on {when}.',
        uz: '{name}ni {when} kuni soat {time} ga band qilyapman.',
        ru: 'Записываю {name} на {when}, {time}.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'time': m.group(2)!,
        'when': _localizeEmbeddedCalendar(l10n, m.group(3)!),
      },
    ),
    RegExp(
      r'^I found (\d+) free slots on (.+) for (.+)\. Which time works\?$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'I found {count} free slots on {when} for {name}. Which time works?',
        uz: '{name} uchun {when} kuni {count} ta bo‘sh vaqt topdim. Qaysi vaqt qulay?',
        ru: 'Нашла {count} свободных окон {when} для {name}. Какое время подходит?',
      ),
      {
        'count': m.group(1)!,
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
        'name': intelligencePatientLabel(l10n, m.group(3)),
      },
    ),
    RegExp(
      r'^You have no free( morning| afternoon| evening)? slots on (.+)\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You have no free{part} slots on {when}.',
        uz: '{when} kuni{part} bo‘sh vaqt yo‘q.',
        ru: '{when} нет свободных окон{part}.',
      ),
      {
        'part': _dayPart(l10n, m.group(1)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
      },
    ),
    RegExp(
      r'^You have 1 free( morning| afternoon| evening)? slot on (.+): (\d{2}:\d{2})\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You have 1 free{part} slot on {when}: {time}.',
        uz: '{when} kuni{part} 1 ta bo‘sh vaqt bor: {time}.',
        ru: '{when} есть 1 свободное окно{part}: {time}.',
      ),
      {
        'part': _dayPart(l10n, m.group(1)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
        'time': m.group(3)!,
      },
    ),
    RegExp(
      r'^You have (\d+) free( morning| afternoon| evening)? slots on (.+)\. Which time works\?$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You have {count} free{part} slots on {when}. Which time works?',
        uz: '{when} kuni{part} {count} ta bo‘sh vaqt bor. Qaysi vaqt qulay?',
        ru: '{when} есть {count} свободных окон{part}. Какое время подходит?',
      ),
      {
        'count': m.group(1)!,
        'part': _dayPart(l10n, m.group(2)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(3)!),
      },
    ),
    RegExp(r'^Which of those visits should I cancel\? (.+)$'): (m) => _fill(
      _line(
        l10n,
        en: 'Which of those visits should I cancel? {names}',
        uz: 'Shu tashriflardan qaysilarini bekor qilay? {names}',
        ru: 'Какие из этих визитов отменить? {names}',
      ),
      {'names': _localizeVisitList(l10n, m.group(1)!)},
    ),
    RegExp(r'^Found (\d+) patients matching "(.+)"\. Which one\?$'): (m) =>
        _fill(
          _line(
            l10n,
            en: 'Found {count} patients matching "{query}". Which one?',
            uz: '“{query}” bo‘yicha {count} ta bemor topildi. Qaysi biri?',
            ru: 'Найдено пациентов по запросу «{query}»: {count}. Кто именно?',
          ),
          {'count': m.group(1)!, 'query': m.group(2)!},
        ),
    RegExp(r'^Found (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Found {name}.',
        uz: '{name} topildi.',
        ru: 'Найден {name}.',
      ),
      {'name': intelligencePatientLabel(l10n, m.group(1))},
    ),
    RegExp(
      r'^No patients matched ["“”«»](.+?)["“”«»] in your roster\.?$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'No patients matched "{query}" in your roster.',
        uz: 'Ro‘yxatingizda “{query}” bo‘yicha bemor topilmadi.',
        ru: 'В вашем списке нет пациентов по запросу «{query}».',
      ),
      {'query': m.group(1)!},
    ),
    RegExp(r'^No exact match for "(.+)"\. Did you mean (.+)\?$'): (m) => _fill(
      _line(
        l10n,
        en: 'No exact match for "{query}". Did you mean {name}?',
        uz: '“{query}” bo‘yicha aniq moslik yo‘q. {name}ni nazarda tutdingizmi?',
        ru: 'Нет точного совпадения для «{query}». Вы имели в виду {name}?',
      ),
      {'query': m.group(1)!, 'name': m.group(2)!},
    ),
    RegExp(
      r'^No exact match for "(.+)"\. Closest matches: (.+)\. Which one did you mean\?$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'No exact match for "{query}". Closest matches: {names}. Which one did you mean?',
        uz: '“{query}” bo‘yicha aniq moslik yo‘q. Eng yaqinlar: {names}. Qaysi birini nazarda tutdingiz?',
        ru: 'Нет точного совпадения для «{query}». Ближе всего: {names}. Кого вы имели в виду?',
      ),
      {'query': m.group(1)!, 'names': m.group(2)!},
    ),
    RegExp('^You have no appointments with (.+) ($_rangeTail)\\.\$'): (m) =>
        _fill(
          _line(
            l10n,
            en: 'You have no appointments with {name} {when}.',
            uz: '{name} bilan {when} uchrashuv yo‘q.',
            ru: 'Нет записей с {name} {when}.',
          ),
          {
            'name': intelligencePatientLabel(l10n, m.group(1)),
            'when': intelligenceRangeLabel(l10n, m.group(2)!),
          },
        ),
    RegExp(
      '^You have (\\d+) appointments? with (.+) ($_rangeTail): (.+)\\.\$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You have {count} appointments with {name} {when}: {details}.',
        uz: '{name} bilan {when} {count} ta uchrashuv bor: {details}.',
        ru: 'С {name} {when} записей: {count}. {details}.',
      ),
      {
        'count': m.group(1)!,
        'name': intelligencePatientLabel(l10n, m.group(2)),
        'when': intelligenceRangeLabel(l10n, m.group(3)!),
        'details': _localizeEmbeddedCalendar(l10n, m.group(4)!),
      },
    ),
    RegExp(
      r'^Your most visited patient is (.+) with (\d+) visits\. Next: (.+)\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Your most visited patient is {name} with {count} visits. Next: {rest}.',
        uz: 'Eng ko‘p tashrif buyurgan bemor — {name}, {count} ta tashrif. Keyingilar: {rest}.',
        ru: 'Чаще всего приходит {name}: {count} визитов. Далее: {rest}.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'count': m.group(2)!,
        'rest': m.group(3)!,
      },
    ),
    RegExp(r'^Your most visited patient is (.+) with (\d+) visits\.$'): (m) =>
        _fill(
          _line(
            l10n,
            en: 'Your most visited patient is {name} with {count} visits.',
            uz: 'Eng ko‘p tashrif buyurgan bemor — {name}, {count} ta tashrif.',
            ru: 'Чаще всего приходит {name}: {count} визитов.',
          ),
          {
            'name': intelligencePatientLabel(l10n, m.group(1)),
            'count': m.group(2)!,
          },
        ),
    RegExp(r'^There are (\d+) draft facts\..+$'): (m) => _fill(
      _line(
        l10n,
        en: 'There are {count} draft facts. Confirm to document them.',
        uz: '{count} ta qoralama fakt bor. Hujjatlashtirish uchun tasdiqlang.',
        ru: 'Черновиков фактов: {count}. Подтвердите, чтобы задокументировать.',
      ),
      {'count': m.group(1)!},
    ),
    RegExp(r'^There are no viewable documents on file for (.+) yet\.$'): (m) =>
        _fill(
          _line(
            l10n,
            en: 'There are no viewable documents on file for {name} yet.',
            uz: '{name} uchun hali ko‘rish mumkin bo‘lgan hujjat yo‘q.',
            ru: 'У {name} пока нет документов, которые можно открыть.',
          ),
          {'name': intelligencePatientLabel(l10n, m.group(1))},
        ),
    RegExp(
      r'^I found 1 document for (.+): (.+)\. Opening it in chat\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'I found 1 document for {name}: {title}. Opening it in chat.',
        uz: '{name} uchun 1 ta hujjat topdim: {title}. Chatda ochyapman.',
        ru: 'Нашла 1 документ для {name}: {title}. Открываю в чате.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'title': m.group(2)!,
      },
    ),
    RegExp(
      r'^I found (\d+) documents for (.+)\. They are listed in the chat\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'I found {count} documents for {name}. They are listed in the chat.',
        uz: '{name} uchun {count} ta hujjat topdim. Ular chatda ko‘rsatilgan.',
        ru: 'Нашла {count} документов для {name}. Они перечислены в чате.',
      ),
      {
        'count': m.group(1)!,
        'name': intelligencePatientLabel(l10n, m.group(2)),
      },
    ),
    RegExp(r'^(.+) has no viewable documents yet\.$'): (m) => _fill(
      _line(
        l10n,
        en: '{name} has no viewable documents yet.',
        uz: '{name}da hali ko‘rish mumkin bo‘lgan hujjat yo‘q.',
        ru: 'У {name} пока нет документов, которые можно открыть.',
      ),
      {'name': intelligencePatientLabel(l10n, m.group(1))},
    ),
    RegExp(r'^Opening (.+) for (.+) in the chat\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Opening {title} for {name} in the chat.',
        uz: '{name} uchun {title} chatda ochilmoqda.',
        ru: 'Открываю {title} для {name} в чате.',
      ),
      {
        'title': m.group(1)!,
        'name': intelligencePatientLabel(l10n, m.group(2)),
      },
    ),
    RegExp(
      r'^Which document for (.+) should I open\? They are listed in the chat\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Which document for {name} should I open? They are listed in the chat.',
        uz: '{name}ning qaysi hujjatini ochay? Ular chatda ko‘rsatilgan.',
        ru: 'Какой документ {name} открыть? Они перечислены в чате.',
      ),
      {'name': intelligencePatientLabel(l10n, m.group(1))},
    ),
    RegExp(
      r'^I could not match "(.+)"\. Here are (\d+) documents for (.+) — tap one to open in chat\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'I could not match "{query}". Here are {count} documents for {name} — tap one to open in chat.',
        uz: '“{query}” ga mos hujjat topilmadi. {name}ning {count} ta hujjati bor — chatda ochish uchun birini bosing.',
        ru: 'Не нашла «{query}». Вот {count} документов {name} — нажмите, чтобы открыть в чате.',
      ),
      {
        'query': m.group(1)!,
        'count': m.group(2)!,
        'name': intelligencePatientLabel(l10n, m.group(3)),
      },
    ),
    RegExp(
      r'^Here is the dental diagram for (.+) from (.+) — (\d+) teeth have recorded services\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Here is the dental diagram for {name} from {when} — {count} teeth have recorded services.',
        uz: '{name}ning {when} dagi tish diagrammasi — {count} ta tishda xizmatlar yozilgan.',
        ru: 'Зубная диаграмма {name} от {when} — услуги записаны по {count} зубам.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
        'count': m.group(3)!,
      },
    ),
    RegExp(
      r'^Here is the dental diagram for (.+) from (.+) — 1 tooth has recorded services\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Here is the dental diagram for {name} from {when} — 1 tooth has recorded services.',
        uz: '{name}ning {when} dagi tish diagrammasi — 1 ta tishda xizmat yozilgan.',
        ru: 'Зубная диаграмма {name} от {when} — услуги записаны по 1 зубу.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
      },
    ),
    RegExp(r'^Here is the dental diagram for (.+) from (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Here is the dental diagram for {name} from {when}.',
        uz: '{name}ning {when} dagi tish diagrammasi.',
        ru: 'Зубная диаграмма {name} от {when}.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
      },
    ),
    RegExp(
      r"^There is no dental diagram saved for (.+)'s latest visit yet\.$",
    ): (m) => _fill(
      _line(
        l10n,
        en: "There is no dental diagram saved for {name}'s latest visit yet.",
        uz: '{name}ning so‘nggi tashrifida hali tish diagrammasi yo‘q.',
        ru: 'Для последнего визита {name} зубная диаграмма ещё не сохранена.',
      ),
      {'name': intelligencePatientLabel(l10n, m.group(1))},
    ),
    RegExp(
      r'^No visit dental chart yet\. Opening (.+) for (.+) in the chat\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'No visit dental chart yet. Opening {title} for {name} in the chat.',
        uz: 'Tashrif tish diagrammasi hali yo‘q. {name} uchun {title} chatda ochilmoqda.',
        ru: 'Диаграммы визита ещё нет. Открываю {title} для {name} в чате.',
      ),
      {
        'title': m.group(1)!,
        'name': intelligencePatientLabel(l10n, m.group(2)),
      },
    ),
    RegExp(
      r'^(.+) has no visits on your calendar yet, so there is no dental diagram\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: '{name} has no visits on your calendar yet, so there is no dental diagram.',
        uz: '{name}ning kalendaringizda hali tashrifi yo‘q, shuning uchun tish diagrammasi ham yo‘q.',
        ru: 'У {name} ещё нет визитов в вашем календаре, поэтому зубной диаграммы нет.',
      ),
      {'name': intelligencePatientLabel(l10n, m.group(1))},
    ),
    RegExp(
      r'^Cancelled (\d+)\. The patients were notified in the app\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Cancelled {count}. The patients were notified in the app.',
        uz: '{count} ta qabul bekor qilindi. Bemorlarga ilovada xabar berildi.',
        ru: 'Отменено: {count}. Пациенты уведомлены в приложении.',
      ),
      {'count': m.group(1)!},
    ),
    RegExp(
      r'^Cancelled (\d+)\. The patient was notified in the app\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Cancelled {count}. The patient was notified in the app.',
        uz: '{count} ta qabul bekor qilindi. Bemorga ilovada xabar berildi.',
        ru: 'Отменено: {count}. Пациент уведомлён в приложении.',
      ),
      {'count': m.group(1)!},
    ),
    RegExp(r'^Cancelled (\d+)\. (\d+) could not be cancelled\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Cancelled {count}. {failed} could not be cancelled.',
        uz: '{count} ta qabul bekor qilindi. {failed} tasini bekor qilib bo‘lmadi.',
        ru: 'Отменено: {count}. Не удалось отменить: {failed}.',
      ),
      {'count': m.group(1)!, 'failed': m.group(2)!},
    ),
    RegExp(
      r'^I found (\d+) visits that can be cancelled\.(.*) Which one should I cancel\?$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'I found {count} visits that can be cancelled.{note} Which one should I cancel?',
        uz: 'Bekor qilish mumkin bo‘lgan {count} ta tashrif topdim.{note} Qaysi birini bekor qilay?',
        ru: 'Нашла {count} визитов, которые можно отменить.{note} Какой отменить?',
      ),
      {
        'count': m.group(1)!,
        'note': m.group(2)!.trim().isEmpty
            ? ''
            : ' ${localizeIntelligenceHint(l10n, m.group(2)!.trim())}',
      },
    ),
    RegExp(
      r'^Moved (.+?) to (\d{1,2}:\d{2})(?: on ([^.]+))?\.?$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Moved {name} to {time}{when}.',
        uz: m.group(3) == null
            ? '{name} soat {time} ga ko‘chirildi.'
            : '{name} {when}kuni, soat {time} ga ko‘chirildi.',
        ru: '{name} перенесён на {time}{when}.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'time': _clock(m.group(2)!),
        'when': m.group(3) == null
            ? ''
            : _line(
                l10n,
                en: ' on ${m.group(3)}',
                uz: '${_localizeEmbeddedCalendar(l10n, m.group(3)!.trim())} ',
                ru: ' ${_localizeEmbeddedCalendar(l10n, m.group(3)!.trim())}, ',
              ),
      },
    ),
    RegExp(
      r'^Please confirm booking (.+?) at (\d{1,2}:\d{2}) on ([^.]+)\.?$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Please confirm booking {name} at {time} on {when}.',
        uz: 'Iltimos, {name}ning {when} kuni soat {time} dagi qabulini tasdiqlang.',
        ru: 'Подтвердите запись {name} на {time}, {when}.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'time': _clock(m.group(2)!),
        'when': _localizeEmbeddedCalendar(l10n, m.group(3)!.trim()),
      },
    ),
    RegExp(
      r'^I can move (.+?) to (\d{1,2}:\d{2}) on ([^.]+)\. Please confirm\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'I can move {name} to {time} on {when}. Please confirm.',
        uz: '{name}ni soat {time} ga, {when} kuniga ko‘chira olaman. Tasdiqlang.',
        ru: 'Могу перенести {name} на {time}, {when}. Подтвердите.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'time': _clock(m.group(2)!),
        'when': _localizeEmbeddedCalendar(l10n, m.group(3)!.trim()),
      },
    ),
    RegExp(r'^I abandoned that booking\.$'): (_) => _line(
      l10n,
      en: 'I abandoned that booking.',
      uz: 'Bu band qilishni bekor qildim.',
      ru: 'Я отменила это бронирование.',
    ),
    RegExp(r'^I did not book that appointment\.$'): (_) => _line(
      l10n,
      en: 'I did not book that appointment.',
      uz: 'Bu uchrashuvni band qilmadim.',
      ru: 'Я не записала этот приём.',
    ),
    RegExp(
      r'^Booked (.+?) at (\d{1,2}:\d{2})(?: on ([^.]+))?\.?$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Booked {name} at {time}{when}.',
        uz: m.group(3) == null
            ? '{name} soat {time} ga band qilindi.'
            : '{name} {when}kuni, soat {time} ga band qilindi.',
        ru: '{name} записан на {time}{when}.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'time': _clock(m.group(2)!),
        'when': m.group(3) == null
            ? ''
            : _line(
                l10n,
                en: ' on ${m.group(3)!.trim()}',
                uz: '${_localizeEmbeddedCalendar(l10n, m.group(3)!.trim())} ',
                ru: ' ${_localizeEmbeddedCalendar(l10n, m.group(3)!.trim())}, ',
              ),
      },
    ),
    RegExp(
      r'^You have (\d+) appointments? today\. The first is with (.+) (in about .+)\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You have {count} appointments today. The first is with {name} {when}.',
        uz: 'Bugun {count} ta uchrashuv bor. Birinchisi {name} bilan {when}.',
        ru: 'Сегодня записей: {count}. Первая — {name}, {when}.',
      ),
      {
        'count': m.group(1)!,
        'name': intelligencePatientLabel(l10n, m.group(2)),
        'when': _soonPhrase(l10n, m.group(3)!),
      },
    ),
    RegExp(
      r'^You have (\d+) appointments? today\. The next one with (.+) is starting now\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You have {count} appointments today. The next one with {name} is starting now.',
        uz: 'Bugun {count} ta uchrashuv bor. Keyingisi {name} bilan hozir boshlanmoqda.',
        ru: 'Сегодня записей: {count}. Следующая, {name}, начинается сейчас.',
      ),
      {
        'count': m.group(1)!,
        'name': intelligencePatientLabel(l10n, m.group(2)),
      },
    ),
    RegExp(
      r'^You (have|had) (\d+) (patient|patients) with appointments scheduled (.+)\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You {verb} {count} patients with appointments scheduled {when}.',
        uz: '{when} uchrashuvi bor bemorlar: {count}.',
        ru: '{when} пациентов с записями: {count}.',
      ),
      {
        'verb': m.group(1)!,
        'count': m.group(2)!,
        'when': intelligenceRangeLabel(l10n, m.group(4)!),
      },
    ),
    RegExp(
      r'^You (have|had) no( cancelled)? appointments scheduled (.+?)(?: with (.+))?\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You {verb} no{cancelled} appointments scheduled {when}{name}.',
        uz: '{when}{name} rejalashtirilgan{cancelled} uchrashuv yo‘q.',
        ru: '{when}{name} нет запланированных{cancelled} записей.',
      ),
      {
        'verb': m.group(1)!,
        'cancelled': m.group(2) == null
            ? ''
            : _line(
                l10n,
                en: ' cancelled',
                uz: ' bekor qilingan',
                ru: ' отменённых',
              ),
        'when': intelligenceRangeLabel(l10n, m.group(3)!),
        'name': m.group(4) == null
            ? ''
            : ' ${intelligencePatientLabel(l10n, m.group(4))}',
      },
    ),
    RegExp(
      r'^You (have|had) (\d+) (appointment|appointments) scheduled ([^:.]+?)(?: with ([^:]+?))?(?:: (.+))?\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You {verb} {count} appointments scheduled {when}{name}{details}.',
        uz: '{when}{name} {count} ta uchrashuv rejalashtirilgan{details}.',
        ru: '{when}{name} записей: {count}{details}.',
      ),
      {
        'verb': m.group(1)!,
        'count': m.group(2)!,
        'when': intelligenceRangeLabel(l10n, m.group(4)!),
        'name': m.group(5) == null
            ? ''
            : ' ${intelligencePatientLabel(l10n, m.group(5))}',
        'details': m.group(6) == null
            ? ''
            : ': ${_localizeEmbeddedCalendar(l10n, m.group(6)!)}',
      },
    ),
    RegExp(
      r'^(\d+) (appointment|appointments) scheduled (.+?)(?: with (.+))? (?:is|are) currently cancelled\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: '{count} appointments scheduled {when}{name} are currently cancelled.',
        uz: '{when}{name} rejalashtirilgan {count} ta uchrashuv hozir bekor qilingan.',
        ru: '{when}{name} сейчас отменено записей: {count}.',
      ),
      {
        'count': m.group(1)!,
        'when': intelligenceRangeLabel(l10n, m.group(3)!),
        'name': m.group(4) == null
            ? ''
            : ' ${intelligencePatientLabel(l10n, m.group(4))}',
      },
    ),
    RegExp(
      r'^You (have|had) (\d+) (appointment|appointments) scheduled (.+?)(?: with (.+))?, of which (\d+) were confirmed and (\d+) are awaiting confirmation\.(?: Also (.+))?$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'You {verb} {count} appointments scheduled {when}{name}, of which {confirmed} were confirmed and {requested} are awaiting confirmation.{extra}',
        uz: '{when}{name} {count} ta uchrashuv rejalashtirilgan, shundan {confirmed} tasi tasdiqlangan, {requested} tasi tasdiq kutmoqda.{extra}',
        ru: '{when}{name} записей: {count}, из них подтверждено {confirmed}, ожидают подтверждения {requested}.{extra}',
      ),
      {
        'verb': m.group(1)!,
        'count': m.group(2)!,
        'when': intelligenceRangeLabel(l10n, m.group(4)!),
        'name': m.group(5) == null
            ? ''
            : ' ${intelligencePatientLabel(l10n, m.group(5))}',
        'confirmed': m.group(6)!,
        'requested': m.group(7)!,
        'extra': m.group(8) == null
            ? ''
            : ' ${_localizeStatusBits(l10n, m.group(8)!)}',
      },
    ),
    RegExp(r'^No appointments scheduled (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'No appointments scheduled {when}.',
        uz: '{when} rejalashtirilgan uchrashuv yo‘q.',
        ru: '{when} записей нет.',
      ),
      {'when': intelligenceRangeLabel(l10n, m.group(1)!)},
    ),
    RegExp(r'^Scheduled (.+) by ([a-z_]+): (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Scheduled {when} by {dimension}: {rows}.',
        uz: '{when} bo‘yicha {dimension}: {rows}.',
        ru: '{when} по {dimension}: {rows}.',
      ),
      {
        'when': intelligenceRangeLabel(l10n, m.group(1)!),
        'dimension': _dimensionLabel(l10n, m.group(2)!),
        'rows': _localizeStatusBits(l10n, m.group(3)!),
      },
    ),
    RegExp(r'^Compared with (.+), that was (\d+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Compared with {when}, that was {count}.',
        uz: '{when} bilan solishtirganda bu {count} edi.',
        ru: 'По сравнению с {when} тогда было {count}.',
      ),
      {'when': intelligenceRangeLabel(l10n, m.group(1)!), 'count': m.group(2)!},
    ),
    RegExp(r'^Showing (\d+) of (\d+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Showing {shown} of {total}.',
        uz: '{total} tadan {shown} tasi ko‘rsatilgan.',
        ru: 'Показано {shown} из {total}.',
      ),
      {'shown': m.group(1)!, 'total': m.group(2)!},
    ),
    RegExp(
      r'^There are no past visits scheduled (.+), so the cancellation rate is not available\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'There are no past visits scheduled {when}, so the cancellation rate is not available.',
        uz: '{when} o‘tgan tashrif yo‘q, shuning uchun bekor qilish ulushi noma’lum.',
        ru: 'Прошедших визитов {when} нет, поэтому долю отмен посчитать нельзя.',
      ),
      {'when': intelligenceRangeLabel(l10n, m.group(1)!)},
    ),
    RegExp(
      r'^Cancellation rate for visits scheduled (.+), excluding future visits, is ([0-9.]+) percent \((\d+) of (\d+)\)\. Visits that have not happened yet are left out\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Cancellation rate for visits scheduled {when}, excluding future visits, is {percent} percent ({num} of {den}). Visits that have not happened yet are left out.',
        uz: '{when} rejalashtirilgan tashriflarning bekor qilish ulushi, kelajakdagilar hisobga olinmagan holda, {percent} foiz ({num} / {den}). Hali bo‘lmagan tashriflar kiritilmagan.',
        ru: 'Доля отмен визитов, запланированных {when}, без будущих визитов — {percent} процентов ({num} из {den}). Ещё не состоявшиеся визиты не входят.',
      ),
      {
        'when': intelligenceRangeLabel(l10n, m.group(1)!),
        'percent': m.group(2)!,
        'num': m.group(3)!,
        'den': m.group(4)!,
      },
    ),
    RegExp(r'^For (.+) the rate is not available\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'For {when} the rate is not available.',
        uz: '{when} uchun ulush noma’lum.',
        ru: 'Для {when} долю посчитать нельзя.',
      ),
      {'when': intelligenceRangeLabel(l10n, m.group(1)!)},
    ),
    RegExp(r'^For (.+) it was ([0-9.]+) percent \((\d+) of (\d+)\)\.$'): (m) =>
        _fill(
          _line(
            l10n,
            en: 'For {when} it was {percent} percent ({num} of {den}).',
            uz: '{when} uchun bu {percent} foiz edi ({num} / {den}).',
            ru: 'Для {when} было {percent} процентов ({num} из {den}).',
          ),
          {
            'when': intelligenceRangeLabel(l10n, m.group(1)!),
            'percent': m.group(2)!,
            'num': m.group(3)!,
            'den': m.group(4)!,
          },
        ),
    RegExp(r'^You collected nothing (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'You collected nothing {when}.',
        uz: '{when} hech narsa tushum qilinmagan.',
        ru: '{when} поступлений не было.',
      ),
      {'when': intelligenceRangeLabel(l10n, m.group(1)!)},
    ),
    RegExp(r'^Collected (.+): (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Collected {when}: {parts}.',
        uz: '{when} tushum: {parts}.',
        ru: 'Поступления {when}: {parts}.',
      ),
      {
        'when': intelligenceRangeLabel(l10n, m.group(1)!),
        'parts': _moneyParts(l10n, m.group(2)!),
      },
    ),
    RegExp(
      r'^(Appointment prices|Pending amounts) scheduled (.+): nothing with a stored price\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: '{lead} scheduled {when}: nothing with a stored price.',
        uz: '{when} {lead}: saqlangan narx yo‘q.',
        ru: '{lead} {when}: сохранённой цены нет.',
      ),
      {
        'lead': _moneyLead(l10n, m.group(1)!),
        'when': intelligenceRangeLabel(l10n, m.group(2)!),
      },
    ),
    RegExp(
      r'^(Appointment prices|Pending amounts) scheduled (.+): (.+)\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: '{lead} scheduled {when}: {parts}.',
        uz: '{when} {lead}: {parts}.',
        ru: '{lead} {when}: {parts}.',
      ),
      {
        'lead': _moneyLead(l10n, m.group(1)!),
        'when': intelligenceRangeLabel(l10n, m.group(2)!),
        'parts': _moneyParts(l10n, m.group(3)!),
      },
    ),
    RegExp(
      r'^(.+?) · (.+?) · ([A-Z_]+)\. Opening the (appointment|documentation|patient record)\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: '{name} · {when} · {status}. Opening the {where}.',
        uz: '{name} · {when} · {status}. {where} ochilmoqda.',
        ru: '{name} · {when} · {status}. Открываю {where}.',
      ),
      {
        'name': intelligencePatientLabel(l10n, m.group(1)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
        'status': intelligenceStatusLabel(l10n, m.group(3)!),
        'where': _openWhere(l10n, m.group(4)!),
      },
    ),
    RegExp(r'^The note says: (.+)$'): (m) => _fill(
      _line(
        l10n,
        en: 'The note says: {text}',
        uz: 'Qayd matni: {text}',
        ru: 'В заметке сказано: {text}',
      ),
      {'text': m.group(1)!},
    ),
    RegExp(
      r'^Last visit was on (.+) at (\d{2}:\d{2})\. Reason: (.+)\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Last visit was on {when} at {time}. Reason: {reason}.',
        uz: 'So‘nggi tashrif {when}, soat {time}. Sabab: {reason}.',
        ru: 'Последний визит {when}, {time}. Причина: {reason}.',
      ),
      {
        'when': _localizeEmbeddedCalendar(l10n, m.group(1)!),
        'time': m.group(2)!,
        'reason': _reasonText(l10n, m.group(3)!),
      },
    ),
    RegExp(r'^Last visit (.+) at (\d{2}:\d{2})\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Last visit {when} at {time}.',
        uz: 'So‘nggi tashrif {when}, soat {time}.',
        ru: 'Последний визит {when}, {time}.',
      ),
      {
        'when': _localizeEmbeddedCalendar(l10n, m.group(1)!),
        'time': m.group(2)!,
      },
    ),
    RegExp(r'^You have seen them (\d+) times\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'You have seen them {count} times.',
        uz: 'Siz ularni {count} marta ko‘rgansiz.',
        ru: 'Вы принимали их {count} раз.',
      ),
      {'count': m.group(1)!},
    ),
    RegExp(r'^Chart notes include: (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Chart notes include: {text}.',
        uz: 'Kartadagi qaydlar: {text}.',
        ru: 'В карте есть: {text}.',
      ),
      {'text': m.group(1)!},
    ),
    RegExp(r'^Previously you planned: (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Previously you planned: {text}.',
        uz: 'Avval rejalashtirganingiz: {text}.',
        ru: 'Ранее вы планировали: {text}.',
      ),
      {'text': m.group(1)!},
    ),
    RegExp(
      r'^(\d+) (?:is|are) already cancelled, including (.+) ([A-Za-z]+ \d{1,2} [A-Za-z]+ at \d{2}:\d{2})\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: '{count} are already cancelled, including {name} {when}.',
        uz: '{count} tasi allaqachon bekor qilingan, jumladan {name} {when}.',
        ru: 'Уже отменено: {count}, в том числе {name} {when}.',
      ),
      {
        'count': m.group(1)!,
        'name': intelligencePatientLabel(l10n, m.group(2)),
        'when': _localizeEmbeddedCalendar(l10n, m.group(3)!),
      },
    ),
    RegExp(
      r'^Documented (\d+) facts(?: and drafted (\d+) medication tasks)?\. They are on the patient timeline\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: 'Documented {count} facts{tasks}. They are on the patient timeline.',
        uz: '{count} ta fakt hujjatlashtirildi{tasks}. Ular bemor vaqt chizig‘ida.',
        ru: 'Задокументировано фактов: {count}{tasks}. Они на временной шкале пациента.',
      ),
      {
        'count': m.group(1)!,
        'tasks': m.group(2) == null
            ? ''
            : _fill(
                _line(
                  l10n,
                  en: ' and drafted {n} medication tasks',
                  uz: ' va {n} ta dori vazifasi tayyorlandi',
                  ru: ' и создано задач по лекарствам: {n}',
                ),
                {'n': m.group(2)!},
              ),
      },
    ),
    RegExp(r'^Top patterns: (.+)\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'Top patterns: {rest}',
        uz: 'Asosiy naqshlar: {rest}',
        ru: 'Основные шаблоны: {rest}',
      ),
      {'rest': m.group(1)!},
    ),
    RegExp(r'^You have (\d+) appointments today\.$'): (m) => _fill(
      _line(
        l10n,
        en: 'You have {count} appointments today.',
        uz: 'Bugun {count} ta uchrashuv bor.',
        ru: 'Сегодня записей: {count}.',
      ),
      {'count': m.group(1)!},
    ),
    RegExp(r'^You have 1 appointment today\.$'): (m) => _line(
      l10n,
      en: 'You have 1 appointment today.',
      uz: 'Bugun 1 ta uchrashuv bor.',
      ru: 'Сегодня 1 запись.',
    ),
    RegExp(
      r'^You (?:have|had) (\d+) (?:appointment|appointments|patient|patients)(?: with .+)? scheduled (.+?)\.$',
    ): (m) => _fill(
      _line(
        l10n,
        en: text,
        uz: '{count} ta yozuv {when} ga rejalashtirilgan.',
        ru: '{count} записей запланировано {when}.',
      ),
      {
        'count': m.group(1)!,
        'when': _localizeEmbeddedCalendar(l10n, m.group(2)!),
      },
    ),
  };

  for (final entry in patterns.entries) {
    final match = entry.key.firstMatch(text);
    if (match != null) return entry.value(match);
  }
  return null;
}

Map<String, String> _exactSentences(AppLocalizations l10n) {
  String line(String en, String uz, String ru) =>
      _line(l10n, en: en, uz: uz, ru: ru);
  return {
    'I could not tell which visit to move. Say the patient and the day.': line(
      'I could not tell which visit to move. Say the patient and the day.',
      'Qaysi tashrifni ko‘chirishni bilmayman. Bemor va kunni ayting.',
      'Не поняла, какой визит переносить. Назовите пациента и день.',
    ),
    'Which patient should I check appointments for?': line(
      'Which patient should I check appointments for?',
      'Qaysi bemorning uchrashuvlarini tekshiray?',
      'Для какого пациента проверить записи?',
    ),
    'I could not find that patient.': line(
      'I could not find that patient.',
      'Bu bemorni topa olmadim.',
      'Не нашла этого пациента.',
    ),
    'Please give a patient name to search.': line(
      'Please give a patient name to search.',
      'Qidirish uchun bemor ismini ayting.',
      'Назовите имя пациента для поиска.',
    ),
    'Which day should I check for free slots?': line(
      'Which day should I check for free slots?',
      'Bo‘sh vaqtlarni qaysi kunga tekshiray?',
      'На какой день проверить свободное время?',
    ),
    'I need a patient id before I can propose a booking.': line(
      'I need a patient id before I can propose a booking.',
      'Band qilishdan oldin bemor kerak.',
      'Чтобы предложить запись, нужен пациент.',
    ),
    'I need an appointment to list draft visit facts.': line(
      'I need an appointment to list draft visit facts.',
      'Qoralama faktlar uchun uchrashuv kerak.',
      'Чтобы показать черновики фактов, нужна запись.',
    ),
    'That appointment was not found.': line(
      'That appointment was not found.',
      'Bu uchrashuv topilmadi.',
      'Эта запись не найдена.',
    ),
    'That appointment is not on your calendar.': line(
      'That appointment is not on your calendar.',
      'Bu uchrashuv sizning kalendaringizda yo‘q.',
      'Этой записи нет в вашем календаре.',
    ),
    'Which patient should I list documents for?': line(
      'Which patient should I list documents for?',
      'Qaysi bemorning hujjatlarini ko‘rsatay?',
      'Для какого пациента показать документы?',
    ),
    "Which patient's document should I open?": line(
      "Which patient's document should I open?",
      'Qaysi bemorning hujjatini ochay?',
      'Документ какого пациента открыть?',
    ),
    "Which patient's dental diagram should I open?": line(
      "Which patient's dental diagram should I open?",
      'Qaysi bemorning tish diagrammasini ochay?',
      'Зубную диаграмму какого пациента открыть?',
    ),
    'Which visit should I complete?': line(
      'Which visit should I complete?',
      'Qaysi tashrifni yakunlay?',
      'Какой визит завершить?',
    ),
    'Which visit should I request a signature for?': line(
      'Which visit should I request a signature for?',
      'Qaysi tashrif uchun imzo so‘ray?',
      'Для какого визита запросить подпись?',
    ),
    'Which visit should I document? Open or start the appointment first.': line(
      'Which visit should I document? Open or start the appointment first.',
      'Qaysi tashrifni hujjatlashtiray? Avval uchrashuvni oching yoki boshlang.',
      'Какой визит документировать? Сначала откройте или начните приём.',
    ),
    'I could not save that note.': line(
      'I could not save that note.',
      'Bu qaydni saqlay olmadim.',
      'Не удалось сохранить эту запись.',
    ),
    'There is no consultation note for this visit yet.': line(
      'There is no consultation note for this visit yet.',
      'Bu tashrif uchun hali maslahat qaydi yo‘q.',
      'Для этого визита ещё нет заметки консультации.',
    ),
    'That is already in the consultation note.': line(
      'That is already in the consultation note.',
      'Bu allaqachon maslahat qaydida bor.',
      'Это уже есть в заметке консультации.',
    ),
    "I added that to the consultation note. Say when you want to request the patient's signature.": line(
      "I added that to the consultation note. Say when you want to request the patient's signature.",
      'Buni maslahat qaydida qo‘shdim. Bemor imzosini qachon so‘rashni ayting.',
      'Добавила это в заметку консультации. Скажите, когда запросить подпись пациента.',
    ),
    'I could not find a matching visit. Nothing was changed.': line(
      'I could not find a matching visit. Nothing was changed.',
      'Mos tashrif topilmadi. Hech narsa o‘zgartirilmadi.',
      'Подходящий визит не найден. Ничего не изменено.',
    ),
    'The schedule changed, so I did not cancel anything. Ask me again and I will show the updated list.': line(
      'The schedule changed, so I did not cancel anything. Ask me again and I will show the updated list.',
      'Jadval o‘zgardi, shuning uchun hech narsani bekor qilmadim. Qayta so‘rang, yangilangan ro‘yxatni ko‘rsataman.',
      'Расписание изменилось, поэтому я ничего не отменила. Спросите ещё раз, и я покажу обновлённый список.',
    ),
    'That visit was more than 24 hours ago and was never opened, so it is a no-show. I did not open the chart.': line(
      'That visit was more than 24 hours ago and was never opened, so it is a no-show. I did not open the chart.',
      'Bu tashrif 24 soatdan oldin bo‘lgan va ochilmagan, shuning uchun bu kelmagan holat. Kartani ochmadim.',
      'Этот визит был больше 24 часов назад и не открывался, поэтому это неявка. Карту я не открыла.',
    ),
    'Visit completed.': line(
      'Visit completed.',
      'Tashrif yakunlandi.',
      'Визит завершён.',
    ),
    'Signature requested.': line(
      'Signature requested.',
      'Imzo so‘raldi.',
      'Подпись запрошена.',
    ),
    'Nothing was cancelled.': line(
      'Nothing was cancelled.',
      'Hech narsa bekor qilinmadi.',
      'Ничего не отменено.',
    ),
    'No clinic patterns recorded yet.': line(
      'No clinic patterns recorded yet.',
      'Klinika naqshlari hali yozilmagan.',
      'Шаблоны клиники ещё не записаны.',
    ),
    'No patients found.': line(
      'No patients found.',
      'Bemor topilmadi.',
      'Пациенты не найдены.',
    ),
    'You have no appointments today.': line(
      'You have no appointments today.',
      'Bugun uchrashuv yo‘q.',
      'Сегодня записей нет.',
    ),
    'No prior chart notes available yet.': line(
      'No prior chart notes available yet.',
      'Hali oldingi qaydlar yo‘q.',
      'Предыдущих записей карты пока нет.',
    ),
    'There are no draft visit facts ready to document for this visit.': line(
      'There are no draft visit facts ready to document for this visit.',
      'Bu tashrif uchun hujjatlashtirishga tayyor qoralama fakt yo‘q.',
      'Для этого визита нет черновиков фактов, готовых к документированию.',
    ),
    'You do not have ranked visit history yet - no non-cancelled appointments were found in this period.': line(
      'You do not have ranked visit history yet - no non-cancelled appointments were found in this period.',
      'Hali tashrif reytingi yo‘q — bu davrda bekor qilinmagan uchrashuv topilmadi.',
      'Рейтинга визитов ещё нет — за этот период не найдено неотменённых записей.',
    ),
    'I found more than one patient. Which one did you mean?': line(
      'I found more than one patient. Which one did you mean?',
      'Bir nechta bemor topildi. Qaysi birini nazarda tutdingiz?',
      'Нашлась больше чем одна пациентка или пациент. Кого вы имели в виду?',
    ),
    'No patients matched.': line(
      'No patients matched.',
      'Bemor topilmadi.',
      'Пациенты не найдены.',
    ),
    'Appointment booked.': line(
      'Appointment booked.',
      'Uchrashuv band qilindi.',
      'Приём записан.',
    ),
    'That slot could not be booked. Pick another time on the card.': line(
      'That slot could not be booked. Pick another time on the card.',
      'Bu vaqtni band qilib bo‘lmadi. Kartadan boshqa vaqtni tanlang.',
      'Это время занять не удалось. Выберите другое время на карточке.',
    ),
    'Did you mean…?': line(
      'Did you mean…?',
      'Shuni nazarda tutdingizmi…?',
      'Вы имели в виду…?',
    ),
    'Top patterns:': line(
      'Top patterns:',
      'Asosiy naqshlar:',
      'Основные шаблоны:',
    ),
    'This looks like a new patient for you. There is no prior visit history yet.': line(
      'This looks like a new patient for you. There is no prior visit history yet.',
      'Bu siz uchun yangi bemorga o‘xshaydi. Hali oldingi tashrif tarixi yo‘q.',
      'Похоже, для вас это новый пациент. Предыдущей истории визитов ещё нет.',
    ),
    'I have prior visits on file for this patient.': line(
      'I have prior visits on file for this patient.',
      'Bu bemorning oldingi tashriflari bor.',
      'По этому пациенту есть предыдущие визиты.',
    ),
    'That visit is still waiting for payment.': line(
      'That visit is still waiting for payment.',
      'Bu tashrif hali to‘lovni kutmoqda.',
      'Этот визит всё ещё ждёт оплаты.',
    ),
    'That visit expired before payment.': line(
      'That visit expired before payment.',
      'Bu tashrif to‘lovdan oldin muddati o‘tgan.',
      'Срок этого визита истёк до оплаты.',
    ),
    'Payment failed for that visit.': line(
      'Payment failed for that visit.',
      'Bu tashrif uchun to‘lov amalga oshmadi.',
      'Оплата этого визита не прошла.',
    ),
    'I can only start a non-video visit for today or tomorrow, or within 24 hours after its start.': line(
      'I can only start a non-video visit for today or tomorrow, or within 24 hours after its start.',
      'Video bo‘lmagan tashrifni faqat bugun, ertaga yoki boshlanishidan keyin 24 soat ichida boshlay olaman.',
      'Очный визит можно начать только сегодня, завтра или в течение 24 часов после начала.',
    ),
    'That visit is already in progress.': line(
      'That visit is already in progress.',
      'Bu tashrif allaqachon boshlangan.',
      'Этот визит уже идёт.',
    ),
    'That visit is already completed.': line(
      'That visit is already completed.',
      'Bu tashrif allaqachon yakunlangan.',
      'Этот визит уже завершён.',
    ),
    'I can only complete a visit that is in progress.': line(
      'I can only complete a visit that is in progress.',
      'Faqat boshlangan tashrifni yakunlay olaman.',
      'Завершить можно только визит, который уже идёт.',
    ),
    'I could not verify that the visit was completed.': line(
      'I could not verify that the visit was completed.',
      'Tashrif yakunlanganini tasdiqlay olmadim.',
      'Не удалось проверить, что визит завершён.',
    ),
    'That visit was cancelled.': line(
      'That visit was cancelled.',
      'Bu tashrif bekor qilingan.',
      'Этот визит был отменён.',
    ),
    'That visit was marked as a no-show.': line(
      'That visit was marked as a no-show.',
      'Bu tashrif kelmagan deb belgilangan.',
      'Этот визит отмечен как неявка.',
    ),
    'The patient has already signed.': line(
      'The patient has already signed.',
      'Bemor allaqachon imzo qo‘ygan.',
      'Пациент уже подписал.',
    ),
    'A signature was already requested. I did not send another notification.': line(
      'A signature was already requested. I did not send another notification.',
      'Imzo allaqachon so‘ralgan. Yana xabar yubormadim.',
      'Подпись уже запрашивали. Повторное уведомление я не отправила.',
    ),
    'Signature requested. The patient was notified in the app.': line(
      'Signature requested. The patient was notified in the app.',
      'Imzo so‘raldi. Bemorga ilovada xabar berildi.',
      'Подпись запрошена. Пациент уведомлён в приложении.',
    ),
    'I could not find that patient on your roster.': line(
      'I could not find that patient on your roster.',
      'Ro‘yxatingizda bu bemor topilmadi.',
      'Этого пациента нет в вашем списке.',
    ),
    'Which period should I use?': line(
      'Which period should I use?',
      'Qaysi davrni olay?',
      'Какой период использовать?',
    ),
    'I could not retrieve that appointment information.': line(
      'I could not retrieve that appointment information.',
      'Uchrashuv ma’lumotini olib bo‘lmadi.',
      'Не удалось получить эти данные о записях.',
    ),
    'I could not tell which dates you mean.': line(
      'I could not tell which dates you mean.',
      'Qaysi sanalarni nazarda tutganingizni bilmayman.',
      'Не поняла, какие даты вы имеете в виду.',
    ),
    'That period is outside the supported range.': line(
      'That period is outside the supported range.',
      'Bu davr qo‘llab-quvvatlanadigan oralig‘dan tashqarida.',
      'Этот период вне поддерживаемого диапазона.',
    ),
    'The end date is before the start date.': line(
      'The end date is before the start date.',
      'Tugash sanasi boshlanish sanasidan oldin.',
      'Дата окончания раньше даты начала.',
    ),
    'That start date is too far in the future.': line(
      'That start date is too far in the future.',
      'Boshlanish sanasi juda uzoq kelajakda.',
      'Дата начала слишком далеко в будущем.',
    ),
    'That date is too far in the future.': line(
      'That date is too far in the future.',
      'Bu sana juda uzoq kelajakda.',
      'Эта дата слишком далеко в будущем.',
    ),
    'I could not read that number of days.': line(
      'I could not read that number of days.',
      'Kunlar sonini o‘qiy olmadim.',
      'Не удалось прочитать число дней.',
    ),
    'Shifa does not store a rescheduled status. A doctor reschedule changes the visit time. A patient reschedule cancels the old visit and books a new one. Those cannot be counted as reschedules.': line(
      'Shifa does not store a rescheduled status. A doctor reschedule changes the visit time. A patient reschedule cancels the old visit and books a new one. Those cannot be counted as reschedules.',
      'Shifa ko‘chirilgan holatni saqlamaydi. Shifokor ko‘chirsa, tashrif vaqti o‘zgaradi. Bemor ko‘chirsa, eski tashrif bekor qilinib yangisi yoziladi. Ularni ko‘chirishlar sifatida sanab bo‘lmaydi.',
      'Shifa не хранит статус переноса. Перенос врачом меняет время визита. Перенос пациентом отменяет старый визит и создаёт новый. Это нельзя посчитать как переносы.',
    ),
    'Consultation notes do not have a finalized or doctor-signed state. I can count visits with or without a consultation note, and whether the patient has signed.': line(
      'Consultation notes do not have a finalized or doctor-signed state. I can count visits with or without a consultation note, and whether the patient has signed.',
      'Maslahat qaydlarida yakuniy yoki shifokor imzolagan holat yo‘q. Qayd bor-yo‘qligini va bemor imzolaganini sanay olaman.',
      'У заметок консультации нет состояния «завершено» или «подписано врачом». Могу посчитать визиты с заметкой и без неё, и подписал ли пациент.',
    ),
    'Partial payments live on treatment plans, not as an appointment status. I can\'t count partially paid visits without mixing plan balances and visit prices.': line(
      'Partial payments live on treatment plans, not as an appointment status. I can\'t count partially paid visits without mixing plan balances and visit prices.',
      'Qisman to‘lovlar davolash rejalarida, uchrashuv holatida emas. Reja balansi va tashrif narxini aralashtirmasdan qisman to‘langan tashriflarni sanay olmayman.',
      'Частичные оплаты живут в планах лечения, а не в статусе записи. Не могу посчитать частично оплаченные визиты, не смешав балансы планов и цены визитов.',
    ),
    'An average is not defined for this. I can count appointments, or total collected money by currency.': line(
      'An average is not defined for this. I can count appointments, or total collected money by currency.',
      'Bu yerda o‘rtacha aniqlanmagan. Uchrashuvlarni yoki valyuta bo‘yicha tushumni sanay olaman.',
      'Среднее здесь не определено. Могу посчитать записи или собранные деньги по валютам.',
    ),
    'Refunds are not posted by the current payment flow, and they are not subtracted from the clinic ledger. I can\'t report a refund total.': line(
      'Refunds are not posted by the current payment flow, and they are not subtracted from the clinic ledger. I can\'t report a refund total.',
      'Hozirgi to‘lov oqimi qaytarishlarni yozmaydi va ular klinika daftarchasidan ayirilmaydi. Qaytarish yig‘indisini ayta olmayman.',
      'Текущий платёжный поток не проводит возвраты, и они не вычитаются из журнала клиники. Итог возвратов я сообщить не могу.',
    ),
    'Appointments do not store a booking channel.': line(
      'Appointments do not store a booking channel.',
      'Uchrashuvlarda band qilish kanali saqlanmaydi.',
      'В записях не хранится канал бронирования.',
    ),
    'I can only report appointments for the doctor who is signed in.': line(
      'I can only report appointments for the doctor who is signed in.',
      'Faqat tizimga kirgan shifokorning uchrashuvlari haqida ayta olaman.',
      'Могу сообщить только о записях врача, который вошёл в систему.',
    ),
    'Collected money is paid video-consultation checkouts plus clinic ledger payments for plans you attend, excluding waived amounts. A gateway treatment-plan payment is copied onto the ledger, so it is counted once. Appointment prices and receipt copies are not added. Currencies stay separate. The period is the payment time, not the visit time.': line(
      'Collected money is paid video-consultation checkouts plus clinic ledger payments for plans you attend, excluding waived amounts. A gateway treatment-plan payment is copied onto the ledger, so it is counted once. Appointment prices and receipt copies are not added. Currencies stay separate. The period is the payment time, not the visit time.',
      'Tushum — to‘langan video maslahatlar va siz qatnashgan rejalar bo‘yicha klinika daftarchasi to‘lovlari, kechirilgan summalarsiz. To‘lov shlyuzi orqali reja to‘lovi daftarchaga ko‘chiriladi va bir marta sanaladi. Uchrashuv narxi va chek nusxalari qo‘shilmaydi. Valyutalar alohida. Davr — to‘lov vaqti, tashrif vaqti emas.',
      'Поступления — оплаченные видеоконсультации плюс платежи журнала клиники по планам, которые вы ведёте, без списанных сумм. Платёж плана через шлюз копируется в журнал и считается один раз. Цены визитов и копии чеков не добавляются. Валюты отдельно. Период — время оплаты, не время визита.',
    ),
    'Appointment value is the price stored on the visit. It is not money collected.': line(
      'Appointment value is the price stored on the visit. It is not money collected.',
      'Uchrashuv qiymati — tashrifda saqlangan narx. Bu tushum emas.',
      'Стоимость записи — цена, сохранённая на визите. Это не поступившие деньги.',
    ),
    'Outstanding here is the price on visits marked pending. It does not include treatment-plan balances.': line(
      'Outstanding here is the price on visits marked pending. It does not include treatment-plan balances.',
      'Bu yerdagi qarzdorlik — kutilayotgan tashriflar narxi. Davolash rejasi balanslari kirmaydi.',
      'Задолженность здесь — цена визитов со статусом ожидания. Балансы планов лечения не входят.',
    ),
    'This uses the visit time, not the time the appointment was cancelled, because that time is not stored.': line(
      'This uses the visit time, not the time the appointment was cancelled, because that time is not stored.',
      'Bu tashrif vaqtidan foydalanadi, bekor qilingan vaqtdan emas, chunki u saqlanmaydi.',
      'Здесь используется время визита, а не время отмены, потому что оно не хранится.',
    ),
    'Counts use the scheduled start of the visit. Cancellation time is not stored.': line(
      'Counts use the scheduled start of the visit. Cancellation time is not stored.',
      'Hisob tashrifning rejalashtirilgan boshlanishiga qarab. Bekor qilish vaqti saqlanmaydi.',
      'Подсчёт идёт по запланированному началу визита. Время отмены не хранится.',
    ),
    'Collected money uses payment time for your practice. I can\'t combine that with a patient, status, or documentation filter without mixing different records.': line(
      'Collected money uses payment time for your practice. I can\'t combine that with a patient, status, or documentation filter without mixing different records.',
      'Tushum amaliyotingizdagi to‘lov vaqtiga qarab. Buni bemor, holat yoki hujjat filtri bilan aralashtirmasdan qo‘sha olmayman.',
      'Поступления считаются по времени оплаты вашей практики. Не могу совместить это с фильтром пациента, статуса или документации, не смешав разные записи.',
    ),
    'The cancellation rate already chooses which visits count. Ask for a count if you want one status only.': line(
      'The cancellation rate already chooses which visits count. Ask for a count if you want one status only.',
      'Bekor qilish ulushi qaysi tashriflar kirishini o‘zi tanlaydi. Faqat bitta holat kerak bo‘lsa, sonini so‘rang.',
      'Доля отмен уже сама выбирает, какие визиты входят. Если нужен один статус, попросите количество.',
    ),
    'Upcoming visits are requested, confirmed, or in progress.': line(
      'Upcoming visits are requested, confirmed, or in progress.',
      'Yaqinlashayotgan tashriflar — so‘ralgan, tasdiqlangan yoki jarayonda.',
      'Предстоящие визиты — запрошенные, подтверждённые или идущие.',
    ),
    'I can group appointment counts. I can\'t group that total.': line(
      'I can group appointment counts. I can\'t group that total.',
      'Uchrashuv sonlarini guruhlay olaman. Bu yig‘indini guruhlay olmayman.',
      'Могу группировать количество записей. Этот итог сгруппировать не могу.',
    ),
    'Outstanding amounts are visits marked pending. That conflicts with the payment filter.':
        line(
          'Outstanding amounts are visits marked pending. That conflicts with the payment filter.',
          'Qarzdorlik — kutilayotgan tashriflar. Bu to‘lov filtri bilan zid.',
          'Задолженность — визиты в ожидании. Это противоречит фильтру оплаты.',
        ),
    'Pending is not an appointment status. Unconfirmed visits are requested. Pending payment is a payment status.': line(
      'Pending is not an appointment status. Unconfirmed visits are requested. Pending payment is a payment status.',
      'Kutilmoqda uchrashuv holati emas. Tasdiqlanmagan tashriflar so‘ralgan. To‘lov kutilishi — to‘lov holati.',
      '«Ожидание» — не статус записи. Неподтверждённые визиты — запрошенные. Ожидание оплаты — статус платежа.',
    ),
  };
}

String _namedPeriod(AppLocalizations l10n, String lower) {
  const en = <String, String>{
    'last week': 'last week',
    'this month': 'this month',
    'last month': 'last month',
    'next month': 'next month',
    'this quarter': 'this quarter',
    'last quarter': 'last quarter',
    'next quarter': 'next quarter',
    'this year': 'this year',
    'last year': 'last year',
    'next year': 'next year',
    'coming up': 'coming up',
    'from tomorrow onwards': 'from tomorrow onwards',
    'friday of next week': 'Friday of next week',
    'the upcoming year': 'the upcoming year',
  };
  return _line(
    l10n,
    en: en[lower] ?? lower,
    uz:
        const {
          'last week': 'o‘tgan hafta',
          'this month': 'shu oy',
          'last month': 'o‘tgan oy',
          'next month': 'keyingi oy',
          'this quarter': 'shu chorak',
          'last quarter': 'o‘tgan chorak',
          'next quarter': 'keyingi chorak',
          'this year': 'shu yil',
          'last year': 'o‘tgan yil',
          'next year': 'keyingi yil',
          'coming up': 'yaqinlashayotgan',
          'from tomorrow onwards': 'ertadan boshlab',
          'friday of next week': 'keyingi haftaning juma kuni',
          'the upcoming year': 'kelasi yil',
        }[lower] ??
        lower,
    ru:
        const {
          'last week': 'на прошлой неделе',
          'this month': 'в этом месяце',
          'last month': 'в прошлом месяце',
          'next month': 'в следующем месяце',
          'this quarter': 'в этом квартале',
          'last quarter': 'в прошлом квартале',
          'next quarter': 'в следующем квартале',
          'this year': 'в этом году',
          'last year': 'в прошлом году',
          'next year': 'в следующем году',
          'coming up': 'предстоящие',
          'from tomorrow onwards': 'с завтрашнего дня',
          'friday of next week': 'пятница следующей недели',
          'the upcoming year': 'предстоящий год',
        }[lower] ??
        lower,
  );
}

String _rollingPeriod(
  AppLocalizations l10n,
  String direction,
  String count,
  String unit,
) {
  final next = direction == 'next';
  final one = count == '1';
  final uzUnit = switch (unit) {
    'day' || 'days' => 'kun',
    'week' || 'weeks' => 'hafta',
    _ => 'oy',
  };
  final ruUnit = switch (unit) {
    'day' || 'days' => one ? 'день' : 'дней',
    'week' || 'weeks' => one ? 'неделю' : 'недель',
    _ => one ? 'месяц' : 'месяцев',
  };
  return _line(
    l10n,
    en: 'the $direction $count $unit',
    uz: '${next ? 'keyingi' : 'oxirgi'} $count $uzUnit',
    ru: '${next ? 'следующие' : 'последние'} $count $ruUnit',
  );
}

String _boundedDate(AppLocalizations l10n, String kind, String date) {
  final when = _localizeEnglishDates(l10n, date);
  return _line(
    l10n,
    en: '$kind $date',
    uz: switch (kind.toLowerCase()) {
      'since' => '$when dan beri',
      'before' => '$when dan oldin',
      'after' => '$when dan keyin',
      _ => when,
    },
    ru: switch (kind.toLowerCase()) {
      'since' => 'с $when',
      'before' => 'до $when',
      'after' => 'после $when',
      _ => when,
    },
  );
}

String _betweenDates(AppLocalizations l10n, String start, String end) {
  return _line(
    l10n,
    en: 'from $start until $end',
    uz: '${_localizeEnglishDates(l10n, start)} dan ${_localizeEnglishDates(l10n, end)} gacha',
    ru: 'с ${_localizeEnglishDates(l10n, start)} по ${_localizeEnglishDates(l10n, end)}',
  );
}

String _soonPhrase(AppLocalizations l10n, String raw) {
  final minute = RegExp(r'^in about (\d+) minutes$').firstMatch(raw);
  if (minute != null) {
    return _line(
      l10n,
      en: raw,
      uz: 'taxminan ${minute.group(1)} daqiqadan so‘ng',
      ru: 'примерно через ${minute.group(1)} мин.',
    );
  }
  final hour = RegExp(r'^in about (\d+) hours$').firstMatch(raw);
  if (hour != null) {
    return _line(
      l10n,
      en: raw,
      uz: 'taxminan ${hour.group(1)} soatdan so‘ng',
      ru: 'примерно через ${hour.group(1)} ч.',
    );
  }
  if (raw == 'in about 1 minute') {
    return _line(
      l10n,
      en: raw,
      uz: 'taxminan 1 daqiqadan so‘ng',
      ru: 'примерно через 1 минуту',
    );
  }
  if (raw == 'in about 1 hour') {
    return _line(
      l10n,
      en: raw,
      uz: 'taxminan 1 soatdan so‘ng',
      ru: 'примерно через 1 час',
    );
  }
  return raw;
}

String _andMore(AppLocalizations l10n) =>
    _line(l10n, en: ', and more', uz: ', va yana', ru: ', и ещё');

String _reasonText(AppLocalizations l10n, String raw) {
  if (raw.trim().toLowerCase() == 'not recorded') {
    return _line(l10n, en: 'not recorded', uz: 'yozilmagan', ru: 'не указана');
  }
  return raw;
}

String _openWhere(AppLocalizations l10n, String raw) {
  switch (raw) {
    case 'documentation':
      return _line(
        l10n,
        en: 'documentation',
        uz: 'hujjatlar',
        ru: 'документацию',
      );
    case 'patient record':
      return _line(
        l10n,
        en: 'patient record',
        uz: 'bemor kartasi',
        ru: 'карту пациента',
      );
    default:
      return _line(l10n, en: 'appointment', uz: 'uchrashuv', ru: 'запись');
  }
}

String _dimensionLabel(AppLocalizations l10n, String raw) {
  switch (raw) {
    case 'status':
      return _line(l10n, en: 'status', uz: 'holat', ru: 'статусу');
    case 'patient':
      return _line(l10n, en: 'patient', uz: 'bemor', ru: 'пациенту');
    case 'day':
      return _line(l10n, en: 'day', uz: 'kun', ru: 'дню');
    case 'modality':
      return _line(l10n, en: 'modality', uz: 'turi', ru: 'формату');
    default:
      return raw;
  }
}

String _localizeStatusBits(AppLocalizations l10n, String raw) {
  return raw.replaceAllMapped(
    RegExp(
      r'\b(confirmed|requested|cancelled|canceled|completed|no_show|no-show|in_progress|pending|scheduled)\b',
      caseSensitive: false,
    ),
    (m) => intelligenceStatusLabel(l10n, m.group(1)!.replaceAll('-', '_')),
  );
}

String _moneyLead(AppLocalizations l10n, String raw) {
  if (raw.startsWith('Pending')) {
    return _line(
      l10n,
      en: 'Pending amounts',
      uz: 'kutilayotgan summalar',
      ru: 'Ожидаемые суммы',
    );
  }
  return _line(
    l10n,
    en: 'Appointment prices',
    uz: 'uchrashuv narxlari',
    ru: 'Цены записей',
  );
}

String _moneyParts(AppLocalizations l10n, String raw) {
  var text = raw.replaceAll(
    'from video consultation checkouts',
    _line(
      l10n,
      en: 'from video consultation checkouts',
      uz: 'video maslahat to‘lovlaridan',
      ru: 'из оплат видеоконсультаций',
    ),
  );
  text = text.replaceAll(
    'from clinic ledger payments',
    _line(
      l10n,
      en: 'from clinic ledger payments',
      uz: 'klinika daftarchasi to‘lovlaridan',
      ru: 'из платежей журнала клиники',
    ),
  );
  text = text.replaceAllMapped(RegExp(r'across (\d+) visits'), (m) {
    return _line(
      l10n,
      en: 'across ${m.group(1)} visits',
      uz: '${m.group(1)} ta tashrif bo‘yicha',
      ru: 'по ${m.group(1)} визитам',
    );
  });
  return text;
}

/// Straighten quotes and spaces so server hints match the templates.
String _normalizeToolHint(String hint) {
  return hint
      .replaceAll('“', '"')
      .replaceAll('”', '"')
      .replaceAll('«', '"')
      .replaceAll('»', '"')
      .replaceAll('\u00A0', ' ')
      .replaceAll('\u202F', ' ')
      .trim();
}

String _clock(String raw) {
  final parts = raw.split(':');
  if (parts.length != 2) return raw;
  final hour = int.tryParse(parts[0]);
  if (hour == null) return raw;
  return '${hour.toString().padLeft(2, '0')}:${parts[1]}';
}

String _line(
  AppLocalizations l10n, {
  required String en,
  required String uz,
  required String ru,
}) {
  switch (l10n.locale.languageCode) {
    case 'uz':
      return uz;
    case 'ru':
      return ru;
    default:
      return en;
  }
}

String _fill(String template, Map<String, String> vars) {
  var text = template;
  for (final entry in vars.entries) {
    text = text.replaceAll('{${entry.key}}', entry.value);
  }
  return text;
}

String _speakableDay(
  AppLocalizations l10n,
  String weekday,
  String day,
  String month,
) {
  final dayName = l10n.translate(weekday.toLowerCase());
  var monthName = l10n.translate(_fullMonthKey(month));
  if (l10n.locale.languageCode == 'uz') monthName = monthName.toLowerCase();
  if (l10n.locale.languageCode == 'uz') return '$day-$monthName, $dayName';
  if (l10n.locale.languageCode == 'ru') return '$dayName, $day $monthName';
  return '$weekday, $day $month';
}

String _dayPart(AppLocalizations l10n, String? raw) {
  switch (raw?.trim()) {
    case 'morning':
      return _line(l10n, en: ' morning', uz: ' ertalab', ru: ' утром');
    case 'afternoon':
      return _line(l10n, en: ' afternoon', uz: ' tushdan keyin', ru: ' днём');
    case 'evening':
      return _line(l10n, en: ' evening', uz: ' kechqurun', ru: ' вечером');
    default:
      return '';
  }
}

String _localizeEmbeddedCalendar(AppLocalizations l10n, String raw) {
  var text = raw.replaceAllMapped(RegExp(r'\b(\d{4})-(\d{2})-(\d{2})\b'), (m) {
    final date = DateTime(
      int.parse(m.group(1)!),
      int.parse(m.group(2)!),
      int.parse(m.group(3)!),
    );
    if (date.month != int.parse(m.group(2)!) ||
        date.day != int.parse(m.group(3)!)) {
      return m.group(0)!;
    }
    const weekdays = [
      'Monday',
      'Tuesday',
      'Wednesday',
      'Thursday',
      'Friday',
      'Saturday',
      'Sunday',
    ];
    const months = [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December',
    ];
    return _speakableDay(
      l10n,
      weekdays[date.weekday - 1],
      '${date.day}',
      months[date.month - 1],
    );
  });
  text = text.replaceAllMapped(
    RegExp(
      r'\bfrom (\d{1,2} [A-Za-z]+ \d{4}) until (\d{1,2} [A-Za-z]+ \d{4})\b',
    ),
    (m) => _betweenDates(l10n, m.group(1)!, m.group(2)!),
  );
  text = text.replaceAllMapped(
    RegExp(r'\b(on|since|before|after) (\d{1,2} [A-Za-z]+ \d{4})\b'),
    (m) => _boundedDate(l10n, m.group(1)!, m.group(2)!),
  );
  text = text.replaceAllMapped(
    RegExp(
      r'\b(Friday of next week|from tomorrow onwards|the upcoming year|coming up|this quarter|last quarter|next quarter|this month|last month|next month|this year|last year|next year|last week|this week|next week|today|tomorrow|yesterday)\b',
      caseSensitive: false,
    ),
    (m) => intelligenceRangeLabel(l10n, m.group(1)!),
  );
  text = text.replaceAllMapped(
    RegExp(
      r'\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s*(\d{1,2})\s+(January|February|March|April|May|June|July|August|September|October|November|December)\b',
    ),
    (m) => _speakableDay(l10n, m.group(1)!, m.group(2)!, m.group(3)!),
  );
  text = _localizeVisitList(l10n, text);
  text = text.replaceAllMapped(
    RegExp(
      r'\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b at (\d{2}:\d{2})',
    ),
    (m) {
      final day = l10n.translate(m.group(1)!.toLowerCase());
      return _line(
        l10n,
        en: '${m.group(1)} at ${m.group(2)}',
        uz: '$day soat ${m.group(2)}',
        ru: '$day ${m.group(2)}',
      );
    },
  );
  text = text.replaceAllMapped(
    RegExp(r'\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b'),
    (m) => l10n.translate(m.group(1)!.toLowerCase()),
  );
  text = _localizeEnglishDates(l10n, text);
  text = text.replaceAll(', and more', _andMore(l10n));
  text = text.replaceAllMapped(
    RegExp(r'\bthe (next|last) (\d+) (day|days|week|weeks|month|months)\b'),
    (m) => _rollingPeriod(l10n, m.group(1)!, m.group(2)!, m.group(3)!),
  );
  return text;
}

const _rangeTail =
    'Friday of next week|from tomorrow onwards|the upcoming year|coming up|from .+ onwards|the next \\d+ (?:day|days|week|weeks|month|months)|the last \\d+ (?:day|days|week|weeks|month|months)|on \\d{1,2} [A-Za-z]+ \\d{4}|since \\d{1,2} [A-Za-z]+ \\d{4}|before \\d{1,2} [A-Za-z]+ \\d{4}|after \\d{1,2} [A-Za-z]+ \\d{4}|from \\d{1,2} [A-Za-z]+ \\d{4} until \\d{1,2} [A-Za-z]+ \\d{4}|this quarter|last quarter|next quarter|this month|last month|next month|this year|last year|next year|last week|this week|next week|today|tomorrow|yesterday|[A-Za-z]+, \\d{1,2} [A-Za-z]+(?: \\d{4})?';

final _whenPattern = RegExp(
  r'\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday) (\d{1,2}) (January|February|March|April|May|June|July|August|September|October|November|December) at (\d{2}:\d{2})\b',
);

const _shortMonths = <String, int>{
  'jan': 1,
  'feb': 2,
  'mar': 3,
  'apr': 4,
  'may': 5,
  'jun': 6,
  'jul': 7,
  'aug': 8,
  'sep': 9,
  'sept': 9,
  'oct': 10,
  'nov': 11,
  'dec': 12,
};

const _fullMonths = <String, int>{
  'january': 1,
  'february': 2,
  'march': 3,
  'april': 4,
  'may': 5,
  'june': 6,
  'july': 7,
  'august': 8,
  'september': 9,
  'october': 10,
  'november': 11,
  'december': 12,
};

String _localizeVisitList(AppLocalizations l10n, String names) {
  final withMore = names.replaceAllMapped(RegExp(r', and (\d+) more\b'), (m) {
    return l10n
        .translate('intelCancelAndMore')
        .replaceAll('{count}', m.group(1)!);
  });
  return withMore.replaceAllMapped(_whenPattern, (m) {
    final weekday = l10n.translate(m.group(1)!.toLowerCase());
    var month = l10n.translate(_fullMonthKey(m.group(3)!));
    if (l10n.locale.languageCode == 'uz') month = month.toLowerCase();
    return l10n
        .translate('intelWhenAt')
        .replaceAll('{weekday}', weekday)
        .replaceAll('{day}', m.group(2)!)
        .replaceAll('{month}', month)
        .replaceAll('{time}', m.group(4)!);
  });
}

String _localizeEnglishDates(AppLocalizations l10n, String text) {
  final full = text.replaceAllMapped(
    RegExp(
      r'\b(January|February|March|April|May|June|July|August|September|October|November|December)\b',
    ),
    (m) => l10n.translate(_fullMonthKey(m.group(1)!)),
  );
  return full.replaceAllMapped(
    RegExp(r'\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\b'),
    (m) {
      final month = _shortMonths[m.group(1)!.toLowerCase()];
      if (month == null) return m.group(0)!;
      return l10n.monthShort(month);
    },
  );
}

DateTime? _parseIsoDate(String? raw) {
  if (raw == null) return null;
  final match = RegExp(r'^(\d{4})-(\d{2})-(\d{2})').firstMatch(raw.trim());
  if (match == null) return null;
  return DateTime(
    int.parse(match.group(1)!),
    int.parse(match.group(2)!),
    int.parse(match.group(3)!),
  );
}

DateTime? _parseEnglishDate(String? raw) {
  if (raw == null) return null;
  final match = RegExp(
    r'^(\d{1,2})\s+([A-Za-z]+)\s+(\d{4})$',
  ).firstMatch(raw.trim());
  if (match == null) return null;
  final token = match.group(2)!.toLowerCase();
  final month = _fullMonths[token] ?? _shortMonths[token];
  if (month == null) return null;
  return DateTime(
    int.parse(match.group(3)!),
    month,
    int.parse(match.group(1)!),
  );
}

String _fullMonthKey(String english) {
  final lower = english.toLowerCase();
  return 'month${lower[0].toUpperCase()}${lower.substring(1)}';
}
