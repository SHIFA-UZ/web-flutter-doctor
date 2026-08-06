import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_quick_action_navigation.dart';

class HomeQuickActions extends ConsumerWidget {
  const HomeQuickActions({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;

    final actions = [
      _QuickAction(
        icon: Icons.add_circle_outline,
        label: l10n.translate('newAppointment'),
        onTap: () => openCalendarForNewAppointment(ref),
      ),
      _QuickAction(
        icon: Icons.person_add_outlined,
        label: l10n.translate('addPatient'),
        onTap: () => openCreatePatientForm(context, ref),
      ),
      _QuickAction(
        icon: Icons.videocam_outlined,
        label: l10n.videoCall,
        onTap: () => openCalendarForVideoConsultation(ref),
      ),
      _QuickAction(
        icon: Icons.upload_file_outlined,
        label: l10n.translate('uploadDocument'),
        onTap: () => openPatientsForDocumentUpload(ref),
      ),
      _QuickAction(
        icon: Icons.medical_services_outlined,
        label: l10n.translate('createTreatmentPlan'),
        onTap: () => openCreateTreatmentPlanForm(context, ref),
      ),
      _QuickAction(
        icon: Icons.medication_outlined,
        label: l10n.translate('issuePrescription'),
        onTap: () => openCreateRemoteCareTaskForm(context, ref),
      ),
    ];

    return Wrap(
      spacing: 10,
      runSpacing: 10,
      children: actions
          .map(
            (a) => ActionChip(
              avatar: Icon(a.icon, size: 18, color: brand),
              label: Text(a.label),
              onPressed: a.onTap,
              backgroundColor: Colors.white,
              side: BorderSide(color: AppDesignSystem.border),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(24),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            ),
          )
          .toList(),
    );
  }
}

class _QuickAction {
  const _QuickAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
}
