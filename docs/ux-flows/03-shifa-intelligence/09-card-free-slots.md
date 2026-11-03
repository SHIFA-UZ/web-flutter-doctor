# Free slots card

| | |
|---|---|
| Area | Shifa Intelligence · action cards |
| Who sees it | Doctor only · PRO and PREMIUM |
| Reached from | Assistant turn with the `free_slots` card (tool `list_free_slots`), e.g. "What's free on Thursday morning?" |
| Screen type | Inline card inside the chat thread |
| Current Flutter source | `intelligence_action_cards.dart` (`_freeSlotsBody`), `intelligence_card_text.dart` (`intelligenceFieldLabel`, hint sentences "You have {count} free{part} slots on {when}…") |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §4.4 · tickets INTEL-05, INTEL-10 |

## 1. Purpose
Show open times for one day so the doctor can pick one by voice ("book 10:30") or move to the calendar. Weekly.

## 2. Entry points
- Availability questions; often the step before a propose-appointment card (file 14) once a patient is known.
- Restored from history (slots may be gone by then).
- This card is clinic-wide: it never links a patient.

## 3. Layout, top to bottom (phone portrait)
1. **Title** "Free slots".
2. **Date line** (bold): "30 Oct 2026" (from ISO `date`, else the English `dateLabel` translated).
3. **Chips**: up to 24 slot start times "09:00", "09:30"… as compact outlined chips (brand 35 % border), wrapping.
   **Not tappable** today.
4. **Empty copy**: the backend's spoken hint translated, else "No free slots for this day."
5. **"Open calendar"** text button.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Date | `date` ISO / `dateLabel` | "30 Okt 2026" in app language |
| Slot | `slots[].startLocal` "HH:mm" practice time; each also has `startAt` ISO, `slotMinutes`, `locationId`, `locationLabel` (unused visually) | chips in backend order |
| Assistant line | "You have 5 free morning slots on Thursday 30 October. Which time works?" — translated with day-part words (morning / afternoon / evening) | — |

Backend sentences the card can sit under (all translated on the device):

| Count | Sentence |
|---|---|
| 0 | You have no free{ morning/afternoon/evening} slots on {when}. |
| 1 | You have 1 free{ part} slot on {when}: {time}. |
| n | You have {count} free{ part} slots on {when}. Which time works? |
| with patient | I found {count} free slots on {when} for {name}. Which time works? |
| none for patient | No free slots on {when} for {name}. Please choose another day. |
| asked without a day | Which day should I check for free slots? |

## 5. Primary flow (happy path)
1. "Any free time Thursday morning?" → text + chips.
2. Doctor says or types "10:30 for Karimov" → the backend returns a propose-appointment card (file 14) with that
   slot preselected.

## 6. Screen states
- **First load:** n/a.
- **Empty (0 slots):** date line + "No free slots for this day." (or the backend hint such as "You have no free
  afternoon slots on Thursday 30 October.") + Open calendar.
- **Error on load:** `error` payload → default body.
- **Offline:** restored chips render; stale.
- **Refreshing:** no.
- **Partial data:** no date → date line hidden.
- **Permission needed:** none.
- **Locked by tier:** n/a beyond the screen gate.
- **Role difference:** doctor only.

## 7. Interactions and secondary flows
- **Open calendar** → Calendar tab, today (not the slot day — §12).
- Chips inert today. Target: tapping a chip sends "Book {time}" / opens the booking sheet of doc 04 prefilled with
  the slot (`startAt`, `locationId`) and the context patient when known.

```mermaid
flowchart TD
  TAP[Tap slot chip · target] --> CTX{Patient linked in this thread?}
  CTX -->|yes| SEND[Send "Book {name} at {time} on {date}" → propose card · file 14]
  CTX -->|no| ASK[Send "Book {time} on {date}" → backend asks for the patient → search card · file 10]
```

## 8. Edge cases
- 1 slot → one chip; the assistant sentence uses the singular form ("You have 1 free slot on … : 10:30.").
- > 24 slots (short slot length) → only 24 chips; no indication of more (target: "+N more").
- Day in the past → the backend answers with a hint ("… has already passed. Name a future day…") and usually no
  card.
- Slots at multiple locations → chips do not show the location; the propose card does. Target: group by location
  label when more than one.
- Timezone: times are practice times from the backend.
- Restored card from yesterday → chips are stale; no "as of" hint (target: show the turn time).
- 200 % font: chips wrap; fine.
- Screen reader: date line, then "Free slot 09:00" per chip; if chips stay inert they must not be announced as
  buttons.
- Slots of different lengths (15 / 30 / 60 min) look identical; `slotMinutes` is in the payload — target: show the
  length once in the date line ("30-min slots").
- Consecutive slots (09:00, 09:30, 10:00…) could be a range; the backend lists them individually — keep chips.
- A slot that is free at render time but taken at tap time → the propose card returns 409 or a new proposal.
- Day-part phrase in the sentence ("morning") but chips cover the whole day → the backend filters; trust the chips.
- Practice timezone differs from the phone → no effect (strings).
- Fast follow-up "book the first one" → the backend resolves "first" from the list it sent (ids travel with
  history); no client logic.

## 9. Validation and messages
| Action | Rule | Message | Where |
|---|---|---|---|
| Chip tap (target) | slot still free at booking time | handled by the propose card: "That slot is no longer free. Pick another." | propose card |
| Open calendar | always | — | — |

## 10. Navigation out
- Open calendar → Calendar tab.

## 11. Copy
| Key | English |
|---|---|
| `intelCardFreeSlots` | Free slots |
| `intelNoFreeSlots` | No free slots for this day. |
| `intelOpenCalendar` | Open calendar |
| **NEW** `intelBookThisSlot` | Book {time} |
| **NEW** `intelAndMoreRows` | +{count} more |

## 12. Known problems in the current app (do not carry over)
- Chips look tappable but do nothing.
- Open calendar ignores the slot day.
- No location shown when the clinic has several.

## 13. Open questions for design
- Should a tapped chip book directly (with the context patient) or open the calendar booking sheet?
- Visual difference between video and in-clinic slots?
