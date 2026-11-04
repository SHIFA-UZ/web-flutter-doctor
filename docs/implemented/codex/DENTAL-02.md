- **[P1] End silently omits an unread dental record.** [VisitViewModel.kt:387](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitViewModel.kt:387) accepts `flush()` when loading failed or remains pending, because nothing is dirty. At line 489, the unloaded record becomes `null`, so End completes with an incomplete PDF or “no items to save,” without summary retry. Await dental loading and surface failures before generating the summary.

- **[P1] An unreadable record can still be overwritten.** [DentalRepository.kt:22](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/dental/DentalRepository.kt:22) maps `DentalRecord.read` returning `null` into success—including unexpected JSON arrays or primitives. [DentalNotes.kt:83](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/DentalNotes.kt:83) then substitutes an empty record and enables editing; the next autosave replaces the server record. Only the documented empty response (`{}`) should authorize creating an empty record; unexpected shapes should fail loading.

- **[P2] PDF discount disagrees with the saved record and total.** [VisitSummaryPdf.kt:193](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/consultation/VisitSummaryPdf.kt:193) prints the unclamped in-memory percentage. Entering `150` saves `100%` and computes a zero total, but End uploads a PDF saying “Discount: 150%.” Render the normalized percentage used by the payload and totals.

---
**Answers (round 1, all fixed and tested; second round below):**
1. *End left out a dental record that wasn't read:* fixed. End's first step now waits for the read, or reads again,
   before anything else. If the record can't be read, End stops before the visit is completed ("Could not load dental
   record"), so the summary can't silently leave it out. Tested: it stops, and the next End goes through.
2. *An unreadable body became an empty, editable record:* fixed. Only `{}` means "no record yet". Any other body
   that can't be read as a record is a failure, so nothing can be edited or saved over it. Tested with `[]`.
3. *The PDF printed the unclamped discount:* fixed. The summary gets the record as saved (`payload()`: discount
   clamped, notes trimmed), and the PDF clamps too. Tested: 150 prints as 100 %.
