- **[P2] Profession localization always falls back to English.** [ProfileScreen.kt:239](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/ProfileScreen.kt:239) constructs `Profession(it, null)`. `label()` needs the Uzbek catalogue label, so both Uzbek locales display English in the identity card and personal preview.

- **[P2] Completeness can hide required missing information.** [ProfileScreen.kt:248](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/ProfileScreen.kt:248) checks five items, treats services as complete unconditionally, and omits surname, profession and phone. A profile missing all those fields can show 100%; an empty profile starts at 20%. Flow §4 requires eight equally weighted checks.

- **[P2] Schedule and Reports use the wrong navigation.** [MainScreen.kt:281](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:281) selects tab roots instead of opening schedule setup and pushing Reports. Schedule therefore opens Calendar, and Back from Reports returns Home instead of Profile. Both the flow and board specify more specific destinations.

- **[P2] Successful background recovery leaves the error screen visible.** [ProfileScreen.kt:140](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/ProfileScreen.kt:140) updates `me` without clearing `failed`. After an initial failure, a successful shell resume refresh populates the repository, but the screen still selects its error branch until Retry is pressed.

- **[P2] Load failures discard the information needed for error reporting.** [ProfileScreen.kt:151](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/ProfileScreen.kt:151) reduces repository results to a Boolean. Offline, forbidden and server failures all become “Something went wrong,” with no `requestId`. Preserve the mapped error and request ID as required by the project’s error-handling rules.

- **[P3] Missing names render as a medical prefix.** [DoctorName.kt:13](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/utils/DoctorName.kt:13) returns `"Dr. med."` when both names are blank. Consequently, the header never shows `yourName`, and the personal preview bypasses its “Not set” fallback, contrary to flow §4.

- **[P3] Avatar fallback does not show initials.** [ProfileScreen.kt:386](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/ProfileScreen.kt:386) uses `ShifaAvatar`, whose missing-photo and failed-image paths display a fixed brand image. Both the root board and flow require the person’s initials, including when the photo returns 404.
---
**Answers:**
1. *Uzbek profession labels:* fixed; the screen maps each English label through the bundled catalogue (`CatalogRepository.bundledProfessions`, production's list), so uz / Ўзбекча show the Uzbek label.
2. *Completeness:* fixed; flow §4's eight equal checks (first name, last name, profession, timezone, phone, photo, a location, a service), the last two from `GET /doctors/me/locations` and `/services` counts (unknown counts don't count as missing). Fix-it chips for each.
3. *Schedule / Reports navigation:* fixed; both push on the Profile stack (`ShellRoute.Schedule`, `ShellRoute.Reports`, placeholders until SCHED-1 / REP-1), so Back returns to Profile.
4. *Error left visible after recovery:* fixed; a profile arriving from anywhere clears the error. Tested.
5. *Error details:* fixed; the failure is mapped with `toUserMessage` and its `requestId` shown under the message. Tested.
6. *Lone "Dr. med.":* fixed; `doctorDisplayName` returns "" without a name; the card shows "Your name", the preview "Not set".
7. *Initials instead of the brand picture:* kept. The project's `CLAUDE.md` says avatars without a photo, or whose photo fails, show the brand fallback picture (`ShifaAvatar`); that rule overrides the flow and board here.
Local; covered by tests and screenshots; no second round.
