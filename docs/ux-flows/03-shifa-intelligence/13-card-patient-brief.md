# Patient brief card

| | |
|---|---|
| Area | Shifa Intelligence · action cards |
| Who sees it | Doctor only · PRO and PREMIUM (`patientBriefing` and `askShifaAi` are both PRO) |
| Reached from | Assistant turn with the `patient_brief` card (tool `brief_patient`), e.g. "Brief me on Karimov before he comes in" |
| Screen type | Inline card inside the chat thread |
| Current Flutter source | `intelligence_action_cards.dart` (`_briefBody`) · compare `core/widgets/patient_briefing_panel.dart` (file 20) |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §4.4, §6 · tickets INTEL-05, AI-02 |

## 1. Purpose
The same AI briefing as on patient detail (file 20), but inside the conversation: what matters about this patient
from documents and past visits. Before visits, daily for some doctors.

## 2. Entry points
- Patient-specific "brief / summarise / what do I need to know" requests, after the patient is resolved (file 10).
- Restored from history (text as generated then).
- Links the thread to the patient.

## 3. Layout, top to bottom (phone portrait)
1. **Title** "Patient briefing".
2. **Body**: the briefing text as plain text today (`briefing`, else the spoken hint). Target: the same lightweight
   markdown renderer as file 20 (`## headings` with a brand bar, bullets, bold), selectable.
3. No sources line, no copy button today (file 20 has both). Target: add "Based on {docs} document(s) and {appts}
   appointment(s)." when the payload carries counts, and a Copy action.

Target rendering rules for the body (shared with file 20): `## Heading` → bold line with a 3 dp brand bar on the
left; `-` / `•` lines → bullets with 12 dp indent; `**text**` → bold; blank lines → 8 dp spacer; a single paragraph
longer than ~220 characters is split into sentence paragraphs for readability; everything selectable.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Text | `briefing` (markdown subset) or `spokenHint` | generated in the reply language; may be long (several screens) |
| Spoken | only the first 560 characters are read aloud (file 06) | — |
| Sources | not in this payload today (the patient-detail endpoint returns `documentCount` / `appointmentCount`) | backend request: include counts in the tool payload |
| Errors | the briefing tool failing inside the stream → `error` in the payload or a stream error event | 429 → rate-limit sentence (file 03) |

Flow of a briefing request inside the chat:

```mermaid
flowchart TD
  Q["Brief me on Karimov"] --> R{Patient resolved?}
  R -->|no| S[Search card · file 10]
  S --> Q
  R -->|yes| G[Backend generates · 3–15 s · pending "Reviewing…"]
  G -->|ok| CARD[Text intro + Patient briefing card · spoken]
  G -->|no data| HINT[Card with hint: nothing to brief on]
  G -->|429| RL[Error row: daily limit]
```

## 5. Primary flow (happy path)
1. "Brief me on my 10 o'clock" → backend resolves the visit → text intro + card with the briefing.
2. Doctor reads; the voice reads the first sentences; the doctor says "open his last x-ray" → documents card (file 18).

## 6. Screen states
- **First load:** the pending block shows for the whole generation (3–15 s, longer than other tools) — target: the
  pending copy says "Reading documents…" when the tool name is known early.
- **Empty:** `briefing` empty → spoken hint ("I could not find enough information to brief you on this patient." or
  similar backend text) in the body.
- **Error on load:** `error` payload → default body with the error; rate limit → the stream error row (file 03).
- **Offline:** restored text renders.
- **Refreshing:** no (ask again → a new card).
- **Partial data:** text present without counts → no sources line.
- **Permission needed:** none.
- **Locked by tier:** gate is the screen (PRO); the backend may still refuse with 403 for a patient whose documents
  the doctor cannot access → error text in the card.
- **Role difference:** doctor only.

## 7. Interactions and secondary flows
- None today.
- Target: "Copy" secondary button (→ "Briefing copied to clipboard."), "Open chart" text button → patient detail,
  "Read more" when collapsed.
- Follow-up questions ("what about his allergies?") stay in the thread; the backend has the patient in focus.

## 8. Edge cases
- Very long briefing → the card can be taller than the screen; follow-latest scroll must land on the **start** of the
  card, not the end (today it jumps to the bottom).
- Markdown markers shown raw today.
- Briefing in a different language than requested (model drift) → shown as is.
- Briefing for a patient with no documents → short text; the backend says so.
- Restored card for a patient later deleted → text remains (historical).
- 200 % font: fine, text wraps.
- Screen reader: long text; make the card a single readable region, headings as headings.
- Two briefings for the same patient in one thread (asked twice) → two cards; the newer may differ; accepted.
- Patient-detail sheet (file 20) and this card are generated separately and may differ in wording.
- Documents the doctor cannot access are excluded by the backend; the briefing may be thinner than expected with no
  explanation — target: the sources line makes this visible.
- A briefing mentioning another patient's data (model error) → cannot be detected on the device; the disclaimer
  under the composer covers it.
- Slow network: the generation itself is slow; show the pending block for up to 75 s, then the timeout error.

## 9. Validation and messages
| Condition | Message | Where |
|---|---|---|
| Copy (target) | Briefing copied to clipboard. | banner |
| Rate limit | You've reached your daily AI usage limit. Try again tomorrow. | error row |
| No data | backend hint in the card body | card |

## 10. Navigation out
None today.

## 11. Copy
| Key | English |
|---|---|
| `intelCardBrief` | Patient briefing |
| `patientBriefingSourcesWithAppointments` / `patientBriefingSources` / `patientBriefingSourcesAppointmentsOnly` | Based on {docs} document(s) and {appts} appointment(s). / Based on {n} document(s). / Based on {n} appointment(s). |
| `patientBriefingCopy` / `patientBriefingCopied` | Copy / Briefing copied to clipboard. |
| `openPatientCard` | Open chart |
| **NEW** `readMore` / `readLess` | Read more / Read less |
| **NEW** `intelReadingDocuments` | Reading documents… |

## 12. Known problems in the current app (do not carry over)
- Plain `Text` instead of the briefing renderer; no copy; no sources line.
- Auto-scroll to the bottom of a long card hides its beginning.

## 13. Open questions for design
- Collapse long briefings to ~8 lines with "Read more"?
- Should the chat card and the patient-detail sheet (file 20) be the same component?
