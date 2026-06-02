import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/services/app_lock_service.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';

final appLockServiceProvider = Provider<AppLockService>((ref) => AppLockService());

final lockManagerProvider = StateNotifierProvider<LockManager, bool>((ref) {
  final manager = LockManager(ref);
  if (PlatformLayout.isNativeMobile) {
    WidgetsBinding.instance.addObserver(manager);
    ref.onDispose(() => WidgetsBinding.instance.removeObserver(manager));
  }
  return manager;
});

final appLockStateProvider = StateNotifierProvider<AppLockStateNotifier, bool>((ref) {
  return AppLockStateNotifier(ref.watch(appLockServiceProvider));
});

final appLockTemporaryDisableProvider =
    StateNotifierProvider<AppLockTemporaryDisableNotifier, bool>((ref) {
  return AppLockTemporaryDisableNotifier();
});

class AppLockStateNotifier extends StateNotifier<bool> {
  AppLockStateNotifier(this._service) : super(false) {
    _load();
  }

  final AppLockService _service;

  Future<void> _load() async {
    state = await _service.isLockEnabled();
  }

  Future<void> setLockEnabled(bool enabled) async {
    await _service.setLockEnabled(enabled);
    state = enabled;
  }
}

class AppLockTemporaryDisableNotifier extends StateNotifier<bool> {
  AppLockTemporaryDisableNotifier() : super(false);
  void disable() => state = true;
  void enable() => state = false;
}

class LockManager extends StateNotifier<bool> with WidgetsBindingObserver {
  LockManager(this._ref) : super(false);

  final Ref _ref;
  DateTime? _backgroundTimestamp;
  static const _threshold = Duration(seconds: 5);

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
        _backgroundTimestamp = DateTime.now();
        break;
      case AppLifecycleState.resumed:
        if (_backgroundTimestamp == null) return;
        final elapsed = DateTime.now().difference(_backgroundTimestamp!);
        _backgroundTimestamp = null;
        if (elapsed >= _threshold) {
          Future.microtask(_requireUnlockIfNeeded);
        }
        break;
      default:
        break;
    }
  }

  Future<void> _requireUnlockIfNeeded() async {
    if (state) return;
    if (!_ref.read(authProvider).isAuthenticated) return;
    if (_ref.read(appLockTemporaryDisableProvider)) return;
    final service = _ref.read(appLockServiceProvider);
    if (await service.isLockEnabled() && await service.hasPin()) {
      this.state = true;
    }
  }

  void unlock() => state = false;
}
