# Slot duration sheet, time-range pickers and buffer settings

| | |
|---|---|
| Area | 04 Calendar & schedule |
| Who sees it | Doctor only (schedule setup, date-specific hours). No tier gate. |
| Reached from | [10-availability-setup](10-availability-setup.md) Add/Edit range; [11-date-specific-hours](11-date-specific-hours.md) Add expansion; (the preview's "Adjust duration" uses a dropdown, not this sheet) |
| Screen type | Bottom sheet (duration) + system time pickers (start/end) |
| Current Flutter source | `lib/features/schedule/presentation/setup_schedule_screen.dart` (`_pickTime`, `_pickDuration`, `_addSlot`, `_editSlot`); unused copy in `calendar_screen.dart` (`_pickDuration`, `_editText` — dead code) |
| Spec | `docs/kmp-spec/03-calendar-schedule.md` §2.2, §9 item 1; ticket SCHED-2 |

## 1. Purpose
Choose the working window of a day (start/end) and how the backend cuts it into bookable slots (slot size). This defines the grain of the whole calendar: booking, rescheduling and "adjust duration" all step in these units. Set once per range; rarely changed.

**Buffers do not exist.** There is no "10 min between patients", no travel time, no per-location buffer, no minimum notice, no maximum per day. Target decision in §13.

## 2. Entry points
- Weekly editor → **Add** (start picker 08:00 → end picker 17:00 → duration 30).
- Weekly editor → tap "Time Period" or "Slot timeframe" on a range (prefilled with that range; **all three** pickers run even when only the duration is being changed).
- Date-specific → Add expansion (dates first, then start 17:00 → end +1 h → duration 30).

## 3. Layout, top to bottom (phone portrait)
**Time picker** = Material time picker dial, forced 24 h, localised; OK/Cancel. Nothing custom.

**Duration sheet** = plain bottom sheet, list of six rows separated by hairlines: "10 minutes", "15 minutes", "20 minutes", "30 minutes", "45 minutes", "60 minutes". Tap picks and closes. No title, no current-value highlight, no custom value, no drag handle.

Target: one "Hours" sheet per range: From / To as inline wheel or chip rows in 5-min steps; slot size as 6 chips with the current one selected; live preview line "09:00–13:00 → 8 slots of 30 min"; optional (if adopted) "Break between patients: 0 / 5 / 10 / 15 min"; **Save** / **Remove range**.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Start/End | `TimeOfDay`, minute resolution | shown `HH:mm`; stored as `"HH:mm"` strings; minute granularity allowed (09:07 is legal) — target: 5-min steps |
| Slot size | 10/15/20/30/45/60 min | `slotMinutes` per range; all ranges of a doctor may differ |
| Slot count preview | (end − start) ÷ slot size, floor | **target**; remainder minutes are not bookable (UNVERIFIED backend rounding) |
| Grain used elsewhere | GCD of free-slot lengths at a venue on a day (5…240) | drives "Adjust duration" steps |
| Buffers | none | — |

Time rules: wall clock, practice timezone; a range never crosses midnight (end > start). Two ranges on the same day can touch (12:00/12:00) but not overlap. The default new range is 08:00–17:00 — nine hours, 18 slots of 30 min, no lunch.

Examples of what a range produces (backend slot generation; remainder handling UNVERIFIED):

| Range | Slot size | Slots patients can book | Note |
|---|---|---|---|
| 09:00–13:00 | 30 | 8 (09:00 … 12:30) | clean |
| 09:00–13:00 | 45 | 5 (09:00 … 12:00), 12:45–13:00 unused | 15-min remainder |
| 09:00–09:20 | 30 | 0 | range shorter than the slot |
| 09:00–12:00 + 13:00–17:00 | 20 / 30 | 9 + 8 | two ranges = lunch break; sizes may differ |
| 08:00–17:00 (default) | 30 | 18 | no break unless the doctor splits it |
| 22:00–02:00 | any | rejected | crosses midnight → two ranges on two days |

A booking can later merge consecutive slots (09:00 + 09:30 → one 60-min visit), so a *smaller* slot size gives more flexibility at the cost of more rows in the agenda.

## 5. Primary flow (happy path)
1. Add → dial shows 08:00 → OK.
2. Dial shows 17:00 → set 13:00 → OK.
3. Sheet → "30 minutes".
4. Range card "08:00 - 13:00 · 30 minutes" appears; nothing is saved until Complete on the parent screen.

## 6. Screen states
- **First load:** instant.
- **Empty:** n/a.
- **Error on load:** n/a.
- **Offline:** works (local).
- **Refreshing:** n/a.
- **Partial data:** n/a.
- **Permission needed:** n/a.
- **Locked by tier:** n/a.
- **Role difference:** n/a.

## 7. Interactions and secondary flows
- **Cancel on a time picker (Add flow)** → **the default value is used anyway** and the flow continues (bug) — target: cancel aborts the whole add.
- **Cancel on a time picker (Edit flow)** → keeps the previous value and continues to the next picker.
- **Cancel on the duration sheet** → default/previous duration used.
- **Tap outside the sheet** → same as cancel.
- **Keyboard input mode** of the Material picker (keyboard icon) → allowed; 24 h.

## 8. Edge cases
- **Typing 25:00 in keyboard mode:** picker rejects.
- **Start = end:** rejected "End time must be after start time." (checked after both pickers; the duration sheet is not shown).
- **End − start < slot size** (09:00–09:20, 30 min): accepted; likely zero slots → target warning.
- **End − start not a multiple of the slot size** (09:00–10:15, 30 min): accepted; last 15 min unbookable silently.
- **Odd minutes (09:07):** allowed; slots at 09:07, 09:37… Target: snap to 5.
- **Many ranges per day (e.g. six 1-hour ranges):** allowed; each has its own slot size.
- **Phone set to 12-h clock:** the picker is forced to 24 h here (good); the block dialog's pickers are **not** forced (inconsistent).
- **200 % font:** Material picker handles it; sheet rows grow.
- **Language switch:** "minutes" suffix localised; dial labels localised by the system.
- **Screen reader:** rows announced "30 minutes, button"; no indication of the current value (target: "selected").
- **Rotation:** Material picker switches to landscape layout; the sheet persists.
- **Editing a range that already has appointments booked in the coming weeks:** allowed; the backend decides what happens to slots that disappear (UNVERIFIED — may orphan appointments or reject the PUT). Target: warn "Changing hours affects {n} upcoming appointments" (needs a count endpoint; none exists).
- **Shrinking the slot size from 30 to 15 on a day with existing 30-min bookings:** bookings stay as they are; new free slots are 15 min; "adjust duration" steps become 15.
- **Two ranges with different slot sizes touching (09:00–12:00 @30, 12:00–15:00 @20):** chaining a booking across 12:00 is allowed by the UI (same location); the GCD grain becomes 10 for that day.
- **Range of exactly one slot (09:00–09:30 @30):** fine; one row in the agenda.
- **Duration sheet opened while the keyboard is up (keyboard picker mode):** keyboard must be dismissed first; today the sheet appears above the keyboard area.

## 9. Validation and messages
| Field/action | Rule | When checked | Message (English) | Where shown |
|---|---|---|---|---|
| End | > start | after end picker | "End time must be after start time." | banner → target inline in the sheet |
| Range | no overlap with the day's other ranges | after end picker | "This time overlaps an existing slot." | banner → inline |
| (target) Fit | (end − start) ≥ slot size | live | "No slot fits in this range" | inline |
| (target) Remainder | (end − start) mod slot ≠ 0 | live | "Last {n} min won't be bookable" | inline hint |

## 10. Navigation out
- Returns to the day editor with the range added/updated. Nothing persisted until the parent's Complete.

## 11. Copy
- `minutes` "minutes"; `timePeriod` "Time Period"; `slotTimeframe` "Slot timeframe"; `endTimeMustBeAfterStartTime`; `thisTimeOverlapsExistingSlot`; `add` "Add"; `remove` "Remove".
- **NEW:** "Hours" · "From" · "To" · "Slot size" · "{n} slots of {m} min" · "Break between patients" (if adopted) · "No slot fits in this range" · "Last {n} min won't be bookable" · "Save range" · "Remove range".

## 12. Known problems in the current app (do not carry over)
- Three modal pickers in sequence to change one value; Cancel does not cancel in the Add flow.
- Duration sheet has no title, no current value, no custom entry.
- Minute-level start times allowed; no fit/remainder feedback.
- Default range 08:00–17:00 with no break encourages a nine-hour unbroken schedule.
- 12 h vs 24 h inconsistency between this screen and the block dialog.

## 13. Open questions for design
- **Buffers:** add "break between patients" as a real setting? It needs backend support (slot generation) — UNVERIFIED; the alternative is to keep "slot size" as the only knob and let the doctor pick 20 min for a 15-min visit.
- Slot size per range (today) vs one default with per-range override?
- Time entry: wheel, chips in 15-min steps, or the Material dial?
- Should the doctor see the resulting slots ("09:00, 09:30, …") before saving?
