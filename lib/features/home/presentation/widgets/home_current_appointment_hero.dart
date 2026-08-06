import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/person_avatar.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_appointment_card.dart';

/// Prominent hero card for the appointment happening now (or next up).
class HomeCurrentAppointmentHero extends StatelessWidget {
  const HomeCurrentAppointmentHero({
    super.key,
    required this.appointment,
    required this.patient,
    required this.isNow,
    required this.startButtonEnabled,
    required this.videoTooltip,
    required this.durationMinutes,
    required this.onStart,
    this.onOpenChart,
    this.onOpenDocuments,
    this.onMessagePatient,
    this.onBriefing,
    this.onVisitBriefing,
  });

  final Appointment appointment;
  final Patient? patient;
  final bool isNow;
  final bool startButtonEnabled;
  final String videoTooltip;
  final int durationMinutes;
  final VoidCallback onStart;
  final VoidCallback? onOpenChart;
  final VoidCallback? onOpenDocuments;
  final VoidCallback? onMessagePatient;
  final VoidCallback? onBriefing;
  final VoidCallback? onVisitBriefing;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final alertChips = buildPatientAlertChips(patient, l10n);
    final age = _patientAge(patient);

    return AnimatedContainer(
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOutCubic,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            brand.withValues(alpha: 0.12),
            Colors.white,
            brand.withValues(alpha: 0.04),
          ],
        ),
        borderRadius: BorderRadius.circular(AppDesignSystem.cardRadius),
        border: Border.all(color: brand.withValues(alpha: 0.35), width: 2),
        boxShadow: AppDesignSystem.elevatedShadow,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                l10n.translate('currentAppointment'),
                style: AppDesignSystem.h2(context).copyWith(color: brand),
              ),
              const Spacer(),
              _HeroBadge(
                label: isNow ? l10n.translate('now') : l10n.translate('waiting'),
                color: brand,
              ),
            ],
          ),
          const SizedBox(height: 20),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              PersonAvatar(
                name: appointment.patientName,
                photoUrl: appointment.photoUrl,
                radius: 36,
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      appointment.patientName,
                      style: AppDesignSystem.display(context).copyWith(fontSize: 24),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      age != null
                          ? '${l10n.translate('age')} $age'
                          : appointment.patientName,
                      style: AppDesignSystem.body2(context),
                    ),
                    if ((appointment.reason ?? '').trim().isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Text(
                        l10n.translate('visitReason'),
                        style: AppDesignSystem.caption(context),
                      ),
                      Text(
                        appointment.reason!.trim(),
                        style: AppDesignSystem.body1(context),
                      ),
                    ],
                    if (alertChips.isNotEmpty) ...[
                      const SizedBox(height: 10),
                      Wrap(spacing: 6, runSpacing: 6, children: alertChips),
                    ],
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    appointment.start.format(context),
                    style: TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                      color: brand,
                      letterSpacing: -1,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    l10n.translate('durationMin').replaceAll(
                          '{minutes}',
                          durationMinutes.toString(),
                        ),
                    style: AppDesignSystem.body2(context),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 16,
            runSpacing: 8,
            children: [
              _MetaChip(
                icon: appointment.isVideo
                    ? Icons.videocam_outlined
                    : Icons.location_on_outlined,
                label: appointment.isVideo ? l10n.videoCall : l10n.inClinic,
              ),
              if (appointment.location.isNotEmpty && !appointment.isVideo)
                _MetaChip(icon: Icons.meeting_room_outlined, label: appointment.location),
            ],
          ),
          const SizedBox(height: 20),
          Tooltip(
            message: videoTooltip,
            child: ShifaPrimaryButton(
              label: l10n.translate('startAppointment'),
              onPressed: startButtonEnabled ? onStart : null,
              width: ButtonWidth.fill,
            ),
          ),
          if (appointment.hasVisitBriefing) ...[
            const SizedBox(height: 10),
            ShifaSecondaryButton(
              label: l10n.translate('viewVisitBriefing'),
              onPressed: onVisitBriefing,
              width: ButtonWidth.fill,
            ),
          ],
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: ShifaSecondaryButton(
                  label: l10n.translate('openChart'),
                  onPressed: onOpenChart,
                  width: ButtonWidth.fill,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ShifaSecondaryButton(
                  label: l10n.translate('openDocuments'),
                  onPressed: onOpenDocuments,
                  width: ButtonWidth.fill,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ShifaSecondaryButton(
                  label: l10n.translate('messagePatient'),
                  onPressed: onMessagePatient,
                  width: ButtonWidth.fill,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  int? _patientAge(Patient? patient) {
    final birth = patient?.general.birthDate;
    if (birth == null) return null;
    return DateTime.now().year - birth.year;
  }
}

class _HeroBadge extends StatelessWidget {
  const _HeroBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w700,
          fontSize: 12,
        ),
      ),
    );
  }
}

class _MetaChip extends StatelessWidget {
  const _MetaChip({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: AppDesignSystem.backgroundSecondary,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppDesignSystem.border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: AppColors.primaryTeal),
          const SizedBox(width: 6),
          Text(label, style: AppDesignSystem.body2(context)),
        ],
      ),
    );
  }
}

/// Compact card for horizontal upcoming timeline strip.
class HomeUpcomingTimelineCard extends StatelessWidget {
  const HomeUpcomingTimelineCard({
    super.key,
    required this.appointment,
    required this.patient,
    required this.selected,
    required this.startButtonEnabled,
    required this.videoTooltip,
    required this.onTap,
    required this.onStart,
    this.onVisitBriefing,
  });

  final Appointment appointment;
  final Patient? patient;
  final bool selected;
  final bool startButtonEnabled;
  final String videoTooltip;
  final VoidCallback onTap;
  final VoidCallback onStart;
  final VoidCallback? onVisitBriefing;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 280,
      child: HomeAppointmentCard(
        appointment: appointment,
        selected: selected,
        isCurrent: false,
        isNext: false,
        startButtonEnabled: startButtonEnabled,
        videoTooltip: videoTooltip,
        patient: patient,
        onTap: onTap,
        onStart: onStart,
        onVisitBriefing: onVisitBriefing,
        compact: true,
      ),
    );
  }
}
