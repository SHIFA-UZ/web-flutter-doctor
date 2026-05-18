// lib/state/patients/patient_documents_provider.dart
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';

/// Cache key for patient documents: optional [clinicId] for clinic workspace (server checks roster link).
@immutable
class PatientDocumentsKey {
  const PatientDocumentsKey({required this.patientId, this.clinicId});

  final String patientId;
  final int? clinicId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is PatientDocumentsKey &&
          patientId == other.patientId &&
          clinicId == other.clinicId;

  @override
  int get hashCode => Object.hash(patientId, clinicId);
}

final patientDocumentsProvider =
    FutureProvider.family<List<PatientDocument>, PatientDocumentsKey>((
      ref,
      key,
    ) async {
      final client = ref.read(apiClientProvider);
      return fetchPatientDocumentsWithClient(
        client: client,
        patientId: key.patientId,
        clinicId: key.clinicId,
      );
    });
