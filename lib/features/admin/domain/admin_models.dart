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
  /// ADMIN role users always behave as PREMIUM and the value is informational.
  /// PATIENT users are restricted to PRO or PREMIUM by the backend.
  final String subscriptionTier;

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
