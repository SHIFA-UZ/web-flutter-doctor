import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../state/auth/auth_controller.dart';
import '../util/jwt_utils.dart';

const String _lastActivityStorageKey = 'shifa_doctor_last_activity_ms';

/// Service that tracks user inactivity and automatically logs out after 1 hour.
/// Persists last activity so background/throttled browser tabs still enforce timeout on resume.
class InactivityTimerService {
  Timer? _timer;
  final Duration _timeoutDuration;
  final VoidCallback _onTimeout;
  final VoidCallback? _onJwtExpired;
  DateTime? _lastActivityTime;

  InactivityTimerService({
    Duration? timeoutDuration,
    required VoidCallback onTimeout,
    VoidCallback? onJwtExpired,
  })  : _timeoutDuration = timeoutDuration ?? const Duration(hours: 1),
        _onTimeout = onTimeout,
        _onJwtExpired = onJwtExpired;

  /// Reset the inactivity timer (call this on user activity).
  void resetTimer() {
    _lastActivityTime = DateTime.now();
    unawaited(_persistLastActivity(_lastActivityTime!));
    _timer?.cancel();
    _timer = Timer(_timeoutDuration, _onTimeout);
  }

  /// Enforce timeout after tab resume or long background idle (timers may not fire when throttled).
  Future<void> enforceTimeoutIfNeeded({String? jwt}) async {
    if (jwt != null && isJwtExpired(jwt)) {
      _onJwtExpired?.call();
      return;
    }

    final last = _lastActivityTime ?? await _loadLastActivity();
    if (last == null) return;
    _lastActivityTime = last;

    if (DateTime.now().difference(last) >= _timeoutDuration) {
      _onTimeout();
    }
  }

  /// Stop the timer (e.g., when user logs out).
  void stopTimer() {
    _timer?.cancel();
    _timer = null;
    _lastActivityTime = null;
    unawaited(_clearLastActivity());
  }

  DateTime? get lastActivityTime => _lastActivityTime;

  void dispose() {
    stopTimer();
  }

  Future<void> _persistLastActivity(DateTime time) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(_lastActivityStorageKey, time.millisecondsSinceEpoch);
    } catch (_) {
      // Best effort.
    }
  }

  Future<DateTime?> _loadLastActivity() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final ms = prefs.getInt(_lastActivityStorageKey);
      if (ms == null) return null;
      return DateTime.fromMillisecondsSinceEpoch(ms);
    } catch (_) {
      return null;
    }
  }

  Future<void> _clearLastActivity() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_lastActivityStorageKey);
    } catch (_) {
      // Best effort.
    }
  }
}

/// Provider for the inactivity timer service.
final inactivityTimerProvider = Provider<InactivityTimerService?>((ref) {
  final authState = ref.watch(authProvider);

  if (!authState.isAuthenticated) {
    return null;
  }

  final service = InactivityTimerService(
    onTimeout: () {
      ref.read(authProvider.notifier).logout();
    },
    onJwtExpired: () {
      ref.read(authProvider.notifier).logout();
    },
  );

  service.resetTimer();

  ref.onDispose(() {
    service.dispose();
  });

  return service;
});
