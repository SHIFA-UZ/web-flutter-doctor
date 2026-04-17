import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/api/consultation_notes_api.dart';

final consultationNotesForAppointmentProvider =
    FutureProvider.family<List<ConsultationNoteDto>, String>((ref, appointmentId) async {
  final api = ref.read(apiClientProvider);
  return getConsultationNotesForAppointment(
    api: api,
    appointmentId: appointmentId,
  );
});

final draftNotesForAppointmentProvider =
    FutureProvider.family<List<DraftNoteDto>, String>((ref, appointmentId) async {
  final api = ref.read(apiClientProvider);
  return getDraftNotesForAppointment(
    api: api,
    appointmentId: appointmentId,
  );
});
