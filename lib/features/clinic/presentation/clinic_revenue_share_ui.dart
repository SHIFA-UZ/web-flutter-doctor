import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';

String formatRevenueShareLabel(AppLocalizations l10n, int? doctorPercent) {
  if (doctorPercent == null) {
    return l10n.translate('clinicDoctorRevenueShareNotSet');
  }
  final clinicPercent = 100 - doctorPercent;
  return l10n
      .translate('clinicDoctorRevenueShareSummary')
      .replaceAll('{{doctor}}', '$doctorPercent')
      .replaceAll('{{clinic}}', '$clinicPercent');
}

Future<void> showDoctorRevenueShareDialog({
  required BuildContext context,
  required WidgetRef ref,
  required int clinicId,
  required ClinicMember member,
}) async {
  final l10n = AppLocalizations.of(context)!;
  final initial = member.doctorRevenueSharePercent;
  final controller = TextEditingController(
    text: initial?.toString() ?? '',
  );
  var previewDoctor = initial ?? member.effectiveRevenueSharePercent ?? 0;
  var saving = false;

  await showDialog<void>(
    context: context,
    builder: (ctx) {
      return StatefulBuilder(
        builder: (ctx, setLocal) {
          void updatePreview(String raw) {
            final parsed = int.tryParse(raw.trim());
            setLocal(() {
              previewDoctor = parsed?.clamp(0, 100) ?? previewDoctor;
            });
          }

          final clinicPart = 100 - previewDoctor;
          return AlertDialog(
            title: Text(l10n.translate('clinicDoctorRevenueShareDialogTitle')),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(member.displayName),
                const SizedBox(height: 12),
                TextField(
                  controller: controller,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(
                    labelText: l10n.translate('clinicDoctorRevenueShareDoctorLabel'),
                    suffixText: '%',
                    helperText: l10n.translate('clinicFinanceDefaultRevenueShareHint'),
                  ),
                  onChanged: updatePreview,
                ),
                const SizedBox(height: 8),
                Slider(
                  value: previewDoctor.toDouble(),
                  min: 0,
                  max: 100,
                  divisions: 100,
                  label: '$previewDoctor%',
                  activeColor: AppColors.primaryTeal,
                  onChanged: (v) {
                    final i = v.round();
                    controller.text = '$i';
                    setLocal(() => previewDoctor = i);
                  },
                ),
                Text(
                  l10n
                      .translate('clinicDoctorRevenueSharePreview')
                      .replaceAll('{{doctor}}', '$previewDoctor')
                      .replaceAll('{{clinic}}', '$clinicPart'),
                  style: TextStyle(color: Colors.grey.shade700),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: saving
                    ? null
                    : () async {
                        Navigator.pop(ctx);
                        try {
                          await updateMemberRevenueShare(
                            ref,
                            clinicId,
                            member.doctorProfileId,
                            null,
                          );
                        } catch (e) {
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('$e')),
                          );
                        }
                      },
                child: Text(l10n.translate('clinicDoctorRevenueShareClear')),
              ),
              TextButton(
                onPressed: saving ? null : () => Navigator.pop(ctx),
                child: Text(l10n.cancel),
              ),
              FilledButton(
                onPressed: saving
                    ? null
                    : () async {
                        final parsed = int.tryParse(controller.text.trim());
                        if (parsed == null || parsed < 0 || parsed > 100) {
                          ScaffoldMessenger.of(ctx).showSnackBar(
                            SnackBar(
                              content: Text(
                                l10n.translate('clinicDoctorRevenueShareInvalid'),
                              ),
                            ),
                          );
                          return;
                        }
                        setLocal(() => saving = true);
                        try {
                          await updateMemberRevenueShare(
                            ref,
                            clinicId,
                            member.doctorProfileId,
                            parsed,
                          );
                          if (ctx.mounted) Navigator.pop(ctx);
                        } catch (e) {
                          setLocal(() => saving = false);
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('$e')),
                          );
                        }
                      },
                child: saving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(l10n.translate('clinicDoctorRevenueShareSave')),
              ),
            ],
          );
        },
      );
    },
  );
  controller.dispose();
}
