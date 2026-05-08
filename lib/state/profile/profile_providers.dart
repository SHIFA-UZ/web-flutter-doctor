// lib/state/profile/profile_providers.dart
import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api/api_providers.dart';
import '../../state/auth/auth_controller.dart';
import 'profile_models.dart';

/// Bump this after uploading a new photo so NetworkImage refetches (avoids cache).
final photoCacheBusterProvider = StateProvider<int>((ref) => 0);

final profileAllProvider = FutureProvider<ProfileAll>((ref) async {
  final token = ref.watch(authTokenProvider);
  if (token == null || token.isEmpty) {
    throw StateError('Not authenticated');
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
