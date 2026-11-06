- **[P2] SMS totals remain attributed to the wrong range during loading.** [AnalyticsViewModel.kt:122](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsViewModel.kt:122) changes the range and marks only the trend as loading, retaining the previous SMS result. The new range immediately displays the old sent count and spend until its request completes. Clear the SMS result or add a loading flag, as flow §5 requires.

- **[P2] Reports freezes its initial practice date.** [ReportsScreen.kt:44](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/ReportsScreen.kt:44) uses `today` only when creating the fixed-key ViewModel; subsequent dates never reach `refreshToday(today)`. After midnight or a timezone change, even pull-to-refresh retains the old default range and picker bounds. Opening before the profile loads can likewise preserve the UTC fallback date.

- **[P2] The tab bar obscures the bottom of Reports.** [ReportsScreen.kt:64](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/ReportsScreen.kt:64) provides only ordinary content padding, while [MainScreen.kt:346](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:346) overlays the bottom navigation. At maximum scroll, the final card remains behind the bar. Add navigation-bar clearance for the tab-root variant.

- **[P2] REPORT-01’s Fix #19 remains incomplete.** [AnalyticsCsv.kt:23](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/analytics/AnalyticsCsv.kt:23) prefixes the CSV with the selected period, but lines 32–33 export unqualified video/in-person totals from the range-insensitive endpoint. Historical exports therefore imply those totals belong to the selected dates. Label their actual scope or explicitly state that the selected range does not apply.

- **[P2] Reopening Reports does not reload data, contrary to the implementation claim.** [ReportsScreen.kt:44](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/ReportsScreen.kt:44) retrieves the retained ViewModel without an entry refresh; its initial fetch runs only in the constructor. Leaving Reports, changing appointment data, and reopening shows cached counts until manual refresh. [profile.md:397](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/docs/implemented/profile.md:397) incorrectly claims reads occur on open.

---

Answers (2026-11-06):

- P2 SMS numbers of the old range under the new label: fixed. A new range clears the SMS block to its skeleton until
  its answer arrives. Tested.
- P2 the practice date frozen: fixed. Each visit to Reports passes the practice day as it is now (`refreshToday`) and
  reads again, so a new day or the profile's zone arriving later moves an untouched range.
- P2 the tab bar over the last card: fixed. As the More tab's root the page keeps the other tab roots' 120 clearance.
- P2 the CSV's visit types under the period: fixed. The backend counts them over the last 30 days (checked in
  `DoctorAnalyticsService.consultationTypes`); the rows say "(30d)". Home's export too (the same CSV).
- P2 no read on reopening: fixed by the same resume read; the doc says so.
