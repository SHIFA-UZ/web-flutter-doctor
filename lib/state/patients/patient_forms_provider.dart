import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_form_models.dart';

final patientFormsProvider =
    FutureProvider.family<List<PatientForm>, String>((ref, patientId) async {
      final client = ref.read(apiClientProvider);
      return fetchPatientFormsWithClient(
        client: client,
        patientId: patientId,
      );
    });

/// Latest 025-2 form for a patient (by date desc, then id desc so same-day newest-created wins).
/// Used to offer Shikoyati / Tashxis / Davolanish in appointment notes.
final last0252FormForPatientProvider =
    Provider.family<AsyncValue<PatientForm?>, String>((ref, patientId) {
  final async = ref.watch(patientFormsProvider(patientId));
  return async.when(
    data: (forms) {
      final o252 = forms.where((f) => f.templateId == '025-2').toList()
        ..sort((a, b) {
          final dateCmp = b.date.compareTo(a.date);
          if (dateCmp != 0) return dateCmp;
          final idA = int.tryParse(a.id ?? '0') ?? 0;
          final idB = int.tryParse(b.id ?? '0') ?? 0;
          return idB.compareTo(idA);
        });
      return AsyncValue.data(o252.isNotEmpty ? o252.first : null);
    },
    loading: () => const AsyncValue.loading(),
    error: (e, s) => AsyncValue.error(e, s),
  );
});
