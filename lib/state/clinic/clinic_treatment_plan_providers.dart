import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

/// `[clinicId, patientId]`
final treatmentPlansForPatientProvider =
    FutureProvider.autoDispose.family<List<TreatmentPlanSummaryDto>, List<int>>(
  (ref, ids) async {
    if (ids.length < 2) return [];
    return fetchTreatmentPlansForPatient(
      ref,
      clinicId: ids[0],
      patientId: ids[1],
    );
  },
);

final treatmentPlanDetailProvider =
    FutureProvider.autoDispose.family<TreatmentPlanDetailDto?, int>(
  (ref, planId) => fetchTreatmentPlanDetail(ref, planId),
);

/// Filter inputs for [treatmentPlansForClinicProvider]. Implements value
/// equality so Riverpod can cache results between rebuilds when the user
/// hasn't actually changed the filters.
class ClinicPlansFilter {
  final int clinicId;
  final String? status;
  final String? query;

  const ClinicPlansFilter({
    required this.clinicId,
    this.status,
    this.query,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ClinicPlansFilter &&
          other.clinicId == clinicId &&
          other.status == status &&
          other.query == query;

  @override
  int get hashCode => Object.hash(clinicId, status, query);
}

/// Clinic-wide plan list (no patient required), with optional filters.
final treatmentPlansForClinicProvider = FutureProvider.autoDispose
    .family<List<TreatmentPlanSummaryDto>, ClinicPlansFilter>(
  (ref, f) => fetchTreatmentPlansForClinic(
    ref,
    clinicId: f.clinicId,
    status: f.status,
    query: f.query,
  ),
);
