- **[P2] Decimal UZS prices cannot be entered.** [ClinicServices.kt:151](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicServices.kt:151) delegates to `Money.parse`, which rejects UZS fractions; line 702 also selects an integer keyboard. Thus `1500.50` fails instead of becoming `150050` minor units, contrary to flow §8 and CLINIC-07’s decimal-parsing requirement.

- **[P2] Free services cannot be created or edited.** [ClinicServices.kt:150](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicServices.kt:150) requires both stored and parsed prices to be strictly positive. An existing zero-priced service cannot even have its title changed without increasing its price. Flow §§8–9 explicitly allow zero, as does the backend’s `@Min(0)` contract.

- **[P2] Currency selection is missing.** [ClinicServices.kt:135](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicServices.kt:135) fixes currency when opening the editor, and the form provides no currency control. Users cannot create a USD service in a UZS clinic or change an existing service’s currency, both required by flow §§3 and 8 and the ticket.

- **[P2] Failed catalog refreshes leave stale editing enabled.** [ClinicServices.kt:237](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicServices.kt:237) retains the previous catalog on failure; menu availability checks only whether an item exists. After a successful save followed by a failed catalog refresh, reopening Edit uses old values, and saving another field can overwrite the successful change. Disable editing while `catalogFailed`, as flow §6 requires.

- **[P2] Swiping a changed editor never asks to discard.** [ClinicServices.kt:637](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicServices.kt:637) vetoes hiding whenever `dirty` without invoking the discard action. The sheet simply stays open on swipe, contrary to flow §7’s required discard confirmation.

---

- **P2 decimal UZS:** kept as is, deliberately. UZS is whole sums everywhere in the app (`Money`, CORE-09: "never
  tiyin"; the flow's own §13 recommends "never"); "1500.50 UZS" says "Enter a valid price". Other currencies take two
  decimals, parsed without floats (quirk 25).
- **P2 free services:** fixed. A price of 0 is allowed (flow §8, the backend's minimum) with the soft note "Price is 0:
  a free service"; an empty or unreadable price is refused. The board's "> 0" gave way to the flow and the backend.
- **P2 currency:** fixed. A Currency picker (UZS · USD · EUR · RUB, plus a service's own if it's another); the stored
  price is kept only while both the price and the currency are untouched. Test.
- **P2 stale catalogue:** fixed. While the last catalogue read failed, clinic rows have no menu and Edit does nothing
  (the kept catalogue may be out of date). Test.
- **P2 swipe without discard question:** kept, deliberately. On the emulator the sheet asks to hide while the keyboard
  resizes it, so asking "Discard changes?" from that hook popped the question while typing; a changed sheet now refuses a
  swipe or scrim tap (nothing is lost), and Cancel / Back ask.
