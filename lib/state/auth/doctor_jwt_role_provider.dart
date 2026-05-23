import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';

/// Matches JWT `role` claim from [/api/auth/login] and [/api/auth/register-*].
enum DoctorAppJwtRole {
  doctor,
  clinicStaff,
}

String? jwtRoleClaimFromAccessToken(String? token) {
  if (token == null || token.isEmpty) return null;
  try {
    final parts = token.split('.');
    if (parts.length < 2) return null;
    final payload = utf8.decode(base64Url.decode(base64Url.normalize(parts[1])));
    final json = jsonDecode(payload) as Map<String, dynamic>;
    return json['role']?.toString();
  } catch (_) {
    return null;
  }
}

/// Resolved from current doctor-app JWT (`authTokenProvider`).
final doctorAppJwtRoleProvider = Provider<DoctorAppJwtRole>((ref) {
  final token = ref.watch(authTokenProvider);
  final raw = jwtRoleClaimFromAccessToken(token)?.toUpperCase();
  if (raw == 'CLINIC_STAFF') return DoctorAppJwtRole.clinicStaff;
  return DoctorAppJwtRole.doctor;
});

/// True when JWT claims clinic staff principal (narrow shell + clinics).
bool isDoctorAppClinicStaffToken(String? token) {
  return jwtRoleClaimFromAccessToken(token)?.toUpperCase() == 'CLINIC_STAFF';
}
