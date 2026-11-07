- **[P2] Reopening search retains the previous query and skips refresh.** [HomeSearch.kt:137](composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeSearch.kt#L137) uses the shell’s shared ViewModel store; dismissing search does not clear it. Loading runs only in `init` (lines 105–107). Search → type → Back → reopen therefore restores the old query and never checks directory staleness. Flow §§2/7 require a fresh, empty query.

- **[P2] Search does not dismiss the keyboard when there is no unique match.** [HomeSearch.kt:175](composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeSearch.kt#L175) does nothing for zero or multiple matches. The custom keyboard action consumes Search without hiding the keyboard, contrary to flow §7. A blank query with exactly one patient also cannot open that patient.

- **[P2] An empty directory has no empty state.** [HomeSearch.kt:193](composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeSearch.kt#L193) shows `NoMatch` only for a nonblank query. With zero patients and an empty query, the screen displays only “Patients,” without the “No patients yet” message or Add patient action required by flow §6.

- **[P2] Selecting a patient leaves search in Home’s stack.** [MainScreen.kt:473](composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt#L473) pushes patient detail onto the current Home stack. Flow §§5/10 and the board’s `openPatient` require removing search and opening the selected patient in the Patients tab. Consequently, Back returns to search instead of the Patients directory.

---
**Answers (round 1, all fixed; small and checked on dev, so no second round):**
1. *Reopening kept the old query:* fixed. `ShellRoute.Search` carries its open time and keys its ViewModel, so each
   open starts empty and checks the directory's staleness again. Checked: Home → Search shows the blank list after an
   earlier "sar".
2. *Search didn't hide the keyboard:* fixed. The keyboard's Search opens the only match (also with a blank query and
   one patient), otherwise hides the keyboard. Checked with "zz".
3. *No empty directory state:* fixed. No patients at all: "No patients yet" with Add patient (the Patients tab, where
   patients are created).
4. *The patient opened inside Home's stack:* fixed. `ShellViewModel.openPatientFromSearch` drops the search, switches to
   Patients and opens the patient over the directory, so Back leads to the directory (flow 02/07 §10, the board's
   `openPatient`). Tested in `ShellViewModelTest`; checked on dev.

