// lib/state/subscription/doctor_subscription_provider.dart
//
// Reactive provider that resolves the authenticated doctor's admin-managed
// subscription tier. Reads `profileAllProvider` so any profile refresh (e.g.
// after admin pushes a tier change and re-login) automatically re-evaluates
// every gated UI surface.

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

/// Resolved tier for the current doctor. Defaults to PREMIUM while the profile
/// is loading so feature gates do not flicker the UI on cold start.
final doctorTierProvider = Provider<DoctorTier>((ref) {
  final allAsync = ref.watch(profileAllProvider);
  return allAsync.maybeWhen(
    data: (all) {
      final raw = all.subscription['tier'];
      if (raw is String && raw.isNotEmpty) return parseDoctorTier(raw);
      // Legacy fallback: tier was sometimes embedded in profile or settings.
      final fromProfile = all.profile['subscriptionTier'];
      if (fromProfile is String) return parseDoctorTier(fromProfile);
      return DoctorTier.premium;
    },
    orElse: () => DoctorTier.premium,
  );
});

/// Convenience helper for widgets: `ref.watch(doctorFeatureProvider(...))` →
/// boolean indicating whether the current doctor can use the feature.
final doctorFeatureProvider = Provider.family<bool, DoctorFeature>((ref, feature) {
  final tier = ref.watch(doctorTierProvider);
  return tierAllows(tier, feature);
});
