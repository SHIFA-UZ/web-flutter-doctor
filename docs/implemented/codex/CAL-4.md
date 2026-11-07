- **P1 — Opening before the profile loads selects the wrong day.** [CalendarScreen.kt:162](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:162): initialization uses UTC when `profile.me` is null. When the profile arrives, lines 170–172 replace only the clock; selected date/month remain unchanged. Near midnight, opening Calendar during profile loading leaves yesterday’s agenda selected instead of practice-zone today.

- **P1 — Changing practice timezone retains days grouped under the old zone.** [CalendarScreen.kt:170](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:170): the zone observer neither invalidates cached days nor reloads them. Entries near midnight are displayed with new wall times inside the old date’s cached agenda; returning within the 60-second throttle still serves that cache.

- **P2 — No user-accessible refresh after a successful load.** [CalendarScreen.kt:263](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:263): the page has ordinary scrolling without pull-to-refresh, and the only forced-refresh control appears after an error (line 277). The board’s Refresh action is also absent. A doctor staying on Calendar cannot refresh bookings made elsewhere, contrary to flow 15.

- **P2 — Long appointment reasons can hide the patient name.** [CalendarScreen.kt:418](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:418): the unweighted time/reason column is measured before the weighted patient column and has no width cap. In details mode, a long reason consumes the available width, leaving the name/location column zero width. `maxLines = 1` does not reserve space for the patient; flow 02 requires a bounded reason column.

- **P2 — Dismissing filters commits changes instead of cancelling them.** [CalendarScreen.kt:498](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:498): checkboxes mutate the active filter immediately, while dismissal only closes the sheet. There is no draft, Cancel, or Apply, so backing out still hides appointments. Both flow 01 §7 and the board specify draft changes committed by Apply.

- **P2 — Appointment status and payment information are missing from the compact agenda.** [CalendarScreen.kt:406](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:406): compact rows show only location, whereas the board includes status and video-payment state in their summary. Details mode adds status but never reads `paymentStatus` (lines 477–491), leaving paid, pending, and failed video appointments indistinguishable in either mode.

- **P2 — Duration is omitted even with “Show details” enabled.** [CalendarScreen.kt:418](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:418): the right column renders only time and reason/block caption. CAL-4 explicitly includes the duration badge, and the board retains it for appointments longer than five minutes.

- **P3 — `NO_SHOW` bypasses localization.** [CalendarScreen.kt:524](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:524): this supported appointment status falls through to the raw uppercase backend code in every locale. Flow 02 specifies “No-show,” and CAL-4’s Fix line requires localized strings.---
**Answers (all fixed):**
1. *Wrong day when the profile loads late (P1):* when the practice zone arrives or changes, "today" moves with it unless the doctor had picked another day. Tested.
2. *Days grouped under the old zone (P1):* a zone change drops the calendar cache and reads the day again. Tested.
3. *No refresh after a good load:* a Refresh button in the header forces the day.
4. *Long reason hides the name:* the right column is capped (160 dp).
5. *Filters commit on dismiss:* the sheet edits a draft; Apply commits, dismissing drops it. Tested.
6. *Status and payment missing in compact rows:* the compact line reads place · status · payment (video); details add the payment chip for video.
7. *No duration badge:* details show "30 min" / "1 h" / "1 h 30 min" for appointments over 5 minutes.
8. *NO_SHOW not localised:* "No-show" in en / uz / ru.
Local; no second round.
