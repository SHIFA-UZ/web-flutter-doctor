- **[P2] Missing unified refresh operation** — [AnalyticsRepository.kt:26](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/analytics/AnalyticsRepository.kt:26). The repository exposes only five independent reads; nothing refreshes all five datasets together. ANALYTICS-01 explicitly requires “one refresh entry point that invalidates all five datasets” and “refresh everything, not just overview” (`docs/kmp-spec/02-home-ai-intelligence.md:753–756`). Removing caching does not ensure the other datasets reload when overview refreshes. Add a unified refresh operation that preserves independent results for each block.

---
**Answer (round 1, fixed; small, so no second round):**
1. *No single refresh:* fixed. `AnalyticsRepository.all(range, withSms)` reads every block in parallel and returns an
   `AnalyticsSnapshot` with one `Result` per block, so a refresh always reloads all of them and a failure stays in its
   own block. Home's open, pull-to-refresh and export, and Reports go through it. Tested.

