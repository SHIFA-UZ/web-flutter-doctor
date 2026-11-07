- **[P2] Form navigation drops clinic context.** [MainScreen.kt:660](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:660) opens `ShellRoute.Form` without `clinicId`. The editor then reads the patient without scope (`FormEditorViewModel.kt:136`) and uploads/replaces its PDF without scope (`:422–435`). Patients accessible only through the clinic therefore cannot complete this flow. Carry clinic context through the form route and its patient/document calls.

- **[P2] Clinic reads add patients to the personal directory.** [PatientDetail.kt:368](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientDetail.kt:368) now performs a scoped read, but `PatientsRepository.kt:114` unconditionally caches its result; `:230` inserts patients absent from the personal roster. After opening another clinic doctor’s patient, returning to Patients shows that patient and opens them without clinic context. Keep clinic reads separate from personal-directory membership.

- **[P2] An empty decoded page prevents further paging.** [ClinicPatients.kt:199](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicPatients.kt:199) shows only the empty state, even when `last == false`. Since `ClinicsRepository.kt:127–130` deliberately drops invalid/missing-ID rows, a first page containing only rejected rows makes valid later pages unreachable. Show Load more whenever another page exists, or automatically advance past empty decoded pages.

---

- **P2 form drops clinic context:** fixed. `ShellRoute.Form` carries the clinic; the editor reads the patient and
  uploads / replaces the form's PDF in that scope. Test (`GET patients/14?clinicId=2`, `POST …/documents?clinicId=2`).
- **P2 clinic reads join the personal directory:** fixed in the repository (every caller): a clinic-scoped read only
  refreshes a patient already in the doctor's own directory, never adds one. Test.
- **P2 empty decoded page stops paging:** fixed. The empty state shows only when no page follows; a page whose rows
  were all unreadable still offers Load more.
