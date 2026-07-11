// lib/features/admin/domain/admin_models.dart
class AdminToken {
  final int id;
  final String keyCode;
  final bool consumed;
  final String? expiresAt;
  final String purpose; // Defaults to 'DOCTOR_ONBOARDING' if missing in JSON
  final String? notes;
  final String? emailSentTo;
  final String? emailSentAt;
  final String createdAt;

  AdminToken({
    required this.id,
    required this.keyCode,
    required this.consumed,
    this.expiresAt,
    required this.purpose,
    this.notes,
    this.emailSentTo,
    this.emailSentAt,
    required this.createdAt,
  });

  factory AdminToken.fromJson(Map<String, dynamic> json) {
    return AdminToken(
      id: json['id'] as int,
      keyCode: json['keyCode'] as String,
      consumed: json['consumed'] as bool,
      expiresAt: json['expiresAt'] as String?,
      purpose: json['purpose'] as String? ?? 'DOCTOR_ONBOARDING', // Default if missing
      notes: json['notes'] as String?,
      emailSentTo: json['emailSentTo'] as String?,
      emailSentAt: json['emailSentAt'] as String?,
      createdAt: json['createdAt'] as String,
    );
  }

  bool get isExpired {
    if (expiresAt == null) return false;
    final expiry = DateTime.parse(expiresAt!);
    return expiry.isBefore(DateTime.now());
  }

  bool get isValid => !consumed && !isExpired;
}

class AdminUser {
  final int id;
  final String? email;
  final String? phone;
  final String role;
  final bool enabled;
  final String? lastLoginAt;
  final int failedLoginAttempts;
  final String? lockedUntil;
  final Map<String, dynamic>? profile;

  /// Admin-managed subscription tier (BASIC | PRO | PREMIUM).
  final String subscriptionTier;

  /// Device registered via mobile app (FCM token present).
  final bool deviceRegistered;

  /// Whether a role-specific profile exists.
  final bool hasProfile;

  final String? createdAt;
  final bool emailVerified;

  AdminUser({
    required this.id,
    this.email,
    this.phone,
    required this.role,
    required this.enabled,
    this.lastLoginAt,
    required this.failedLoginAttempts,
    this.lockedUntil,
    this.profile,
    this.subscriptionTier = 'PREMIUM',
    this.deviceRegistered = false,
    this.hasProfile = false,
    this.createdAt,
    this.emailVerified = false,
  });

  factory AdminUser.fromJson(Map<String, dynamic> json) {
    return AdminUser(
      id: json['id'] as int,
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      role: json['role'] as String,
      enabled: json['enabled'] as bool,
      lastLoginAt: json['lastLoginAt'] as String?,
      failedLoginAttempts: json['failedLoginAttempts'] as int,
      lockedUntil: json['lockedUntil'] as String?,
      profile: json['profile'] as Map<String, dynamic>?,
      subscriptionTier:
          (json['subscriptionTier'] as String?)?.toUpperCase() ?? 'PREMIUM',
      deviceRegistered: json['deviceRegistered'] as bool? ?? false,
      hasProfile: json['hasProfile'] as bool? ?? false,
      createdAt: json['createdAt'] as String?,
      emailVerified: json['emailVerified'] as bool? ?? false,
    );
  }

  bool get isLocked {
    if (lockedUntil == null) return false;
    final lockTime = DateTime.parse(lockedUntil!);
    return lockTime.isAfter(DateTime.now());
  }

  String get displayName {
    if (profile != null) {
      // Patient: backend sends fullName (and firstName=fullName, lastName="")
      final fullName = profile!['fullName'] as String?;
      if (fullName != null && fullName.trim().isNotEmpty) return fullName.trim();
      final firstName = profile!['firstName'] as String? ?? '';
      final lastName = profile!['lastName'] as String? ?? '';
      if (firstName.isNotEmpty || lastName.isNotEmpty) {
        return '$firstName $lastName'.trim();
      }
    }
    return email ?? phone ?? 'User $id';
  }

