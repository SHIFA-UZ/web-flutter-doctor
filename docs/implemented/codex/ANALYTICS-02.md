- **[P2] Export can corrupt the cached range data.** [AnalyticsViewModel.kt:106](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsViewModel.kt:106) unconditionally installs the export’s fetched snapshot. If export starts for range A, the user selects B, and B’s trend finishes first, A’s export then overwrites it while `trendLoading` remains false. The next export labels A’s trend as B. Keep export results local or validate the range before updating the cache.

- **[P2] Today’s KPIs stay stale after automatic schedule refreshes.** [HomeScreen.kt:192](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeScreen.kt:192) connects analytics only to manual refresh. Resume and midnight refreshes at lines 149–177 update the schedule without refreshing the retained analytics ViewModel. Yesterday’s counts can therefore remain under “Today” until a manual refresh, contrary to flow 05 §4’s requirement to refetch when today’s appointment list changes.

- **[P2] A slow analytics request blocks every visible result.** [AnalyticsViewModel.kt:67](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsViewModel.kt:67) awaits `repo.all(range)` before publishing any block. A stalled trend or consultation-types request leaves already-successful KPIs and engagement showing skeletons until that request finishes or times out—even for BASIC users whose advanced blocks are hidden. Flow 05 §§5 and 8 require independent rendering as each result arrives.

---
**Answers (round 1, all fixed and tested; the ViewModel was reworked, so a second round follows):**
1. *An export could overwrite the new range's trend:* fixed. A read made for an export is the export's own and never
   replaces what the card shows. Tested (export on the 7-day range, switch to 30 days: the 30-day trend stays).
2. *Today's KPIs stayed stale after Home's automatic reads:* fixed. Each new read of today's schedule (coming back, a
   hero leaving its slot, a new day, a refresh; at most once a minute, gated like that read) reads today's counts
   again (`refreshToday`); the first is the card's own open.
3. *A slow request held back every block:* fixed. Each block is read and shown on its own as it answers (one job per
   block, a newer read cancelling the older). Tested with consultation types held back while the KPIs show.

