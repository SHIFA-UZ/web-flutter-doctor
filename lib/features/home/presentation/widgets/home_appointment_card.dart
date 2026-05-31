import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/person_avatar.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

/// Risk / alert chips visible on appointment cards without opening the chart.
class PatientAlertChip extends StatelessWidget {
  const PatientAlertChip({
    super.key,
    required this.label,
    this.variant = AlertChipVariant.warning,
  });

  final String label;
  final AlertChipVariant variant;

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = switch (variant) {
      AlertChipVariant.danger => (
          AppColors.destructiveRed.withValues(alpha: 0.1),
          AppColors.destructiveRed,
        ),
      AlertChipVariant.warning => (
          const Color(0xFFD97706).withValues(alpha: 0.12),
          const Color(0xFFD97706),
        ),
      AlertChipVariant.info => (
          AppDesignSystem.info.withValues(alpha: 0.1),
          AppDesignSystem.info,
        ),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w600,
          color: fg,
        ),
      ),
    );
  }
}

enum AlertChipVariant { danger, warning, info }

List<Widget> buildPatientAlertChips(Patient? patient, AppLocalizations l10n) {
  if (patient == null) return const [];
  final chips = <Widget>[];
  final chronic = patient.general.chronicDisease;
  if (chronic != null &&
      chronic.isNotEmpty &&
      chronic.toLowerCase() != 'none') {
    chips.add(PatientAlertChip(
      label: l10n.translateChronicDisease(chronic),
      variant: AlertChipVariant.danger,
    ));
  }
  return chips;
}

/// Compact timeline card for upcoming appointments.
class HomeAppointmentCard extends StatelessWidget {
  const HomeAppointmentCard({
    super.key,
    required this.appointment,
    required this.selected,
    required this.isCurrent,
    required this.isNext,
    required this.startButtonEnabled,
    required this.videoTooltip,
    required this.patient,
    required this.onTap,
    required this.onStart,
    this.onBriefing,
    this.compact = false,
  });

  final Appointment appointment;
  final bool selected;
  final bool isCurrent;
  final bool isNext;
  final bool startButtonEnabled;
  final String videoTooltip;
  final Patient? patient;
  final VoidCallback onTap;
  final VoidCallback onStart;
  final VoidCallback? onBriefing;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final isCompleted = appointment.isCompleted;
    final alertChips = buildPatientAlertChips(patient, l10n);

