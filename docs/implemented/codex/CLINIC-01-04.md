- **[P1] Clinic state survives logout and leaks into another account.** [ClinicsRepository.kt:46](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/clinics/ClinicsRepository.kt:46) never clears the singleton’s state on session changes. [ClinicWorkspace.kt:161](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicWorkspace.kt:161) immediately displays that cached list; if the next account’s read fails, the previous account’s clinics remain visible. Additionally, `mine()` reads the user **after** the response (lines 55–58), allowing a late response to be attributed to a newer session. Clear memory on session changes and reject responses from earlier sessions.

- **[P2] Switching clinics displays the previous clinic’s metrics under the new clinic’s name.** [ClinicWorkspace.kt:206](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicWorkspace.kt:206) cancels the previous request but clears only `statsError`, retaining `stats`. After switching A → B, A’s appointment, doctor and patient counts remain visible until B’s request completes. Clear metrics when the clinic ID changes, or store them with their clinic ID.

- **[P2] Shell membership visibility does not follow the shared repository.** [ShellViewModel.kt:243](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/ShellViewModel.kt:243) shares the repository, but `hasClinic` is updated only by the shell’s own requests (lines 288 and 567). A workspace refresh that discovers the last membership was removed updates the workspace to empty while More still exposes Clinic until another app resume. Derive the shell gate from the repository’s known, sticky state so every successful membership refresh updates both consumers.

---

- **P1 another account's clinics:** fixed. `ClinicsRepository.mine()` starts from an empty state for a new user, reads
  the user before the request and drops an answer that arrives after the user changed; `isCurrent` keeps the
  workspace from showing a previous user's list even before it reloads. Test: "another sign-in never sees the previous
  account's clinics, a late answer is dropped".
- **P2 the previous clinic's numbers:** fixed. The numbers are cleared when the clinic changes (they belong to one
  clinic id). Test in `ClinicWorkspaceViewModelTest`.
- **P2 shell gate:** fixed. The shell follows the shared repository's state: any successful read of the memberships
  (the workspace's too) updates the Clinic entry.
