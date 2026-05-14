// lib/state/auth/registration_state.dart
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';

/// State holder for registration flow.
class RegistrationData {
  // Required by backend /api/auth/register
  final String? key; // invitation key (non-null at submit)
  final String? firstName; // required
  final String? lastName; // required
  final String? phone; // optional
  final String? email; // required
  final String? password; // required

  // Optional extras to apply later via /api/doctors/me/profile
  final DateTime? dob; // LocalDate (YYYY-MM-DD)
  final String? gender;
  final String? address;
  final String? clinic;
  final String? profession;
  /// IANA time zone (e.g. Europe/Berlin) for practice; set during onboarding.
  final String? timeZone;

  const RegistrationData({
    this.key,
    this.firstName,
    this.lastName,
    this.phone,
    this.email,
    this.password,
    this.dob,
    this.gender,
    this.address,
    this.clinic,
    this.profession,
    this.timeZone,
  });

  RegistrationData copyWith({
    String? key,
    String? firstName,
    String? lastName,
    String? phone,
    String? email,
    String? password,
    DateTime? dob,
    String? gender,
    String? address,
    String? clinic,
    String? profession,
    String? timeZone,
  }) {
    return RegistrationData(
      key: key ?? this.key,
      firstName: firstName ?? this.firstName,
      lastName: lastName ?? this.lastName,
      phone: phone ?? this.phone,
      email: email ?? this.email,
      password: password ?? this.password,
      dob: dob ?? this.dob,
      gender: gender ?? this.gender,
      address: address ?? this.address,
      clinic: clinic ?? this.clinic,
      profession: profession ?? this.profession,
      timeZone: timeZone ?? this.timeZone,
    );
  }
}

/// Controller for registration flow.
class RegistrationController extends StateNotifier<RegistrationData> {
  RegistrationController(this.ref) : super(const RegistrationData());
  final Ref ref;

  static const _prefsKey = 'shifa_verified_invite_key';

