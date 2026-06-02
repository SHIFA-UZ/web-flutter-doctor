import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/services/app_lock_provider.dart';
import 'package:shifa_doc_app_v1/core/services/app_lock_service.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';

class AppLockScreen extends ConsumerStatefulWidget {
  const AppLockScreen({super.key, required this.onUnlock, this.onForceLogin});

  final VoidCallback onUnlock;
  final VoidCallback? onForceLogin;

  @override
  ConsumerState<AppLockScreen> createState() => _AppLockScreenState();
}

class _AppLockScreenState extends ConsumerState<AppLockScreen> {
  String _enteredPin = '';
  String? _errorMessage;
  int _failedAttempts = 0;

  Future<void> _tryBiometric() async {
    final service = ref.read(appLockServiceProvider);
    if (await service.authenticateWithBiometric()) {
      widget.onUnlock();
    }
  }

  Future<void> _verifyPin() async {
    if (!AppLockService.isValidPin(_enteredPin)) return;
    final service = ref.read(appLockServiceProvider);
    if (await service.verifyPin(_enteredPin)) {
      widget.onUnlock();
      return;
    }
    final attempts = _failedAttempts + 1;
    setState(() {
      _failedAttempts = attempts;
      _errorMessage = AppLocalizations.of(context)?.translate('incorrectPin') ?? 'Incorrect PIN';
      _enteredPin = '';
    });
    HapticFeedback.vibrate();
    if (attempts >= AppLockService.maxPinAttempts) {
      widget.onForceLogin?.call();
    }
  }

  void _onDigit(String digit) {
    if (_enteredPin.length >= AppLockService.maxPinLength) return;
    setState(() {
      _enteredPin += digit;
      _errorMessage = null;
    });
    if (_enteredPin.length >= AppLockService.minPinLength) {
      _verifyPin();
    }
  }

  void _onBackspace() {
    if (_enteredPin.isEmpty) return;
    setState(() => _enteredPin = _enteredPin.substring(0, _enteredPin.length - 1));
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _tryBiometric());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              const Spacer(),
              Image.asset('assets/branding/shifa_logo.png', width: 64, height: 64),
              const SizedBox(height: 24),
              Text(l10n.translate('enterPin') ?? 'Enter PIN', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(AppLockService.maxPinLength, (i) {
                  final filled = i < _enteredPin.length;
                  return Container(
                    width: 14,
                    height: 14,
                    margin: const EdgeInsets.symmetric(horizontal: 6),
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: filled ? AppColors.primaryTeal : Colors.grey.shade300,
                    ),
                  );
                }),
              ),
              if (_errorMessage != null) ...[
                const SizedBox(height: 12),
                Text(_errorMessage!, style: const TextStyle(color: Colors.red)),
              ],
              const Spacer(),
              _PinPad(onDigit: _onDigit, onBackspace: _onBackspace),
            ],
          ),
        ),
      ),
    );
  }
}

class _PinPad extends StatelessWidget {
  const _PinPad({required this.onDigit, required this.onBackspace});

  final ValueChanged<String> onDigit;
  final VoidCallback onBackspace;

  @override
  Widget build(BuildContext context) {
    const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '⌫'];
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        childAspectRatio: 1.4,
      ),
      itemCount: keys.length,
      itemBuilder: (context, index) {
        final key = keys[index];
        if (key.isEmpty) return const SizedBox.shrink();
        return Material(
          color: Colors.grey.shade100,
          borderRadius: BorderRadius.circular(12),
          child: InkWell(
            borderRadius: BorderRadius.circular(12),
            onTap: () => key == '⌫' ? onBackspace() : onDigit(key),
            child: Center(
              child: Text(key, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w600)),
            ),
          ),
        );
      },
    );
  }
}

class AppLockLifecycleLayer extends ConsumerStatefulWidget {
  const AppLockLifecycleLayer({super.key, required this.child});

  final Widget child;

  @override
  ConsumerState<AppLockLifecycleLayer> createState() => _AppLockLifecycleLayerState();
}

class _AppLockLifecycleLayerState extends ConsumerState<AppLockLifecycleLayer> {
  bool _coldStartLocked = false;
  bool _checkedColdStart = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _checkColdStart());
  }

  Future<void> _checkColdStart() async {
    if (_checkedColdStart || !mounted) return;
    _checkedColdStart = true;
    if (!ref.read(authProvider).isAuthenticated) return;
    final service = ref.read(appLockServiceProvider);
    if (await service.isLockEnabled() && await service.hasPin()) {
      setState(() => _coldStartLocked = true);
    }
  }

  void _onUnlock() {
    ref.read(lockManagerProvider.notifier).unlock();
    setState(() => _coldStartLocked = false);
  }

  void _onForceLogin() {
    ref.read(authProvider.notifier).logout();
    ref.read(lockManagerProvider.notifier).unlock();
    setState(() => _coldStartLocked = false);
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(lockManagerProvider);

    ref.listen(authProvider, (prev, next) {
      if (next.isAuthenticated && !_checkedColdStart) {
        WidgetsBinding.instance.addPostFrameCallback((_) => _checkColdStart());
      }
    });

    final resumeLock = ref.watch(lockManagerProvider);
    if (!_coldStartLocked && !resumeLock) return widget.child;

    return AppLockScreen(
      onUnlock: _onUnlock,
      onForceLogin: _onForceLogin,
    );
  }
}
