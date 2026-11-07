- **[P1] Empty feature lists grant tier-based access.** [FeatureGate.kt:44](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/subscription/FeatureGate.kt:44) treats `features: []` as permission to fall back to the tier. Thus `{"tier":"PREMIUM","features":[]}` enables Tasks and Intelligence despite neither feature being listed. This violates D-03’s authoritative `subscription.features` gating. An empty list must deny access.

- **[P2] Intelligence remains visible while the gate is unknown.** [MainScreen.kt:347](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:347) renders the Intelligence row unconditionally; line 351 only hides its badge. During profile loading or failure, users see a disabled “PRO required” entry despite no resolved subscription. D-03 requires gated entries to stay hidden until the profile loads; conditionally render this row on `state.gate.known`.
---
**Answers (both fixed):**
1. *Empty list falling back to the tier:* `DoctorSubscription.features` is now nullable; a list that is present, even empty, decides alone (empty = nothing included); only a missing list (old backend) falls back to the tier. Tested with `{"tier":"PREMIUM","features":[]}`.
2. *Intelligence visible while unknown:* the row and its divider render only once the gate is known.
Local and covered by tests; no second round.
