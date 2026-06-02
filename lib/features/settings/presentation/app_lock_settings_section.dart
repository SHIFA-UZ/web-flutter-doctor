import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/services/app_lock_provider.dart';
import 'package:shifa_doc_app_v1/core/services/app_lock_service.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';

class AppLockSettingsSection extends ConsumerStatefulWidget {
  const AppLockSettingsSection({super.key});

  @override
  ConsumerState<AppLockSettingsSection> createState() => _AppLockSettingsSectionState();
}

class _AppLockSettingsSectionState extends ConsumerState<AppLockSettingsSection> {
  bool _biometricAvailable = false;

  @override
  void initState() {
    super.initState();
    if (PlatformLayout.isNativeMobile) {
      ref.read(appLockServiceProvider).isBiometricAvailable().then((v) {
        if (mounted) setState(() => _biometricAvailable = v);
      });
    }
  }

  Future<void> _enableLock() async {
    final service = ref.read(appLockServiceProvider);
    final pin = await _promptForPin(context, confirm: true);
    if (pin == null) return;
    await service.setPin(pin);
    await ref.read(appLockStateProvider.notifier).setLockEnabled(true);
  }

  Future<void> _disableLock() async {
    final service = ref.read(appLockServiceProvider);
    final pin = await _promptForPin(context, confirm: false);
    if (pin == null) return;
    if (!await service.verifyPin(pin)) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.translate('incorrectPin') ?? 'Incorrect PIN')),
        );
      }
      return;
    }
    await service.clearPin();
    await service.setBiometricEnabled(false);
    await ref.read(appLockStateProvider.notifier).setLockEnabled(false);
  }

  Future<String?> _promptForPin(BuildContext context, {required bool confirm}) async {
    final controller = TextEditingController();
    final confirmController = TextEditingController();
    final l10n = AppLocalizations.of(context)!;
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.translate('setPin') ?? 'Set PIN'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: controller,
              keyboardType: TextInputType.number,
              obscureText: true,
              maxLength: AppLockService.maxPinLength,
              decoration: InputDecoration(labelText: l10n.translate('pin') ?? 'PIN'),
            ),
            if (confirm)
              TextField(
                controller: confirmController,
                keyboardType: TextInputType.number,
                obscureText: true,
                maxLength: AppLockService.maxPinLength,
                decoration: InputDecoration(labelText: l10n.translate('confirmPin') ?? 'Confirm PIN'),
              ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: Text(l10n.cancel)),
          FilledButton(
            onPressed: () {
              final pin = controller.text.trim();
              if (!AppLockService.isValidPin(pin)) return;
              if (confirm && pin != confirmController.text.trim()) return;
              Navigator.pop(ctx, pin);
            },
            child: Text(l10n.save),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!PlatformLayout.isNativeMobile) return const SizedBox.shrink();

    final l10n = AppLocalizations.of(context)!;
    final lockEnabled = ref.watch(appLockStateProvider);
    final service = ref.watch(appLockServiceProvider);

    return Card(
      child: Column(
        children: [
          SwitchListTile(
            title: Text(l10n.translate('appLock') ?? 'App lock'),
            subtitle: Text(l10n.translate('appLockSubtitle') ?? 'Require PIN when reopening the app'),
            value: lockEnabled,
            activeColor: AppColors.primaryTeal,
            onChanged: (v) async {
              if (v) {
                await _enableLock();
              } else {
                await _disableLock();
              }
            },
          ),
          if (lockEnabled && _biometricAvailable)
            FutureBuilder<bool>(
              future: service.isBiometricEnabled(),
              builder: (context, snapshot) {
                final bioEnabled = snapshot.data ?? false;
                return SwitchListTile(
                  title: Text(l10n.translate('biometricUnlock') ?? 'Biometric unlock'),
                  value: bioEnabled,
                  activeColor: AppColors.primaryTeal,
                  onChanged: (v) => service.setBiometricEnabled(v),
                );
              },
            ),
        ],
      ),
    );
  }
}
