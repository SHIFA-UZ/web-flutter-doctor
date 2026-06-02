import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Persists doctor/admin JWT tokens securely on mobile; uses SharedPreferences on web.
class SecureTokenStorage {
  static const _doctorKey = 'shifa_doctor_auth_token';
  static const _adminKey = 'shifa_admin_auth_token';

  static final FlutterSecureStorage _secure = const FlutterSecureStorage(
    aOptions: AndroidOptions(
      sharedPreferencesName: 'shifa_doctor_auth_secure',
      resetOnError: false,
    ),
  );

  Future<void> saveToken(String token, {required bool forAdmin}) async {
    final key = forAdmin ? _adminKey : _doctorKey;
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(key, token);
      return;
    }
    await _secure.write(key: key, value: token);
  }

  Future<String?> readToken({required bool forAdmin}) async {
    final key = forAdmin ? _adminKey : _doctorKey;
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(key);
    }
    return _secure.read(key: key);
  }

  Future<void> clearToken({required bool adminOnly}) async {
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      if (adminOnly) {
        await prefs.remove(_adminKey);
      } else {
        await prefs.remove(_doctorKey);
        await prefs.remove(_adminKey);
      }
      return;
    }
    if (adminOnly) {
      await _secure.delete(key: _adminKey);
    } else {
      await _secure.delete(key: _doctorKey);
      await _secure.delete(key: _adminKey);
    }
  }

  /// One-time migration from legacy SharedPreferences storage on mobile.
  Future<void> migrateFromSharedPreferencesIfNeeded() async {
    if (kIsWeb) return;
    final prefs = await SharedPreferences.getInstance();
    for (final entry in <String, bool>{
      _doctorKey: false,
      _adminKey: true,
    }.entries) {
      final legacy = prefs.getString(entry.key);
      if (legacy == null || legacy.isEmpty) continue;
      final existing = await _secure.read(key: entry.key);
      if (existing == null || existing.isEmpty) {
        await _secure.write(key: entry.key, value: legacy);
      }
      await prefs.remove(entry.key);
    }
  }
}
