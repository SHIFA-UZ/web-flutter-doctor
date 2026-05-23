// lib/state/profile/profile_providers.dart
import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api/api_providers.dart';
import '../../state/auth/auth_controller.dart';
import '../../state/auth/doctor_jwt_role_provider.dart';
import 'profile_models.dart';

/// Bump this after uploading a new photo so NetworkImage refetches (avoids cache).
final photoCacheBusterProvider = StateProvider<int>((ref) => 0);

final profileAllProvider = FutureProvider<ProfileAll>((ref) async {
  final token = ref.watch(authTokenProvider);
  if (token == null || token.isEmpty) {
    throw StateError('Not authenticated');
  }
  if (ref.watch(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff) {
    throw StateError('Doctor profile unavailable for clinic staff');
  }
  final api = ref.read(apiClientProvider);
  final res = await api.get('/api/doctors/me');
  if (res.statusCode != 200) {
    throw Exception('Profile load failed: ${res.body}');
  }
  final m = jsonDecode(res.body) as Map<String, dynamic>;
  return ProfileAll(
    m['profile'] as Map<String, dynamic>,
    m['contact'] as Map<String, dynamic>,
    m['billing'] as Map<String, dynamic>,
    m['settings'] as Map<String, dynamic>,
    (m['subscription'] as Map<String, dynamic>?) ?? const <String, dynamic>{},
  );
});

/// Doctor + clinic staff identity (always available while authenticated).
final meProfileProvider = FutureProvider<MeProfile>((ref) async {
  final token = ref.watch(authTokenProvider);
  if (token == null || token.isEmpty) {
    throw StateError('Not authenticated');
  }
  final api = ref.read(apiClientProvider);
  final res = await api.get('/api/me/profile');
  if (res.statusCode != 200) {
    throw Exception('Failed to load profile: ${res.statusCode} ${res.body}');
  }
  final m = jsonDecode(res.body) as Map<String, dynamic>;
  return MeProfile.fromJson(m);
});
