- **P2 — Occupancy is inaccessible to screen readers.** [MonthPicker.kt:199](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/MonthPicker.kt:199) exposes only the numeric date and selection state. Appointment dots and blocked shading have no semantic equivalent. The board’s day labels include appointment counts and blocked status (`DaCalendar.dc.html:476`); add localised equivalents.

- **P2 — Partial blocks can appear as fully blocked days.** [MonthPicker.kt:75](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/MonthPicker.kt:75) marks any block-only day red, including a single midday block. The board checks for a block starting at midnight (`DaCalendar.dc.html:472`), independently of appointment/free-slot counts. Carry that information into the shading input.

- **P2 — Navigation lacks the required date bounds.** [MonthPicker.kt:111](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/MonthPicker.kt:111) and line 121 always enable and dispatch month changes, including backwards from January 2020 and forwards from December 2030. Flow §3.4/§8 requires those boundary chevrons to be disabled.

- **P2 — Horizontal month swipes do nothing.** [MonthPicker.kt:107](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/MonthPicker.kt:107) installs no swipe handler; only the chevrons invoke `onMonth`. Horizontal paging is explicitly required by CAL-3 and flow §3.4.

- **P3 — Half-booked days use the wrong board shade.** [MonthPicker.kt:78](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/MonthPicker.kt:78) switches to Busy at one-third occupancy. The rendered board binds `c.cls` (`DaCalendar.dc.html:88`), which uses Light below `0.67` (line 476); CSS maps that to teal 15%. Thus four appointments plus four free slots incorrectly render teal 25%. The board’s separately computed `bg` value is not bound to the button.---
**Answers (all fixed):**
1. *Occupancy not spoken:* each day reads its date, then "N appointments" and "Day blocked" when they apply.
2. *Partial blocks shown as a day off:* the red day now needs a block that starts at midnight in the practice zone (`DayOccupancy.fullDayBlock`, computed from the entries with the practice clock); a midday block alone leaves the day plain. Tested.
3. *No month bounds:* the chevrons (and the swipe) stop at January 2020 and December 2030 (`MONTH_RANGE`).
4. *No swipe:* a horizontal swipe over the days pages the month.
5. *Half-booked shade:* light teal now runs up to two thirds booked, as the board's bound classes do. Tested.
Local; no second round.
