- **[P2] Recognized cards discard backend errors.** [IntelCards.kt:108](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/intelligence/IntelCards.kt:108) converts failed patient lookups into empty appointment lists, displaying “Appointments: 0 / No appointments in this period.” The backend explicitly returns `error` and `spokenHint` for missing/unknown patients (`DoctorMorningToolService.kt:786–798`). Similarly, line 161 turns `brief_patient`’s error-only payload (backend line 847) into a blank briefing. Handle error payloads before dispatching to success-card types.

- **[P2] Patient appointment dates lose their year.** [Cards.kt:171](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/Cards.kt:171) renders ISO dates as only day and month, overriding the backend’s year-bearing `dateLabel`. Upcoming ranges can extend a year, and restored conversations persist across years, so “6 Nov” no longer identifies the appointment’s year. Flow 03/08 §§3–4 requires a year-bearing date; retain the year in the localized rendering.

---

- **[P2] Error payloads became empty cards:** fixed. A read-only card that comes back with `error` (an unknown patient,
  a briefing that couldn't be made) is `IntelCard.Failed`: the spoken hint, else the error, under the card's own
  title. Test and screenshot added.
- **[P2] Dates lost their year:** fixed; rows read "6 Nov 2026" (short month, year kept) in a 112 dp column.
