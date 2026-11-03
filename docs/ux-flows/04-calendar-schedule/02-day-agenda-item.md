# Day agenda row (free slot · appointment · blocked) and status chips

| | |
|---|---|
| Area | 04 Calendar & schedule |
| Who sees it | Doctor (Calendar tab, staff sections); Clinic staff (inside the clinic doctor schedule screen). No tier gate. |
| Reached from | Rendered inside [01-calendar-tab](01-calendar-tab.md) and [14-staff-calendar-switcher](14-staff-calendar-switcher.md) |
| Screen type | Component (list row) + chip set used by the preview sheet |
| Current Flutter source | `lib/features/calendar/presentation/calendar_screen.dart` (`CalendarDayEntriesList`, `_buildStatusChip`, `_buildPaymentChip`), `lib/features/calendar/domain/calendar_models.dart` |
| Spec | `docs/kmp-spec/03-calendar-schedule.md` §1.3 "Day list", §1.3 "Status chip", §7; tickets CAL-1, CAL-4 |

## 1. Purpose
One row = one slice of the doctor's day. The doctor scans rows to know who is next, where (clinic vs video), how long, and whether a gap is bookable. Read dozens of times a day.

## 2. Entry points
- Produced by the day agenda list for every entry returned by `GET /api/calendar?day=`.
- Status/payment chips are reused in the appointment preview header ([05](05-appointment-preview-sheet.md)).

## 3. Layout, top to bottom (phone portrait)
Rows are white cards, 12 dp radius, soft shadow, 16 dp horizontal / 8 dp vertical padding, 12 dp apart. Selected row = 2 dp teal border (target: selection only matters on tablet two-pane; on phone the row is tapped and a sheet opens).

**A. Free slot row** (min height 64)
1. Left: 40 dp white circle with teal "+" icon.
2. Middle: title "Free Slots" (semibold); below, a location chip (teal pill, pin icon, label = the location label the backend sent, e.g. "Main Clinic"; blue video pill when the label contains "video").
3. Right: time range "09:00 - 09:30" (semibold).

**B. Appointment row** (min height 68, grows with duration: `68 + minutes × 0.45`, capped at 154 — a 3-hour visit is a tall card)
1. Left: 40 dp avatar — patient photo if any, else grey circle with white person icon.
2. Middle: patient name (semibold, max 2 lines, ellipsis); location chip (video = blue pill with camera icon and "Video Call"; in-person = teal pill with pin icon and the location label).
3. Right column (right-aligned): time range "10:00 - 10:30"; duration badge (tiny teal-tinted pill: "30 minutes", "1h", "1h 30 minutes") only when duration > 5 min; reason line in grey 12 pt ("Check Up").
4. **Completed** appointments: name and time get a red strike-through and a 2.5 px red line is drawn across the whole card.

**C. Blocked row** (min height 64)
1. Left: red-100 circle with red block icon.
2. Middle: reason as title in dark red ("Emergency", "Vacation"); if no reason: "Blocked".
3. Right: time range; below it the caption "Blocked" in red 12 pt.
4. Card background red-50 with red-200 border.

**Status chip** (inside preview header, target also on the row): pill, 11 pt bold, icon 13 dp, 10/6 dp padding, 25 % border of the text colour.

| Status (backend) | Label | Colour | Icon |
|---|---|---|---|
| `COMPLETED` | "Complete" | green-50 bg / green-800 text | check circle |
| `CANCELLED` | "Cancel" (today reuses the button label — **target "Cancelled"**) | red-50 / red-700 | cancel |
| `IN_PROGRESS` | "In Progress" | blue-50 / blue-700 | timelapse |
| `CONFIRMED` | "Confirmed" | amber-50 / amber-800 | verified |
| `REQUESTED`, `NO_SHOW`, anything else | raw backend string in caps | grey-100 / grey-800 | info |
| empty | "UNKNOWN" (key `unknown`, no English value) | grey | info |

