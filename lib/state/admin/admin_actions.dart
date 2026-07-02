// lib/state/admin/admin_actions.dart
import 'dart:convert';
import 'package:flutter/foundation.dart';
import '../../core/api/api_client.dart';
import '../../features/admin/domain/admin_models.dart';

class AdminActions {
  final ApiClient apiClient;

  AdminActions({required this.apiClient});

  // ==================== DASHBOARD ====================

  Future<DashboardStats> getDashboardStats() async {
    final response = await apiClient.get('/api/admin/dashboard/stats');

    if (response.statusCode != 200) {
      throw Exception('Failed to load dashboard stats: ${response.body}');
    }

    return DashboardStats.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<UserManagementStats> getUserManagementStats() async {
    final response = await apiClient.get('/api/admin/users/stats');

    if (response.statusCode != 200) {
      throw Exception('Failed to load user management stats: ${response.body}');
    }

    return UserManagementStats.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ==================== TOKEN MANAGEMENT ====================

  Future<AdminToken> generateToken({
    int? expiresInDays,
    String purpose = 'DOCTOR_ONBOARDING',
    String? notes,
    bool sendEmail = false,
    String? emailTo,
  }) async {
    final response = await apiClient.post('/api/admin/tokens/generate', {
      'expiresInDays': expiresInDays,
      'purpose': purpose,
      'notes': notes,
      'sendEmail': sendEmail,
      'emailTo': emailTo,
    });

    if (response.statusCode != 200) {
      throw Exception('Failed to generate token: ${response.body}');
    }

    return AdminToken.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<Map<String, dynamic>> listTokens({
    bool? consumed,
    String? purpose,
    int page = 0,
    int size = 20,
  }) async {
    final queryParams = <String, String>{
      'page': page.toString(),
      'size': size.toString(),
    };
    if (consumed != null) queryParams['consumed'] = consumed.toString();
    if (purpose != null) queryParams['purpose'] = purpose;

    final response = await apiClient.get('/api/admin/tokens', params: queryParams);

    debugPrint('AdminActions.listTokens: Response status: ${response.statusCode}');
    final bodyPreview = response.body.length > 500 
        ? '${response.body.substring(0, 500)}...' 
        : response.body;
    debugPrint('AdminActions.listTokens: Response body: $bodyPreview');

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw Exception('Authentication failed. Please log in again.');
    }
    
    if (response.statusCode != 200) {
      throw Exception('Failed to list tokens (${response.statusCode}): ${response.body}');
    }

    try {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      debugPrint('AdminActions.listTokens: Parsed JSON keys: ${json.keys}');
      
      if (!json.containsKey('content')) {
        throw Exception('Response missing "content" field. Keys: ${json.keys}');
      }
      
      final content = json['content'];
      if (content is! List) {
        throw Exception('Response "content" is not a List. Type: ${content.runtimeType}');
      }
      
      debugPrint('AdminActions.listTokens: Content is List with ${content.length} items');
      
      final tokens = <AdminToken>[];
      for (var i = 0; i < content.length; i++) {
        try {
          final item = content[i];
          if (item is! Map<String, dynamic>) {
            debugPrint('AdminActions.listTokens: Item $i is not a Map: ${item.runtimeType}');
            continue;
          }
          tokens.add(AdminToken.fromJson(item));
        } catch (ex, stackTrace) {
          debugPrint('AdminActions.listTokens: Error parsing token at index $i: $ex');
          debugPrint('AdminActions.listTokens: Token data: ${content[i]}');
          debugPrint('AdminActions.listTokens: Stack trace: $stackTrace');
          // Continue parsing other tokens instead of failing completely
        }
      }
      
      debugPrint('AdminActions.listTokens: Successfully parsed ${tokens.length} tokens');
      
      return {
        'content': tokens,
        'totalElements': json['totalElements'] as int? ?? 0,
        'totalPages': json['totalPages'] as int? ?? 0,
        'number': json['number'] as int? ?? 0,
      };
    } catch (e, stackTrace) {
      debugPrint('AdminActions.listTokens: Error parsing response: $e');
      debugPrint('AdminActions.listTokens: Stack trace: $stackTrace');
      debugPrint('AdminActions.listTokens: Full response body: ${response.body}');
      throw Exception('Failed to parse tokens response: $e');
    }
  }

  Future<AdminToken> revokeToken(int tokenId) async {
    final response = await apiClient.post('/api/admin/tokens/$tokenId/revoke', {});

    if (response.statusCode != 200) {
      throw Exception('Failed to revoke token: ${response.body}');
    }

    return AdminToken.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<AdminToken> regenerateToken(int tokenId, {int? expiresInDays}) async {
    final response = await apiClient.post('/api/admin/tokens/$tokenId/regenerate', {
      'expiresInDays': expiresInDays,
      'purpose': 'DOCTOR_ONBOARDING',
    });

    if (response.statusCode != 200) {
      throw Exception('Failed to regenerate token: ${response.body}');
    }

    return AdminToken.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ==================== USER MANAGEMENT ====================

  Future<Map<String, dynamic>> listUsers({
    String? role,
    bool? enabled,
    String? search,
    bool? deviceRegistered,
    int page = 0,
    int size = 20,
  }) async {
    final queryParams = <String, String>{
      'page': page.toString(),
      'size': size.toString(),
    };
    if (role != null) queryParams['role'] = role;
    if (enabled != null) queryParams['enabled'] = enabled.toString();
    if (search != null && search.trim().isNotEmpty) queryParams['search'] = search.trim();
    if (deviceRegistered != null) queryParams['deviceRegistered'] = deviceRegistered.toString();

    final response = await apiClient.get('/api/admin/users', params: queryParams);

    if (response.statusCode != 200) {
      throw Exception('Failed to list users: ${response.body}');
    }

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return {
      'content': (json['content'] as List)
          .map((e) => AdminUser.fromJson(e as Map<String, dynamic>))
          .toList(),
      'totalElements': json['totalElements'] as int,
      'totalPages': json['totalPages'] as int,
      'number': json['number'] as int,
    };
  }

  Future<AdminUser> getUser(int userId) async {
    final response = await apiClient.get('/api/admin/users/$userId');

    if (response.statusCode != 200) {
      throw Exception('Failed to get user: ${response.body}');
    }

    return AdminUser.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<AdminUser> setUserEnabled(int userId, bool enabled) async {
    final response = await apiClient.post('/api/admin/users/$userId/enable', {'enabled': enabled});

    if (response.statusCode != 200) {
      throw Exception('Failed to update user: ${response.body}');
    }

    return AdminUser.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<String> resetUserPassword(int userId) async {
    final response = await apiClient.post('/api/admin/users/$userId/reset-password', {});

    if (response.statusCode != 200) {
      throw Exception('Failed to reset password: ${response.body}');
    }

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return json['temporaryPassword'] as String;
  }

  Future<void> forceLogout(int userId) async {
    final response = await apiClient.post('/api/admin/users/$userId/force-logout', {});

    if (response.statusCode != 200) {
      throw Exception('Failed to force logout: ${response.body}');
    }
  }

  Future<AdminUser> unlockUser(int userId) async {
    final response = await apiClient.post('/api/admin/users/$userId/unlock', {});

    if (response.statusCode != 200) {
      throw Exception('Failed to unlock user: ${response.body}');
    }

    return AdminUser.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Set the admin-managed subscription tier (BASIC | PRO | PREMIUM) for a user.
  /// Backend rejects BASIC for PATIENT users and forces a logout so the
  /// new tier is applied as soon as the user signs back in.
  Future<AdminUser> setUserSubscriptionTier(int userId, String tier) async {
    final response = await apiClient.patch(
      '/api/admin/users/$userId/subscription-tier',
      {'tier': tier.toUpperCase()},
    );

    if (response.statusCode != 200) {
      final body = response.body;
      try {
        final json = jsonDecode(body) as Map<String, dynamic>?;
        final msg = json?['message'] ?? json?['error'] ?? body;
        throw Exception(msg);
      } catch (e) {
        if (e is Exception) rethrow;
        throw Exception('Failed to update subscription tier: $body');
      }
    }

    return AdminUser.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Create a new admin user only (role ADMIN with admin profile).
  Future<AdminUser> createAdminUser({
    required String email,
    required String password,
    required String firstName,
    required String lastName,
    String adminLevel = 'ADMIN',
  }) async {
    final response = await apiClient.post('/api/admin/users/create-admin', {
      'email': email,
      'password': password,
      'firstName': firstName,
      'lastName': lastName,
      'adminLevel': adminLevel,
    });

    if (response.statusCode != 200) {
      final body = response.body;
      try {
        final json = jsonDecode(body) as Map<String, dynamic>?;
        final msg = json?['message'] ?? json?['error'] ?? body;
        throw Exception(msg);
      } catch (e) {
        if (e is Exception) rethrow;
        throw Exception('Failed to create admin user: $body');
      }
    }

    return AdminUser.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Permanently delete a user and all related data so the phone/email can be used for a new account.
  /// Fails for ADMIN users.
  Future<void> deleteUser(int userId) async {
    final response = await apiClient.delete('/api/admin/users/$userId');

    if (response.statusCode == 403) {
      throw Exception('You do not have permission to delete users.');
    }
    if (response.statusCode == 404) {
      throw Exception('User not found.');
    }
    if (response.statusCode == 400) {
      final body = response.body;
      try {
        final json = jsonDecode(body) as Map<String, dynamic>?;
        final msg = json?['message'] ?? json?['error'] ?? body;
        throw Exception(msg);
      } catch (e) {
        if (e is Exception) rethrow;
        throw Exception('Cannot delete user: $body');
      }
    }
    if (response.statusCode != 200) {
      throw Exception('Failed to delete user: ${response.body}');
    }
  }

  /// Admin-only: permanently delete all appointments and availability for a doctor.
  /// Does NOT change credentials, profile, or patient data.
  Future<void> resetDoctorCalendar(int doctorId) async {
    final response = await apiClient.post('/api/admin/doctors/$doctorId/reset-calendar', {});

    if (response.statusCode == 404) {
      throw Exception('Doctor not found');
    }
    if (response.statusCode != 200) {
      throw Exception('Failed to reset doctor calendar: ${response.body}');
    }
  }

  // ==================== DELETED PATIENT EXPORT ====================

  Future<List<DeletedPatientMatch>> searchDeletedPatients({
    String? phone,
    String? email,
    int? userId,
  }) async {
    final response = await apiClient.post(
      '/api/admin/patients/deleted/search',
      {
        'phone': phone,
        'email': email,
        'userId': userId,
      },
    );

    if (response.statusCode != 200) {
      throw Exception('Failed to search deleted patients: ${response.body}');
    }

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final matchesJson = (json['matches'] as List?) ?? const [];
    return matchesJson
        .map((e) => DeletedPatientMatch.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<Map<String, dynamic>> exportDeletedPatient(int patientProfileId) async {
    final response = await apiClient.get('/api/admin/patients/deleted/$patientProfileId/export');
    if (response.statusCode != 200) {
      throw Exception('Failed to export deleted patient: ${response.body}');
    }
    return jsonDecode(response.body) as Map<String, dynamic>;
  }

  Future<Uint8List> exportDeletedPatientPdfBytes(int patientProfileId) async {
    final response = await apiClient.get('/api/admin/patients/deleted/$patientProfileId/export/pdf');
    if (response.statusCode != 200) {
      throw Exception('Failed to export deleted patient PDF: ${response.body}');
    }
    return response.bodyBytes;
  }

  // ==================== CLINICS ====================

  Future<Map<String, dynamic>> listClinics({int page = 0, int size = 50}) async {
    final queryParams = <String, String>{
      'page': page.toString(),
      'size': size.toString(),
      'sort': 'name',
    };
    final response = await apiClient.get('/api/admin/clinics', params: queryParams);
    if (response.statusCode != 200) {
      throw Exception('Failed to list clinics: ${response.body}');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final contentRaw = json['content'] as List?;
    final content = contentRaw ?? const [];
    return {
      'content': content
          .map((e) => AdminClinicSummary.fromJson(e as Map<String, dynamic>))
          .toList(),
      'totalElements': json['totalElements'] as int? ?? 0,
      'totalPages': json['totalPages'] as int? ?? 0,
      'number': json['number'] as int? ?? 0,
    };
  }

  Future<AdminClinicDetail> getClinic(int clinicId) async {
    final response = await apiClient.get('/api/admin/clinics/$clinicId');
    if (response.statusCode != 200) {
      throw Exception('Failed to load clinic: ${response.body}');
    }
    return AdminClinicDetail.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<AdminClinicDetail> createClinic({
    required String name,
    String? phone,
    String? email,
    String? address,
    String? timeZone,
  }) async {
    final response = await apiClient.post('/api/admin/clinics', {
      'name': name,
      if (phone != null && phone.isNotEmpty) 'phone': phone,
      if (email != null && email.isNotEmpty) 'email': email,
      if (address != null && address.isNotEmpty) 'address': address,
      if (timeZone != null && timeZone.trim().isNotEmpty) 'timeZone': timeZone.trim(),
    });
    if (response.statusCode != 200) {
      throw Exception('Failed to create clinic: ${response.body}');
    }
    return AdminClinicDetail.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<AdminClinicDetail> updateClinic({
    required int clinicId,
    required String name,
    String? phone,
    String? email,
    String? address,
    String? timeZone,
  }) async {
    final body = <String, dynamic>{
      'name': name,
      if (phone != null && phone.isNotEmpty) 'phone': phone,
      if (email != null && email.isNotEmpty) 'email': email,
      if (address != null && address.isNotEmpty) 'address': address,
      if (timeZone != null && timeZone.trim().isNotEmpty) 'timeZone': timeZone.trim(),
    };
    final response = await apiClient.put('/api/admin/clinics/$clinicId', body);
    if (response.statusCode != 200) {
      throw Exception('Failed to update clinic: ${response.body}');
    }
    return AdminClinicDetail.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<AdminClinicDetail> assignDoctorToClinic({
    required int clinicId,
    required int doctorProfileId,
  }) async {
    final response = await apiClient.post('/api/admin/clinics/$clinicId/doctors/$doctorProfileId', {});
    if (response.statusCode != 200) {
      throw Exception('Failed to assign doctor: ${response.body}');
    }
    return AdminClinicDetail.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<AdminClinicDetail> updateClinicMemberRole({
    required int clinicId,
    required int doctorProfileId,
    required String membershipRole,
  }) async {
    final response = await apiClient.patch(
      '/api/admin/clinics/$clinicId/doctors/$doctorProfileId/role',
      {'membershipRole': membershipRole.toUpperCase()},
    );
    if (response.statusCode != 200) {
      final body = response.body;
      try {
        final json = jsonDecode(body) as Map<String, dynamic>?;
        final msg = json?['message'] ?? json?['error'] ?? body;
        throw Exception(msg);
      } catch (e) {
        if (e is Exception) rethrow;
        throw Exception('Failed to update member role: $body');
      }
    }
    return AdminClinicDetail.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<AdminClinicDetail> removeDoctorFromClinic({
    required int clinicId,
    required int doctorProfileId,
  }) async {
    final response = await apiClient.delete('/api/admin/clinics/$clinicId/doctors/$doctorProfileId');
    if (response.statusCode != 200) {
      throw Exception('Failed to remove doctor: ${response.body}');
    }
    return AdminClinicDetail.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  // ==================== DOCTOR ACTIVITY ====================

  Future<Map<String, dynamic>> listDoctorActivity({
    String? fromIso,
    String? toIso,
    String? search,
    String sort = 'appointments',
    String dir = 'desc',
    int page = 0,
    int size = 25,
  }) async {
    final queryParams = <String, String>{
      'page': page.toString(),
      'size': size.toString(),
      'sort': sort,
      'dir': dir,
    };
    if (fromIso != null && fromIso.isNotEmpty) queryParams['from'] = fromIso;
    if (toIso != null && toIso.isNotEmpty) queryParams['to'] = toIso;
    if (search != null && search.trim().isNotEmpty) queryParams['search'] = search.trim();

    final response = await apiClient.get('/api/admin/doctors/activity', params: queryParams);

    if (response.statusCode != 200) {
      throw Exception('Failed to load doctor activity: ${response.body}');
    }

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final contentRaw = json['content'];
    final rawList = contentRaw is List ? contentRaw : const [];
    final rows = rawList.map((e) => AdminDoctorActivityRow.fromJson(e as Map<String, dynamic>)).toList(growable: false);
    return {
      'content': rows,
      'totalElements': (json['totalElements'] as num?)?.toInt() ?? rows.length,
      'totalPages': (json['totalPages'] as num?)?.toInt() ?? 1,
      'number': (json['number'] as num?)?.toInt() ?? 0,
    };
  }

  Future<AdminDoctorActivityDetail> getDoctorActivityDetail({
    required int doctorId,
    String? fromIso,
    String? toIso,
  }) async {
    final queryParams = <String, String>{};
    if (fromIso != null && fromIso.isNotEmpty) queryParams['from'] = fromIso;
    if (toIso != null && toIso.isNotEmpty) queryParams['to'] = toIso;

    final response = await apiClient.get('/api/admin/doctors/$doctorId/activity', params: queryParams);

    if (response.statusCode != 200) {
      throw Exception('Failed to load doctor activity detail: ${response.body}');
    }

    return AdminDoctorActivityDetail.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<void> setDoctorSmsRemindersAllowed({
    required int doctorId,
    required bool allowed,
  }) async {
    final response = await apiClient.patch(
      '/api/admin/doctors/$doctorId/sms-reminders-allowed',
      {'allowed': allowed},
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Failed to update SMS permission: ${response.body}');
    }
  }

  Future<void> updateDoctorSubscriptionBilling({
    required int doctorId,
    int? trialPeriodMonths,
    int? monthlyChargeUsd,
  }) async {
    final body = <String, dynamic>{};
    if (trialPeriodMonths != null) body['trialPeriodMonths'] = trialPeriodMonths;
    if (monthlyChargeUsd != null) body['monthlyChargeUsd'] = monthlyChargeUsd;
    if (body.isEmpty) return;

    final response = await apiClient.patch(
      '/api/admin/doctors/$doctorId/subscription-billing',
      body,
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Failed to update subscription billing: ${response.body}');
    }
  }

  /// Allocate or refresh early-partner contract (sequential number for new doctors).
  Future<dynamic> issueEarlyPartnerContract(int doctorId) async {
    final response = await apiClient.post(
      '/api/admin/doctors/$doctorId/early-partner-contract/issue',
      {},
    );
    if (response.statusCode != 200) {
      throw Exception('Failed to issue contract: ${response.body}');
    }
    return response;
  }

  // ==================== AUDIT LOGS ====================

  Future<Map<String, dynamic>> getAuditLogs({
    int? adminUserId,
    String? entityType,
    int? entityId,
    String? actionType,
    int page = 0,
    int size = 50,
  }) async {
    final queryParams = <String, String>{
      'page': page.toString(),
      'size': size.toString(),
    };
    if (adminUserId != null) queryParams['adminUserId'] = adminUserId.toString();
    if (entityType != null) queryParams['entityType'] = entityType;
    if (entityId != null) queryParams['entityId'] = entityId.toString();
    if (actionType != null) queryParams['actionType'] = actionType;

    final response = await apiClient.get('/api/admin/audit-logs', params: queryParams);

    if (response.statusCode != 200) {
      throw Exception('Failed to get audit logs: ${response.body}');
    }

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return {
      'content': (json['content'] as List)
          .map((e) => AuditLogEntry.fromJson(e as Map<String, dynamic>))
          .toList(),
      'totalElements': json['totalElements'] as int,
      'totalPages': json['totalPages'] as int,
      'number': json['number'] as int,
    };
  }

  Future<Map<String, dynamic>> getActivityLogs({
    int? userId,
    String? activityType,
    int page = 0,
    int size = 50,
  }) async {
    final queryParams = <String, String>{
      'page': page.toString(),
      'size': size.toString(),
    };
    if (userId != null) queryParams['userId'] = userId.toString();
    if (activityType != null) queryParams['activityType'] = activityType;

    final response = await apiClient.get('/api/admin/activity-logs', params: queryParams);

    if (response.statusCode != 200) {
      throw Exception('Failed to get activity logs: ${response.body}');
    }

    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return {
      'content': (json['content'] as List)
          .map((e) => ActivityLogEntry.fromJson(e as Map<String, dynamic>))
          .toList(),
      'totalElements': json['totalElements'] as int,
      'totalPages': json['totalPages'] as int,
      'number': json['number'] as int,
    };
  }

  // ==================== SYSTEM CONFIG ====================

  Future<Map<String, String>> getSystemConfig() async {
    final response = await apiClient.get('/api/admin/config');

    if (response.statusCode != 200) {
      throw Exception('Failed to get config: ${response.body}');
    }

    return Map<String, String>.from(jsonDecode(response.body) as Map);
  }

  Future<Map<String, String>> updateConfig(String key, String value) async {
    final response = await apiClient.put('/api/admin/config/$key', {'value': value});

    if (response.statusCode != 200) {
      throw Exception('Failed to update config: ${response.body}');
    }

    return Map<String, String>.from(jsonDecode(response.body) as Map);
  }

  Future<List<FailedWebhookEvent>> listFailedStripeWebhooks() async {
    final response = await apiClient.get('/api/admin/payments/webhooks/stripe/failed');
    if (response.statusCode != 200) {
      throw Exception('Failed to load failed Stripe webhooks: ${response.body}');
    }
    final data = jsonDecode(response.body) as List<dynamic>;
    return data
        .map((e) => FailedWebhookEvent.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<bool> retryFailedStripeWebhook(int paymentEventId) async {
    final response = await apiClient.post(
      '/api/admin/payments/webhooks/stripe/failed/$paymentEventId/retry',
      {},
    );
    if (response.statusCode != 200) {
      throw Exception('Failed to retry Stripe webhook: ${response.body}');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return json['success'] == true;
  }
}
