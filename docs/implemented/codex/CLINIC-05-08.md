- **P2 — Invitation history is never fetched.** [ClinicsRepository.kt:135](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/clinics/ClinicsRepository.kt:135) omits `status=all`. Backend `ClinicInvitationController.kt:85–96` defaults to `active` and filters out used/expired invitations. Consequently, the “Used / expired” group disappears after reload. Request all statuses; the demo currently masks this mismatch.

- **P2 — Invalid share input can become a valid, unintended financial change.** [ClinicPeople.kt:221](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicPeople.kt:221) strips every non-digit and truncates before validation: `-5` becomes `5`, `10.5` becomes `105`, and `abc` becomes empty, which Save accepts as clearing the share. Flow 09/03 §8–9 requires rejecting negative/fractional/text input and blocking empty per-doctor Save. Preserve invalid input for validation; reserve clearing for the explicit action.

- **P2 — Refresh failures silently leave stale, actionable data.** [ClinicPeople.kt:387](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicPeople.kt:387) and [ClinicPeople.kt:566](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicPeople.kt:566) display errors only when the corresponding list is null. After a successful load, a failed post-save/post-revoke refresh—or failed reload when returning to a clinic—shows neither error nor Retry. Old shares and revoked invitations remain actionable. Render refresh errors alongside retained rows.

- **P2 — Inherited shares show an incorrect preview.** [ClinicPeople.kt:524](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicPeople.kt:524) derives the slider and labels only from the override text. Opening a doctor with no override and a 60% clinic default shows slider `0`, “Not set,” and no clinic remainder. Flow 09/03 §4 requires the effective 60/40 split while keeping the override field empty.

- **P2 — Content composables violate the required state/callback boundary.** [ClinicPeople.kt:354](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicPeople.kt:354) and [ClinicPeople.kt:556](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicPeople.kt:556) accept `ClinicPeopleViewModel?` and invoke it directly throughout content and sheets. Previews consequently pass null and make every action inert. The architecture requires stateless content accepting state and callbacks, with ViewModel wiring confined to the screen wrapper.

---

- **P2 invitation history:** fixed. The list asks `?status=all` (the backend's default lists only live ones; checked
  in `ClinicInvitationController`, and on dev). Test.
- **P2 invalid share input:** fixed. The field keeps what was typed; only a whole number 0–100 saves ("-5", "10.5",
  "abc", "101" are refused, never turned into another number); an empty field doesn't save either — clearing is its
  own action ("Use clinic default" / "Clear default"). Tests.
- **P2 refresh failures:** fixed. A failed read shows its banner and Retry also over a list kept from before.
- **P2 inherited preview:** fixed. A doctor on the clinic default sees its split (60/40) while their own field stays
  empty. Test.
- **P2 content takes the ViewModel:** fixed. `DoctorsContent` / `InvitationsContent` take state and a `PeopleActions`
  of callbacks; only the section wrappers know the ViewModel.
