// lib/state/clinic/clinic_models.dart

bool _membershipRoleCanManageFinance(String membershipRole) =>
    membershipRole == 'OWNER' || membershipRole == 'CLINIC_ADMIN';

class MyClinicSummary {
  final int clinicId;
  final String name;
  final String timeZone;
  final String? phone;
  final String? email;
  final String? address;
  final String membershipRole;
  final bool isPracticeClinic;
  final String currency;
  final int? defaultDoctorRevenueSharePercent;
  final bool canManageFinanceSettings;

  const MyClinicSummary({
    required this.clinicId,
    required this.name,
    required this.timeZone,
    required this.phone,
    required this.email,
    required this.address,
    required this.membershipRole,
    required this.isPracticeClinic,
    required this.currency,
    this.defaultDoctorRevenueSharePercent,
    this.canManageFinanceSettings = false,
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
      currency: json['currency'] as String? ?? 'UZS',
      defaultDoctorRevenueSharePercent:
          (json['defaultDoctorRevenueSharePercent'] as num?)?.toInt(),
      canManageFinanceSettings: json['canManageFinanceSettings'] == true ||
          _membershipRoleCanManageFinance(
            json['membershipRole'] as String? ?? 'DOCTOR',
          ),
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
  final int? doctorRevenueSharePercent;
  final int? effectiveRevenueSharePercent;

  const ClinicMember({
    required this.doctorProfileId,
    required this.userId,
    required this.displayName,
    required this.membershipRole,
    this.doctorRevenueSharePercent,
    this.effectiveRevenueSharePercent,
  });

  factory ClinicMember.fromJson(Map<String, dynamic> json) {
    return ClinicMember(
      doctorProfileId: (json['doctorProfileId'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      displayName: json['displayName'] as String? ?? '',
      membershipRole: json['membershipRole'] as String? ?? 'DOCTOR',
      doctorRevenueSharePercent:
          (json['doctorRevenueSharePercent'] as num?)?.toInt(),
      effectiveRevenueSharePercent:
          (json['effectiveRevenueSharePercent'] as num?)?.toInt(),
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
  final bool appliesToAllDoctors;
  final List<int> assignedDoctorProfileIds;

  const ClinicCatalogItem({
    required this.id,
    required this.clinicId,
    required this.code,
    required this.title,
    required this.defaultPriceMinor,
    required this.currency,
    required this.active,
    required this.sortOrder,
    required this.appliesToAllDoctors,
    required this.assignedDoctorProfileIds,
  });

  factory ClinicCatalogItem.fromJson(Map<String, dynamic> json) {
    final rawAssigned = json['assignedDoctorProfileIds'];
    final assigned = <int>[];
    if (rawAssigned is List) {
      for (final e in rawAssigned) {
        if (e is num) assigned.add(e.toInt());
      }
    }
    return ClinicCatalogItem(
      id: (json['id'] as num).toInt(),
      clinicId: (json['clinicId'] as num).toInt(),
      code: json['code'] as String?,
      title: json['title'] as String? ?? '',
      defaultPriceMinor: (json['defaultPriceMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency'] as String? ?? 'UZS',
      active: json['active'] as bool? ?? true,
      sortOrder: (json['sortOrder'] as num?)?.toInt() ?? 0,
      appliesToAllDoctors: json['appliesToAllDoctors'] as bool? ?? true,
      assignedDoctorProfileIds: assigned,
    );
  }
}

/// Unified service option served by `GET /api/treatment-plans/plan-services`.
///
/// Powers the Clinic → Services tab (full view) and the treatment-plan wizard
/// (filtered by selected attending doctors). Each row is either a clinic
/// catalog item ([kindClinicCatalog]) or a doctor's profile service
/// ([kindDoctorService]); the [key] is unique across both kinds so widgets can
/// use it directly as a map/selection identifier.
class PlanServiceOption {
  static const String kindClinicCatalog = 'CLINIC_CATALOG';
  static const String kindDoctorService = 'DOCTOR_SERVICE';

  /// Stable unique id across kinds, e.g. `catalog:42` or `doctor:7:service:123`.
  final String key;
  final String kind;
  final int? catalogItemId;
  final int? doctorServiceId;
  final String title;
  final String? code;
  final int defaultPriceMinor;
  final String currency;
  final bool active;
  final List<int> offeredByDoctorIds;
  final List<String> offeredByDoctorNames;

  const PlanServiceOption({
    required this.key,
    required this.kind,
    required this.catalogItemId,
    required this.doctorServiceId,
    required this.title,
    required this.code,
    required this.defaultPriceMinor,
    required this.currency,
    required this.active,
    required this.offeredByDoctorIds,
    required this.offeredByDoctorNames,
  });

  bool get isClinicCatalog => kind == kindClinicCatalog;
  bool get isDoctorService => kind == kindDoctorService;

  factory PlanServiceOption.fromJson(Map<String, dynamic> json) {
    List<int> intList(dynamic raw) {
      if (raw is! List) return const <int>[];
      final out = <int>[];
      for (final e in raw) {
        if (e is num) out.add(e.toInt());
      }
      return out;
    }

    List<String> stringList(dynamic raw) {
      if (raw is! List) return const <String>[];
      return raw.map((e) => e?.toString() ?? '').toList();
    }

    return PlanServiceOption(
      key: json['key'] as String? ?? '',
      kind: json['kind'] as String? ?? kindClinicCatalog,
      catalogItemId: (json['catalogItemId'] as num?)?.toInt(),
      doctorServiceId: (json['doctorServiceId'] as num?)?.toInt(),
      title: json['title'] as String? ?? '',
      code: json['code'] as String?,
      defaultPriceMinor: (json['defaultPriceMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency'] as String? ?? 'UZS',
      active: json['active'] as bool? ?? true,
      offeredByDoctorIds: intList(json['offeredByDoctorIds']),
      offeredByDoctorNames: stringList(json['offeredByDoctorNames']),
    );
  }
}
