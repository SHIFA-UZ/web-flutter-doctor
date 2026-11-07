- **[P1] Signature requests proceed after failed saves.** [VisitViewModel.kt:174](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitViewModel.kt:174): `notes.flush()` returns whether notes were saved, but its result is ignored. A failed PUT still triggers the signature request and success banner, asking the patient to sign without the latest notes.

- **[P2] Signature polling survives leaving the visit.** [VisitViewModel.kt:238](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitViewModel.kt:238): neither leave path cancels `watching`. The ViewModel uses the shell’s store, which survives route pops; reopening creates another instance through the `opened` key. Unsigned visits therefore retain polling jobs and reopening adds duplicate pollers.

- **[P2] Document access results are invisible.** [VisitScreen.kt:884](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:884): `documentRows` renders `state.error` but never `state.banner`. Access-request success, already-requested responses, failures, and failed document refreshes all populate `banner`, so these outcomes disappear without feedback or their request IDs.

- **[P2] Opening the patient bypasses the save guard.** [VisitScreen.kt:281](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:281): patient-chip navigation invokes `onOpenPatient` immediately without flushing notes. Tapping before the debounce expires leaves with unsaved edits; a subsequent save failure is hidden behind the patient screen. Flow 05/04 §10 explicitly requires saving drafts before opening the patient.

- **[P2] SOAP fields are single-line inputs.** [VisitScreen.kt:664](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:664): all four fields omit `multiline = true`, while `ShifaInputField` defaults to single-line. Doctors cannot enter line-separated findings or plans; flow 05/05 requires growing, multiline SOAP fields.

- **[P2] Missing patient IDs leave permanently loading documents and inert actions.** [VisitScreen.kt:284](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:284): this branch supplies default document state and no-op document callbacks. Consequently, documents show an endless skeleton and Upload remains enabled but does nothing; Open Form also does nothing. Flow 05/04 requires an explicit “Patient record unavailable” state.
---
**Answers (round 1, all fixed; no second round: each fix is small and the risky ones are tested):**
1. *Signature asked after a failed save:* fixed; when the notes can't be saved, the patient isn't asked (the save error shows). Tested.
2. *Signature reads survive leaving:* fixed; going back stops them (a reopened visit gets a new ViewModel and its own reads).
3. *Document results invisible:* fixed; the documents' banner (access requested, already sent, failures, a failed refresh) shows above the list.
4. *Opening the patient skips the save:* fixed; opening the patient or the 025-2 editor goes the way back does: notes saved first, "Notes not saved" asked on failure, then on. Tested.
5. *SOAP fields one line:* fixed; multiline, growing from a short field (`ShifaInputField` gained a starting height for multiline fields).
6. *A visit without a patient:* fixed; "Patient record unavailable" instead of the documents and upload, and no Open Form 025-2.
