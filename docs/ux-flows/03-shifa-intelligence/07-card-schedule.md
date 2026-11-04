# Schedule card

| | |
|---|---|
| Area | Shifa Intelligence · action cards |
| Who sees it | Doctor only · PRO and PREMIUM |
| Reached from | Assistant turn carrying the `schedule` card (tool `get_today_schedule`), e.g. after "Summarize my schedule for today." |
| Screen type | Inline card inside the chat thread |
| Current Flutter source | `lib/features/intelligence/presentation/intelligence_action_cards.dart` (`_scheduleBody`), `intelligence_card_text.dart` (`intelligenceStatusLabel`, `intelligencePatientLabel`) |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §4.4, §9 payloads · tickets INTEL-05, INTEL-10 |

## 1. Purpose
Give the doctor today's visits at a glance inside the conversation, with a shortcut to the calendar. Daily, often the
first card of the day.

## 2. Entry points
- The welcome's "Today's schedule" starter prompt.
- Any question the model answers with the schedule tool ("what's next", "how busy am I", "who is at 10").
- Restored from history: the card renders again from its stored payload (data as of that time, not live).

## 3. Layout, top to bottom (phone portrait)
Shared card frame (all cards): white, radius 12, 1 px border, soft shadow, padding 14, top margin 10, under the
assistant text.
1. **Title** "Today's schedule" (brand, bold).
2. **Count line** "Appointments: 6" (`count` from the payload, else the number of rows).
3. **Rows** (max 8): `HH:mm` in a 52 dp column (bold) · "Patient name · Status".
4. **Empty copy** instead of rows: "No appointments today."
5. **"Open calendar"** text button, left-aligned.
6. Optional red error caption / green status caption (shared frame; unused here).

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Time | `startLocal` from the backend ("HH:mm" in practice time) | shown as sent; never recomputed on the device |
| Patient | `patientName`; placeholder "Patient" when empty | localised placeholder; full name, no truncation (wraps) |
| Status | `status` enum | COMPLETED→Completed, CONFIRMED→Confirmed, CANCELLED/CANCELED→Cancelled, REQUESTED→Requested, IN_PROGRESS/STARTED→In progress, PENDING→Pending, SCHEDULED→Scheduled, NO_SHOW→No-show, else raw |
| Count | `count` | integer; may exceed the 8 rows shown |
| Assistant text above | model text or spoken hint ("You have no appointments today." is translated) | — |

Worked example (payload → row):

| Payload row | Rendered (EN) | Rendered (UZ) |
|---|---|---|
| `{startLocal:"09:00", patientName:"Karimov Anvar", status:"CONFIRMED"}` | 09:00 · Karimov Anvar · Confirmed | 09:00 · Karimov Anvar · Tasdiqlangan |
| `{startLocal:"10:30", patientName:"", status:"IN_PROGRESS"}` | 10:30 · Patient · In progress | 10:30 · Bemor · Jarayonda |
| `{startLocal:"15:00", patientName:"Li Wei", status:"NEW_ENUM"}` | 15:00 · Li Wei · NEW_ENUM (raw) | same raw |

## 5. Primary flow (happy path)
1. Doctor asks for the schedule → text streams ("You have 6 visits today…") → the card appears under it as soon as
   the tool event arrives.
2. Doctor reads; taps "Open calendar" → Intelligence closes, Calendar tab opens on today.

## 6. Screen states
- **First load:** card renders only when the payload exists; no skeleton (the pending block precedes it).
- **Empty:** "No appointments today." with "Open calendar" still available.
- **Error on load:** payload with `error` and no rows → the generic body shows the error text (shared default).
- **Offline:** restored card renders; "Open calendar" works (navigation only).
- **Refreshing:** never refreshes itself; ask again for fresh data.
- **Partial data:** rows missing `patientName` show "Patient"; missing status shows nothing after the dot (target:
  omit the separator).
- **Permission needed:** none.
- **Locked by tier:** n/a (the screen gate applies; the card has no own gate).
- **Role difference:** doctor only (staff never reach Intelligence).

## 7. Interactions and secondary flows
- **Open calendar** → leave Intelligence → Calendar tab (today). Appointments are refreshed on the way out.
- Rows are **not tappable** today. Target: tapping a row opens that appointment (doc 05) — the payload carries
  `appointmentId` and `patientId`; a long-press offers "Open patient chart".
- Voice follow-ups work on the rows without tapping: "move the 10:30", "cancel the last one" — the listed ids travel
  back with the conversation so the backend knows which visit "the last one" is.
- Long-press: none.

## 8. Edge cases
- More than 8 visits → only 8 rows; the count line says the real total; no "and N more" today (target: add "+N more"
  linking to the calendar).
- 0 visits → empty copy.
- Visits across midnight in practice time → `startLocal` only; the date is implied "today" by the backend.
- Phone timezone ≠ practice timezone → times are practice times by construction (strings from the backend).
- Cancelled visits may be listed with status Cancelled; no visual de-emphasis today (target: strike-through or muted).
- Very long patient names wrap to a second line; the time column stays fixed.
- Status unknown to the map (new enum) → raw English code shown.
- Restored old card → stale data without any "as of" hint (target: show the message timestamp as "as of").
- Language switch → labels and statuses re-render; patient names unchanged.
- Font 200 %: rows wrap; time column keeps 52 dp.
- Screen reader: title, count, then each row as one sentence "09:00, Karimov Anvar, Confirmed".
- `count` missing → the number of rows is used; `count` smaller than the rows (backend bug) → show the rows' count.
- Two visits at the same time (double booking) → two rows with the same time; no merge.
- The visit currently in progress is not highlighted, unlike Home's timeline → target: same "now" chip as Home.
- Completed visits are listed here (Home hides them) — accepted: the card is a factual answer, not a to-do list.
- Asked at 23:50 practice time, answered after midnight → the backend decides which "today"; the card shows what
  the backend sent with no date line — target: add the date line when the payload carries one.
- Slow network (> 3 s): the text streams first and the card pops in later; keep the card slot height stable to
  avoid the text jumping (reserve the title row as soon as the tool event name is known).

## 9. Validation and messages
| Action | Rule | Message | Where |
|---|---|---|---|
| Open calendar | always allowed | — | — |
| Row tap (target) | appointment id present | — (row inert when missing) | — |

## 10. Navigation out
- Open calendar → Calendar tab; Intelligence mode closed; nothing unsaved.

## 11. Copy
| Key | English |
|---|---|
| `intelCardSchedule` | Today's schedule |
| `intelAppointmentsCount` | Appointments |
| `intelNoAppointmentsToday` | No appointments today. |
| `intelOpenCalendar` | Open calendar |
| `intelPatient` | Patient |
| `appointmentStatusCompleted` / `appointmentStatusConfirmed` / `appointmentStatusCancelled` / `appointmentStatusRequested` / `appointmentStatusInProgress` / `appointmentStatusNoShow` / `pending` / `scheduled` | Completed / Confirmed / Cancelled / Requested / In progress / No-show / Pending / Scheduled |
| **NEW** `intelAndMoreRows` | +{count} more |

## 12. Known problems in the current app (do not carry over)
- Rows are inert; the obvious tap (open the visit) does nothing.
- No "as of" indication on restored cards.
- Status unknown → raw code leaks.

## 13. Open questions for design
- Should the card highlight the current / next visit (as Home does)?
- Compact mode when more than 8 rows: list or "Open calendar" only?
