- **[P2] Record mode makes golden verification pass unconditionally** — [VisitSummaryPdfTest.kt:257](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/androidUnitTest/kotlin/shifa/doctor/data/consultation/VisitSummaryPdfTest.kt:257). With `RECORD_GOLDEN=1`, the test overwrites the expected file with actual output immediately before comparing them. An inherited recording flag therefore silently accepts regressions, including empty output. Separate recording from verification, or fail verification when recording is enabled.

- **[P2] Missing Russian title still passes its content assertion** — [VisitSummaryPdfTest.kt:306](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/androidUnitTest/kotlin/shifa/doctor/data/consultation/VisitSummaryPdfTest.kt:306). If `"Итоги приёма"` is missing, its index becomes `-1`, which remains strictly less than every subsequent matching index. The test therefore passes without the title it claims to verify; the English golden cannot catch this Russian-specific regression. Assert that every expected string exists before checking order.

---
**Answers (round 1, both fixed; tested, no second round):**
1. *Record mode passed unconditionally:* fixed. Recording writes the golden and then fails on purpose ("run again
   without RECORD_GOLDEN to verify"), so a flag left on can't accept a regression.
2. *A missing text passed the order check:* fixed for every order check in the file. A shared `assertInOrder` first
   asserts each expected text is there, then the order.
