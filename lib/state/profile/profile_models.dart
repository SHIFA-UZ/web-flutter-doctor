// lib/state/profile/profile_models.dart
class ProfileAll {
  final Map<String, dynamic> profile;
  final Map<String, dynamic> contact;
  final Map<String, dynamic> billing;
  final Map<String, dynamic> settings;
  ProfileAll(this.profile, this.contact, this.billing, this.settings);
}
