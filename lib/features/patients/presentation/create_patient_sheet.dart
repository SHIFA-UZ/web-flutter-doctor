import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';

const patientLanguageOptions = <String>[
  'english',
  'uzbek',
  'russian',
  'german',
  'karakalpak',
  'kazakh',
  'kyrgyz',
  'tajik',
  'turkmen',
  'arabic',
];

/// Shows the new-patient form and creates the patient on confirm.
/// Returns the created [Patient], or null if cancelled or creation failed.
Future<Patient?> showCreatePatientSheet(
  BuildContext context,
  WidgetRef ref, {
  bool reloadPatientsList = true,
}) async {
  final nameCtrl = TextEditingController();
  final phoneCtrl = TextEditingController();
  final emailCtrl = TextEditingController();
  final addressCtrl = TextEditingController();
  String? selectedLanguage = patientLanguageOptions.first;
  DateTime? birthDate;

  final confirmed = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    builder: (ctx) {
      return Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 16,
          bottom: 16 + MediaQuery.of(ctx).viewInsets.bottom,
        ),
        child: StatefulBuilder(
          builder: (ctx, setModalState) {
            final l10n = AppLocalizations.of(ctx)!;
            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  l10n.translate('createNewPatient') ?? 'Create New Patient',
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: nameCtrl,
                  decoration: InputDecoration(
                    labelText:
                        '${l10n.translate('fullName') ?? 'Full Name'} *',
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: phoneCtrl,
                  decoration: InputDecoration(
                    labelText: '${l10n.phoneNumber} (${l10n.optional})',
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: emailCtrl,
                  decoration: InputDecoration(labelText: l10n.email),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: addressCtrl,
                  decoration: InputDecoration(labelText: l10n.address),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: selectedLanguage,
                  decoration: InputDecoration(
                    labelText: l10n.language,
                    border: const OutlineInputBorder(),
                  ),
                  items: patientLanguageOptions
                      .map(
                        (String lang) => DropdownMenuItem<String>(
                          value: lang,
                          child: Text(
                            lang[0].toUpperCase() + lang.substring(1),
                          ),
                        ),
                      )
                      .toList(),
                  onChanged: (String? value) {
                    if (value != null) {
                      setModalState(() => selectedLanguage = value);
                    }
                  },
                ),
                const SizedBox(height: 12),
                InkWell(
                  onTap: () async {
                    final picked = await showDatePicker(
                      context: ctx,
                      locale: localeForMaterialIntl(
                        Localizations.localeOf(ctx),
                      ),
                      firstDate: DateTime(1900),
                      lastDate: DateTime.now(),
                      initialDate: DateTime(1990),
                    );
                    if (picked != null) {
                      setModalState(() => birthDate = picked);
                    }
                  },
                  child: InputDecorator(
                    decoration: InputDecoration(labelText: l10n.birthDate),
                    child: Text(
                      birthDate == null
                          ? l10n.selectDate
                          : '${birthDate!.day}.${birthDate!.month}.${birthDate!.year}',
                    ),
                  ),
                ),
                const SizedBox(height: 20),
                ShifaPrimaryButton(
                  width: ButtonWidth.fill,
                  onPressed: () {
                    if (nameCtrl.text.trim().isEmpty) return;
                    Navigator.pop(ctx, true);
                  },
                  label: l10n.translate('createPatient') ?? 'Create Patient',
                ),
              ],
            );
          },
        ),
      );
    },
  );

  if (confirmed != true || !context.mounted) {
    nameCtrl.dispose();
    phoneCtrl.dispose();
    emailCtrl.dispose();
    addressCtrl.dispose();
    return null;
  }

  try {
    final client = ref.read(apiClientProvider);
    final phoneRaw = phoneCtrl.text.trim();
    final created = await createPatientWithClient(
      client: client,
      name: nameCtrl.text.trim(),
      phone: phoneRaw.isEmpty ? null : phoneRaw,
      email: emailCtrl.text.trim(),
      address: addressCtrl.text.trim(),
      birthDate: birthDate,
      language: selectedLanguage,
      photoUrl: null,
    );

    if (reloadPatientsList) {
      await ref.read(patientsProvider.notifier).loadPatients();
    }

    if (context.mounted) {
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.translate('patientCreated') ?? 'Patient created'),
        ),
      );
    }

    return created;
  } catch (e) {
    if (context.mounted) {
      final l10n = AppLocalizations.of(context)!;
      final msg = e.toString().replaceFirst('Exception: ', '');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            msg.contains('already exists')
                ? msg
                : '${l10n.translate('createFailed') ?? 'Create failed'}: $msg',
          ),
          backgroundColor: Colors.red,
        ),
      );
    }
    return null;
  } finally {
    nameCtrl.dispose();
    phoneCtrl.dispose();
    emailCtrl.dispose();
    addressCtrl.dispose();
  }
}
