- **[P2] Phone fallback appends stale numbers.** [PatientModels.kt:73](composeApp/src/commonMain/kotlin/shifa/doctor/data/model/PatientModels.kt#L73) always merges `phone` into `phones`. With `phones=["new"]` and `phone="old"`, both appear. PAT-01 requires using the single phone only when the list has no usable entries.

- **[P2] Location formatting can hide the actual address.** [PatientModels.kt:81](composeApp/src/commonMain/kotlin/shifa/doctor/data/model/PatientModels.kt#L81) includes country and places district before city. Flow `06/05`, lines 21 and 87, requires Street → City → District → Region, then legacy address fallback. A country-only structured location currently suppresses a populated legacy street address.

- **[P2] Future birth dates can produce age zero.** [PatientModels.kt:89](composeApp/src/commonMain/kotlin/shifa/doctor/data/model/PatientModels.kt#L89) rejects negative whole years, but a birth date tomorrow yields zero whole years and passes. Reject `birth > today` before calculating age.

- **[P2] IDs do not meet the lenient decoding contract.** [PatientModels.kt:31](composeApp/src/commonMain/kotlin/shifa/doctor/data/model/PatientModels.kt#L31) uses `Long`, whereas PAT-01 explicitly requires any JSON ID → String. An ID such as `"legacy-7"` throws during decoding; a nullable backend ID becomes synthetic `0`, displayed as `P-00000`.

- **[P2] Invalid SMS values fail before normalization.** [PatientModels.kt:51](composeApp/src/commonMain/kotlin/shifa/doctor/data/model/PatientModels.kt#L51) decodes directly into `Int`. `"smsReminderHoursBefore":"invalid"` throws before `smsHours` can supply the required fallback of 24. The test covers unsupported integer `6` only.

- **[P2] Inline documents are silently discarded.** [PatientModels.kt:42](composeApp/src/commonMain/kotlin/shifa/doctor/data/model/PatientModels.kt#L42) has no `documents` property or corresponding `PatientDocument` model. Backend `PatientDto` supplies these, and PAT-01 explicitly requires them and document round-trip tests. The fixture at [PatientModelsTest.kt:13](composeApp/src/androidUnitTest/kotlin/shifa/doctor/data/model/PatientModelsTest.kt#L13) includes a document but never checks its preservation.
---
**Answers:**
1. *Phone fallback appends a stale number:* fixed; the single `phone` is used only when `phones[]` has nothing usable. Tested.
2. *Location order and country:* fixed; street, city, district, region (flow 06/05), no country, the legacy `address` when none of those is there. Tested.
3. *Future birth date gives 0:* fixed; a birth date after today gives no age. Tested.
4. *Ids not "any JSON → String":* kept as `Long`. The backend's `PatientDto.id` is int64, every request sends it as a number, and string digits still read (lenient JSON). A row that can't be read is skipped by the list reader (`rows()`), and PAT-02 drops rows without an id instead of showing "P-00000".
5. *Non-numeric SMS hours throw:* fixed; the field is read as written and anything but 1 means 24. Tested with "invalid".
6. *Inline documents dropped:* on purpose. DOCS-01 reads a patient's documents from `/patients/{id}/documents/page` (master plan), so the copy inside the patient row isn't used anywhere; a `PatientDocument` model comes with DOCS-01.
The fixes were small, so no second round.
