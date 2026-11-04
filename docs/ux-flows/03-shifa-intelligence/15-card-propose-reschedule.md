# Propose reschedule card (move)

| | |
|---|---|
| Area | Shifa Intelligence · action cards |
| Who sees it | Doctor only · PRO and PREMIUM |
| Reached from | Assistant turn with the `propose_reschedule` card after "Move Karimov's Thursday visit to Friday 11:00" |
| Screen type | Inline card inside the chat thread |
| Current Flutter source | `intelligence_action_cards.dart` (`_proposeMoveBody`, `_moveSlot`), `intelligence_card_text.dart` (moving sentences: "{name} is on {when} at {time}. Which day should I move that visit to?", "Moving {name} to {time} on {when}.", "No free slots on {when} to move {name}…", "{when} has already passed. Name a future day for {name}.") |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §3.6, §4.4, §0, §11.2 (missing `locationId`) · ticket INTEL-06 |

## 1. Purpose
Move an existing visit to a new slot with one confirmation. Weekly.

Same two modes as file 14: **backend-confirmed** (`workflowId`; the doctor says "yes") and **local button**
(`PUT /api/appointments/{id}/change`).

## 2. Entry points
- Reschedule requests; the backend first identifies the visit (it may ask "Which day should I move that visit
  to?" or list the patient's visits, file 08).
- Restored from history.
- Links the thread to the patient.

## 3. Layout, top to bottom (phone portrait)
1. **Title** "Propose reschedule".
2. **Key–value rows**: Patient · name; Current · "30 Okt 2026 10:30" (current date label + time, translated);
   New date · "31 Okt 2026" (or "—"); Time · "11:00" (only when a preferred slot exists).
3. **Body by state:**
   - no slots → spoken hint translated, else "No free slots for this day."
   - backend-confirmed, preferred slot, not moved → hint line, else "Please confirm this exact reschedule in the
     conversation." (English fallback today — NEW key)
   - local, preferred slot → filled "Confirm 11:00"
   - no preferred slot → outlined buttons for every slot (local mode)
4. **Cancel** text button while not moved → hides the card.
5. Red error line / green "Appointment moved." or the backend status "Moved Karimov Anvar to 11:00 on Friday 31
   October."

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Appointment | `appointmentId` | required for the local call |
| Current | `currentDateLabel` + `currentStartLocal` | "30 Okt 2026 10:30"; "—" when missing |
| New date / slots / preferred | as file 14 | — |
| Local call | `PUT /api/appointments/{id}/change {startAt, slotMinutes}` — **no `locationId` today** (bug; target: send `locationId` like the booking body) | 200/204 ok; 409 conflict; else "Reschedule failed: {code}" |
| After success | notifications + appointment providers refreshed for the new day | — |

## 5. Primary flow (happy path, backend-confirmed)
1. "Move Karimov from Thursday to Friday 11" → text "Moving Karimov Anvar to 11:00 on Friday 31 October. Say yes to
   confirm." + card.
2. "Yes" → backend moves → `booked: true` payload → green status "Moved …"; spoken; calendar refreshed.

```mermaid
flowchart TD
  REQ[Move request] --> ID{Visit identified?}
  ID -->|no| ASK[Text: which visit / which day · maybe patient appointments card]
  ASK --> REQ
  ID -->|yes| P{Payload}
  P -->|workflowId| RO[Read-only card + hint] -->|yes| DONE[Moved … · refresh]
  P -->|local, preferred| BTN[Confirm {time}] --> PUT[PUT /change]
  P -->|local, none preferred| ALL[Slot buttons] --> PUT
  PUT -->|200/204| OK[Appointment moved.]
  PUT -->|409| C[That slot is no longer free. Pick another.]
  PUT -->|other| F[Reschedule failed: {code}]
  P -->|no slots| N[No free slots on {when} to move {name}. Please choose another day.]
```

## 6. Screen states
- **Empty (0 slots):** rows + hint + Cancel.
- **Error on load:** payload `error` → shown via hint; otherwise default.
- **Offline:** local Confirm fails with exception text (target: network message).
- **Partial data:** missing appointment id or slot → "Missing appointment or slot."
- **First load / Refreshing / Permission / Locked / Role:** n/a.

## 7. Interactions and secondary flows
- **Confirm {time}** → busy → success / error.
- **Slot button** → moves to that slot.
- **Cancel** → hides the card; the visit is unchanged.
- **Say "yes"** (backend mode) → moved.
- **Say a different day** → new proposal; the old card stays (target: mark superseded).

## 8. Edge cases
- Target day in the past → backend text "{when} has already passed. Name a future day for {name}." and no slots.
- Same slot as current → backend should refuse; local mode would PUT and get 409 or 200 (no-op) — show whatever
  the status says.
- Visit already completed / cancelled → backend answers in text ("That appointment is not on your calendar.").
- Visit at another location than the new slot → local mode loses the location (no `locationId`) — fix in target.
- 409 → conflict line; other slots stay tappable.
- Double tap → disabled while busy.
- Patient notified? The backend decides; the card does not say. Target: status sentence from the backend should
  mention it ("The patient was notified in the app.") as the cancellation flow does.
- Restored moved card → green line only.
- 200 % font / screen reader: as file 14.

## 9. Validation and messages
| Action | Rule | Message | Where |
|---|---|---|---|
| Confirm | appointment id + slot start | Missing appointment or slot. | red line |
| Confirm 200/204 | — | Appointment moved. | green line |
| Confirm 409 | — | That slot is no longer free. Pick another. | red line |
| Confirm other | — | Reschedule failed: {code} | red line |
| Exception | — | raw text → target "Reschedule failed. Check your connection and try again." | red line |

## 10. Navigation out
None; calendar refreshed on leaving.

## 11. Copy
| Key | English |
|---|---|
| `intelCardProposeMove` | Propose reschedule |
| `intelPatient` / `intelCurrentSlot` / `intelNewDate` / `intelTime` | Patient / Current / New date / Time |
| `intelConfirmTime` | Confirm {time} |
| `cancel` | Cancel |
| `intelNoFreeSlots` | No free slots for this day. |
| `intelMovedOk` | Appointment moved. |
| `intelSlotConflict` | That slot is no longer free. Pick another. |
| `intelRescheduleFailed` | Reschedule failed: {code} |
| `intelMissingAppointmentOrSlot` | Missing appointment or slot. |
| backend sentences (translated) | Moving {name} to {time} on {when}. · Moving {name} to {when}. Which time? · {name} is on {when} at {time}. Which day should I move that visit to? · No free slots on {when} to move {name}. Please choose another day. · {when} has already passed. Name a future day for {name}. · I could not tell which visit to move. Say the patient and the day. |
| **NEW** `intelConfirmRescheduleWithShifa` | Confirm this exact reschedule with Shifa — say or type “yes”. |
| **NEW** `intelRescheduleFailedNetwork` | Reschedule failed. Check your connection and try again. |

## 12. Known problems in the current app (do not carry over)
- Local reschedule omits `locationId` (the voice path used to send it).
- English fallback hint without a key.
- Raw exception text on network failure.
- No "Other times" toggle here, unlike the booking card (inconsistent).

## 13. Open questions for design
- Show "before → after" as one visual row (strike-through old, bold new)?
- Offer a tappable "Yes, move" in backend mode?
