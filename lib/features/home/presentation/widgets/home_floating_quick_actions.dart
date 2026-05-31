import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_quick_action_navigation.dart';

/// Floating speed-dial for one-click clinical actions (prompt Section 6).
class HomeFloatingQuickActions extends StatefulWidget {
  const HomeFloatingQuickActions({super.key});

  @override
  State<HomeFloatingQuickActions> createState() =>
      _HomeFloatingQuickActionsState();
}

class _HomeFloatingQuickActionsState extends State<HomeFloatingQuickActions>
    with SingleTickerProviderStateMixin {
  bool _open = false;

  @override
  Widget build(BuildContext context) {
    return Consumer(
      builder: (context, ref, _) {
        final l10n = AppLocalizations.of(context)!;
        final brand = Theme.of(context).colorScheme.primary;

        final actions = [
          _FabAction(Icons.add_circle_outline, l10n.translate('newAppointment'),
              () => openCalendarForNewAppointment(ref)),
          _FabAction(Icons.person_add_outlined, l10n.translate('addPatient'),
              () => openCreatePatientForm(context, ref)),
          _FabAction(Icons.videocam_outlined, l10n.videoCall,
              () => openCalendarForVideoConsultation(ref)),
          _FabAction(Icons.upload_file_outlined, l10n.translate('uploadDocument'),
              () => openPatientsForDocumentUpload(ref)),
          _FabAction(Icons.medical_services_outlined,
              l10n.translate('createTreatmentPlan'),
              () => openCreateTreatmentPlanForm(context, ref)),
          _FabAction(Icons.medication_outlined, l10n.translate('issuePrescription'),
              () => openCreateRemoteCareTaskForm(context, ref)),
        ];

        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            AnimatedOpacity(
              opacity: _open ? 1 : 0,
              duration: const Duration(milliseconds: 200),
              child: IgnorePointer(
                ignoring: !_open,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    for (final action in actions) ...[
                      _MiniAction(action: action, brand: brand),
                      const SizedBox(height: 8),
                    ],
                  ],
                ),
              ),
            ),
            FloatingActionButton.extended(
              onPressed: () => setState(() => _open = !_open),
              backgroundColor: brand,
              icon: Icon(_open ? Icons.close : Icons.bolt, color: Colors.white),
              label: Text(
                l10n.translate('quickActions'),
                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _FabAction {
  const _FabAction(this.icon, this.label, this.onTap);
  final IconData icon;
  final String label;
  final VoidCallback onTap;
}

class _MiniAction extends StatelessWidget {
  const _MiniAction({required this.action, required this.brand});

  final _FabAction action;
  final Color brand;

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 4,
      borderRadius: BorderRadius.circular(24),
      color: Colors.white,
      child: InkWell(
        onTap: action.onTap,
        borderRadius: BorderRadius.circular(24),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(action.icon, size: 18, color: brand),
              const SizedBox(width: 8),
              Text(
                action.label,
                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
