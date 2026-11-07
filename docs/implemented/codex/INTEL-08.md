- **[P2] Identical live answers auto-open the same visit repeatedly.** [ThreadViewModel.kt:391](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadViewModel.kt:391) emits `autoOpen` for every valid answer; `autoOpened()` only clears the pending value. Asking twice with unchanged appointment/route/status/start time therefore navigates twice, contrary to flow §8. Preserve `startAt` and maintain the required per-process claim set.

- **[P2] Invalid statuses pass navigation validation.** [IntelCards.kt:456](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/intelligence/IntelCards.kt:456) accepts any non-null status except two literal values. A payload with `status: "BOGUS"` enables the button and auto-opens. The referenced planner rejects unparseable statuses; validate against recognized appointment statuses before marking the plan openable.

- **[P2] A patient-target payload without a patient ID opens the visit instead.** [IntelligenceScreen.kt:590](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/IntelligenceScreen.kt:590) falls through to `ShellRoute.Visit` when `target == "patient"` but `patientId` is absent. Validation permits this, while the button says “Open chart.” Require a patient ID for this target and disable navigation when it is missing.

- **[P2] Auto-open preserves an open history sheet.** [IntelligenceScreen.kt:159](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/IntelligenceScreen.kt:159) navigates without clearing `viewModel.state.history`. Open history while a turn finishes, then return from the automatically opened visit: the retained ViewModel recreates the history sheet over the thread. Flow §8 requires closing the sheet before pushing; clear its state before navigation.

---

- **P2 the same visit auto-opened again:** fixed. `ThreadViewModel.openedVisits` claims each visit once per app run by
  Flutter's key (id, route, status, start instant; `startAt` is now read). Restored chats still never auto-open.
  Test: "a live answer that opens a visit asks the screen to open it once, a closed or already opened visit does not".
- **P2 unknown statuses openable:** fixed. Only booked states open (REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETED,
  PENDING_PAYMENT, from the backend's `Appointment.Status`); BOGUS, EXPIRED, PAYMENT_FAILED, CANCELLED, NO_SHOW don't.
- **P2 patient target without a patient:** fixed. Such a card isn't openable (no "Open chart", no auto-open).
- **P2 history sheet kept under an auto-open:** fixed. The sheet closes before the visit opens.
