# Propose appointment card (book)

| | |
|---|---|
| Area | Shifa Intelligence · action cards |
| Who sees it | Doctor only · PRO and PREMIUM |
| Reached from | Assistant turn with the `propose_appointment` card after a request like "Book Karimov Thursday at 10:30" / "Find a slot for Karimov next week" |
| Screen type | Inline card inside the chat thread |
| Current Flutter source | `intelligence_action_cards.dart` (`_proposeBookBody`, `_bookSlot`, `_refreshHomeAfterScheduleChange`), `home_voice_session.dart` (`booked` tool handling, `_refreshAfterConfirmedWorkflow`, `_assistantLine`), `intelligence_card_text.dart` (booking sentences) |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §3.6, §4.4, §0 · tickets INTEL-06, INTEL-10 |

## 1. Purpose
Turn a spoken or typed booking request into a real appointment with one explicit confirmation. Daily for doctors who
book by voice between patients.

**Two confirmation modes exist today, decided by the backend payload:**
- **Backend-confirmed (`workflowId` present)** — the card is read-only; the doctor confirms by saying or typing
  "yes" / the time; the backend books and sends a second payload with `booked: true` and a status line. This is the
  flow on the current backend.
- **Local buttons (no `workflowId`)** — the card has "Confirm 10:30", "Other times" and slot buttons; the app books
  directly. Kept for backends that do not run the workflow.

The old client-side guessing (booking on "ok", "please", or on a named time without asking) **no longer exists**
in the code; do not design for it.

## 2. Entry points
- Booking requests after the patient is resolved (file 10) and a day/time is known; often preceded by a free-slots
  card (file 09).
- Restored from history: a restored card keeps its last state (booked status shown; buttons hidden once done).
- Links the thread to the patient.

## 3. Layout, top to bottom (phone portrait)
1. **Title** "Propose appointment".
2. **Key–value rows** (label column 110 dp, secondary caption; value body text):
   - Patient · "Karimov Anvar"
   - Date · "30 Okt 2026" (or "—")
   - Time · "10:30" (preferred slot) / "Not selected" (no slots) / "Choose a time below" (slots, none preferred)
3. **Error caption** from the payload (`error`, translated), e.g. "10:30 is not free on Thursday 30 October. Here
   are other open times."
4. **Body by state:**
   - no slots → spoken hint translated, else "No free slots for this day."
   - backend-confirmed, preferred slot, not booked → one line: the spoken hint, else "Confirm this exact booking
     with Shifa." (English fallback today — §11 NEW)
   - local, preferred slot, not booked → filled button "Confirm 10:30" (spinner inside while busy) · text button
     "Other times" / "Hide other times" → wrap of outlined buttons for every other slot
   - no preferred slot → wrap of outlined slot buttons (disabled in backend-confirmed mode)
5. **"Cancel"** text button while not booked → hides the card (dismiss; the payload stays in history).
6. **Red error line** (booking failure) / **green status line** "Appointment booked." or the backend status
   "Booked Karimov Anvar at 10:30 on Thursday 30 October." (translated/rebuilt).

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Patient | `patientName`, `patientId` | placeholder "Patient" |
| Date | `date` ISO or `dateLabel` English | app-language short date |
| Slots | `slots[] {startAt ISO UTC, startLocal HH:mm, slotMinutes, locationId, locationLabel}`; `preferredSlot` | times are practice-local strings |
| Video | `isVideo` | affects the booking body only (location "Video Consultation") |
| Reason | `reason` | sent as is |
| Booking call (local mode) | `POST /api/schedule/book {startAt, slotMinutes (default 30), patientId, location, reason, isVideo, locationId?}` | 200/201 ok; 409 conflict; other → "Booking failed: {code}" |
| Status (backend mode) | `statusMessage` on the follow-up payload; also spoken instead of the reply text | rebuilt as "Booked {name} at {time} on {when}." when the sentence is not translatable |
| After success | notifications + appointment providers refreshed for the booked day | calendar is fresh when the doctor leaves |

## 5. Primary flow (happy path, backend-confirmed)
1. "Book Karimov on Thursday at 10:30" → search resolves → text "Booking Karimov Anvar at 10:30 on Thursday 30
   October. Say yes to confirm." + card (Patient / Date / Time, hint line).
2. Doctor says "yes" → the backend books → new turn whose card payload has `booked: true` → the card shows the green
   status; the spoken reply is the status line; calendar providers refresh.

```mermaid
flowchart TD
  REQ[Booking request] --> P{Payload}
  P -->|workflowId| RO[Read-only card + hint "Confirm … with Shifa"]
  RO -->|doctor says yes / a listed time| BK[Backend books → booked:true → green status]
  RO -->|doctor says another day| REQ
  RO -->|Cancel tap| HIDE[Card hidden]
  P -->|no workflowId, preferred slot| BTN[Confirm {time} · Other times · Cancel]
  P -->|no workflowId, no preferred| ALL[All slots as buttons · Cancel]
  BTN -->|Confirm| POST[POST /api/schedule/book]
  ALL -->|slot tap| POST
  POST -->|200/201| OK[Appointment booked. · refresh calendar]
  POST -->|409| CONFLICT[That slot is no longer free. Pick another.]
  POST -->|other| FAIL[Booking failed: {code}]
  P -->|slots empty| NONE[No free slots for this day.]
```

