- **[P2] Dates are not normalized when saving.** [FormModels.kt:80](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/FormModels.kt:80) forwards `date` unchanged, as it does follow-up dates at line 87. An accepted ISO timestamp such as `2026-03-12T00:00:00Z` is therefore sent unchanged instead of the `YYYY-MM-DD` required by §3.3. Normalize both date fields in the request conversion.

- **[P2] Empty collections are sent instead of omitted.** [FormModels.kt:127](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/FormModels.kt:127)–131 declares empty defaults for `dentalChart`, `followups`, and `clinicalChipSelections`, but shared `apiJson` enables `encodeDefaults`. Saving an empty form emits `{}`/`[]` for these fields, violating §3.3 and FORM-01’s explicit omit-empty requirement.

- **[P2] The canonical ICD spelling contradicts the ticket’s Fix line.** [FormModels.kt:92](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/FormModels.kt:92) defines `"ICD-10"` and converts `"ICD10"` to it. FORM-01 explicitly requires the constant `"ICD10"` (`06-patients-forms-documents-care.md:464`).

- **[P2] The required typed dental codes are missing.** [FormModels.kt:63](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/FormModels.kt:63) exposes only unrestricted strings, and no `DentalCode` enum exists. FORM-01’s Fix line explicitly requires the typed codes `O,R,C,P,Pt,П,A,I,II,III,K,И`; that requirement remains unimplemented.
---
**Answers (round 1, all fixed; small, so no second round):**
1. *Dates not normalised on save:* fixed; the form date and every follow-up date go as `YYYY-MM-DD` (a timestamp is cut to its day). Tested.
2. *Empty chart and lists sent:* fixed; the request's chart, follow-ups and chip selections are null when empty, so they are left out. Tested.
3. *ICD spelling:* fixed to the ticket's constant, "ICD10" ("ICD-10" from the clinical engine is rewritten to it). Tested.
4. *No typed dental codes:* added `DentalCode` with Flutter's twelve codes (O, R, C, P, Pt, П, A, I, II, III, K, И), read as written; a stored value outside the list stays a string in the chart. Tested.
