# Draft visit facts card + "Document everything"

| | |
|---|---|
| Area | Shifa Intelligence · action cards |
| Who sees it | Doctor only · PRO and PREMIUM |
| Reached from | Assistant turn with the `draft_visit_facts` card (tool `list_draft_visit_facts`) after "Help me review my clinical notes." / "Document everything from the last visit" |
| Screen type | Inline card inside the chat thread |
| Current Flutter source | `intelligence_action_cards.dart` (`_draftFactsBody`, `_commitFacts`), `intelligence_card_text.dart` (`intelligenceFactTypeLabel`), `core/api/ai_api.dart` (`commitVisitFacts`) |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §4.4, §3.2 visit-facts endpoints, §9 · tickets INTEL-07, AI-10 |

## 1. Purpose
After a recorded (scribed) visit, the AI extracted facts that were actually said — medications, diagnoses,
therapies, symptoms. This card lets the doctor commit all of them to the patient timeline in one tap from the chat.
The richer keep/reject version lives on the visit screen (file 21). After visits, daily for scribe users.

## 2. Entry points
- "Clinical notes" starter prompt → the model asks which visit → this card; or directly when the backend knows the
  current / last appointment.
- Restored from history (facts may already be committed; the card does not know).
- Links the thread to the patient.

## 3. Layout, top to bottom (phone portrait)
1. **Title** "Draft visit facts".
2. **Patient** row (label/value as in file 14).
3. **Fact rows**: "Medication: Amoxicillin 500 mg 3× daily for 7 days" — fact type label (Symptom / Diagnosis /
   Therapy / Medication, translated; unknown types raw) + colon + text. No keep/reject per row here.
4. **Empty copy**: spoken hint translated, else "No draft facts ready."
5. **Buttons** (when facts exist): filled "Document everything" (spinner inside while busy) · text "Cancel"
   (hides the card).
6. Green status "Documented 4 facts." / red error line.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Facts | `facts[] {factId, factType, text}` | all shown (no cap) |
| Appointment | `appointmentId` (required to commit) | — |
| Commit call | `POST /api/ai/visit-facts/commit {appointmentId, acceptFactIds: all listed ids}` → `{committedFacts[], createdTaskIds[], patterns[]}` | success count from `committedFacts.length` |
| After commit | visit facts, consultation notes and patient timeline caches refreshed | the visit screen and patient chart show the facts |
| Assistant line | "There are {count} draft facts. Confirm to document them." (translated) | — |

## 5. Primary flow (happy path)
1. "Document everything from Karimov's visit" → text "There are 4 draft facts. Confirm to document them." + card.
2. Tap "Document everything" → spinner → "Documented 4 facts." Buttons disappear.
3. Later, the patient chart's Clinical timeline (file 21) lists them.

## 6. Screen states
- **Empty:** patient row + "No draft facts ready." (or the hint "I need an appointment to list draft visit facts.").
- **Error on load:** payload `error` → default body.
- **Offline:** commit fails → red exception text (target: "Could not document the facts. Check your connection.").
- **Partial data:** `appointmentId` missing → button disabled (no message — target: hint line).
- **First load:** n/a (payload-driven).
- **Refreshing:** no; a new request produces a new card.
- **Permission needed:** none.
- **Locked by tier:** the scribe producing facts is PRO (AI notes); the card has no own gate.
- **Role difference:** doctor only.

## 7. Interactions and secondary flows
- **Document everything** → commit all → status. One-shot; the button is disabled after success.
- **Cancel** → hides the card; nothing committed.
- Voice: saying "document everything" in Intelligence does **not** commit directly; it produces this card (the
  spoken shortcut exists only in the dead non-embedded session).
- Per-fact reject is not available here; direct the doctor to the visit screen (target: "Review on the visit" link
  that opens the appointment with the panel of file 21).

## 8. Edge cases
- 0 facts → empty copy; button hidden.
- 20+ facts → long card; all committed together.
- Facts already committed by another path → the backend returns 0 or the same facts; status "Documented 0 facts."
  can appear (target: backend should say "already documented"; card shows "These visit facts are already in the
  patient timeline.").
- Medication facts create medication tasks on commit (`createdTaskIds`) — the card status does not mention them
  today (the panel in file 21 does: "… and drafted M medication tasks").
- Restored card after commit → still shows the button (state is local); a second tap re-commits → likely 0.
  Target: persist "documented" into the stored payload.
- Double tap → disabled while busy.
- Appointment deleted meanwhile → 404 → red exception text.
- Fact text very long → wraps.
- Unknown fact type (e.g. ALLERGY) → raw code shown as the label.
- 200 % font / screen reader: rows as sentences "Medication: …"; status line live.
- The card arrives for a visit other than the one the doctor meant (backend picked "current appointment") → the
  patient row is the only hint; target: add date/time of the visit to the card header.
- Slow commit (> 3 s): spinner inside the button; Cancel disabled meanwhile.
- Session expired on commit → 401 → raw error today; target logout.

## 9. Validation and messages
| Action | Rule | Message | Where |
|---|---|---|---|
| Document everything | appointmentId present, ≥ 1 fact | — (button disabled) | — |
| Commit success | — | Documented {count} facts. | green line |
| Commit failure | — | raw exception → target "Could not document the facts. Check your connection and try again." | red line |

## 10. Navigation out
- None today. Target: "Review on the visit" → appointment screen (doc 05) with the visit facts panel.

## 11. Copy
| Key | English |
|---|---|
| `intelCardDraftFacts` | Draft visit facts |
| `intelPatient` | Patient |
| `intelNoDraftFacts` | No draft facts ready. |
| `intelConfirmDocument` | Document everything |
| `cancel` | Cancel |
| `intelDocumentedFacts` | Documented {count} facts. |
| fact types (no keys, inline table) | Symptom / Diagnosis / Therapy / Medication |
| `factsAlreadyDocumented` | These visit facts are already in the patient timeline. |
| **NEW** `intelDocumentFailed` | Could not document the facts. Check your connection and try again. |
| **NEW** `intelReviewOnVisit` | Review on the visit |
| **NEW** `factTypeSymptom` / `factTypeDiagnosis` / `factTypeTherapy` / `factTypeMedication` | Symptom / Diagnosis / Therapy / Medication |

## 12. Known problems in the current app (do not carry over)
- No per-fact keep/reject in the chat card; all-or-nothing.
- Fact type labels are an inline en/uz/ru table in code, not string keys.
- Success count does not mention drafted medication tasks.
- Documented state is not persisted; restored cards can be committed again.
- Raw exception on failure.

## 13. Open questions for design
- Should the chat card reuse the full panel (file 21) with keep/reject toggles instead of a flat list?
- Show the quote ("what was said") under each fact as the panel does?
