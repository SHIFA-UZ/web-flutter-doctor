// lib/state/profile/profile_models.dart
class ProfileAll {
  final Map<String, dynamic> profile;
  final Map<String, dynamic> contact;
  final Map<String, dynamic> billing;
  final Map<String, dynamic> settings;
  /// Admin-managed subscription block (`{ tier, features[] }`). Empty map when
  /// the backend response does not include it.
  final Map<String, dynamic> subscription;
  ProfileAll(
    this.profile,
    this.contact,
    this.billing,
    this.settings, [
    Map<String, dynamic>? subscription,
  ]) : subscription = subscription ?? const {};
}

/// Lightweight identity from [/api/me/profile] (doctor + clinic staff).
class MeProfile {
  final int id;
  final String role;
  final String firstName;
  final String lastName;
  final String? email;
  final String? phone;
  final String? photoUrl;
  final String? timeZone;

  const MeProfile({
    required this.id,
    required this.role,
    required this.firstName,
    required this.lastName,
    this.email,
    this.phone,
    this.photoUrl,
    this.timeZone,
  });

  factory MeProfile.fromJson(Map<String, dynamic> json) {
    return MeProfile(
      id: (json['id'] as num).toInt(),
      role: json['role'] as String? ?? 'DOCTOR',
      firstName: json['firstName'] as String? ?? '',
      lastName: json['lastName'] as String? ?? '',
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      photoUrl: json['photoUrl'] as String?,
      timeZone: json['timeZone'] as String?,
    );
  }

  bool get isClinicStaff => role.toUpperCase() == 'CLINIC_STAFF';
}
