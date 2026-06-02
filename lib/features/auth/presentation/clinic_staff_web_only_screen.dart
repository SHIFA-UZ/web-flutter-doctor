import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/auth/doctor_jwt_role_provider.dart';

/// Blocks clinic staff on native mobile — v1 is doctors-only on phone.
class ClinicStaffWebOnlyScreen extends ConsumerWidget {
  const ClinicStaffWebOnlyScreen({super.key});

  static bool shouldShow(WidgetRef ref) {
    return PlatformLayout.isNativeMobile &&
        ref.watch(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: AppColors.scaffoldBackground,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Icon(Icons.computer_outlined, size: 72, color: AppColors.primaryTeal),
              const SizedBox(height: 24),
              Text(
                l10n.translate('clinicStaffMobileTitle') ?? 'Clinic staff portal',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              Text(
                l10n.translate('clinicStaffMobileBody') ??
                    'The clinic receptionist portal is available on the web. '
                        'Please sign in at your clinic workspace in a browser.',
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: Colors.grey.shade700,
                    ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 32),
              FilledButton(
                onPressed: () => ref.read(authProvider.notifier).logout(),
                style: FilledButton.styleFrom(
                  backgroundColor: AppColors.primaryTeal,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                child: Text(l10n.signOut),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
