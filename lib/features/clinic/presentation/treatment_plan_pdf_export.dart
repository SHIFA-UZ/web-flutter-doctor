import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_stub.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_web.dart'
    as clinic_pdf_dl;
import 'package:shifa_doc_app_v1/features/clinic/pdf/treatment_plan_pdf_data.dart';
import 'package:shifa_doc_app_v1/features/clinic/pdf/treatment_plan_pdf_service.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

/// Fetches plan detail, builds a PDF, and triggers a browser download.
Future<void> exportTreatmentPlanPdf(
  BuildContext context,
  WidgetRef ref, {
  required int planId,
}) async {
  final l10n = AppLocalizations.of(context)!;
  final messenger = ScaffoldMessenger.of(context);
  final rootNav = Navigator.of(context, rootNavigator: true);

  var dialogShown = false;
  try {
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      useRootNavigator: true,
      builder: (dialogContext) {
        return PopScope(
          canPop: false,
          child: AlertDialog(
            content: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const SizedBox(
                  width: 28,
                  height: 28,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Text(
                    l10n.translate('clinicTreatmentPlanExportPdfPreparing'),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
    dialogShown = true;
    await Future<void>.delayed(const Duration(milliseconds: 50));

    TreatmentPlanDetailDto? detail;
    try {
      detail = await fetchTreatmentPlanDetail(ref, planId);
    } catch (_) {
      if (!context.mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(l10n.translate('clinicTreatmentPlanExportPdfFailed')),
        ),
      );
      return;
    }
    if (!context.mounted) return;
    if (detail == null) {
      messenger.showSnackBar(
        SnackBar(
          content: Text(l10n.translate('clinicTreatmentPlanExportPdfNoDetail')),
        ),
      );
      return;
    }

    final lang = ref.read(languageProvider).locale.languageCode;
    final sel = ref.read(selectedClinicProvider);
    final clinicName = (sel != null && sel.clinicId == detail.summary.clinicId)
        ? sel.name
        : null;

    final pdfData = TreatmentPlanPdfData.fromDetail(
      detail: detail,
      generatedAt: DateTime.now(),
      languageCode: lang,
      clinicDisplayName: clinicName,
    );

    Uint8List bytes;
    try {
      bytes = await generateTreatmentPlanPdf(data: pdfData, languageCode: lang);
    } catch (_) {
      if (!context.mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(l10n.translate('clinicTreatmentPlanExportPdfFailed')),
        ),
      );
      return;
    }

    final now = DateTime.now();
    final ymd =
        '${now.year.toString().padLeft(4, '0')}-'
        '${now.month.toString().padLeft(2, '0')}-'
        '${now.day.toString().padLeft(2, '0')}';
    final fname = 'treatment_plan_${planId}_$ymd.pdf';

    try {
      await clinic_pdf_dl.downloadPdfBytes(bytes, filename: fname);
    } on UnsupportedError {
      if (!context.mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            l10n.translate('clinicTreatmentPlanExportPdfWrongPlatform'),
          ),
        ),
      );
    }
  } finally {
    if (dialogShown && rootNav.canPop()) {
      rootNav.pop();
    }
  }
}
