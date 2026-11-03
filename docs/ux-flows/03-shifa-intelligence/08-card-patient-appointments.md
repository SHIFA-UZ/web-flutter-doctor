# Patient appointments card

| | |
|---|---|
| Area | Shifa Intelligence · action cards |
| Who sees it | Doctor only · PRO and PREMIUM |
| Reached from | Assistant turn with the `patient_appointments` card (tool `list_patient_appointments`), e.g. "When is Karimov's next visit?" / "Show his visits last month" |
| Screen type | Inline card inside the chat thread |
| Current Flutter source | `intelligence_action_cards.dart` (`_patientAppointmentsBody`), `intelligence_card_text.dart` (`intelligenceRangeLabel`, `intelligenceDateLabel`, `intelligenceStatusLabel`) |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §4.4, §0 (range localisation) · tickets INTEL-05, INTEL-10 |

## 1. Purpose
Answer "when did / when will this patient come" with a dated list for a period, and link the thread to that patient
so follow-up requests ("move the second one") work. Weekly.

## 2. Entry points
- Natural questions about a patient's visits; the backend resolves the patient (possibly after a search card, file
  10) and a period.
- Restored from history.
- This card makes the conversation **patient-linked** (context line "Patient · Name" appears above the composer).

## 3. Layout, top to bottom (phone portrait)
1. **Title** "Patient appointments".
2. **Patient name** (bold) when the payload has one.
3. **Range label** (secondary): "Today", "Tomorrow", "This week (20 Oct – 26 Oct 2026)", "Next week (…)",
   "Upcoming (from 24 Oct 2026)", "the next 30 days", "since 1 Sep 2026", "from 1 Sep 2026 until 30 Sep 2026" —
   translated on the device.
4. **Count line** "Appointments: 3".
5. **Rows** (max 12): date in a 100 dp column (bold, "24 Oct 2026" from ISO or from English text) · time in a 48 dp
   column · status (secondary). When the card has **no header name** (multi-patient answer), the row shows
   "Patient name · Status" instead.
6. **Empty copy**: "No appointments in this period."
7. **"Open calendar"** text button.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Date | row `date` (ISO yyyy-MM-dd) or `dateLabel` (English "Tue 24 Oct 2026") | rendered "24 Okt 2026"-style in the app language; weekday dropped |
| Time | `startLocal` "HH:mm" practice time | as sent |
| Status | enum → localised (file 07 table) | — |
| Range | `rangeLabel` English sentence from the backend | exact-match or regex translation; unknown → shown as sent |
| Patient | `patientName` | "Patient" placeholder when empty |

Range label examples (backend English → shown):

| Backend | EN | RU |
|---|---|---|
| `Today` | Today | Сегодня |
| `This week (20 Oct 2026 – 26 Oct 2026)` | This week (20 Oct 2026 – 26 Oct 2026) | На этой неделе (20 окт 2026 – 26 окт 2026) |
| `the next 30 days` | the next 30 days | ближайшие 30 дней |
| `Upcoming (from Tue 24 Oct 2026)` | Upcoming (from 24 Oct 2026) | Предстоящие (с 24 окт 2026) |
| anything else | as sent | as sent (English leaks) |

## 5. Primary flow (happy path)
1. "Show Karimov's visits this month" → (if ambiguous, a search card first) → text + this card.
2. Doctor says "move the one on the 28th to Friday" → propose reschedule card (file 15); the backend knows which
   visit because the card's appointment ids travel back with the history.

## 6. Screen states
- **First load:** n/a (appears with the payload).
- **Empty:** name + range + "Appointments: 0" + "No appointments in this period."
- **Error on load:** payload `error` → default body with the message.
- **Offline:** restored card renders; Open calendar works.
- **Refreshing:** no; ask again.
- **Partial data:** no name → rows carry names; no range → line omitted; no date → empty 100 dp column (target:
  hide the column).
- **Permission needed:** none.
- **Locked by tier:** n/a beyond the screen gate.
- **Role difference:** doctor only.

## 7. Interactions and secondary flows
- **Open calendar** → Calendar tab (today, not the listed date — §12).
- Rows inert today; target: tap opens the appointment; long-press "Open patient chart".
- Voice follow-ups ("move the second one to Friday") are resolved by the backend from the ids sent with the history.
- Context line "Patient · Name" appears above the composer from this card on; "Find another patient" simply
  replaces the context when a new search card arrives.

## 8. Edge cases
- More than 12 visits → 12 rows, count shows the total; no "+N more" (target: add).
- Range spanning years → "from 20 Dec 2026 until 5 Jan 2027".
- Backend range sentence not in the translator → English text in a Russian UI.
- Dates in the payload are ISO (no TZ) and represent practice days — no conversion.
- Patient placeholder name "Patient" never links the conversation to a patient.
- Same patient name twice in the roster → the backend asks which (file 10) before this card.
- Status Cancelled rows are not de-emphasised (target: muted).
- 200 % font: the three fixed columns overflow on narrow phones → wrap status under date/time.
- Screen reader: one sentence per row "24 October 2026, 09:00, Confirmed".
- Row date missing but `dateLabel` in English ("Tue 24 Oct 2026") → parsed and localised; an unparseable label is
  shown raw.
- Appointments on the same day → the date repeats on each row (no grouping) — target: group by day.
- A visit that is both in the list and currently in progress → status "In progress"; no highlight.
- Multi-patient answer (header name empty) → rows read "Karimov Anvar · Confirmed"; the context line shows "Patient"
  without a name because no single patient is linked.
- The card arrives before the text finishes (tool events are early) → the list may render while the sentence above
  still streams; keep the card from jumping.
- Doctor asks for a past range → statuses Completed / No-show / Cancelled; consider muted styling for all three.

## 9. Validation and messages
| Action | Rule | Message | Where |
|---|---|---|---|
| Open calendar | always | — | — |
| Row tap (target) | appointment id present | — | — |

## 10. Navigation out
- Open calendar → Calendar tab; Intelligence closed; schedule refreshed.

## 11. Copy
| Key | English |
|---|---|
| `intelCardPatientAppointments` | Patient appointments |
| `intelAppointmentsCount` | Appointments |
| `intelNoPatientAppointmentsInRange` | No appointments in this period. |
| `intelOpenCalendar` | Open calendar |
| `intelRangeThisWeek` / `intelRangeNextWeek` / `intelTomorrow` / `intelRangeUpcomingFrom` / `today` | This week / Next week / Tomorrow / Upcoming (from {date}) / Today |
| status keys | see file 07 |
| **NEW** `intelAndMoreRows` | +{count} more |

## 12. Known problems in the current app (do not carry over)
- "Open calendar" lands on today, not on the first listed date.
- Rows inert.
- English range sentences leak when the backend phrases them differently from the regex list (stop-gap translator).

## 13. Open questions for design
- Group rows by month when the range is long?
- Show a chip for the status instead of plain text?
