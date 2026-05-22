import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shifa_doc_app_v1/core/config/app_config.dart';

/// Cached values from GET /api/public/config (no auth).
class PublicBackendConfig {
  PublicBackendConfig._();

  static Map<String, dynamic>? _cache;
  static bool _fetching = false;

  static void clearCache() {
    _cache = null;
  }

  static Future<Map<String, dynamic>> _ensureLoaded() async {
    final hit = _cache;
    if (hit != null) return hit;

    if (_fetching) {
      for (var i = 0; i < 20; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 80));
        if (_cache != null) return _cache!;
      }
    }

    if (AppConfig.apiBaseUrl.isEmpty) return {};

    try {
      _fetching = true;
      final uri = Uri.parse('${AppConfig.apiBaseUrl}/api/public/config');
      final response =
          await http.get(uri).timeout(const Duration(seconds: 5));
      if (response.statusCode == 200) {
        final body = json.decode(response.body);
        if (body is Map<String, dynamic>) {
          _cache = body;
          return body;
        }
      }
    } catch (_) {
      // ignore
    } finally {
      _fetching = false;
    }
    _cache ??= {};
    return _cache!;
  }

  static Future<bool> transcriptionFeedbackEnabled() async {
    final map = await _ensureLoaded();
    final raw = map['transcriptionFeedbackEnabled'];
    return raw == true || raw == 'true';
  }
}
