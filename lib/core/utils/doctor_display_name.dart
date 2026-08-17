/// Display name for the signed-in doctor in the shell and home greeting.
const String kDoctorMedPrefix = 'Dr. med.';

String formatDoctorMedName({
  required String firstName,
  required String lastName,
  bool includePrefix = true,
}) {
  final full = '$firstName $lastName'.trim();
  if (!includePrefix) return full;
  if (full.isEmpty) return kDoctorMedPrefix;
  return '$kDoctorMedPrefix $full';
}
