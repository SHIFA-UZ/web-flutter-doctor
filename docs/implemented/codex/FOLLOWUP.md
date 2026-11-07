- **[P2] Restore the End sheet when follow-up booking is cancelled.** [VisitScreen.kt:593](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:593) sets `end.hidden = true`, but both picker callbacks at lines 604–607 only clear `followUp`; neither restores the sheet. Closing the picker—or selecting a slot and backing out of booking—therefore loses Done, Open Summary, and Book follow-up. The completed visit cannot reopen End through its footer (lines 1408–1412). Restore the completion sheet on cancellation/return so the doctor can retry booking or finish normally.

---
**Answer (round 1, fixed; checked on the emulator):**
1. *Closing the picker lost the done step:* fixed. Closing the picker, or picking a slot, brings the End sheet back,
   so it is there on return from the booking with Done, Open Summary and Book follow-up. Checked in demo mode: Book
   follow-up → Close → "Appointment Ended" with its three actions again.
