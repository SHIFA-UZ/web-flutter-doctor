// lib/state/clinic/clinic_models.dart

class MyClinicSummary {
  final int clinicId;
  final String name;
  final String timeZone;
  final String? phone;
  final String? email;
  final String? address;
  final String membershipRole;
  final bool isPracticeClinic;

  const MyClinicSummary({
    required this.clinicId,
    required this.name,
    required this.timeZone,
    required this.phone,
    required this.email,
    required this.address,
    required this.membershipRole,
    required this.isPracticeClinic,
  });

  factory MyClinicSummary.fromJson(Map<String, dynamic> json) {
    return MyClinicSummary(
      clinicId: (json['clinicId'] as num).toInt(),
      name: json['name'] as String? ?? '',
      timeZone: json['timeZone'] as String? ?? 'UTC',
      phone: json['phone'] as String?,
      email: json['email'] as String?,
      address: json['address'] as String?,
      membershipRole: json['membershipRole'] as String? ?? 'DOCTOR',
      isPracticeClinic: json['isPracticeClinic'] as bool? ?? false,
    );
  }
}

class ClinicOverviewStats {
  final int appointmentsToday;
  final int activeDoctors;
  final int patientsThisMonth;
  final int? averageWaitingMinutes;
  final int? occupancyPercent;

  const ClinicOverviewStats({
    required this.appointmentsToday,
    required this.activeDoctors,
    required this.patientsThisMonth,
    this.averageWaitingMinutes,
    this.occupancyPercent,
  });

  factory ClinicOverviewStats.fromJson(Map<String, dynamic> json) {
    return ClinicOverviewStats(
      appointmentsToday: (json['appointmentsToday'] as num?)?.toInt() ?? 0,
      activeDoctors: (json['activeDoctors'] as num?)?.toInt() ?? 0,
      patientsThisMonth: (json['patientsThisMonth'] as num?)?.toInt() ?? 0,
      averageWaitingMinutes: (json['averageWaitingMinutes'] as num?)?.toInt(),
      occupancyPercent: (json['occupancyPercent'] as num?)?.toInt(),
    );
  }
}

class ClinicMember {
  final int doctorProfileId;
  final int userId;
  final String displayName;
  final String membershipRole;

  const ClinicMember({
    required this.doctorProfileId,
    required this.userId,
    required this.displayName,
    required this.membershipRole,
  });

  factory ClinicMember.fromJson(Map<String, dynamic> json) {
    return ClinicMember(
      doctorProfileId: (json['doctorProfileId'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      displayName: json['displayName'] as String? ?? '',
      membershipRole: json['membershipRole'] as String? ?? 'DOCTOR',
    );
  }
}

class ClinicPatientRow {
  final int patientId;
  final String fullName;
  final String? phone;
  final String? email;

  const ClinicPatientRow({
    required this.patientId,
    required this.fullName,
    required this.phone,
    required this.email,
  });

  factory ClinicPatientRow.fromJson(Map<String, dynamic> json) {
    return ClinicPatientRow(
      patientId: (json['patientId'] as num).toInt(),
      fullName: json['fullName'] as String? ?? '',
      phone: json['phone'] as String?,
      email: json['email'] as String?,
    );
  }
}

class ClinicCatalogItem {
  final int id;
  final int clinicId;
  final String? code;
  final String title;
  final int defaultPriceMinor;
  final String currency;
  final bool active;
  final int sortOrder;

  const ClinicCatalogItem({
    required this.id,
    required this.clinicId,
    required this.code,
    required this.title,
    required this.defaultPriceMinor,
    required this.currency,
    required this.active,
    required this.sortOrder,
  });

  factory ClinicCatalogItem.fromJson(Map<String, dynamic> json) {
    return ClinicCatalogItem(
      id: (json['id'] as num).toInt(),
      clinicId: (json['clinicId'] as num).toInt(),
      code: json['code'] as String?,
      title: json['title'] as String? ?? '',
      defaultPriceMinor: (json['defaultPriceMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency'] as String? ?? 'UZS',
      active: json['active'] as bool? ?? true,
      sortOrder: (json['sortOrder'] as num?)?.toInt() ?? 0,
    );
  }
}
