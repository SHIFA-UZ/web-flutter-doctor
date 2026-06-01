import 'dart:ui' show PlatformDispatcher;

import 'package:flutter/material.dart';

import 'locale_detection_stub.dart'
    if (dart.library.html) 'locale_detection_web.dart';

const _kLanguageExplicitKey = 'doctor_language_explicit';

/// Whether the user manually picked a language (do not override with region defaults).
String get languageExplicitPrefKey => _kLanguageExplicitKey;

/// True when the device/browser likely indicates Uzbekistan.
bool isLikelyUzbekistan({String? phoneCountryCode}) {
  final code = phoneCountryCode?.trim();
  if (code == '+998' || code == '998') return true;

  final locale = PlatformDispatcher.instance.locale;
  if (locale.countryCode?.toUpperCase() == 'UZ') return true;
  if (locale.languageCode.toLowerCase() == 'uz') return true;

  final tz = readBrowserTimeZoneId();
  if (tz == 'Asia/Tashkent') return true;

  return false;
}

/// Default locale before the user makes an explicit choice.
Locale detectDefaultLocale({String? phoneCountryCode}) {
  if (isLikelyUzbekistan(phoneCountryCode: phoneCountryCode)) {
    return const Locale('uz');
  }
  return const Locale('en');
}

/// Extracts a dial code from an E.164-style phone number (e.g. `+998901234567` → `+998`).
String? countryCodeFromPhone(String fullPhone) {
  final trimmed = fullPhone.trim();
  if (trimmed.startsWith('+998') || trimmed.startsWith('998')) return '+998';
  return null;
}
