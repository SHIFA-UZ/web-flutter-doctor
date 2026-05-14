import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Persisted tag / [DropdownMenuItem] value for Uzbek Cyrillic UI (`Locale(uz, Cyrl)`).
const String kUzbekCyrillicLocaleTag = 'uz-Cyrl';

extension DoctorAppLocale on Locale {
  /// API / backend language (`en`, `uz`, `ru`). Cyrillic Uzbek still maps to `uz`.
  String get backendLanguageCode {
    switch (languageCode) {
      case 'uz':
        return 'uz';
      case 'ru':
        return 'ru';
      default:
        return 'en';
    }
  }

  bool get isUzbekCyrillic =>
      languageCode == 'uz' && (scriptCode ?? '') == 'Cyrl';

  /// [SharedPreferences] `doctor_language` and profile `settings.language`.
  String get persistenceTag {
    if (isUzbekCyrillic) return kUzbekCyrillicLocaleTag;
    return languageCode;
  }
}

Locale localeFromPersistenceTag(String raw) {
  final t = raw.trim().toLowerCase();
  if (t == 'uz-cyrl' || t == 'uz_cyrl') {
    return Locale.fromSubtags(languageCode: 'uz', scriptCode: 'Cyrl');
  }
  final primary = raw.split(RegExp(r'[-_]')).first.trim();
  if (primary.isEmpty) return const Locale('en');
  return Locale(primary);
}

class LanguageState {
  final Locale locale;

  LanguageState(this.locale);
}

class LanguageNotifier extends StateNotifier<LanguageState> {
  LanguageNotifier() : super(LanguageState(const Locale('en'))) {
    _loadLanguage();
  }

  Future<void> _loadLanguage() async {
    final prefs = await SharedPreferences.getInstance();
    final tag = prefs.getString('doctor_language') ?? 'en';
    state = LanguageState(localeFromPersistenceTag(tag));
  }

  Future<void> setLanguage(Locale locale) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('doctor_language', locale.persistenceTag);
    state = LanguageState(locale);
  }
}

final languageProvider = StateNotifierProvider<LanguageNotifier, LanguageState>((ref) {
  return LanguageNotifier();
});
