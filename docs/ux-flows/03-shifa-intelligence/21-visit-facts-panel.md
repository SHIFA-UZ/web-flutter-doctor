# Visit facts panel (on a visit) + Clinical timeline card (on the patient chart)

| | |
|---|---|
| Area | Shifa Intelligence · AI outside the chat |
| Who sees it | Doctor only · PRO and PREMIUM (AI notes / scribe are PRO; the panel itself has no extra gate) |
| Reached from | Video call screen notes area (panel); patient detail overview, left column under the clinical summary (timeline card). The in-person screen does **not** embed the panel today |
| Screen type | Inline panels inside existing screens |
| Current Flutter source | `lib/features/home/presentation/widgets/visit_facts_panel.dart` (`VisitFactsPanel`, `_FactCard`, `PatientVisitTimelineCard`, providers), `lib/core/api/ai_api.dart` (visit-facts endpoints), placements in `video_call_screen.dart` and `patient_detail_panel.dart` |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §0 (copy), §3.2, §9 (`VisitFactDto`) · ticket AI-10 |

## 1. Purpose
After the AI scribe listened to a visit, show exactly what was said as discrete facts (medication, diagnosis,
therapy, symptom), let the doctor drop the wrong ones and commit the rest to the patient's permanent clinical
timeline in one tap. Then show that timeline on the patient chart. Per visit for scribe users.

## 2. Entry points
- **Panel**: rendered at the top of the notes area on the video call screen once the appointment id is known;
  loads automatically. Target: also on the in-person consultation screen (same place as the SOAP notes).
- **Timeline card**: patient detail → Overview; loads automatically.
- Refresh icon in the panel header re-fetches.

## 3. Layout, top to bottom (phone portrait)

**Visit facts panel** (white 95 % card, radius 16, brand 20 % border, padding 14)
1. Header row: title "Visit facts from the call" · refresh icon button (tooltip "Refresh").
2. Hint caption: "Only what was spoken. Review, then document everything."
3. **Kept draft facts** — one `_FactCard` each: icon by type (medication / diagnosis / therapy / symptom / notes),
   bold line "Medication · Amoxicillin 500 mg…", italic quote line "“…what the doctor said…”" when present,
   caption "Schedule: twice daily for 5 days" when a schedule hint exists; trailing icon button ✕ "Reject" (red)
   that toggles to ↶ "Keep" (brand). Rejected cards are 55 % opacity with strike-through.
4. **Dropped (not said)** group (orange caption) — read-only fact cards at 55 % opacity with the drop reason in
   orange under them.
5. **Clinic patterns** (caption title) — top 3 "Label · 14×".
6. **"Document everything"** primary button, full width; label "Documenting…" while busy.
7. When nothing is left to commit but some are committed: caption "These visit facts are already in the patient
   timeline."
8. Commit message (brand bold caption): "Documented 4 facts and drafted 1 medication tasks. They are on the patient
   timeline." (translated via the hint localiser).

**Clinical timeline card** (white, radius small, border, padding 16)
1. Timeline icon + title "Clinical timeline"; hint "Accepted visit facts, step by step. Sourced from what was
   spoken."
2. Rows: brand dot · "Diagnosis · K02.1 caries" (bold) · italic quote · timestamp caption (`committedAt` or
   `createdAt` **raw ISO string today**).
3. Divider; "Clinic patterns" caption + top 4 rows "Label · 14×".

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Panel data | `GET /api/ai/visit-facts/appointment/{id}` → `facts[]` (status DRAFT / DROPPED / COMMITTED, `reflectionKept`), `patterns[]` | kept = DRAFT and kept; dropped = DROPPED or not kept |
| Timeline data | `GET /api/ai/visit-facts/patient/{id}/timeline` → committed facts + patterns | newest first (backend order) |
| Fact type | SYMPTOM / DIAGNOSIS / THERAPY / MEDICATION (translated), others raw | icon per type |
| Commit | `POST /api/ai/visit-facts/commit {appointmentId, acceptFactIds, rejectFactIds}` → `{committedFacts[], createdTaskIds[], patterns[]}` | after: panel re-fetched, timeline cache invalidated |
| Timestamps | ISO strings from the backend | **target**: "24 Okt 2026 · 10:42" in practice time |
| Medication tasks | each committed medication can create a remote-care task (`createdTaskIds`) — PREMIUM feature elsewhere | mentioned in the commit message only |

## 5. Primary flow (happy path)
1. Visit ends; the scribe finishes (doc 05); the panel loads: 2 px progress line → 5 kept facts, 1 dropped.
2. Doctor taps ✕ on one wrong medication (card dims), leaves the rest.
3. Taps "Document everything" → "Documenting…" → commit → message "Documented 4 facts and drafted 1 medication
   tasks. They are on the patient timeline." → the panel reloads and now shows the "already in the patient timeline"
   caption (kept list empty).
4. Patient chart → Overview → Clinical timeline card lists the 4 facts.

## 6. Screen states
- **First load (panel):** thin linear progress (2 px). Target: skeleton of 3 fact cards.
- **Empty (panel):** nothing kept, dropped or committed → the panel is **not rendered at all** (no empty state; the
  notes area simply lacks it). Acceptable: there was no scribe.
- **Error on load (panel):** hidden silently today. Target: a small inline error row "Could not load visit facts ·
  Retry".
- **Offline:** load fails → hidden/error; commit fails → message shows the raw exception (target network copy).
- **Refreshing:** refresh icon → progress line replaces content (content jumps); target: keep content, show the
  progress line above.
