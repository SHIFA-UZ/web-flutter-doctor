import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';

/// When set, [ChatScreen] opens the conversation for this patient id.
final chatPendingPatientIdProvider = StateProvider<String?>((ref) => null);

void openChatWithPatient(WidgetRef ref, String patientId) {
  ref.read(chatPendingPatientIdProvider.notifier).state = patientId;
  ref.read(shellProvider.notifier).setTab(DoctorShellTab.chat);
}
