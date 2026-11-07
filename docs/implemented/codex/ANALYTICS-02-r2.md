- **[P2] Automatic KPI refresh still misses successful schedule reads.** [HomeScreen.kt:185](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeScreen.kt:185) keys the effect on `state.schedule`, a structurally compared data class. A refresh returning the same schedule does not restart it. For example, a newly registered patient changes `newPatientsToday` without changing appointments; returning to Home leaves that KPI stale. Trigger `refreshToday` from successful read completion or a read-generation counter.

- **[P2] Date-picker presets remain anchored to the ViewModel’s creation day.** [AnalyticsViewModel.kt:72](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsViewModel.kt:72) initializes `today` once; `refreshToday()` never updates it. Home retains this VM by zone, and [AnalyticsSection.kt:200](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsSection.kt:200) uses that stale date for picker bounds and presets. After midnight, “Last 7 days” still ends yesterday, even though the trend request returns seven days ending today. Update the practice date independently of the user’s selected range.

---
**Answers (round 2, both fixed and tested):**
1. *An unchanged schedule read didn't refresh the KPIs:* fixed. `HomeUiState.reads` counts every successful schedule
   read, and the card's `refreshToday` follows that counter, so a read with the same visits still refreshes today's
   counts (a new patient, for one). Tested in `HomeViewModelTest`.
2. *The presets kept the day the card was made:* fixed. `refreshToday(today)` brings the practice date with each read;
   on a new day the picker's presets and bounds move, and a range the doctor never changed moves with them (its trend
   is read), while a range they picked stays. Tested.