**Payment chip** (video appointments only): "Payment: Paid" green · "Payment: Pending" orange · "Payment: Failed" red · "Payment: Not required" blue-grey · unknown → grey with the raw state. All `payment*` keys currently lack English values.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Time range | `startAt`/`endAt` UTC → practice TZ wall clock | `HH:mm - HH:mm`, 24 h, zero-padded, always practice timezone |
| Duration | end − start in wall-clock minutes, clamped 0–1440 | `{h}h {m} minutes` / `{h}h` / `{m} minutes`; hidden when ≤ 5 min; hidden when negative (crosses midnight) |
| Patient name | `patientName` | fallback today "Appointments"; **target "Patient"** |
| Photo | `patientPhotoUrl` ?? `photoUrl` | 40 dp circle, cached image, person icon fallback |
| Location label | `locationLabel` ?? `location` ?? "" | chip hidden when empty; "video" substring → video chip with label "Video Call". **Target:** explicit `isVideo` flag from backend (spec bug 10), label stays as sent |
| Reason | `reason` | default "" (booking writes "Check Up" when empty); single line |
| Block reason | `blockReason` | fallback "Blocked" |
| Status | `status` raw string | mapped as above; **target:** enum `REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW, UNKNOWN` |
| Payment | `paymentStatus` | `PAID, PENDING, FAILED, NOT_REQUIRED`, else raw |
| Staff colour | position of the doctor in the sorted clinic roster → 8-colour palette | only when ≥ 2 schedulable staff; applied to the "+" icon, selection border and duration badge tint |

