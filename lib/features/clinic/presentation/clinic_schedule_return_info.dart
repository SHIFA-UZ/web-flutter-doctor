/// Restore [ClinicDoctorScheduleRoute] after leaving the patients overlay.
class ClinicScheduleReturnInfo {
  const ClinicScheduleReturnInfo({
    required this.doctorProfileId,
    required this.doctorDisplayName,
    required this.clinicScheduleTimeZone,
    this.clinicStreetAddress,
  });

  final int doctorProfileId;
  final String doctorDisplayName;
  final String clinicScheduleTimeZone;
  final String? clinicStreetAddress;

  static ClinicScheduleReturnInfo? fromRouteArgs(Object? args) {
    if (args is! Map) return null;
    final raw = args['clinicScheduleReturn'];
    if (raw is! Map) return null;
    final doctorProfileId = int.tryParse(raw['doctorProfileId']?.toString() ?? '');
    final doctorDisplayName = raw['doctorDisplayName']?.toString().trim() ?? '';
    final timeZone = raw['clinicScheduleTimeZone']?.toString().trim() ?? '';
    if (doctorProfileId == null || doctorDisplayName.isEmpty || timeZone.isEmpty) {
      return null;
    }
    final street = raw['clinicStreetAddress']?.toString().trim();
    return ClinicScheduleReturnInfo(
      doctorProfileId: doctorProfileId,
      doctorDisplayName: doctorDisplayName,
      clinicScheduleTimeZone: timeZone,
      clinicStreetAddress: street?.isNotEmpty == true ? street : null,
    );
  }

  Map<String, dynamic> toMap() => {
        'doctorProfileId': doctorProfileId,
        'doctorDisplayName': doctorDisplayName,
        'clinicScheduleTimeZone': clinicScheduleTimeZone,
        if (clinicStreetAddress != null) 'clinicStreetAddress': clinicStreetAddress,
      };
}