## 6. Screen states
- **First load:** n/a (payload-driven).
- **Empty (0 slots):** rows with Time "Not selected" + "No free slots for this day." + Cancel.
- **Error on load:** payload `error` shown as a caption above the slots (soft; the card still offers times).
- **Offline:** local Confirm fails → red "Booking failed: …" / exception text; backend mode: "yes" cannot be sent
  (stream error).
- **Refreshing:** n/a.
- **Partial data:** missing patient id or slot start → "Missing patient or slot." when confirming (local mode).
- **Permission / Locked / Role:** n/a.

## 7. Interactions and secondary flows
- **Confirm {time}** (local) → busy spinner → success status / error line; buttons disappear on success.
- **Other times** → toggles the list of the remaining slots; tapping one books that slot.
- **Slot button** (no preferred) → books that slot.
- **Cancel** → card disappears (not a backend cancel; the proposal simply goes away). Saying "no" to Shifa also works.
- **Say / type "yes"** (backend mode) → booked; the "yes" is forwarded as a normal message (the previous card's
  context travels along, the backend knows what is pending).
- **Say a different time** → the backend either books it (if listed) or returns a new proposal.
- **409 conflict** → "That slot is no longer free. Pick another." — the other slots remain tappable.

## 8. Edge cases
- Card with 0 slots → explained above; the doctor must name another day.
- 409 on Confirm → conflict line; "Other times" opens the rest; a second 409 shows the same line.
- Double tap on Confirm → the button is disabled while busy; one request.
- Two propose cards in the thread (the doctor changed their mind) → each has its own state; only the newest is
  "pending" for the backend. Target: visually mark superseded proposals as inactive.
- Saying "yes" twice (backend mode) → the second "yes" gets "I don't have a cancellation/booking waiting…"-style
  text or a repeat; nothing double-books (backend owns the state).
- Saying "yes" with nothing pending → backend replies that there is nothing to confirm.
- Booked status restored from history → green line, no buttons; cannot be undone from here.
- Local mode success then network failure on refresh → booking exists; calendar refreshes on leaving Intelligence.
- Slot in the past (clock skew) → backend 4xx → "Booking failed: 400".
- Video patient → the body uses location "Video Consultation" (hardcoded English sent to the backend — spec bug 10).
- Reschedule vs book confusion → separate card (file 15).
- 200 % font: label column 110 dp + value wraps; buttons wrap into a column.
- Screen reader: rows as "Patient, Karimov Anvar"; status line announced on change (live region).
- Language switch → labels and the hint line re-render; the backend status sentence is rebuilt in the new language.

## 9. Validation and messages
| Action | Rule | When | Message | Where |
|---|---|---|---|---|
| Confirm (local) | patientId and slot start present | tap | Missing patient or slot. | red line in card |
| Confirm (local) | 200/201 | response | Appointment booked. | green line |
| Confirm (local) | 409 | response | That slot is no longer free. Pick another. | red line |
| Confirm (local) | other status | response | Booking failed: {code} | red line |
| Confirm (local) | exception | — | raw exception text (target: "Booking failed. Check your connection.") | red line |
| Backend booked | `booked: true` | payload | Booked {name} at {time} on {when}. (backend) / Appointment booked. (fallback) | green line + spoken |

## 10. Navigation out
- None from the card. Leaving Intelligence afterwards refreshes the calendar for the booked day.

## 11. Copy
| Key | English |
|---|---|
| `intelCardProposeBook` | Propose appointment |
| `intelPatient` / `intelDate` / `intelTime` | Patient / Date / Time |
| `intelTimeNotSelected` / `intelChooseTime` | Not selected / Choose a time below |
| `intelConfirmTime` | Confirm {time} |
| `intelOtherTimes` / `intelHideOtherTimes` | Other times / Hide other times |
| `cancel` | Cancel |
| `intelNoFreeSlots` | No free slots for this day. |
| `intelBookedOk` | Appointment booked. |
| `intelSlotConflict` | That slot is no longer free. Pick another. |
| `intelBookingFailed` | Booking failed: {code} |
| `intelMissingPatientOrSlot` | Missing patient or slot. |
| backend sentences (translated) | Booking {name} at {time} on {when}. · {time} is not free on {when}. Here are other open times. · I found {count} free slots on {when} for {name}. Which time works? · No free slots on {when} for {name}. Please choose another day. |
| **NEW** `intelConfirmBookingWithShifa` | Confirm this exact booking with Shifa — say or type “yes”. |
| **NEW** `intelBookingFailedNetwork` | Booking failed. Check your connection and try again. |

## 12. Known problems in the current app (do not carry over)
- "Confirm this exact booking with Shifa." is an English fallback with no key.
- Raw exception text in the red line on network errors.
- Hardcoded English location strings ("Video Consultation", "Clinic") sent to the backend.
- Superseded proposal cards stay interactive-looking.
- Spec §3.6 describes client-side auto-booking on affirmatives and named times — removed in code; do not revive.

## 13. Open questions for design
- In backend-confirmed mode, should the card still offer a tappable "Yes, book" button that sends "yes" for the
  doctor (one tap instead of speaking)?
- Show the location label on the card when the clinic has several?
