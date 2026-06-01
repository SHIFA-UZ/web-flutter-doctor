import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:shifa_doc_app_v1/core/localization/locale_detection.dart';

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

/// Material [showDatePicker] / `intl` use language-only locales (`uz`, not `uz`/Cyrl).
Locale localeForMaterialIntl(Locale locale) => Locale(locale.languageCode);

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
    final saved = prefs.getString('doctor_language');
    final explicit = prefs.getBool(languageExplicitPrefKey) ?? false;

    if (saved != null && saved.isNotEmpty) {
      if (explicit) {
        state = LanguageState(localeFromPersistenceTag(saved));
        return;
      }
      // Legacy or auto-saved — re-check region on each cold start when not explicit.
      final regional = detectDefaultLocale();
      if (regional.languageCode != saved) {
        await prefs.setString('doctor_language', regional.persistenceTag);
        state = LanguageState(regional);
        return;
      }
      state = LanguageState(localeFromPersistenceTag(saved));
      return;
    }

    final detected = detectDefaultLocale();
    await prefs.setString('doctor_language', detected.persistenceTag);
    state = LanguageState(detected);
  }

  /// Call on login screens; sets Uzbek when in Uzbekistan unless user chose a language.
  Future<void> applyRegionalDefaultIfUnset({String? phoneCountryCode}) async {
    final prefs = await SharedPreferences.getInstance();
    if (prefs.getBool(languageExplicitPrefKey) ?? false) return;

    if (!isLikelyUzbekistan(phoneCountryCode: phoneCountryCode)) return;

    const uz = Locale('uz');
    await prefs.setString('doctor_language', uz.persistenceTag);
    state = LanguageState(uz);
  }

  Future<void> setLanguage(Locale locale) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('doctor_language', locale.persistenceTag);
    await prefs.setBool(languageExplicitPrefKey, true);
    state = LanguageState(locale);
  }
}

final languageProvider = StateNotifierProvider<LanguageNotifier, LanguageState>((ref) {
  return LanguageNotifier();
});
