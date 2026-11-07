- **P1 — Reopening during a save permits competing PATCHes.** [TimeZonePicker.kt:120](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/TimeZonePicker.kt:120): `open()` resets `saving`, but dismissing the sheet leaves its ViewModel coroutine running. Close during a slow save, reopen, and select again: a second request starts; the first completion can close the reopened sheet and overwrite its state.

- **P2 — Confirmation disappears before saving finishes.** [TimeZonePicker.kt:143](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/TimeZonePicker.kt:143): clearing `confirm` immediately removes the warning. Neither sheet displays `saving`; failures appear in the picker instead of retaining the dialog with an inline error and retry button, contrary to flow 06 §§5–7.

- **P2 — Rotation resets search and confirmation.** [TimeZonePicker.kt:172](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/TimeZonePicker.kt:172): every composition entry calls `open()`, replacing the retained state. Activity recreation loses the query and pending warning, violating both flows’ rotation requirements.

- **P2 — Offset search includes zones with the opposite offset.** [TimeZones.kt:55](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/utils/TimeZones.kt:55): ID matching runs before offset matching. Searching `+5` includes `Etc/GMT+5`, whose actual offset is **UTC−5**; searching `utc+5` produces different results. Offset-shaped queries need to match computed offsets exclusively.

- **P2 — Warning drops date changes and can use the wrong DST offset.** [TimeZonePicker.kt:235](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/TimeZonePicker.kt:235): converting today’s actual 09:00 then extracting only `.time` loses “previous day” for Auckland → Los Angeles. It also uses offsets at 09:00 rather than now: before Berlin’s autumn transition, the labels show UTC+2 while the example uses UTC+1. Flow 06 §4 explicitly requires the current offset difference and date qualifier.

- **P2 — Invalid stored zones produce a misleading reassurance.** [TimeZonePicker.kt:215](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/TimeZonePicker.kt:215): failed conversion (`null`) takes the “Times will look the same today” branch. An invalid imported zone therefore claims a known impact instead of identifying the invalid timezone and omitting the example, as flow 06 §8 requires.---
**Answers (all fixed):**
1. *Reopening during a save (P1):* `open()` keeps a sheet that is still saving (and one already open), so no second PATCH starts and the first answer lands in the same state. Tested.
2. *Warning vanishes before the save ends:* the warning stays up while saving; a failure shows inside it (message and request id) and Confirm retries. Tested.
3. *Rotation resets the sheet:* the ViewModel marks the sheet open until it closes, so a new composition keeps the search and the pending warning. Tested.
4. *"+5" finds Etc/GMT+5:* an offset-shaped query now matches the offset now only, never the id. Tested both ways.
5. *Date change and DST in the example:* the example is 09:00 shifted by the offsets now (the ones the labels show) and says "the day before" / "the next day". Tested with Auckland ↔ Los Angeles and Berlin in summer.
6. *Invalid stored zone:* no example line at all rather than "Times will look the same".
Second round below.
