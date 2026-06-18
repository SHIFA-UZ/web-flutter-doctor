// Treatment plan DTOs (doctor API: /api/treatment-plans)

/// Doctor practice location for the treatment-plan slot picker.
class PlanDoctorLocationDto {
  final int id;
  final String label;
  final String? clinic;
  final String? address;
  final bool isPrimary;

  const PlanDoctorLocationDto({
    required this.id,
    required this.label,
    this.clinic,
    this.address,
    this.isPrimary = false,
  });

  /// True when this is a legacy synthetic row (no structured location id).
  bool get isLegacySynthetic => id <= 0;

  String get displayLabel {
    final c = clinic?.trim();
    if (c != null && c.isNotEmpty) return '$label · $c';
    return label;
  }

  factory PlanDoctorLocationDto.fromJson(Map<String, dynamic> json) =>
      PlanDoctorLocationDto(
        id: (json['id'] as num?)?.toInt() ?? 0,
        label: json['label']?.toString() ?? '',
        clinic: json['clinic'] as String?,
        address: json['address'] as String?,
        isPrimary: json['isPrimary'] == true,
      );
}

class FreeSlotDto {
  final String startAt;
  final String endAt;
  final int slotMinutes;
  final int? locationId;
  final String? locationLabel;

  const FreeSlotDto({
    required this.startAt,
    required this.endAt,
    required this.slotMinutes,
    this.locationId,
    this.locationLabel,
  });

  factory FreeSlotDto.fromJson(Map<String, dynamic> json) => FreeSlotDto(
        startAt: json['startAt']?.toString() ?? '',
        endAt: json['endAt']?.toString() ?? '',
        slotMinutes: (json['slotMinutes'] as num?)?.toInt() ?? 30,
        locationId: (json['locationId'] as num?)?.toInt(),
        locationLabel: json['locationLabel'] as String?,
      );
}

class TreatmentPlanDoctorRef {
  final int id;
  final String name;
  const TreatmentPlanDoctorRef({required this.id, required this.name});

  factory TreatmentPlanDoctorRef.fromJson(Map<String, dynamic> json) =>
      TreatmentPlanDoctorRef(
        id: (json['id'] as num?)?.toInt() ?? 0,
        name: json['name']?.toString() ?? '',
      );
}

class TreatmentPlanSummaryDto {
  final int id;
  final int clinicId;
  final int? patientId;
  final String? patientName;
  final int? attendingDoctorId;
  final String? attendingDoctorName;
  final List<TreatmentPlanDoctorRef> attendingDoctors;
  final String? title;
  final String? diagnosis;
  final String status;
  final String? notes;
  final int? paymentReminderDays;
  final String planKind;
  final List<String> symptoms;
  final int totalMinor;
  final int paidMinor;
  final int owedMinor;
  final String currency;
  final String planPaymentStatus;
  final int visitCount;
  final int linesCompletedCount;
  final int linesTotalCount;
  final String? createdAt;
  final String? updatedAt;

  TreatmentPlanSummaryDto({
    required this.id,
    required this.clinicId,
    this.patientId,
    this.patientName,
    this.attendingDoctorId,
    this.attendingDoctorName,
    this.attendingDoctors = const [],
    this.title,
    this.diagnosis,
    required this.status,
    this.notes,
    this.paymentReminderDays,
    required this.planKind,
    required this.symptoms,
    required this.totalMinor,
    required this.paidMinor,
    required this.owedMinor,
    required this.currency,
    required this.planPaymentStatus,
    this.visitCount = 0,
    this.linesCompletedCount = 0,
    this.linesTotalCount = 0,
    this.createdAt,
    this.updatedAt,
  });

