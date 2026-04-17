// lib/state/patients/patient_documents_provider.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';

final patientDocumentsProvider =
    FutureProvider.family<List<PatientDocument>, String>((
      ref,
      patientId,
    ) async {
      final client = ref.read(apiClientProvider);
      return fetchPatientDocumentsWithClient(
        client: client,
        patientId: patientId,
      );
    });
