import 'dart:convert';

class IntelligenceChatMessage {
  const IntelligenceChatMessage({
    required this.role,
    required this.content,
    required this.at,
    this.toolName,
    this.cardPayload,
  });

  final String role; // user | assistant
  final String content;
  final DateTime at;

  /// Morning tool name when this assistant turn has a structured card.
  final String? toolName;

  /// Structured tool payload for restoring action cards.
  final Map<String, dynamic>? cardPayload;

  Map<String, dynamic> toJson() => {
    'role': role,
    'content': content,
    'at': at.toIso8601String(),
    if (toolName != null) 'toolName': toolName,
    if (cardPayload != null) 'cardPayload': cardPayload,
  };

  factory IntelligenceChatMessage.fromJson(Map<String, dynamic> json) {
    Map<String, dynamic>? card;
    final raw = json['cardPayload'];
    if (raw is Map) {
      card = Map<String, dynamic>.from(raw);
    }
    return IntelligenceChatMessage(
      role: (json['role'] as String?) ?? 'user',
      content: (json['content'] as String?) ?? '',
      at: DateTime.tryParse((json['at'] as String?) ?? '') ?? DateTime.now(),
      toolName: json['toolName'] as String?,
      cardPayload: card,
    );
  }
}

/// Clinic-wide cards. Nested patient rows here do not make the chat patient-linked.
const _clinicWideCardTypes = {
  'schedule',
  'free_slots',
  'clinic_patterns',
  'most_visited',
};

const _clinicWideTools = {'get_today_schedule', 'list_free_slots'};

/// Title, preview, and patient-link helpers for locally stored Intelligence chats.
abstract final class IntelligenceChatTitles {
  static const maxTitleLength = 48;
  static const maxPreviewLength = 90;

  /// Prompt text (punctuation ignored) → short title in that prompt's language.
  /// Keep in step with `intelPrompt*` and the previous prompt strings.
  static const starterTitles = <String, String>{
    'summarize my schedule for today': "Today's schedule",
    'what is my schedule today': "Today's schedule",
    'help me find a patient': 'Find a patient',
    'help me manage my appointments': 'Manage appointments',
    'help me manage an appointment — find a patient and propose free slots':
        'Manage appointments',
    'help me manage an appointment - find a patient and propose free slots':
        'Manage appointments',
    'help me review my clinical notes': 'Clinical notes',
    'list draft visit facts for my current appointment so i can document them':
        'Clinical notes',
    'bugungi jadvalimni qisqacha ayting': 'Bugungi jadval',
    'bugungi jadvalim qanday': 'Bugungi jadval',
    'bemorni topishga yordam bering': 'Bemorni topish',
    'uchrashuvlarimni boshqarishga yordam bering': 'Uchrashuvlarni boshqarish',
    'uchrashuvni boshqarishga yordam bering — bemorni toping va bo\'sh slotlarni taklif qiling':
        'Uchrashuvlarni boshqarish',
    'klinik qaydlarimni ko\'rib chiqishga yordam bering': 'Klinik qaydlar',
    'joriy uchrashuvim uchun qoralama faktlarni ro\'yxatlang, hujjatlashtirish uchun':
        'Klinik qaydlar',
    'кратко изложите моё расписание на сегодня': 'Расписание на сегодня',
    'кратко изложите мое расписание на сегодня': 'Расписание на сегодня',
    'какое у меня расписание сегодня': 'Расписание на сегодня',
    'помогите найти пациента': 'Найти пациента',
    'помогите управлять моими приёмами': 'Управление приёмами',
    'помогите управлять моими приемами': 'Управление приёмами',
    'помогите с приёмом — найдите пациента и предложите свободные слоты':
        'Управление приёмами',
    'помогите с приемом — найдите пациента и предложите свободные слоты':
        'Управление приёмами',
    'помогите просмотреть мои клинические заметки': 'Клинические заметки',
    'покажите черновики фактов текущего приёма для документирования':
        'Клинические заметки',
    'покажите черновики фактов текущего приема для документирования':
        'Клинические заметки',
  };

  static const _shifaNameTokens = {
    'shifa',
    'shifah',
    'shefa',
    'shefah',
    'shiva',
    'sheva',
    'шифа',
  };

  static List<String> _wakeWords(String text) {
    final t = text
        .toLowerCase()
        .replaceAll(RegExp(r'[^\p{L}\p{N}]+', unicode: true), ' ')
        .trim();
    if (t.isEmpty) return const [];
    return t.split(RegExp(r'\s+'));
  }

