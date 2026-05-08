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
