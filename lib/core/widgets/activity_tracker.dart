import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/inactivity_timer_service.dart';

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
    // Reset timer when app comes to foreground
    if (state == AppLifecycleState.resumed) {
      _resetTimer();
    }
  }

  void _resetTimer() {
    final timer = ref.read(inactivityTimerProvider);
    timer?.resetTimer();
  }

  @override
  Widget build(BuildContext context) {
    // Listen to timer provider to ensure it's created when authenticated
    ref.watch(inactivityTimerProvider);
    
    return Listener(
      onPointerDown: (_) => _resetTimer(),
      onPointerMove: (_) => _resetTimer(),
      onPointerUp: (_) => _resetTimer(),
      child: GestureDetector(
        onTap: () => _resetTimer(),
        onPanStart: (_) => _resetTimer(),
        onPanUpdate: (_) => _resetTimer(),
        onPanEnd: (_) => _resetTimer(),
        behavior: HitTestBehavior.translucent,
        child: widget.child,
      ),
    );
  }
}