  int? doctorProfileId() {
    final p = profile;
    if (p == null) return null;
    final v = p['doctorId'];
    if (v == null) return null;
    if (v is int) return v;
    if (v is num) return v.toInt();
    if (v is String) return int.tryParse(v);
    return null;
  }
}

class AdminPatientProfileRow {
  final int patientProfileId;
  final String fullName;
  final String? phone;
  final String? email;
  final String? createdAt;
  final int? createdByDoctorId;
  final String? createdByDoctorName;

  AdminPatientProfileRow({
    required this.patientProfileId,
    required this.fullName,
    this.phone,
    this.email,
    this.createdAt,
    this.createdByDoctorId,
    this.createdByDoctorName,
  });

  factory AdminPatientProfileRow.fromJson(Map<String, dynamic> json) {
    int? parseNullableInt(dynamic v) {
      if (v == null) return null;
      if (v is int) return v;
      if (v is num) return v.toInt();
      return int.tryParse(v.toString());
    }

    return AdminPatientProfileRow(
      patientProfileId: parseNullableInt(json['patientProfileId']) ?? 0,
      fullName: json['fullName']?.toString() ?? '',
      phone: json['phone'] as String?,
      email: json['email'] as String?,
      createdAt: json['createdAt'] as String?,
      createdByDoctorId: parseNullableInt(json['createdByDoctorId']),
      createdByDoctorName: json['createdByDoctorName'] as String?,
    );
  }
}

class AdminClinicSummary {
  final int id;
  final String name;
  final String? phone;
  final String? email;
  final String? address;
  final String timeZone;
  final int doctorCount;
  final String updatedAt;

  AdminClinicSummary({
    required this.id,
    required this.name,
    this.phone,
    this.email,
    this.address,
    required this.timeZone,
    required this.doctorCount,
    required this.updatedAt,
  });

  factory AdminClinicSummary.fromJson(Map<String, dynamic> json) {
    int toInt(dynamic v) {
      if (v == null) return 0;
      if (v is int) return v;
      if (v is num) return v.toInt();
      return int.tryParse(v.toString()) ?? 0;
    }

    return AdminClinicSummary(
      id: toInt(json['id']),
      name: json['name']?.toString() ?? '',
      phone: json['phone']?.toString(),
      email: json['email']?.toString(),
      address: json['address']?.toString(),
      timeZone: json['timeZone']?.toString() ?? 'Asia/Tashkent',
      doctorCount: toInt(json['doctorCount']),
      updatedAt: json['updatedAt']?.toString() ?? '',
    );
  }
}

class AdminClinicDoctorMember {
  final int doctorProfileId;
  final int userId;
  final String displayName;
  final String membershipRole;

  AdminClinicDoctorMember({
    required this.doctorProfileId,
    required this.userId,
    required this.displayName,
    required this.membershipRole,
  });

  factory AdminClinicDoctorMember.fromJson(Map<String, dynamic> json) {
    int toInt(dynamic v) {
      if (v == null) return 0;
      if (v is int) return v;
      if (v is num) return v.toInt();
      return int.tryParse(v.toString()) ?? 0;
    }

    return AdminClinicDoctorMember(
      doctorProfileId: toInt(json['doctorProfileId']),
      userId: toInt(json['userId']),
      displayName: json['displayName']?.toString() ?? '',
      membershipRole: json['membershipRole']?.toString() ?? 'DOCTOR',
    );
  }
}

class AdminClinicDetail {
  final int id;
  final String name;
  final String? phone;
  final String? email;
  final String? address;
  final String timeZone;
  final String createdAt;
  final String updatedAt;
  final List<AdminClinicDoctorMember> doctors;

  AdminClinicDetail({
    required this.id,
    required this.name,
    this.phone,
    this.email,
    this.address,
    required this.timeZone,
    required this.createdAt,
    required this.updatedAt,
    required this.doctors,
  });

