- **[P2] Seven-day selections still get the wrong title.** [AnalyticsCharts.kt:97](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsCharts.kt:97): Selecting a historical seven-day range, such as September 1–7, displays “Last 7 Days” while loading, empty or failed. The branch checks only duration, bypassing the requested-range fix. Format the actual range unless it is the last seven days.

- **[P2] A previous failure overrides loading for a new range.** [AnalyticsCharts.kt:102](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsCharts.kt:102): After a failed request, changing the range retains the failed result and sets `trendLoading = true`. Because failure is checked first, the new range immediately shows “Could not load” and Retry throughout its pending request. Check loading before the retained failure.

---
**Answers (round 2, both fixed):**
1. *A past seven days was titled "Last 7 Days":* fixed. "Last 7 Days" only for the seven days ending on the practice
   date; any other span, seven days or not, is named ("Appointments (1 Sep – 7 Sep, 2026)").
2. *A previous failure showed while a new range loaded:* fixed. Loading is checked first, so a new range shows its
   skeleton until its own answer, and "Could not load" only for that answer.