- **Partial data:** facts without quote/schedule hint → lines omitted; patterns empty → block omitted.
- **Timeline card:** loading, error and empty are all **invisible** (card not rendered). Target: show the card with
  "No documented visit facts yet." when the request succeeds empty; error row with Retry on failure.
- **Permission needed:** none.
- **Locked by tier:** not gated here (the scribe that produces facts is PRO); the medication task creation is a
  PREMIUM feature on the backend — the message may say "drafted 0 medication tasks".
- **Role difference:** staff do not open visit screens; the patient chart for staff hides doctor-only AI blocks.

## 7. Interactions and secondary flows
- **✕ Reject / ↶ Keep** toggles per kept fact; purely local until commit.
- **Document everything** → commits accepted and rejected ids together (rejected are recorded as rejected, not
  deleted). Disabled while busy or when nothing is kept.
- **Refresh** → re-fetch; local reject toggles are **lost** (state reset — target: keep toggles across refresh).
- **Timeline rows**: inert. Target: tap → the originating appointment.
- Spoken "document everything" inside the Intelligence chat produces the chat card (file 17) instead.

## 8. Edge cases
- All facts rejected → "Document everything" still enabled (commits with accept = [] and reject = all) → message
  "Documented 0 facts…". Target: label changes to "Reject all" or the button disables with a hint.
- Facts already committed (panel reopened later) → kept list empty, "already in the patient timeline" caption, no
  button.
- Scribe still running → the endpoint returns no facts yet → panel hidden; it appears on the next refresh (no
  polling). Target: poll every 3 s while the scribe status is pending (doc 05 already polls scribe status).
- Double tap on Document everything → disabled while busy.
- Commit succeeds but the re-fetch fails → message shown, list stale; refresh icon recovers.
- 30+ facts → long panel inside a scrolling notes area; acceptable.
- Quote very long → wraps; italic.
- Drop reason in the backend language (English) → shown as sent.
- Patterns labels are not translated (file 12).
- Timestamps raw ISO ("2026-10-24T10:42:11Z") on the timeline — must be formatted in practice time.
- Language switch → fact-type labels and captions re-render; quotes unchanged.
- 200 % font: fact card title wraps; the ✕ button stays 40 dp at the top right.
- Screen reader: each fact card as "Medication, Amoxicillin…, quote …, Reject button"; commit message live region.
- Appointment deleted → 404 → panel hidden today; target error row.

## 9. Validation and messages
| Action | Rule | Message | Where |
|---|---|---|---|
| Document everything | ≥ 1 kept fact (today: ≥ 1 draft, even if all rejected) | — | button disabled |
| Commit success | — | Documented {n} facts[ and drafted {m} medication tasks]. They are on the patient timeline. | caption under button |
| Commit failure | — | raw exception → **NEW** Could not document the facts. Check your connection and try again. | caption |
| Already documented | kept empty, committed > 0 | These visit facts are already in the patient timeline. | caption |
| Load failure | — | **NEW** Could not load visit facts. · Retry | inline row |
| Timeline empty | — | **NEW** No documented visit facts yet. | card body |

## 10. Navigation out
- None from the panel. Committing refreshes the consultation notes and the patient timeline caches so the patient
  chart is up to date when opened next.

## 11. Copy
| Key | English |
|---|---|
| `visitFactCards` | Visit facts from the call |
| `visitFactCardsHint` | Only what was spoken. Review, then document everything. |
| `refresh` | Refresh |
| `intelKeep` / `intelReject` | Keep / Reject |
| `intelScheduleHint` | Schedule: {hint} |
| `droppedFacts` | Dropped (not said) |
| `intelCardPatterns` | Clinic patterns |
| `documentEverything` / `documenting` | Document everything / Documenting… |
| `factsAlreadyDocumented` | These visit facts are already in the patient timeline. |
| `clinicalTimeline` / `clinicalTimelineHint` | Clinical timeline / Accepted visit facts, step by step. Sourced from what was spoken. |
| commit sentence (localiser, no key) | Documented {n} facts and drafted {m} medication tasks. They are on the patient timeline. |
| **NEW** `visitFactsCommitted` (plural-aware) | Documented {n} facts. They are on the patient timeline. |
| **NEW** `visitFactsCommittedWithTasks` | Documented {n} facts and drafted {m} medication tasks. They are on the patient timeline. |
| **NEW** `visitFactsLoadFailed` / `visitFactsDocumentFailed` / `clinicalTimelineEmpty` | Could not load visit facts. / Could not document the facts. Check your connection and try again. / No documented visit facts yet. |
| **NEW** `factTypeSymptom` / `factTypeDiagnosis` / `factTypeTherapy` / `factTypeMedication` | Symptom / Diagnosis / Therapy / Medication |

## 12. Known problems in the current app (do not carry over)
- Panel is only on the video call screen; the in-person screen has none, although both produce scribe facts.
- Load errors and empty results hide the panel and the timeline card silently.
- Raw ISO timestamps on the timeline; device time elsewhere.
- Refresh loses the doctor's reject toggles.
- "Document everything" with everything rejected still says "Document".
- Commit message built from an English sentence and translated by regex; "1 medication tasks" grammar.
- Orange hex-ish colours and a `_vf(key, fallback)` pattern instead of straight keys.

## 13. Open questions for design
- Should the panel sit above the SOAP notes (today) or be a tab "Spoken facts" next to Notes?
- Per-fact edit (fix a dosage) before committing — or is edit only possible later on the timeline?
- Should medication facts preview the task they will create (PREMIUM) with an opt-out?
