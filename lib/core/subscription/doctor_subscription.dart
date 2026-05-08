// lib/core/subscription/doctor_subscription.dart
//
// Admin-managed subscription tier for the doctor app. The tier is set from the
// admin panel and returned by the backend in the doctor profile response under
// `subscription.tier`. Frontend mirrors backend gating; backend remains the
// authoritative source.

/// Subscription tier hierarchy: BASIC < PRO < PREMIUM.
enum DoctorTier {
  basic,
  pro,
  premium;

  bool atLeast(DoctorTier other) => index >= other.index;
}

/// Codes for individual gated features. Keep in sync with
/// `com.shifa.domain.SubscriptionFeature` on the backend.
enum DoctorFeature {
  // BASIC and above
  calendar,
  documents,
  chat,
  patientManagement,
  basicAnalytics,
  // PRO and above
  videoConsultation,
  aiNotes,
  speechToText,
  patientBriefing,
  askShifaAi,
  // PREMIUM only
  remoteCareTasks,
  advancedAnalytics,
  differentialDiagnosis,
}

/// Map of feature → minimum tier required.
const Map<DoctorFeature, DoctorTier> _minTier = {
  DoctorFeature.calendar: DoctorTier.basic,
  DoctorFeature.documents: DoctorTier.basic,
  DoctorFeature.chat: DoctorTier.basic,
  DoctorFeature.patientManagement: DoctorTier.basic,
  DoctorFeature.basicAnalytics: DoctorTier.basic,
  DoctorFeature.videoConsultation: DoctorTier.pro,
  DoctorFeature.aiNotes: DoctorTier.pro,
  DoctorFeature.speechToText: DoctorTier.pro,
  DoctorFeature.patientBriefing: DoctorTier.pro,
  DoctorFeature.askShifaAi: DoctorTier.pro,
  DoctorFeature.remoteCareTasks: DoctorTier.premium,
  DoctorFeature.advancedAnalytics: DoctorTier.premium,
  DoctorFeature.differentialDiagnosis: DoctorTier.premium,
};

/// Returns true when the supplied tier is high enough to access [feature].
bool tierAllows(DoctorTier tier, DoctorFeature feature) {
  final required = _minTier[feature] ?? DoctorTier.basic;
  return tier.atLeast(required);
}

/// Parse the backend tier code (`BASIC` / `PRO` / `PREMIUM`). Falls back to
/// premium when the value is missing so an unauthenticated/legacy state never
/// silently strips functionality.
DoctorTier parseDoctorTier(String? raw) {
  switch ((raw ?? '').trim().toUpperCase()) {
    case 'BASIC':
      return DoctorTier.basic;
    case 'PRO':
      return DoctorTier.pro;
    case 'PREMIUM':
      return DoctorTier.premium;
    default:
      return DoctorTier.premium;
  }
}

/// Human-readable label for UI display (e.g. tier badge in the sidebar).
String doctorTierLabel(DoctorTier tier) {
  switch (tier) {
    case DoctorTier.basic:
      return 'Basic';
    case DoctorTier.pro:
      return 'Pro';
    case DoctorTier.premium:
      return 'Premium';
  }
}
