// Analytics DTOs matching backend API. Aggregate values only; no PII.

/// GET /api/doctor/analytics/overview
class DoctorAnalyticsOverview {
  final int appointmentsToday;
  final int completedToday;
  final int cancelledToday;
  final int newPatientsToday;

  const DoctorAnalyticsOverview({
    required this.appointmentsToday,
    required this.completedToday,
    required this.cancelledToday,
    required this.newPatientsToday,
  });

  factory DoctorAnalyticsOverview.fromJson(Map<String, dynamic> json) {
    return DoctorAnalyticsOverview(
      appointmentsToday: (json['appointmentsToday'] as num?)?.toInt() ?? 0,
      completedToday: (json['completedToday'] as num?)?.toInt() ?? 0,
      cancelledToday: (json['cancelledToday'] as num?)?.toInt() ?? 0,
      newPatientsToday: (json['newPatientsToday'] as num?)?.toInt() ?? 0,
    );
  }
}

/// GET /api/doctor/analytics/appointments-trend?days=7
class AppointmentTrendPoint {
  final String date; // yyyy-MM-dd
  final int count;

  const AppointmentTrendPoint({required this.date, required this.count});

  factory AppointmentTrendPoint.fromJson(Map<String, dynamic> json) {
    return AppointmentTrendPoint(
      date: (json['date'] as String?) ?? '',
      count: (json['count'] as num?)?.toInt() ?? 0,
    );
  }
}

/// GET /api/doctor/analytics/consultation-types
class ConsultationTypes {
  final int video;
  final int inPerson;

  const ConsultationTypes({required this.video, required this.inPerson});

  int get total => video + inPerson;

  factory ConsultationTypes.fromJson(Map<String, dynamic> json) {
    return ConsultationTypes(
      video: (json['video'] as num?)?.toInt() ?? 0,
      inPerson: (json['inPerson'] as num?)?.toInt() ?? 0,
    );
  }
}

/// GET /api/doctor/analytics/engagement
class DoctorEngagement {
  final int activePatients;
  final int documentsReceived;

  const DoctorEngagement({
    required this.activePatients,
    required this.documentsReceived,
  });

  factory DoctorEngagement.fromJson(Map<String, dynamic> json) {
    return DoctorEngagement(
      activePatients: (json['activePatients'] as num?)?.toInt() ?? 0,
      documentsReceived: (json['documentsReceived'] as num?)?.toInt() ?? 0,
    );
  }
}

/// GET /api/doctor/analytics/sms-usage
class DoctorSmsUsage {
  final int sentCount;
  final int totalCostMinor;
  final String currency;
  final int pricePerSmsMinor;
  final bool smsRemindersAllowed;

  const DoctorSmsUsage({
    required this.sentCount,
    required this.totalCostMinor,
    required this.currency,
    required this.pricePerSmsMinor,
    required this.smsRemindersAllowed,
  });

  factory DoctorSmsUsage.fromJson(Map<String, dynamic> json) {
    return DoctorSmsUsage(
      sentCount: (json['sentCount'] as num?)?.toInt() ?? 0,
      totalCostMinor: (json['totalCostMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? 'UZS',
      pricePerSmsMinor: (json['pricePerSmsMinor'] as num?)?.toInt() ?? 500,
      smsRemindersAllowed: json['smsRemindersAllowed'] == true,
    );
  }
}
