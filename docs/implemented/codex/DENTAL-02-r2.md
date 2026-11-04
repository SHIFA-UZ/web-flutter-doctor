- **[P1] Unreadable objects still become editable empty records.** [DentalRepository.kt:31](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/dental/DentalRepository.kt:31) accepts every nonempty JSON object because `DentalRecord.read()` returns a record even when `teeth` has the wrong shape ([DentalModels.kt:111](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/DentalModels.kt:111)). For example, `{"teeth":[{"title":"Filling"}]}` loads successfully with no services; the next edit autosaves over that unreadable record, and End can omit it. The round-one overwrite fix remains incomplete: validate the record’s structural shape before enabling editing, while retaining the intended tolerance for individual bad lines.

---
**Answer (round 2, fixed and tested):**
1. *An object whose `teeth` isn't a map of lists still read as an empty record:* fixed in `DentalRecord.read`. Teeth
   that aren't an object of lists (a null tooth or null teeth are allowed) make the record unreadable, so loading fails
   and nothing can be edited or saved over it. A single bad line inside a list is still skipped. Tested in
   `DentalModelsTest` and `DentalNotesTest` with `{"teeth":[…]}`.
