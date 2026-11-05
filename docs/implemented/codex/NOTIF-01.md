- **[P2] Appointment briefings disappear from the Appointments filter.** [NotificationRules.kt:24](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/notifications/NotificationRules.kt:24) assigns `AiBriefing` to `All`, and line 53 maps `APPOINTMENT_BRIEFING_READY` to it. Flow 07/08 §3 requires `APPOINTMENT_*` notifications under Appointments. Assign this kind to `Appointments`.

- **[P2] The required catalogue regression tests are missing.** [NotificationLookTest.kt:76](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/androidUnitTest/kotlin/shifa/doctor/ui/notifications/NotificationLookTest.kt:76) merely calls `look()` for every kind; assertions cover only the reminder label and cancellation tone. `NotificationRulesTest` likewise checks selected filters and targets. NOTIF-01’s “Done when” requires table-driven assertions for **every type → label/colour/filter/target**. Incorrect mappings—including the briefing filter above—currently pass.

---
**Answers (round 1, both fixed; small, so no second round):**
1. *The briefing was missing from Appointments:* fixed. `APPOINTMENT_BRIEFING_READY` sorts under Appointments with
   the other `APPOINTMENT_*` types (flow §3); the AI scribe and visit summary stay under All, as in the flow and board.
2. *No table-driven catalogue test:* added. `NotificationCatalogueTest` checks every backend type → label, icon,
   colour, chip and target in one table (a row carrying every id), and that the table reaches every kind.
