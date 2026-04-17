import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/auth/auth_controller.dart';

/// Service that tracks user inactivity and automatically logs out after 1 hour
class InactivityTimerService {
  Timer? _timer;
  final Duration _timeoutDuration;
  final VoidCallback _onTimeout;
  DateTime? _lastActivityTime;

  InactivityTimerService({
    Duration? timeoutDuration,
    required VoidCallback onTimeout,
  })  : _timeoutDuration = timeoutDuration ?? const Duration(hours: 1),
        _onTimeout = onTimeout;

  /// Reset the inactivity timer (call this on user activity)
  void resetTimer() {
    _lastActivityTime = DateTime.now();
    _timer?.cancel();
    _timer = Timer(_timeoutDuration, () {
      _onTimeout();
    });
  }

  /// Stop the timer (e.g., when user logs out)
  void stopTimer() {
    _timer?.cancel();
    _timer = null;
    _lastActivityTime = null;
  }

  /// Get the last activity time
  DateTime? get lastActivityTime => _lastActivityTime;

  /// Dispose resources
  void dispose() {
    stopTimer();
  }
}

/// Provider for the inactivity timer service
final inactivityTimerProvider = Provider<InactivityTimerService?>((ref) {
  // Only create timer if user is authenticated
  final authState = ref.watch(authProvider);
  
  if (!authState.isAuthenticated) {
    return null;
  }

  final service = InactivityTimerService(
    onTimeout: () {
      // Logout user when timeout occurs
      // Use read to avoid circular dependency
      ref.read(authProvider.notifier).logout();
    },
  );

  // Start/reset the timer when service is created or recreated
  // This happens automatically when auth state changes from unauthenticated to authenticated
  service.resetTimer();

  // Cleanup when provider is disposed
  ref.onDispose(() {
    service.dispose();
  });

  return service;
});
