- **[P1] Failed summary upload permits losing photos without confirmation.** [VisitViewModel.kt:680](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitViewModel.kt:680) suppresses the photo warning once `end.completed` is true, which happens before PDF generation/upload. After upload failure, Back exits although photos remain unsaved and reopening creates a new ViewModel. Guard on successful photo persistence, not appointment completion.

- **[P2] Picker results can receive the wrong treatment kind.** [VisitScreen.kt:378–385](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:378) reads mutable `photoKind` when the callback arrives. Both gallery implementations dismiss before asynchronous image preparation finishes; launching the opposite kind meanwhile relabels the first result. Capture the kind per pending operation or block overlapping picks.

- **[P2] Photo additions have no required limit.** [VisitViewModel.kt:663](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitViewModel.kt:663) appends indefinitely, contrary to flow 04 §8’s 20-photo cap and banner. Compression bounds individual image dimensions, but retained bytes and PDF pages still grow without bound.

- **[P2] Leaving retains every photo in the shell’s ViewModel store.** Photos added at [VisitViewModel.kt:663](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitViewModel.kt:663) are never cleared by `go()` at line 685, including after successful End or confirmed discard. The shell retains these ViewModels, while each reopened visit gets a new key ([VisitScreen.kt:343](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:343)); photo memory therefore accumulates across finished visits.

---
**Answers (round 1, all fixed and tested; the fixes are small and covered by tests, so no second round):**
1. *A failed summary let the photos go without asking:* fixed. Back asks while the visit has photos and no filed
   summary (`end.done`), not just until completion. Tested with a failed upload.
2. *A late picker answer could get the wrong kind:* fixed. Opening a picker records its kind in the ViewModel
   (`pickingPhoto`), and the answer goes to that kind. While a picker is open, no other photo sheet opens. A refused
   camera clears it. Tested.
3. *No cap:* fixed. At most 20 photos per visit (flow 05/04 §8). A 21st shows "Up to 20 photos per visit." and an
   answer past the cap is dropped. Tested.
4. *Photos kept in the shell's ViewModel after leaving:* fixed. Going back clears them (a reopened visit gets a new
   ViewModel). Opening the patient or the 025-2 keeps them, since those return to the visit, and doesn't ask. Tested.
