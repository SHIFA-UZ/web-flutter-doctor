import 'dart:convert';

import 'package:flutter/services.dart';

/// Loads `assets/localization/{lang}.json` once per language code.
class LocalizationAssetLoader {
  LocalizationAssetLoader._();

  static final Map<String, Map<String, String>> _cache = {};

  static Future<Map<String, String>> load(String languageCode) async {
    final cached = _cache[languageCode];
    if (cached != null) return cached;

    try {
      final raw = await rootBundle.loadString(
        'assets/localization/$languageCode.json',
      );
      final decoded = json.decode(raw) as Map<String, dynamic>;
      final map = decoded.map((k, v) => MapEntry(k, v.toString()));
      _cache[languageCode] = map;
      return map;
    } catch (_) {
      _cache[languageCode] = {};
      return {};
    }
  }

  static Map<String, String>? cached(String languageCode) =>
      _cache[languageCode];
}
