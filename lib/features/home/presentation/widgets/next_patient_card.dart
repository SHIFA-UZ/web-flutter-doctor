import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/person_avatar.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';

/// Hero card focused on the next (or current) patient in the workflow.
class NextPatientCard extends StatelessWidget {
  const NextPatientCard({
    super.key,
    required this.appointment,
    this.patient,
    required this.isNow,
    required this.startEnabled,
    required this.onStart,
    this.onOpenChart,
    this.dateLabel,
  });

  final Appointment appointment;
  final Patient? patient;
  final bool isNow;
  final bool startEnabled;
  final VoidCallback onStart;
  final VoidCallback? onOpenChart;
  /// When set, the visit is not today (e.g. "Tue, 18 Aug").
  final String? dateLabel;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final age = _patientAge(patient);
    final statusLabel = _statusLabel(l10n, appointment, isNow);
    final visitType = appointment.isVideo
        ? l10n.translate('onlineVisit')
        : l10n.inClinic;
    final clinicLabel = appointment.isVideo
        ? l10n.videoCall
        : (appointment.location.isNotEmpty
            ? appointment.location
            : l10n.inClinic);

    return Semantics(
      container: true,
      label: '${l10n.translate('nextPatient')}: ${appointment.patientName}',
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppDesignSystem.background,
          borderRadius: BorderRadius.circular(AppDesignSystem.cardRadius),
          border: Border.all(color: brand.withValues(alpha: 0.35), width: 1.5),
          boxShadow: AppDesignSystem.elevatedShadow,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Text(
                  l10n.translate('nextPatient'),
                  style: AppDesignSystem.h2(context).copyWith(color: brand),
                ),
                const Spacer(),
                _StatusBadge(
                  label: statusLabel,
                  color: brand,
                  icon: isNow ? Icons.play_circle_outline : Icons.schedule,
                ),
              ],
            ),
            const SizedBox(height: 14),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                PersonAvatar(
                  name: appointment.patientName,
                  photoUrl: appointment.photoUrl,
                  radius: 28,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        appointment.patientName,
                        style: AppDesignSystem.h2(context).copyWith(
                          fontWeight: FontWeight.w600,
                          fontSize: 17,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        [
                          if (dateLabel != null && dateLabel!.isNotEmpty)
                            dateLabel!,
                          appointment.start.format(context),
                          if (age != null)
                            '${l10n.translate('age')} $age',
                        ].join(' · '),
                        style: AppDesignSystem.body2(context),
                      ),
                      const SizedBox(height: 6),
                      Wrap(
                        spacing: 8,
                        runSpacing: 6,
                        children: [
                          _MetaPill(
                            icon: appointment.isVideo
                                ? Icons.videocam_outlined
                                : Icons.local_hospital_outlined,
                            label: visitType,
                          ),
                          _MetaPill(
                            icon: Icons.place_outlined,
                            label: clinicLabel,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ShifaPrimaryButton(
              label: l10n.translate('startAppointment'),
              onPressed: startEnabled ? onStart : null,
              width: ButtonWidth.fill,
            ),
            if (onOpenChart != null) ...[
              const SizedBox(height: 8),
              ShifaSecondaryButton(
                label: l10n.translate('openPatientCard'),
                onPressed: onOpenChart,
                width: ButtonWidth.fill,
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _statusLabel(
    AppLocalizations l10n,
    Appointment appointment,
    bool isNow,
  ) {
    if (isNow || appointment.isInProgress) {
      return l10n.translate('now');
    }
    if (appointment.status == AppointmentStatus.confirmed) {
      return l10n.translate('confirmed');
    }
    if (appointment.status == AppointmentStatus.requested) {
      return l10n.translate('waiting');
    }
    return l10n.translate('waiting');
  }

  int? _patientAge(Patient? patient) {
    final birth = patient?.general.birthDate;
    if (birth == null) return null;
    final now = DateTime.now();
    var age = now.year - birth.year;
    if (now.month < birth.month ||
        (now.month == birth.month && now.day < birth.day)) {
      age--;
    }
    return age < 0 ? null : age;
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({
    required this.label,
    required this.color,
    required this.icon,
  });

  final String label;
  final Color color;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: label,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: color.withValues(alpha: 0.35)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 14, color: color),
            const SizedBox(width: 4),
            Text(
              label,
              style: TextStyle(
                color: color,
                fontWeight: FontWeight.w600,
                fontSize: 12,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MetaPill extends StatelessWidget {
  const _MetaPill({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: AppDesignSystem.backgroundSecondary,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppDesignSystem.border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: AppDesignSystem.textSecondary),
          const SizedBox(width: 4),
          Text(label, style: AppDesignSystem.caption(context)),
        ],
      ),
    );
  }
}
