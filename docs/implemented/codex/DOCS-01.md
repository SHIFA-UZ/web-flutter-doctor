- **[P2] Quoted filenames are truncated at semicolons.** [DocumentModels.kt:100](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/DocumentModels.kt:100) splits the header without respecting quotes. Valid `filename="scan; final.pdf"` becomes `scan`, losing the filename and extension used for MIME fallback. Parse quoted parameters before splitting.

- **[P2] RFC 5987 filenames with a language tag decode incorrectly.** [DocumentModels.kt:103](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/DocumentModels.kt:103) recognizes only the empty-language separator `''`. Valid `filename*=UTF-8'en'report.pdf` returns `UTF-8'en'report.pdf` instead of `report.pdf`. Parse the charset, optional language, and encoded filename separately.
---
**Answers (round 1, both fixed; small, so no second round):**
1. *Quoted names cut at a semicolon:* fixed; parameters split on `;` outside quotes only, quoted values unquoted (an escaped quote kept). Tested with `"scan; final.pdf"`.
2. *RFC 5987 with a language tag:* fixed; `charset'language'value` is read in its three parts, the language empty or set. Tested with `UTF-8'en'report%20one.pdf`.