    return AnimatedContainer(
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOut,
      decoration: BoxDecoration(
        color: isCurrent
            ? brand.withValues(alpha: 0.06)
            : selected
                ? AppDesignSystem.backgroundSecondary
                : Colors.white,
        borderRadius: BorderRadius.circular(AppDesignSystem.cardRadiusSm),
        border: Border.all(
          color: isCurrent
              ? brand
              : isNext
                  ? brand.withValues(alpha: 0.45)
                  : AppDesignSystem.border,
          width: isCurrent ? 2 : 1,
        ),
        boxShadow: isCurrent ? AppDesignSystem.elevatedShadow : null,
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(AppDesignSystem.cardRadiusSm),
          hoverColor: brand.withValues(alpha: 0.04),
          child: Padding(
            padding: EdgeInsets.all(compact ? 12 : 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    PersonAvatar(
                      name: appointment.patientName,
                      photoUrl: appointment.photoUrl,
                      radius: compact ? 20 : 24,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Flexible(
                                child: Text(
                                  appointment.patientName,
                                  style: TextStyle(
                                    fontSize: compact ? 14 : 16,
                                    fontWeight: FontWeight.w600,
                                    color: AppDesignSystem.textPrimary,
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              if (isCurrent) ...[
                                const SizedBox(width: 8),
                                _StatusBadge(
                                  label: l10n.translate('now'),
                                  color: brand,
                                ),
                              ] else if (isNext && !isCompleted) ...[
                                const SizedBox(width: 8),
                                _StatusBadge(
                                  label: l10n.translate('waiting'),
                                  color: brand.withValues(alpha: 0.85),
                                ),
                              ] else if (isCompleted) ...[
                                const SizedBox(width: 8),
                                _StatusBadge(
                                  label: l10n.complete,
                                  color: AppDesignSystem.textTertiary,
                                ),
                              ],
                            ],
                          ),
                          const SizedBox(height: 2),
                          Text(
                            _subtitleLine(patient, l10n),
                            style: AppDesignSystem.caption(context),
                            maxLines: compact ? 1 : 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                    _TimeBadge(
                      time: appointment.start.format(context),
                      highlighted: isCurrent || isNext,
                    ),
                  ],
                ),
                if (alertChips.isNotEmpty) ...[
                  const SizedBox(height: 10),
                  Wrap(spacing: 6, runSpacing: 6, children: alertChips),
                ],
                const SizedBox(height: 12),
                Row(
                  children: [
                    Icon(
                      appointment.isVideo
                          ? Icons.videocam_outlined
                          : Icons.location_on_outlined,
                      size: 14,
                      color: AppDesignSystem.textSecondary,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      appointment.isVideo ? l10n.videoCall : l10n.inClinic,
                      style: AppDesignSystem.body2(context),
                    ),
                    if (appointment.location.isNotEmpty &&
                        !appointment.isVideo) ...[
                      const SizedBox(width: 8),
                      Text('·', style: AppDesignSystem.body2(context)),
                      const SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          appointment.location,
                          style: AppDesignSystem.body2(context),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ],
                ),
                if (!compact) ...[
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      if (isCompleted)
                        _CompletedButton(label: l10n.complete)
                      else
                        Expanded(
                          child: Tooltip(
                            message: videoTooltip,
                            child: ShifaPrimaryButton(
                              label: isCurrent
                                  ? l10n.translate('startAppointment')
                                  : l10n.start,
                              onPressed:
                                  startButtonEnabled ? onStart : null,
                              width: ButtonWidth.fill,
                            ),
                          ),
                        ),
                      if (onBriefing != null && appointment.patientId != null) ...[
                        const SizedBox(width: 8),
                        IconButton.outlined(
                          onPressed: onBriefing,
                          icon: Icon(Icons.summarize_outlined,
                              size: 18, color: brand),
                          tooltip: l10n.generateBriefing,
                          style: IconButton.styleFrom(
                            minimumSize: const Size(40, 40),
                            side: BorderSide(color: AppDesignSystem.border),
                          ),
                        ),
                      ],
                    ],
                  ),
                ] else if (!isCompleted) ...[
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    child: ShifaActionButton(
                      label: l10n.start,
                      onPressed: startButtonEnabled ? onStart : null,
                      actionStyle: ActionButtonStyle.primary,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  String _subtitleLine(Patient? patient, AppLocalizations l10n) {
    final reason = (appointment.reason ?? '').trim();
    if (reason.isNotEmpty) return reason;
    final age = _patientAge(patient);
    if (age != null) return '${l10n.translate('age')} $age';
    return appointment.isVideo ? l10n.videoCall : l10n.inClinic;
  }

  int? _patientAge(Patient? patient) {
    final birth = patient?.general.birthDate;
    if (birth == null) return null;
    return DateTime.now().year - birth.year;
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w700,
          color: color,
        ),
      ),
    );
  }
}

class _TimeBadge extends StatelessWidget {
  const _TimeBadge({required this.time, this.highlighted = false});

  final String time;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: highlighted
            ? brand.withValues(alpha: 0.12)
            : AppDesignSystem.backgroundTertiary,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        time,
        style: TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w700,
          color: highlighted ? brand : AppDesignSystem.textSecondary,
        ),
      ),
    );
  }
}

class _CompletedButton extends StatelessWidget {
  const _CompletedButton({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: AppDesignSystem.backgroundTertiary,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.check_circle_outline,
                size: 16, color: AppDesignSystem.textTertiary),
            const SizedBox(width: 6),
            Text(
              label,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: AppDesignSystem.textTertiary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
