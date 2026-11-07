package shifa.doctor.data.subscription

import shifa.doctor.data.model.DoctorSubscription

/** The admin-set plan (backend `SubscriptionTier`, 08 §5.1). */
enum class DoctorTier { Basic, Pro, Premium }

/** The backend's `SubscriptionFeature` codes for doctors and the plan each first appears in. */
enum class DoctorFeature(
    val minTier: DoctorTier,
) {
    Calendar(DoctorTier.Basic),
    Documents(DoctorTier.Basic),
    Chat(DoctorTier.Basic),
    PatientManagement(DoctorTier.Basic),
    BasicAnalytics(DoctorTier.Basic),
    VideoConsultation(DoctorTier.Pro),
    AiNotes(DoctorTier.Pro),
    SpeechToText(DoctorTier.Pro),
    PatientBriefing(DoctorTier.Pro),
    AskShifaAi(DoctorTier.Pro),
    RemoteCareTasks(DoctorTier.Premium),
    AdvancedAnalytics(DoctorTier.Premium),
    DifferentialDiagnosis(DoctorTier.Premium),
    ;

    /** The wire code: `REMOTE_CARE_TASKS`. */
    val key: String = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
}

/**
 * What the doctor's plan allows (SHELL-04, SUB-01; 01 §5.3, 08 §5). D-03, the user's call: gate on
 * `subscription.features`, and an unknown plan allows nothing (Flutter treated unknown as PREMIUM). The list decides
 * whenever it is there, also when empty; only an old backend that leaves the list out falls back to the tier. The
 * backend stays the source of truth.
 */
data class FeatureGate(
    val tier: DoctorTier?,
    val features: Set<String>?,
) {
    /** The profile has answered; until then gated entries stay hidden. */
    val known: Boolean get() = tier != null || features != null

    fun allows(feature: DoctorFeature): Boolean =
        features?.let { feature.key in it } ?: (tier != null && tier >= feature.minTier)

    companion object {
        val Unknown = FeatureGate(null, null)

        fun of(subscription: DoctorSubscription?): FeatureGate =
            FeatureGate(
                DoctorTier.entries.firstOrNull { it.name.equals(subscription?.tier, ignoreCase = true) },
                subscription?.features?.toSet(),
            )

        /** Everything [tier] includes, as the backend would list it (previews and tests). */
        fun forTier(tier: DoctorTier) =
            FeatureGate(tier, DoctorFeature.entries.filter { it.minTier <= tier }.map { it.key }.toSet())
    }
}
