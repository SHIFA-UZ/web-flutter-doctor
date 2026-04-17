import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Client-side rate limit: max [maxAttempts] OTP requests per [windowMinutes] per phone.
/// Stores timestamps in SharedPreferences. Does not store OTP or verificationId.
class OtpRateLimitService {
  static const int maxAttempts = 3;
  static const int windowMinutes = 10;
  static const String _prefix = 'otp_rate_';

  final SharedPreferences _prefs;

  OtpRateLimitService(this._prefs);

  /// Normalize phone for storage (digits only, no leading +).
  static String _normalizePhone(String fullPhone) {
    return fullPhone.replaceAll(RegExp(r'\D'), '');
  }

  /// Returns true if another OTP request is allowed for this phone.
  bool canRequestOtp(String fullPhone) {
    final key = _normalizePhone(fullPhone);
    final now = DateTime.now();
    final windowStart = now.subtract(Duration(minutes: windowMinutes));
    final timestamps = _getTimestamps(key);
    final recent = timestamps.where((t) => t.isAfter(windowStart)).toList();
    return recent.length < maxAttempts;
  }

  /// Record an OTP request for this phone. Call after passing [canRequestOtp].
  Future<void> recordRequest(String fullPhone) async {
    final key = _normalizePhone(fullPhone);
    final timestamps = _getTimestamps(key);
    final now = DateTime.now();
    final windowStart = now.subtract(Duration(minutes: windowMinutes));
    final recent = timestamps.where((t) => t.isAfter(windowStart)).toList()
      ..add(now);
    final toStore = recent.map((t) => t.toIso8601String()).toList();
    await _prefs.setString('$_prefix$key', jsonEncode(toStore));
  }

  List<DateTime> _getTimestamps(String key) {
    final raw = _prefs.getString('$_prefix$key');
    if (raw == null) return [];
    try {
      final list = jsonDecode(raw) as List<dynamic>?;
      if (list == null) return [];
      return list
          .map((e) => DateTime.tryParse(e as String))
          .whereType<DateTime>()
          .toList();
    } catch (_) {
      return [];
    }
  }

  /// Seconds until the oldest request in the current window expires (for UI hint).
  int? secondsUntilNextAllowed(String fullPhone) {
    final key = _normalizePhone(fullPhone);
    final timestamps = _getTimestamps(key);
    final now = DateTime.now();
    final windowStart = now.subtract(Duration(minutes: windowMinutes));
    final recent = timestamps.where((t) => t.isAfter(windowStart)).toList();
    if (recent.length < maxAttempts) return null;
    recent.sort();
    final oldest = recent.first;
    final allowedAt = oldest.add(const Duration(minutes: windowMinutes));
    final sec = allowedAt.difference(now).inSeconds;
    return sec > 0 ? sec : null;
  }
}