Time rules: every time on the row is the practice timezone (clinic timezone in a colleague's section). Nothing on the row depends on "now" — the row never says "in 10 min" or greys out past items (target: past slots on today stay bookable, so no greying; past *appointments* could be muted — open question).

## 5. Primary flow (happy path)
1. Row appears from the day load (skeleton → content).
2. Doctor reads name · place · time at a glance.
3. Tap anywhere on the card → opens the matching panel: free slot → [06-book-appointment](06-book-appointment.md); appointment → [05-appointment-preview-sheet](05-appointment-preview-sheet.md); blocked → [09-block-time](09-block-time.md) §"Blocked details".

## 6. Screen states
- **First load:** skeleton row = circle + two bars + right bar, same heights as the real rows.
- **Empty:** not a row state; the list shows "No items for this day".
- **Error on load:** list-level, see [01](01-calendar-tab.md) §6.
- **Offline:** rows render from cache; photos fall back to the icon if not cached.
- **Refreshing:** rows are replaced in place; heights identical so no jump. Selection (tablet) must survive the swap — key rows by `appointmentId` / `blockId` / `startAt+locationId`.
- **Partial data:** an entry missing `startAt` or `endAt` is dropped (never shown); missing name/photo/reason → fallbacks above.
- **Permission needed:** n/a.
- **Locked by tier:** n/a.
- **Role difference:** identical for staff; in a colleague's section the "+" and accents use that colleague's colour.

## 7. Interactions and secondary flows
- **Tap** → open panel (see §5). Ripple on the card.
- **Long-press** → nothing today. Target candidates: quick actions (Call patient, Open chat, Cancel) — open question.
- **Swipe** → nothing; do not add swipe-to-cancel (destructive without confirmation is forbidden by area rules).
- **Tap photo / name** → same as tapping the row (no separate target on the row).

## 8. Edge cases
- **Name 2+ lines:** wraps to 2 lines then ellipsis; card grows; right column stays vertically centred.
- **Very long location label** (e.g. a full street address): chip text is 1 line ellipsis inside the chip; chip width bounded by the middle column.
- **Very long reason:** currently a single `Text` without maxLines in a `Flexible` column → can push the time range left or overflow. Target: 1 line, ellipsis, max 45 % of the card width.
- **Reason empty:** line omitted (zero height) — card still min 68.
- **Duration 5 min or less:** no badge. **Duration > 24 h / negative (crosses midnight):** badge hidden, card min height.
- **Duration 3 h+:** card hits the 154 dp cap; content stays vertically centred leaving empty space (design may prefer a fixed height + duration badge instead of height = duration).
- **Appointment with no `patientId`:** row looks normal; in the preview the name is not tappable.
- **Status CANCELLED on the row:** no visual difference today → target: muted text + "Cancelled" chip; never strike-through (that means completed).
- **COMPLETED + video + payment PENDING:** strike-through row; chips only in preview.
- **Two entries with identical start time** (double booking or free slot + appointment at the same time from two locations): both rows, stable order = start time then backend order.
- **Free slots from two locations interleaved** (09:00 Clinic A, 09:00 Clinic B, 09:30 Clinic A…): the location chip is the only differentiator; target: group by location or colour-code the chip per location.
- **Block spanning multiple days:** the backend returns a slice per day; the row shows 00:00 - 23:59 on middle days (end 23:59 is the current block bug — target 24:00 / "All day" label).
- **200 % font:** name wraps, time range may wrap to two lines ("10:00 -" / "10:30"); duration badge text grows; keep min touch height 48.
- **Language switch:** "Free Slots", "Blocked", "Video Call", "minutes" re-render; Russian "минут" is longer → badge widens.
- **Screen reader:** announce as one item: "Appointment, Anna Karimova, Video call, 10:00 to 10:30, 30 minutes, Check up". Free slot: "Free slot, Main Clinic, 09:00 to 09:30, double-tap to book".
- **Right-to-left:** n/a (no RTL locale).

## 9. Validation and messages
| Field/action | Rule | When checked | Message (English) | Where shown |
|---|---|---|---|---|
| Entry parse | `startAt` and `endAt` present and ISO | on fetch | — (entry silently skipped) | — |
| Ids | numeric `appointmentId`/`patientId`/`blockId` | on fetch | — (string ids become null → preview loses actions) | — |
| n/a otherwise | | | | |

## 10. Navigation out
- Tap → panel for that entry (state: selected entry + its doctor id). Returning refreshes the day.

## 11. Copy
- `freeSlots` "Free Slots" (row title — **target singular "Free slot"**), `blockedTime` "Blocked", `videoCall` "Video Call", `minutes` "minutes", `appointments` "Appointments" (name fallback — **target `patient` "Patient"**).
- Status: `complete` "Complete", `inProgress` "In Progress", `confirmed` "Confirmed", `unknown` (**no English — add "Unknown"**); **NEW** "Cancelled", "Requested", "No-show".
- Payment: `paymentLabel` "Payment: {{status}}", `paymentPaid`, `paymentPending`, `paymentFailed`, `paymentNotRequired`, `paymentUnknown`, `paymentStateRaw` — **all missing English values; add "Paid" / "Pending" / "Failed" / "Not required" / "Unknown" / "{{state}}"**.
- Duration badge: `{h}h {m} minutes` (**NEW** short forms "1 h 30 min" recommended for Russian length).

## 12. Known problems in the current app (do not carry over)
- Video vs clinic is decided by the word "video" inside the location label; a clinic called "Videoclinic" would show as a video call.
- Cancelled status label reuses the "Cancel" button string.
- Name fallback is the word "Appointments".
- Card height encodes duration (68–154 dp) — tall empty cards for long visits.
- Reason line can overflow; photo loaded with a raw network image (no cache, no placeholder animation).
- Status and payment chips are only in the preview, never on the row; payment keys have no English text.
- Selection identity is by object instance, lost after each refresh.

## 13. Open questions for design
- Should the status chip (Confirmed / In progress / Cancelled) appear on the row, or is the strike-through for completed enough?
- Fixed-height rows vs height proportional to duration?
- Long-press quick actions on the row — yes/no, and which three?
- Colour per location for multi-location doctors (chip tint) — needed, or is the label enough?
- Is the "+" free-slot row the right affordance, or should free time be drawn as a gap with a "Book 09:00–09:30" ghost button?
