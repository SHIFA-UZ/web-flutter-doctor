import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/inactivity_timer_service.dart';
import '../../state/auth/auth_controller.dart';
import '../util/jwt_utils.dart';

/// Widget that tracks user activity and resets the inactivity timer
/// Wrap your app content with this widget to enable activity tracking
class ActivityTracker extends ConsumerStatefulWidget {
  final Widget child;

  const ActivityTracker({
    Key? key,
    required this.child,
  }) : super(key: key);

  @override
  ConsumerState<ActivityTracker> createState() => _ActivityTrackerState();
}

class _ActivityTrackerState extends ConsumerState<ActivityTracker>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.resumed) {
      _enforceSessionOnResume();
    }
  }

  void _resetTimer() {
    final jwt = ref.read(authTokenProvider);
    if (jwt != null && isJwtExpired(jwt)) {
      ref.read(authProvider.notifier).logout();
      return;
    }
    final timer = ref.read(inactivityTimerProvider);
    timer?.resetTimer();
  }

  void _enforceSessionOnResume() {
    final timer = ref.read(inactivityTimerProvider);
    if (timer == null) return;
    final jwt = ref.read(authTokenProvider);
    unawaited(() async {
      await timer.enforceTimeoutIfNeeded(jwt: jwt);
      if (!mounted) return;
      if (!ref.read(authProvider).isAuthenticated) return;
      timer.resetTimer();
    }());
  }

  @override
  Widget build(BuildContext context) {
    // Listen to timer provider to ensure it's created when authenticated
    ref.watch(inactivityTimerProvider);
    
    // Only pointer down/up — not move — to avoid timer/disk churn during mouse hover or scroll.
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (_) => _resetTimer(),
      onPointerUp: (_) => _resetTimer(),
      child: widget.child,
    );
  }
}