  /// True for "Hey Shifa", Uzbek "Salom Shifa" / "Assalomu alaykum Shifa",
  /// Russian "Привет Шифа", and common Chrome mishears of the name.
  static bool containsWakePhrase(String text) {
    final words = _wakeWords(text);
    if (words.isEmpty) return false;
    if (words.any(_shifaNameTokens.contains)) return true;
    for (var i = 0; i < words.length - 1; i++) {
      if (words[i] == 'she' &&
          const {'fa', 'fah', 'for', 'va'}.contains(words[i + 1])) {
        return true;
      }
    }
    return false;
  }

  static String stripWakePhrase(String text) {
    var t = text.trim();
    t = t.replaceFirst(
      RegExp(
        r'^(?:hey|hi|hello|ok|okay|salom|salaam|salam|salem|'
        r'assalomu?(?:\s+alaykum)?|assalamu?(?:\s+alaykum)?|'
        r'эй|привет|здравствуйте?|салом|ассалому?(?:\s+алайкум)?)\s+',
        caseSensitive: false,
        unicode: true,
      ),
      '',
    );
    t = t.replaceFirst(
      RegExp(
        r'^(?:shifa+|shifah|shefa+|shefah|shiva+|sheva+|шифа|'
        r'she\s+(?:fa|fah|for|va))\s*[,!.:]?\s*',
        caseSensitive: false,
        unicode: true,
      ),
      '',
    );
    return t.trim();
  }

  static String _lookupKey(String text) {
    var t = text.trim().toLowerCase().replaceAll(RegExp(r'\s+'), ' ');
    t = t.replaceAll(RegExp(r"[‘’ʻʼ`']"), "'");
    t = t.replaceAll(RegExp(r'[.!?…]+$'), '');
    return t;
  }

  static String shortTitle(String raw) {
    final stripped = stripWakePhrase(
      raw,
    ).replaceAll(RegExp(r'\s+'), ' ').trim();
    if (stripped.isEmpty) return '';
    final mapped = starterTitles[_lookupKey(stripped)];
    if (mapped != null) return mapped;
    if (stripped.length <= maxTitleLength) return stripped;
    final cut = stripped.substring(0, maxTitleLength);
    final space = cut.lastIndexOf(' ');
    final base = space > 24 ? cut.substring(0, space) : cut.trimRight();
    return '$base…';
  }

  static String previewLine(String raw) {
    final stripped = stripWakePhrase(
      raw,
    ).replaceAll(RegExp(r'\s+'), ' ').trim();
    if (stripped.isEmpty) return '';
    if (stripped.length <= maxPreviewLength) return stripped;
    final cut = stripped.substring(0, maxPreviewLength);
    final space = cut.lastIndexOf(' ');
    final base = space > 40 ? cut.substring(0, space) : cut.trimRight();
    return '$base…';
  }

  /// Language of one user turn. Null means keep the session language.
  /// Short Uzbek date/time fragments must not flip the thread to English.
  static String? detectReplyLanguage(String text) {
    final t = text.trim();
    if (t.isEmpty) return null;
    if (RegExp(r'[ўқғҳЎҚҒҲ]').hasMatch(t)) return 'UZ';
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
      'soat',
      'oktabr',
      'oktyabr',
      "ko'chir",
      'kochir',
      'dagi',
      'kungi',
      "to'g'ri",
      'keladi',
      'hafta',
      'vaqt',
      "bo'sh",
      'ertaga',
      'qara',
      'uchun',
    ];
    for (final m in uzMarkers) {
      if (lower.contains(m)) return 'UZ';
    }
    const enWords = {
      'the',
      'please',
      'which',
      'should',
      'what',
      'schedule',
      'appointment',
      'reschedule',
      'today',
      'tomorrow',
      'show',
      'list',
      'move',
      'how',
      'many',
    };
    final tokens = lower.split(RegExp(r"[^\p{L}']+", unicode: true));
    if (tokens.where(enWords.contains).length >= 2) return 'EN';
    return null;
  }
}

class IntelligencePatientMark {
  const IntelligencePatientMark({required this.linked, this.name});

  final bool linked;
  final String? name;
}

/// Detects a patient already present on a stored tool card. Schedule and
/// analytics cards stay general even when their rows include names.
abstract final class IntelligenceChatContext {
  static bool _isSearch(Map<String, dynamic> payload, String? toolName) {
    final type = (payload['cardType'] as String?) ?? '';
    return type == 'patient_search' || toolName == 'search_patients';
  }

  static bool _excluded(Map<String, dynamic> payload, String? toolName) {
    final type = (payload['cardType'] as String?) ?? '';
    if (_clinicWideCardTypes.contains(type)) return true;
    if (toolName != null &&
        _clinicWideTools.contains(toolName) &&
        type.isEmpty) {
      return true;
    }
    return false;
  }

  static List<Map<String, dynamic>> _matches(Map<String, dynamic> payload) {
    final raw = payload['matches'];
    if (raw is! List) return const [];
    return raw
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
  }

