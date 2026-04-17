import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';

/// PatientsController fetches patients from backend and manages state.
class PatientsController extends StateNotifier<List<Patient>> {
  PatientsController(this.ref) : super([]) {
    // ✅ Automatically load on provider creation
    _initLoad();
  }

  final Ref ref;

  Future<void> _initLoad() async {
    try {
      await loadPatients();
    } catch (e) {
      // Optional: log/ignore; the UI can still retry
      // debugPrint('Failed to init patients: $e');
    }
  }

  /// Load patients from backend API
  Future<List<Patient>> loadPatients() async {
    final client = ref.read(apiClientProvider);
    final response = await client.get('/api/patients');
    if (response.statusCode == 200) {
      final List data = json.decode(utf8.decode(response.bodyBytes)) as List;
      final patients = data
          .map((e) => Patient.fromApi(e as Map<String, dynamic>))
          .toList();
      state = patients;
      return patients;
    } else if (response.statusCode == 401) {
      throw Exception('Unauthorized: Please login again.');
    } else {
      throw Exception(
        'Failed to load patients: ${response.statusCode} ${response.body}',
      );
    }
  }
}

/// Provider for PatientsController
final patientsProvider =
    StateNotifierProvider<PatientsController, List<Patient>>((ref) {
      // ✅ ctor calls _initLoad()
      return PatientsController(ref);
    });

/// Provider to fetch a single patient by ID
final patientByIdProvider = FutureProvider.family<Patient?, String>((ref, patientId) async {
  try {
    final client = ref.read(apiClientProvider);
    final patient = await fetchPatientWithClient(
      client: client,
      patientId: patientId,
    );
    return patient;
  } catch (e) {
    return null;
  }
});

/// Controller for "patients for assignment" (id + name only) used in calendar assign-patient.
class PatientsForAssignmentController extends StateNotifier<AsyncValue<List<PatientAssignmentItem>>> {
  PatientsForAssignmentController(this.ref) : super(const AsyncValue.data([]));

  final Ref ref;

  Future<List<PatientAssignmentItem>> loadPatientsForAssignment() async {
    state = const AsyncValue.loading();
    try {
      final client = ref.read(apiClientProvider);
      final list = await fetchPatientsForAssignmentWithClient(client: client);
      state = AsyncValue.data(list);
      return list;
    } catch (e, st) {
      state = AsyncValue.error(e, st);
      rethrow;
    }
  }
}

/// Provider for patients-for-assignment list (id + name) used in calendar "assign patient".
final patientsForAssignmentProvider =
    StateNotifierProvider<PatientsForAssignmentController, AsyncValue<List<PatientAssignmentItem>>>((ref) {
  return PatientsForAssignmentController(ref);
});