  factory AdminClinicDetail.fromJson(Map<String, dynamic> json) {
    int toInt(dynamic v) {
      if (v == null) return 0;
      if (v is int) return v;
      if (v is num) return v.toInt();
      return int.tryParse(v.toString()) ?? 0;
    }

    final docList = (json['doctors'] as List?) ?? const [];
    return AdminClinicDetail(
      id: toInt(json['id']),
      name: json['name']?.toString() ?? '',
      phone: json['phone']?.toString(),
      email: json['email']?.toString(),
      address: json['address']?.toString(),
      timeZone: json['timeZone']?.toString() ?? 'Asia/Tashkent',
      createdAt: json['createdAt']?.toString() ?? '',
      updatedAt: json['updatedAt']?.toString() ?? '',
      doctors: docList
          .map((e) => AdminClinicDoctorMember.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class AuditLogEntry {
  final int id;
  final int adminUserId;
  final String actionType;
  final String entityType;
  final int? entityId;
  final Map<String, dynamic>? details;
  final String? ipAddress;
  final String? userAgent;
  final String createdAt;

  AuditLogEntry({
    required this.id,
    required this.adminUserId,
    required this.actionType,
    required this.entityType,
    this.entityId,
    this.details,
    this.ipAddress,
    this.userAgent,
    required this.createdAt,
  });

  factory AuditLogEntry.fromJson(Map<String, dynamic> json) {
    int? parseNullableInt(dynamic value) {
      if (value == null) return null;
      if (value is int) return value;
      if (value is num) return value.toInt();
      if (value is String) {
        final trimmed = value.trim();
        if (trimmed.isEmpty) return null;
        return int.tryParse(trimmed);
      }
      return null;
    }

    int parseIntOrZero(dynamic value) {
      return parseNullableInt(value) ?? 0;
    }

    return AuditLogEntry(
      id: parseIntOrZero(json['id']),
      adminUserId: parseIntOrZero(json['adminUserId']),
      actionType: json['actionType'] as String,
      entityType: json['entityType'] as String,
      entityId: parseNullableInt(json['entityId']),
      details: json['details'] as Map<String, dynamic>?,
      ipAddress: json['ipAddress'] as String?,
      userAgent: json['userAgent'] as String?,
      createdAt: json['createdAt'] as String,
    );
  }
}

class ActivityLogEntry {
  final int id;
  final int userId;
  final String activityType;
  final String? ipAddress;
  final String? userAgent;
  final bool success;
  final String? failureReason;
  final String createdAt;

  ActivityLogEntry({
    required this.id,
    required this.userId,
    required this.activityType,
    this.ipAddress,
    this.userAgent,
    required this.success,
    this.failureReason,
    required this.createdAt,
  });

  factory ActivityLogEntry.fromJson(Map<String, dynamic> json) {
    int parseIntOrZero(dynamic value) {
      if (value is int) return value;
      if (value is num) return value.toInt();
      if (value is String) return int.tryParse(value.trim()) ?? 0;
      return 0;
    }

    return ActivityLogEntry(
      id: parseIntOrZero(json['id']),
      userId: parseIntOrZero(json['userId']),
      activityType: json['activityType'] as String,
      ipAddress: json['ipAddress'] as String?,
      userAgent: json['userAgent'] as String?,
      success: json['success'] as bool,
      failureReason: json['failureReason'] as String?,
      createdAt: json['createdAt'] as String,
    );
  }
}

class UserManagementStats {
  final int totalUsers;
  final int totalDoctors;
  final int activeDoctors;
  final int disabledDoctors;
  final int patientAppUsers;
  final int activePatientUsers;
  final int totalAdmins;
  final int totalPatientProfiles;
  final int profilesWithoutAppAccount;
  final int profilesWithAppAccount;
  final int patientsWithDevice;
  final int patientAppUsersWithoutDevice;
  final int doctorsWithDevice;
  final int doctorsWithoutDevice;
  final int patientsNeverLoggedIn;
  final int patientsLoggedIn;
  final int doctorsNeverLoggedIn;
  final int deviceActivationRate;

  UserManagementStats({
    required this.totalUsers,
    required this.totalDoctors,
    required this.activeDoctors,
    required this.disabledDoctors,
    required this.patientAppUsers,
    required this.activePatientUsers,
    required this.totalAdmins,
    required this.totalPatientProfiles,
    required this.profilesWithoutAppAccount,
    required this.profilesWithAppAccount,
    required this.patientsWithDevice,
    required this.patientAppUsersWithoutDevice,
    required this.doctorsWithDevice,
    required this.doctorsWithoutDevice,
    required this.patientsNeverLoggedIn,
    required this.patientsLoggedIn,
    required this.doctorsNeverLoggedIn,
    required this.deviceActivationRate,
  });

  factory UserManagementStats.fromJson(Map<String, dynamic> json) {
    int i(dynamic v) {
      if (v is int) return v;
      if (v is num) return v.toInt();
      return int.tryParse(v?.toString() ?? '') ?? 0;
    }

    return UserManagementStats(
      totalUsers: i(json['totalUsers']),
      totalDoctors: i(json['totalDoctors']),
      activeDoctors: i(json['activeDoctors']),
      disabledDoctors: i(json['disabledDoctors']),
      patientAppUsers: i(json['patientAppUsers']),
      activePatientUsers: i(json['activePatientUsers']),
      totalAdmins: i(json['totalAdmins']),
      totalPatientProfiles: i(json['totalPatientProfiles']),
      profilesWithoutAppAccount: i(json['profilesWithoutAppAccount']),
      profilesWithAppAccount: i(json['profilesWithAppAccount']),
      patientsWithDevice: i(json['patientsWithDevice']),
      patientAppUsersWithoutDevice: i(json['patientAppUsersWithoutDevice']),
      doctorsWithDevice: i(json['doctorsWithDevice']),
      doctorsWithoutDevice: i(json['doctorsWithoutDevice']),
      patientsNeverLoggedIn: i(json['patientsNeverLoggedIn']),
      patientsLoggedIn: i(json['patientsLoggedIn']),
      doctorsNeverLoggedIn: i(json['doctorsNeverLoggedIn']),
      deviceActivationRate: i(json['deviceActivationRate']),
    );
  }
}

class DashboardStats {
  final int totalDoctors;
  final int activeDoctors;
  final int totalPatients;
  final int totalUsers;
  final int activeTokens;

  DashboardStats({
    required this.totalDoctors,
    required this.activeDoctors,
    required this.totalPatients,
    required this.totalUsers,
    required this.activeTokens,
  });

  factory DashboardStats.fromJson(Map<String, dynamic> json) {
    return DashboardStats(
      totalDoctors: json['totalDoctors'] as int,
      activeDoctors: json['activeDoctors'] as int,
      totalPatients: json['totalPatients'] as int,
      totalUsers: json['totalUsers'] as int,
      activeTokens: json['activeTokens'] as int,
    );
  }
}

class DeletedPatientMatch {
  final int userId;
  final int patientProfileId;
  final String? deletedAt;
  final String matchedBy;
  final String? maskedPhone;
  final String? maskedEmail;

  DeletedPatientMatch({
    required this.userId,
    required this.patientProfileId,
    required this.deletedAt,
    required this.matchedBy,
    this.maskedPhone,
    this.maskedEmail,
  });

  factory DeletedPatientMatch.fromJson(Map<String, dynamic> json) {
    return DeletedPatientMatch(
      userId: json['userId'] as int,
      patientProfileId: json['patientProfileId'] as int,
      deletedAt: json['deletedAt'] as String?,
      matchedBy: json['matchedBy'] as String,
      maskedPhone: json['maskedPhone'] as String?,
      maskedEmail: json['maskedEmail'] as String?,
    );
  }
}

class FailedWebhookEvent {
  final int id;
  final String eventId;
  final String eventType;
  final bool processed;
  final String? processedAt;
  final String? failureReason;
  final int retryCount;
  final String? lastRetryAt;
  final int? retriedByAdminUserId;
  final String createdAt;

  FailedWebhookEvent({
    required this.id,
    required this.eventId,
    required this.eventType,
    required this.processed,
    this.processedAt,
    this.failureReason,
    required this.retryCount,
    this.lastRetryAt,
    this.retriedByAdminUserId,
    required this.createdAt,
  });

  factory FailedWebhookEvent.fromJson(Map<String, dynamic> json) {
    return FailedWebhookEvent(
      id: (json['id'] as num).toInt(),
      eventId: json['eventId']?.toString() ?? '',
      eventType: json['eventType']?.toString() ?? '',
      processed: json['processed'] == true,
      processedAt: json['processedAt']?.toString(),
      failureReason: json['failureReason']?.toString(),
      retryCount: (json['retryCount'] as num?)?.toInt() ?? 0,
      lastRetryAt: json['lastRetryAt']?.toString(),
      retriedByAdminUserId: (json['retriedByAdminUserId'] as num?)?.toInt(),
      createdAt: json['createdAt']?.toString() ?? '',
    );
  }
}

class AdminDoctorActivityDailyPoint {
  final String date;
  final int count;

  const AdminDoctorActivityDailyPoint({
    required this.date,
    required this.count,
  });

  factory AdminDoctorActivityDailyPoint.fromJson(Map<String, dynamic> json) {
    return AdminDoctorActivityDailyPoint(
      date: json['date']?.toString() ?? '',
      count: (json['count'] as num?)?.toInt() ?? 0,
    );
  }
}

class AdminDoctorActivityRow {
  final int doctorId;
  final String doctorName;
  final String? email;
  final int? clinicId;
  final String? clinicName;
  final int appointmentsBooked;
  final int appointmentsCompleted;
  final int appointmentsCancelled;
  final double cancellationRate;
  final int videoAppointments;
  final int activePatients;
  final int patientsCreated;
  final int documentsUploaded;
  final int treatmentPlans;
  final int remoteTasks;
  final int consultationNotes;
  final int patientForms;
  final int aiRequests;
  final int aiDraftNotes;
  final String? lastActiveAt;
  final String? earlyPartnerContractNumber;
  final bool smsRemindersAllowed;
  final int smsSentCount;
  final int smsOwedMinor;
  final String smsCurrency;
  final int smsPricePerUnitMinor;
  final String? dateJoinedAt;
  final int trialPeriodMonths;
  final int monthlyChargeUsd;
  final int monthsAfterTrial;
  final int totalDebtUsd;

  AdminDoctorActivityRow({
    required this.doctorId,
    required this.doctorName,
    this.email,
    this.clinicId,
    this.clinicName,
    required this.appointmentsBooked,
    required this.appointmentsCompleted,
    required this.appointmentsCancelled,
    required this.cancellationRate,
    required this.videoAppointments,
    required this.activePatients,
    required this.patientsCreated,
    required this.documentsUploaded,
    required this.treatmentPlans,
    required this.remoteTasks,
    required this.consultationNotes,
    required this.patientForms,
    required this.aiRequests,
    required this.aiDraftNotes,
    this.lastActiveAt,
    this.earlyPartnerContractNumber,
    this.smsRemindersAllowed = false,
    this.smsSentCount = 0,
    this.smsOwedMinor = 0,
    this.smsCurrency = 'UZS',
    this.smsPricePerUnitMinor = 500,
    this.dateJoinedAt,
    this.trialPeriodMonths = 6,
    this.monthlyChargeUsd = 30,
    this.monthsAfterTrial = 0,
    this.totalDebtUsd = 0,
  });

  factory AdminDoctorActivityRow.fromJson(Map<String, dynamic> json) {
    return AdminDoctorActivityRow(
      doctorId: (json['doctorId'] as num).toInt(),
      doctorName: json['doctorName']?.toString() ?? '',
      email: json['email'] as String?,
      clinicId: (json['clinicId'] as num?)?.toInt(),
      clinicName: json['clinicName'] as String?,
      appointmentsBooked: (json['appointmentsBooked'] as num?)?.toInt() ?? 0,
      appointmentsCompleted: (json['appointmentsCompleted'] as num?)?.toInt() ?? 0,
      appointmentsCancelled: (json['appointmentsCancelled'] as num?)?.toInt() ?? 0,
      cancellationRate: (json['cancellationRate'] as num?)?.toDouble() ?? 0,
      videoAppointments: (json['videoAppointments'] as num?)?.toInt() ?? 0,
      activePatients: (json['activePatients'] as num?)?.toInt() ?? 0,
      patientsCreated: (json['patientsCreated'] as num?)?.toInt() ?? 0,
      documentsUploaded: (json['documentsUploaded'] as num?)?.toInt() ?? 0,
      treatmentPlans: (json['treatmentPlans'] as num?)?.toInt() ?? 0,
      remoteTasks: (json['remoteTasks'] as num?)?.toInt() ?? 0,
      consultationNotes: (json['consultationNotes'] as num?)?.toInt() ?? 0,
      patientForms: (json['patientForms'] as num?)?.toInt() ?? 0,
      aiRequests: (json['aiRequests'] as num?)?.toInt() ?? 0,
      aiDraftNotes: (json['aiDraftNotes'] as num?)?.toInt() ?? 0,
      lastActiveAt: json['lastActiveAt'] as String?,
      earlyPartnerContractNumber: json['earlyPartnerContractNumber'] as String?,
      smsRemindersAllowed: json['smsRemindersAllowed'] == true,
      smsSentCount: (json['smsSentCount'] as num?)?.toInt() ?? 0,
      smsOwedMinor: (json['smsOwedMinor'] as num?)?.toInt() ?? 0,
      smsCurrency: json['smsCurrency']?.toString() ?? 'UZS',
      smsPricePerUnitMinor: (json['smsPricePerUnitMinor'] as num?)?.toInt() ?? 500,
      dateJoinedAt: json['dateJoinedAt'] as String?,
      trialPeriodMonths: (json['trialPeriodMonths'] as num?)?.toInt() ?? 6,
      monthlyChargeUsd: (json['monthlyChargeUsd'] as num?)?.toInt() ?? 30,
      monthsAfterTrial: (json['monthsAfterTrial'] as num?)?.toInt() ?? 0,
      totalDebtUsd: (json['totalDebtUsd'] as num?)?.toInt() ?? 0,
    );
  }
}

class AdminDoctorActivityDetail {
  final AdminDoctorActivityRow row;
  final Map<String, List<AdminDoctorActivityDailyPoint>> dailySeries;

  AdminDoctorActivityDetail({
    required this.row,
    required this.dailySeries,
  });

  factory AdminDoctorActivityDetail.fromJson(Map<String, dynamic> json) {
    final seriesRaw = json['dailySeries'];
    final series = <String, List<AdminDoctorActivityDailyPoint>>{};
    if (seriesRaw is Map) {
      for (final e in seriesRaw.entries) {
        final key = e.key.toString();
        final list = e.value;
        if (list is List) {
          series[key] = list
              .map((p) => AdminDoctorActivityDailyPoint.fromJson(Map<String, dynamic>.from(p as Map)))
              .toList(growable: false);
        }
      }
    }
    return AdminDoctorActivityDetail(
      row: AdminDoctorActivityRow.fromJson(Map<String, dynamic>.from(json['row'] as Map)),
      dailySeries: series,
    );
  }
}