  // ----------------------------
  // 1) Verify key with backend
  // ----------------------------
  // POST /api/auth/verify-key { key } -> { valid: Boolean }
  Future<void> verifyKey(String key) async {
    final api = ref.read(apiClientProvider);
    final res = await api.post('/api/auth/verify-key', {'key': key.trim()});
    if (res.statusCode != 200) {
      throw Exception(_errorFrom(res));
    }
    final json = _safeJson(res.body);
    final valid = json['valid'] == true;
    if (!valid) {
      throw Exception('Invalid or already used key');
    }

    // Store in memory and persist locally (survive navigation/refresh)
    final trimmed = key.trim();
    state = state.copyWith(key: trimmed);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_prefsKey, trimmed);
    } catch (_) {
      // non-blocking
    }
  }

  /// Set an already-verified key without calling backend (e.g., restored from storage).
  void setVerifiedKey(String key) {
    state = state.copyWith(key: key.trim());
  }

  /// Try to restore key from SharedPreferences if missing.
  Future<void> restoreKeyIfMissing() async {
    if (state.key != null && state.key!.isNotEmpty) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      final storedKey = prefs.getString(_prefsKey);
      if (storedKey != null && storedKey.isNotEmpty) {
        state = state.copyWith(key: storedKey);
      }
    } catch (_) {
      // ignore
    }
  }

  // ----------------------------
  // Check existing patient (same phone => existing user with patient profile)
  // ----------------------------
  /// POST /api/auth/check-existing-patient { firstName, lastName, email, phone? }
  /// Returns { found: bool, fullName?, photoUrl?, email? }.
  Future<Map<String, dynamic>> checkExistingPatient({
    required String firstName,
    required String lastName,
    required String email,
    String? phone,
  }) async {
    final api = ref.read(apiClientProvider);
    final res = await api.post('/api/auth/check-existing-patient', {
      'firstName': firstName.trim(),
      'lastName': lastName.trim(),
      'email': email.trim(),
      if (phone != null && phone.trim().isNotEmpty) 'phone': phone.trim(),
    });
    if (res.statusCode != 200) {
      return {'found': false};
    }
    final json = _safeJson(res.body);
    return {
      'found': json['found'] == true,
      'fullName': json['fullName']?.toString(),
      'photoUrl': json['photoUrl']?.toString(),
      'email': json['email']?.toString(),
    };
  }

  // ----------------------------
  // 2) Store basic info (CreateAccount)
  // ----------------------------
  void setBasicInfo({
    required String firstName,
    required String lastName,
    required String email,
    String? phone,
    required String password,
  }) {
    state = RegistrationData(
      key: state.key,
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      phone: (phone == null || phone.trim().isEmpty) ? null : phone.trim(),
      email: email.trim(),
      password: password.trim(),
      dob: state.dob,
      gender: state.gender,
      address: state.address,
      clinic: state.clinic,
      profession: state.profession,
      timeZone: state.timeZone,
    );
  }

  // ----------------------------
  // 3) Store optional extras (AccountInformation)
  // ----------------------------
  void setAccountInfo({
    DateTime? dob,
    String? gender,
    String? address,
    String? clinic,
    String? profession,
    String? timeZone,
  }) {
    state = state.copyWith(
      dob: dob,
      gender: gender,
      address: address,
      clinic: clinic,
      profession: profession,
      timeZone: timeZone,
    );
  }

  // ----------------------------
  // 4) Submit registration
  // ----------------------------
  // POST /api/auth/register
  /// Returns the JWT token if backend returns 200/201 with { token }; otherwise throws.
  Future<String?> submitRegistration() async {
    final api = ref.read(apiClientProvider);

    // Guards for required fields
    final missing = <String>[];
    if (state.key == null || state.key!.trim().isEmpty)
      missing.add('verification key');
    if (state.firstName == null || state.firstName!.trim().isEmpty)
      missing.add('first name');
    if (state.lastName == null || state.lastName!.trim().isEmpty)
      missing.add('last name');
    if (state.email == null || state.email!.trim().isEmpty)
      missing.add('email');
    if (state.password == null || state.password!.trim().isEmpty)
      missing.add('password');
    if (missing.isNotEmpty) {
      throw Exception('Please provide: ${missing.join(', ')}.');
    }

    final payload = {
      'firstName': state.firstName!.trim(),
      'lastName': state.lastName!.trim(),
      'email': state.email!.trim(),
      'password': state.password!.trim(),
      'key': state.key!.trim(),
      if (state.phone != null && state.phone!.trim().isNotEmpty)
        'phone': state.phone!.trim(),
      if (state.timeZone != null && state.timeZone!.trim().isNotEmpty)
        'timeZone': state.timeZone!.trim(),
    };

    final res = await api.post('/api/auth/register', payload);

    if (res.statusCode == 200 || res.statusCode == 201) {
      // Hygiene: clear the persisted invite key so it won't linger.
      try {
        final prefs = await SharedPreferences.getInstance();
        await prefs.remove(_prefsKey);
      } catch (_) {
        // ignore
      }
      final json = _safeJson(res.body);
      final token = json['token'] as String?;
      return (token != null && token.isNotEmpty) ? token : null;
    }

    // Bubble up server error message if present
    throw Exception(_errorFrom(res));
  }

  // -----------------------------------------------------------
  // 5) After first login (JWT set), apply optional profile data
  // -----------------------------------------------------------
  // PATCH /api/doctors/me/profile with only provided fields.
  Future<void> applyOptionalProfileExtrasAfterLogin() async {
    final api = ref.read(apiClientProvider);

    final payload = <String, dynamic>{};
    if (state.dob != null) {
      final d = state.dob!;
      payload['dob'] =
          '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
    }
    if (state.gender != null && state.gender!.isNotEmpty)
      payload['gender'] = state.gender;
    if (state.address != null && state.address!.isNotEmpty)
      payload['address'] = state.address;
    if (state.clinic != null && state.clinic!.isNotEmpty)
      payload['clinic'] = state.clinic;
    if (state.profession != null && state.profession!.isNotEmpty)
      payload['profession'] = state.profession;
    if (state.timeZone != null && state.timeZone!.trim().isNotEmpty)
      payload['timeZone'] = state.timeZone!.trim();

    if (payload.isEmpty) return; // nothing to apply

    final res = await api.patch('/api/doctors/me/profile', payload);
    if (res.statusCode != 200) {
      throw Exception(_errorFrom(res));
    }

    // Clear extras so they won't be re-applied again.
    state = state.copyWith(
      dob: null,
      gender: null,
      address: null,
      clinic: null,
      profession: null,
      timeZone: null,
    );
  }

  // ----------------------------
  // Helpers
  // ----------------------------
  Map<String, dynamic> _safeJson(String body) {
    try {
      final parsed = jsonDecode(body);
      return parsed is Map<String, dynamic> ? parsed : <String, dynamic>{};
    } catch (_) {
      return <String, dynamic>{};
    }
  }

  String _errorFrom(http.Response res) {
    try {
      final j = jsonDecode(res.body);
      if (j is Map && j['message'] is String) return j['message'];
    } catch (_) {}
    return 'Request failed (${res.statusCode})';
  }
}

// Riverpod provider
final registrationProvider =
    StateNotifierProvider<RegistrationController, RegistrationData>(
      (ref) => RegistrationController(ref),
    );
