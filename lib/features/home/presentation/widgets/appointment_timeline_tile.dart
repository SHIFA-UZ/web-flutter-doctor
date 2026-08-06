import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/person_avatar.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';

/// Scannable timeline row: time | accent | avatar + name + type | status.
class AppointmentTimelineTile extends StatelessWidget {
  const AppointmentTimelineTile({
    super.key,
    required this.appointment,
    required this.selected,
    required this.onTap,
    this.isLast = false,
  });

  final Appointment appointment;
  final bool selected;
  final VoidCallback onTap;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final visitLabel = appointment.isVideo
        ? l10n.translate('onlineVisit')
        : (appointment.location.isNotEmpty
            ? appointment.location
            : l10n.inClinic);
    final status = _statusLabel(l10n, appointment);
    final statusColor = _statusColor(appointment, brand);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppDesignSystem.cardRadiusSm),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SizedBox(
                  width: 48,
                  child: Align(
                    alignment: Alignment.topCenter,
                    child: Padding(
                      padding: const EdgeInsets.only(top: 10),
                      child: Text(
                        appointment.start.format(context),
                        style: AppDesignSystem.body2(context).copyWith(
                          fontWeight: FontWeight.w600,
                          color: AppDesignSystem.textPrimary,
                        ),
                      ),
                    ),
                  ),
                ),
                SizedBox(
                  width: 16,
                  child: Column(
                    children: [
                      Container(
                        width: 10,
                        height: 10,
                        margin: const EdgeInsets.only(top: 12),
                        decoration: BoxDecoration(
                          color: selected ? brand : brand.withValues(alpha: 0.45),
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 2),
                          boxShadow: [
                            BoxShadow(
                              color: brand.withValues(alpha: 0.25),
                              blurRadius: 4,
                            ),
                          ],
                        ),
                      ),
                      if (!isLast)
                        Expanded(
                          child: Container(
                            width: 2,
                            margin: const EdgeInsets.symmetric(vertical: 2),
                            color: brand.withValues(alpha: 0.2),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Container(
                    constraints: const BoxConstraints(minHeight: 56),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 10,
                    ),
                    decoration: BoxDecoration(
                      color: selected
                          ? brand.withValues(alpha: 0.06)
                          : AppDesignSystem.background,
                      borderRadius:
                          BorderRadius.circular(AppDesignSystem.cardRadiusSm),
                      border: Border.all(
                        color: selected
                            ? brand.withValues(alpha: 0.35)
                            : AppDesignSystem.border,
                      ),
                    ),
                    child: Row(
                      children: [
                        PersonAvatar(
                          name: appointment.patientName,
                          photoUrl: appointment.photoUrl,
                          radius: 18,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text(
                                appointment.patientName,
                                style: AppDesignSystem.body1(context).copyWith(
                                  fontWeight: FontWeight.w600,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 2),
                              Row(
                                children: [
                                  Icon(
                                    appointment.isVideo
                                        ? Icons.videocam_outlined
                                        : Icons.place_outlined,
                                    size: 13,
                                    color: AppDesignSystem.textSecondary,
                                  ),
                                  const SizedBox(width: 4),
                                  Expanded(
                                    child: Text(
                                      visitLabel,
                                      style: AppDesignSystem.caption(context),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Semantics(
                          label: status,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: statusColor.withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  _statusIcon(appointment),
                                  size: 12,
                                  color: statusColor,
                                ),
                                const SizedBox(width: 4),
                Text(
                  status,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: statusColor,
                  ),
                ),
              ],
            ),
          ),
        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String _statusLabel(AppLocalizations l10n, Appointment appointment) {
    if (appointment.isCompleted) return l10n.complete;
    if (appointment.isInProgress) return l10n.translate('now');
    if (appointment.status == AppointmentStatus.confirmed) {
      return l10n.translate('confirmed');
    }
    if (appointment.status == AppointmentStatus.cancelled) {
      return l10n.translate('appointmentStatusCancelled');
    }
    return l10n.translate('waiting');
  }

  Color _statusColor(Appointment appointment, Color brand) {
    if (appointment.isCompleted) return AppDesignSystem.success;
    if (appointment.isInProgress) return brand;
    if (appointment.status == AppointmentStatus.confirmed) {
      return AppDesignSystem.warning;
    }
    if (appointment.status == AppointmentStatus.cancelled) {
      return const Color(0xFFDC2F2F);
    }
    return AppDesignSystem.info;
  }

  IconData _statusIcon(Appointment appointment) {
    if (appointment.isCompleted) return Icons.check_circle_outline;
    if (appointment.isInProgress) return Icons.play_circle_outline;
    if (appointment.status == AppointmentStatus.confirmed) {
      return Icons.verified_outlined;
    }
    if (appointment.status == AppointmentStatus.cancelled) {
      return Icons.cancel_outlined;
    }
    return Icons.schedule;
  }
}