  factory TreatmentPlanSummaryDto.fromJson(Map<String, dynamic> json) {
    final sym = json['symptoms'];
    return TreatmentPlanSummaryDto(
      id: (json['id'] as num?)?.toInt() ?? 0,
      clinicId: (json['clinicId'] as num?)?.toInt() ?? 0,
      patientId: (json['patientId'] as num?)?.toInt(),
      patientName: json['patientName'] as String?,
      attendingDoctorId: (json['attendingDoctorId'] as num?)?.toInt(),
      attendingDoctorName: json['attendingDoctorName'] as String?,
      attendingDoctors: (json['attendingDoctors'] as List?)
              ?.whereType<Map>()
              .map((e) =>
                  TreatmentPlanDoctorRef.fromJson(Map<String, dynamic>.from(e)))
              .toList() ??
          const [],
      title: json['title'] as String?,
      diagnosis: json['diagnosis'] as String?,
      status: json['status']?.toString() ?? '',
      notes: json['notes'] as String?,
      paymentReminderDays: (json['paymentReminderDays'] as num?)?.toInt(),
      planKind: json['planKind']?.toString() ?? 'COMPREHENSIVE',
      symptoms: sym is List ? sym.map((e) => e.toString()).toList() : const [],
      totalMinor: (json['totalMinor'] as num?)?.toInt() ?? 0,
      paidMinor: (json['paidMinor'] as num?)?.toInt() ?? 0,
      owedMinor: (json['owedMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? 'UZS',
      planPaymentStatus: json['planPaymentStatus']?.toString() ?? '',
      visitCount: (json['visitCount'] as num?)?.toInt() ?? 0,
      linesCompletedCount: (json['linesCompletedCount'] as num?)?.toInt() ?? 0,
      linesTotalCount: (json['linesTotalCount'] as num?)?.toInt() ?? 0,
      createdAt: json['createdAt'] as String?,
      updatedAt: json['updatedAt'] as String?,
    );
  }
}

class AppointmentSummaryDto {
  final int id;
  final String startAt;
  final String endAt;
  final String status;
  final int doctorProfileId;
  final String doctorName;

  AppointmentSummaryDto({
    required this.id,
    required this.startAt,
    required this.endAt,
    required this.status,
    required this.doctorProfileId,
    required this.doctorName,
  });

  factory AppointmentSummaryDto.fromJson(Map<String, dynamic> json) {
    return AppointmentSummaryDto(
      id: (json['id'] as num?)?.toInt() ?? 0,
      startAt: json['startAt']?.toString() ?? '',
      endAt: json['endAt']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      doctorProfileId: (json['doctorProfileId'] as num?)?.toInt() ?? 0,
      doctorName: json['doctorName']?.toString() ?? '',
    );
  }
}

class LineDetailDto {
  final int id;
  final int? catalogItemId;
  final String title;
  final int quantity;
  final int unitPriceMinor;
  final int discountMinor;
  final String currency;
  final int sortOrder;
  final String status;
  final AppointmentSummaryDto? linkedAppointment;
  final int lineTotalMinor;
  final String? specialtyMetadata;
  final int? assignedDoctorId;
  final String? notes;

  LineDetailDto({
    required this.id,
    this.catalogItemId,
    required this.title,
    required this.quantity,
    required this.unitPriceMinor,
    required this.discountMinor,
    required this.currency,
    required this.sortOrder,
    required this.status,
    this.linkedAppointment,
    required this.lineTotalMinor,
    this.specialtyMetadata,
    this.assignedDoctorId,
    this.notes,
  });

  factory LineDetailDto.fromJson(Map<String, dynamic> json) {
    final ap = json['linkedAppointment'];
    return LineDetailDto(
      id: (json['id'] as num?)?.toInt() ?? 0,
      catalogItemId: (json['catalogItemId'] as num?)?.toInt(),
      title: json['title']?.toString() ?? '',
      quantity: (json['quantity'] as num?)?.toInt() ?? 1,
      unitPriceMinor: (json['unitPriceMinor'] as num?)?.toInt() ?? 0,
      discountMinor: (json['discountMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? 'UZS',
      sortOrder: (json['sortOrder'] as num?)?.toInt() ?? 0,
      status: json['status']?.toString() ?? '',
      linkedAppointment: ap is Map
          ? AppointmentSummaryDto.fromJson(Map<String, dynamic>.from(ap))
          : null,
      lineTotalMinor: (json['lineTotalMinor'] as num?)?.toInt() ?? 0,
      specialtyMetadata: json['specialtyMetadata'] as String?,
      assignedDoctorId: (json['assignedDoctorId'] as num?)?.toInt(),
      notes: json['notes'] as String?,
    );
  }
}

class InstallmentScheduleItemDto {
  final int installmentItemId;
  final int sequenceNumber;
  final String dueDate;
  final int amountMinor;
  final String currency;
  final String status;

  InstallmentScheduleItemDto({
    required this.installmentItemId,
    required this.sequenceNumber,
    required this.dueDate,
    required this.amountMinor,
    required this.currency,
    required this.status,
  });

  factory InstallmentScheduleItemDto.fromJson(Map<String, dynamic> json) {
    return InstallmentScheduleItemDto(
      installmentItemId: (json['installmentItemId'] as num?)?.toInt() ?? 0,
      sequenceNumber: (json['sequenceNumber'] as num?)?.toInt() ?? 0,
      dueDate: json['dueDate']?.toString() ?? '',
      amountMinor: (json['amountMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? 'UZS',
      status: json['status']?.toString() ?? '',
    );
  }
}

class InstallmentPlanSummaryDto {
  final int installmentPlanId;
  final String status;
  final int totalAmountMinor;
  final String currency;
  final int numInstallments;
  final List<InstallmentScheduleItemDto> scheduleRows;

  InstallmentPlanSummaryDto({
    required this.installmentPlanId,
    required this.status,
    required this.totalAmountMinor,
    required this.currency,
    required this.numInstallments,
    this.scheduleRows = const [],
  });

  factory InstallmentPlanSummaryDto.fromJson(Map<String, dynamic> json) {
    final srRaw = json['scheduleRows'];
    return InstallmentPlanSummaryDto(
      installmentPlanId:
          (json['installmentPlanId'] as num?)?.toInt() ?? 0,
      status: json['status']?.toString() ?? '',
      totalAmountMinor: (json['totalAmountMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? 'UZS',
      numInstallments: (json['numInstallments'] as num?)?.toInt() ?? 0,
      scheduleRows: srRaw is List
          ? srRaw
              .whereType<Map>()
              .map((e) => InstallmentScheduleItemDto.fromJson(
                    Map<String, dynamic>.from(e),
                  ))
              .toList()
          : const [],
    );
  }
}

class TreatmentPlanDetailDto {
  final TreatmentPlanSummaryDto summary;
  final List<LineDetailDto> lines;
  final List<InstallmentPlanSummaryDto> installmentPlans;
  final String? dentalPlanDocumentation;

  TreatmentPlanDetailDto({
    required this.summary,
    required this.lines,
    required this.installmentPlans,
    this.dentalPlanDocumentation,
  });

  factory TreatmentPlanDetailDto.fromJson(Map<String, dynamic> json) {
    final sum = json['summary'];
    final linesRaw = json['lines'];
    final instRaw = json['installmentPlans'];
    return TreatmentPlanDetailDto(
      summary: sum is Map
          ? TreatmentPlanSummaryDto.fromJson(Map<String, dynamic>.from(sum))
          : TreatmentPlanSummaryDto.fromJson(json),
      lines: linesRaw is List
          ? linesRaw
              .whereType<Map>()
              .map((e) => LineDetailDto.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : const [],
      installmentPlans: instRaw is List
          ? instRaw
              .whereType<Map>()
              .map((e) =>
                  InstallmentPlanSummaryDto.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : const [],
      dentalPlanDocumentation: json['dentalPlanDocumentation'] as String?,
    );
  }
}

class FulfillmentCandidateDto {
  final int lineId;
  final String title;
  final String? fdi;
  final String? dentition;
  final int unitPriceMinor;
  final int quantity;
  final int discountMinor;
  final String currency;
  final int lineTotalMinor;
  final String status;
  final bool toothMatch;

  const FulfillmentCandidateDto({
    required this.lineId,
    required this.title,
    this.fdi,
    this.dentition,
    required this.unitPriceMinor,
    required this.quantity,
    required this.discountMinor,
    required this.currency,
    required this.lineTotalMinor,
    required this.status,
    required this.toothMatch,
  });

  factory FulfillmentCandidateDto.fromJson(Map<String, dynamic> json) =>
      FulfillmentCandidateDto(
        lineId: (json['lineId'] as num?)?.toInt() ?? 0,
        title: json['title']?.toString() ?? '',
        fdi: json['fdi'] as String?,
        dentition: json['dentition'] as String?,
        unitPriceMinor: (json['unitPriceMinor'] as num?)?.toInt() ?? 0,
        quantity: (json['quantity'] as num?)?.toInt() ?? 1,
        discountMinor: (json['discountMinor'] as num?)?.toInt() ?? 0,
        currency: json['currency']?.toString() ?? 'UZS',
        lineTotalMinor: (json['lineTotalMinor'] as num?)?.toInt() ?? 0,
        status: json['status']?.toString() ?? '',
        toothMatch: json['toothMatch'] == true,
      );
}

class ClinicPatientAppointmentDto {
  final int id;
  final String startAt;
  final String endAt;
  final String status;
  final int doctorProfileId;
  final String doctorName;

  ClinicPatientAppointmentDto({
    required this.id,
    required this.startAt,
    required this.endAt,
    required this.status,
    required this.doctorProfileId,
    required this.doctorName,
  });

  factory ClinicPatientAppointmentDto.fromJson(Map<String, dynamic> json) {
    return ClinicPatientAppointmentDto(
      id: (json['id'] as num?)?.toInt() ?? 0,
      startAt: json['startAt']?.toString() ?? '',
      endAt: json['endAt']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      doctorProfileId: (json['doctorProfileId'] as num?)?.toInt() ?? 0,
      doctorName: json['doctorName']?.toString() ?? '',
    );
  }
}

class AppointmentLedgerRowDto {
  final int appointmentId;
  final String startAt;
  final int patientId;
  final String patientName;
  final int doctorProfileId;
  final String doctorName;
  final int treatmentPlanId;
  final List<AppointmentLedgerServiceLineDto> services;
  final int visitTotalMinor;
  final int visitCollectedMinor;
  final String currency;
  final String planPaymentStatus;
  final String planSimplePaymentStatus;

  AppointmentLedgerRowDto({
    required this.appointmentId,
    required this.startAt,
    required this.patientId,
    required this.patientName,
    required this.doctorProfileId,
    required this.doctorName,
    required this.treatmentPlanId,
    required this.services,
    required this.visitTotalMinor,
    required this.visitCollectedMinor,
    required this.currency,
    required this.planPaymentStatus,
    required this.planSimplePaymentStatus,
  });

  factory AppointmentLedgerRowDto.fromJson(Map<String, dynamic> json) {
    final s = json['services'];
    return AppointmentLedgerRowDto(
      appointmentId: (json['appointmentId'] as num?)?.toInt() ?? 0,
      startAt: json['startAt']?.toString() ?? '',
      patientId: (json['patientId'] as num?)?.toInt() ?? 0,
      patientName: json['patientName']?.toString() ?? '',
      doctorProfileId: (json['doctorProfileId'] as num?)?.toInt() ?? 0,
      doctorName: json['doctorName']?.toString() ?? '',
      treatmentPlanId: (json['treatmentPlanId'] as num?)?.toInt() ?? 0,
      services: s is List
          ? s
              .whereType<Map>()
              .map((e) => AppointmentLedgerServiceLineDto.fromJson(
                  Map<String, dynamic>.from(e)))
              .toList()
          : const [],
      visitTotalMinor: (json['visitTotalMinor'] as num?)?.toInt() ?? 0,
      visitCollectedMinor: (json['visitCollectedMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? 'UZS',
      planPaymentStatus: json['planPaymentStatus']?.toString() ?? '',
      planSimplePaymentStatus:
          json['planSimplePaymentStatus']?.toString() ?? '',
    );
  }
}

class AppointmentLedgerServiceLineDto {
  final String title;
  final int lineTotalMinor;

  AppointmentLedgerServiceLineDto({
    required this.title,
    required this.lineTotalMinor,
  });

  factory AppointmentLedgerServiceLineDto.fromJson(Map<String, dynamic> json) {
    return AppointmentLedgerServiceLineDto(
      title: json['title']?.toString() ?? '',
      lineTotalMinor: (json['lineTotalMinor'] as num?)?.toInt() ?? 0,
    );
  }
}

/// One appointment linked to a comprehensive treatment plan (doctor API).
class TreatmentPlanVisitDto {
  final int appointmentId;
  final String startAt;
  final String endAt;
  final String status;
  final int doctorProfileId;
  final String doctorName;
  final String location;
  final List<String> services;
  final int visitTotalMinor;
  final int visitCollectedMinor;
  final int visitOwedMinor;
  final String currency;
  final String visitPaymentStatus;
  /// UPCOMING | PAST | CANCELLED
  final String timing;

  TreatmentPlanVisitDto({
    required this.appointmentId,
    required this.startAt,
    required this.endAt,
    required this.status,
    required this.doctorProfileId,
    required this.doctorName,
    required this.location,
    required this.services,
    required this.visitTotalMinor,
    required this.visitCollectedMinor,
    required this.visitOwedMinor,
    required this.currency,
    required this.visitPaymentStatus,
    required this.timing,
  });

  factory TreatmentPlanVisitDto.fromJson(Map<String, dynamic> json) {
    final servicesRaw = json['services'];
    return TreatmentPlanVisitDto(
      appointmentId: (json['appointmentId'] as num?)?.toInt() ?? 0,
      startAt: json['startAt']?.toString() ?? '',
      endAt: json['endAt']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      doctorProfileId: (json['doctorProfileId'] as num?)?.toInt() ?? 0,
      doctorName: json['doctorName']?.toString() ?? '',
      location: json['location']?.toString() ?? '',
      services: servicesRaw is List
          ? servicesRaw.map((e) => e.toString()).toList()
          : const [],
      visitTotalMinor: (json['visitTotalMinor'] as num?)?.toInt() ?? 0,
      visitCollectedMinor: (json['visitCollectedMinor'] as num?)?.toInt() ?? 0,
      visitOwedMinor: (json['visitOwedMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? 'UZS',
      visitPaymentStatus: json['visitPaymentStatus']?.toString() ?? '',
      timing: json['timing']?.toString() ?? 'PAST',
    );
  }

  bool get isVideo => location.toLowerCase().contains('video');
}

class DoctorEarningRowDto {
  final int doctorProfileId;
  final int visitCount;
  final int grossMinor;
  final int collectedMinor;
  final int outstandingMinor;

  DoctorEarningRowDto({
    required this.doctorProfileId,
    required this.visitCount,
    required this.grossMinor,
    required this.collectedMinor,
    required this.outstandingMinor,
  });

  factory DoctorEarningRowDto.fromJson(Map<String, dynamic> json) {
    return DoctorEarningRowDto(
      doctorProfileId: (json['doctorProfileId'] as num?)?.toInt() ?? 0,
      visitCount: (json['visitCount'] as num?)?.toInt() ?? 0,
      grossMinor: (json['grossMinor'] as num?)?.toInt() ?? 0,
      collectedMinor: (json['collectedMinor'] as num?)?.toInt() ?? 0,
      outstandingMinor: (json['outstandingMinor'] as num?)?.toInt() ?? 0,
    );
  }
}
