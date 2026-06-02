import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';

class AppLockService {
  static final _storage = FlutterSecureStorage(
    aOptions: const AndroidOptions(
      sharedPreferencesName: 'shifa_doctor_app_lock_prefs',
      resetOnError: false,
    ),
  );

  static const _pinKey = 'app_lock_pin';
  static const _biometricEnabledKey = 'biometric_enabled';
  static const _lockEnabledKey = 'app_lock_enabled';
  static const int maxPinAttempts = 5;
  static const int minPinLength = 4;
  static const int maxPinLength = 6;
  static const int defaultInactivitySeconds = 30;

  final LocalAuthentication _localAuth = LocalAuthentication();

  static bool isValidPin(String pin) {
    final trimmed = pin.trim();
    if (trimmed.length < minPinLength || trimmed.length > maxPinLength) return false;
    return RegExp(r'^\d+$').hasMatch(trimmed);
  }

  Future<bool> isLockEnabled() async => (await _storage.read(key: _lockEnabledKey)) == 'true';

  Future<void> setLockEnabled(bool enabled) async {
    await _storage.write(key: _lockEnabledKey, value: enabled.toString());
  }

  Future<bool> isBiometricAvailable() async {
    if (kIsWeb) return false;
    try {
      return await _localAuth.canCheckBiometrics && await _localAuth.isDeviceSupported();
    } catch (_) {
      return false;
    }
  }

  Future<bool> isBiometricEnabled() async =>
      (await _storage.read(key: _biometricEnabledKey)) == 'true';

  Future<void> setBiometricEnabled(bool enabled) async {
    await _storage.write(key: _biometricEnabledKey, value: enabled.toString());
  }

  Future<void> setPin(String pin) async {
    if (!isValidPin(pin)) throw ArgumentError('PIN must be 4-6 digits');
    await _storage.write(key: _pinKey, value: pin.trim());
  }

  Future<bool> verifyPin(String enteredPin) async {
    if (!isValidPin(enteredPin)) return false;
    final stored = await _storage.read(key: _pinKey);
    return stored != null && stored.trim() == enteredPin.trim();
  }

  Future<bool> hasPin() async {
    final pin = await _storage.read(key: _pinKey);
    return pin != null && pin.isNotEmpty;
  }

  Future<void> clearPin() async => _storage.delete(key: _pinKey);

  Future<bool> authenticateWithBiometric() async {
    if (kIsWeb || !await isBiometricAvailable() || !await isBiometricEnabled()) {
      return false;
    }
    try {
      return await _localAuth.authenticate(
        localizedReason: 'Unlock Shifa Doctor',
        options: const AuthenticationOptions(biometricOnly: true, stickyAuth: true),
      );
    } catch (_) {
      return false;
    }
  }
}
