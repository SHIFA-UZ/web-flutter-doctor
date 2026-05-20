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