  static String? _cleanName(Object? raw) {
    final name = raw?.toString().trim() ?? '';
    if (name.isEmpty) return null;
    if (name.toLowerCase() == 'patient') return null;
    return name;
  }

  static bool isPatientLinked(Map<String, dynamic>? payload, String? toolName) {
    if (payload == null || payload.isEmpty) return false;
    if (_excluded(payload, toolName)) return false;
    if (_isSearch(payload, toolName)) {
      return _matches(payload).any(
        (row) =>
            row['patientId'] != null || _cleanName(row['patientName']) != null,
      );
    }
    if (payload['patientId'] != null) return true;
    return _cleanName(payload['patientName']) != null;
  }

  static String? patientName(Map<String, dynamic>? payload, String? toolName) {
    if (payload == null || !isPatientLinked(payload, toolName)) return null;
    if (_isSearch(payload, toolName)) {
      final matches = _matches(payload);
      if (matches.length != 1) return null;
      return _cleanName(matches.first['patientName']);
    }
    return _cleanName(payload['patientName']);
  }

  static IntelligencePatientMark summarize(
    Iterable<({Map<String, dynamic>? payload, String? toolName})> items,
  ) {
    var linked = false;
    String? name;
    for (final item in items.toList().reversed) {
      if (!isPatientLinked(item.payload, item.toolName)) continue;
      linked = true;
      name ??= patientName(item.payload, item.toolName);
    }
    return IntelligencePatientMark(linked: linked, name: name);
  }
}

class IntelligenceChatSession {
  IntelligenceChatSession({
    required this.id,
    required this.title,
    required this.createdAt,
    required this.updatedAt,
    required this.messages,
    this.titleCustom = false,
  });

  static const untitled = 'New chat';

  final String id;
  String title;
  final DateTime createdAt;
  DateTime updatedAt;
  List<IntelligenceChatMessage> messages;

  /// Doctor renamed this chat. [title] is then kept as typed.
  bool titleCustom;

  String get displayTitle {
    if (titleCustom && title.trim().isNotEmpty) return title.trim();
    for (final m in messages) {
      if (m.role == 'user' && m.content.trim().isNotEmpty) {
        final short = IntelligenceChatTitles.shortTitle(m.content);
        if (short.isNotEmpty) return short;
      }
    }
    final t = title.trim();
    if (t.isEmpty || t == untitled) return untitled;
    return IntelligenceChatTitles.shortTitle(t);
  }

  /// Latest message, shortened. Independent of [displayTitle].
  String get preview {
    for (final m in messages.reversed) {
      final text = m.content.trim();
      if (text.isEmpty) continue;
      return IntelligenceChatTitles.previewLine(text);
    }
    return '';
  }

  IntelligencePatientMark get patientMark => IntelligenceChatContext.summarize(
    messages.map((m) => (payload: m.cardPayload, toolName: m.toolName)),
  );

  bool get patientLinked => patientMark.linked;

  bool matchesQuery(String query) {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return true;
    if (displayTitle.toLowerCase().contains(q)) return true;
    if (preview.toLowerCase().contains(q)) return true;
    for (final m in messages) {
      if (m.content.toLowerCase().contains(q)) return true;
    }
    return false;
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'titleCustom': titleCustom,
    'createdAt': createdAt.toIso8601String(),
    'updatedAt': updatedAt.toIso8601String(),
    'messages': messages.map((m) => m.toJson()).toList(),
  };

  factory IntelligenceChatSession.fromJson(Map<String, dynamic> json) {
    final rawMessages = (json['messages'] as List? ?? const [])
        .whereType<Map>()
        .map(
          (e) => IntelligenceChatMessage.fromJson(Map<String, dynamic>.from(e)),
        )
        .toList();
    return IntelligenceChatSession(
      id:
          (json['id'] as String?) ??
          DateTime.now().millisecondsSinceEpoch.toString(),
      title: (json['title'] as String?) ?? untitled,
      titleCustom: json['titleCustom'] == true,
      createdAt:
          DateTime.tryParse((json['createdAt'] as String?) ?? '') ??
          DateTime.now(),
      updatedAt:
          DateTime.tryParse((json['updatedAt'] as String?) ?? '') ??
          DateTime.now(),
      messages: rawMessages,
    );
  }

  static String encodeList(List<IntelligenceChatSession> sessions) =>
      jsonEncode(sessions.map((s) => s.toJson()).toList());

  static List<IntelligenceChatSession> decodeList(String raw) {
    try {
      final list = jsonDecode(raw);
      if (list is! List) return [];
      return list
          .whereType<Map>()
          .map(
            (e) =>
                IntelligenceChatSession.fromJson(Map<String, dynamic>.from(e)),
          )
          .toList()
        ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    } catch (_) {
      return [];
    }
  }
}
