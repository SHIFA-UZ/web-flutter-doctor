- **P2 — Saving still has no visible feedback.** [TimeZonePicker.kt:210](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/TimeZonePicker.kt:210): `ShifaDialogSheet` never receives `confirmLoading = state.saving` or `confirmEnabled`. During a slow PATCH, Confirm looks active but silently ignores taps. Flow 08/06 requires a progress indicator until completion.

- **P2 — Zero-offset searches return no results.** [TimeZones.kt:57](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/utils/TimeZones.kt:57): searching `+0` or `UTC+0` compares `"+0"` against `""`, because `utcLabel()` formats zero as `"UTC"` and the prefix is removed. Consequently, UTC and every currently zero-offset zone disappear. Compare normalized numeric offsets instead of formatted strings; the same comparison also rejects equivalent inputs such as `+05:00`.---
**Answers (both fixed):**
1. *No progress while saving:* the warning's Change timezone button shows its loading state while the PATCH runs.
2. *"+0" / "+05:00" find nothing:* offsets are compared as seconds against the zone's offset now, so every spelling works ("UTC+0" finds UTC). Tested.
Local; no third round.
