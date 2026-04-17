import 'package:flutter/material.dart';
import '../localization/app_localizations.dart';

/// Show a warning dialog when dealing with a patient who has a chronic disease
Future<void> showChronicDiseaseWarning(
  BuildContext context,
  String patientName,
  String chronicDisease,
) async {
  final l10n = AppLocalizations.of(context)!;
  await showDialog(
    context: context,
    barrierDismissible: false,
    builder: (ctx) => AlertDialog(
      icon: const Icon(
        Icons.warning_amber_rounded,
        color: Colors.red,
        size: 48,
      ),
      title: Text(
        l10n.translate('patientWithChronicDisease') ?? 'Patient with Chronic Disease',
        style: const TextStyle(
          color: Colors.red,
          fontWeight: FontWeight.bold,
        ),
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.translate('dealingWithChronicDiseasePatient') ?? 'You are dealing with a patient who has a chronic/severe disease:',
            style: TextStyle(fontSize: 14, color: Colors.grey.shade700),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.red.shade50,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.red.shade200),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${l10n.patient}: $patientName',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '${l10n.chronicDisease}: ${l10n.translateChronicDisease(chronicDisease)}',
                  style: TextStyle(
                    color: Colors.red.shade700,
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Text(
            l10n.translate('takeExtraCareForChronicDiseasePatient') ?? 'Please take extra care and double-check all procedures, medications, and treatments for this patient.',
            style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
          ),
        ],
      ),
      actions: [
        FilledButton(
          onPressed: () => Navigator.pop(ctx),
          style: FilledButton.styleFrom(
            backgroundColor: Colors.red,
          ),
          child: Text(l10n.translate('iUnderstand') ?? 'I Understand'),
        ),
      ],
    ),
  );
}
