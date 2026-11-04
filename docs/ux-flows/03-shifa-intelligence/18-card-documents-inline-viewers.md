# Documents card + inline viewers (PDF / image / dental chart)

| | |
|---|---|
| Area | Shifa Intelligence · action cards |
| Who sees it | Doctor only · PRO and PREMIUM |
| Reached from | Assistant turns with `patient_documents` (tool `list_patient_documents`), `document_viewer` (`open_patient_document`) or `dental_chart` (`open_dental_chart`) — "Show Karimov's documents", "Open his last x-ray", "Open the dental chart from 12 March" |
| Screen type | Inline cards inside the chat thread with an embedded viewer (360 dp tall) |
| Current Flutter source | `intelligence_action_cards.dart` (`_documentsListBody`, `_documentViewerBody`, `_dentalChartBody`), `intelligence_inline_viewers.dart` (`IntelligenceInlineDocumentViewer`, `IntelligenceInlineDentalChart`), `features/appointments/dental/dental_fdi_chart.dart` |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §4.4, §4.5, §0 · ticket INTEL-07 |

## 1. Purpose
Look at a patient's file without leaving the conversation: list documents, open one inline (PDF or image), or see
the dental chart of a visit with services per tooth. Before and during visits.

## 2. Entry points
- Document requests after the patient is resolved. The backend may answer with the list (several documents), with a
  viewer directly (one match: "I found 1 document for {name}: {title}. Opening it in chat."), or with the list plus
  `autoOpenDocumentId` preselected ("I could not match "{query}". Here are {count} documents for {name} — tap one
  to open in chat.").
- Dental requests: "Here is the dental diagram for {name} from {when} — {count} teeth have recorded services." or,
  with no chart, "No visit dental chart yet. Opening {title} for {name} in the chat." (falls back to a document).
- Restored from history: the list restores; the viewer re-downloads the file on render.
- All three link the thread to the patient.

## 3. Layout, top to bottom (phone portrait)

**Documents list card**
1. Title "Patient documents".
2. Rows (max 12): document icon · title (bold; localised when it is one of the known English category names) ·
   date caption ("12 Mar 2026") · trailing "open" icon, or an "eye" icon on the opened row (tinted background).
3. Empty: spoken hint translated ("{name} has no viewable documents yet." / "There are no viewable documents on file
   for {name} yet.") else "No documents on file."
4. When a row is selected (tap or `autoOpenDocumentId`): the **inline viewer** below the list.

**Inline viewer** (also the whole body of the "Document" card)
1. Title line (document title, bold).
2. Grey rounded box, 360 dp tall:
   - loading: small spinner (target: skeleton page);
   - image (jpg/png/gif/webp by magic bytes, content type or extension): pinch-zoom 0.5–4×, fit-contain;
   - PDF: native page viewer with pinch zoom, vertical paging;
   - failure: "Could not load document" + "Retry" text button.
3. Missing ids in the payload → the card shows the backend error/hint text instead of a viewer.

**Dental chart card**
1. Title "Dental diagram".
2. Header "Karimov Anvar · 12 Mar 2026".
3. Horizontally scrollable FDI chart (adult or primary dentition, toggle shown) with per-tooth service counts
   (reused from the dental module, doc 05); read-only.
4. Tap a tooth → "Services for tooth 36" + up to 6 bullet lines (service names; "Service" when unnamed) or
   "No services recorded for this tooth."
5. Notes caption when present.
6. If the payload has an `error` and no chart data → the hint/error text only.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Documents | `documents[] {documentId, title, date}` + `patientId`, `autoOpenDocumentId` | title localised for known categories ("Lab result", "X-ray"…), date from ISO or English |
| Viewer | `patientId`, `documentId`, `title` → download through the patient documents API (bytes + filename + content type) | image vs PDF detection as above; other types (docx) → rendered as PDF attempt → failure state |
| Dental | `dentition` ('primary' else permanent), `toothServiceCounts {fdi: n}`, `documentation.teeth {fdi: [{name|serviceName|label}]}`, `date`, `notes`, `patientName` | FDI keys normalised |
| Size | viewer height fixed 360 dp; chart scrolls horizontally | — |

## 5. Primary flow (happy path)
1. "Show Karimov's documents" → list card with 5 rows.
2. Tap "X-ray · 12 Mar 2026" → row highlights; viewer appears below with a spinner → image renders; pinch to zoom.
3. "Open his dental chart" → dental card; tap tooth 36 → services listed.

## 6. Screen states
- **First load (viewer):** spinner in the grey box (target: skeleton page shape).
- **Empty:** list → "No documents on file." / backend hint; dental → hint text (no chart).
- **Error on load (viewer):** "Could not load document" + Retry (download failed, empty bytes, access denied).
- **Offline:** list renders from the payload; viewer fails → Retry; dental chart renders (data is in the payload).
- **Refreshing:** Retry only.
- **Partial data:** row without `documentId` → not tappable; document without date → no caption; tooth without
  services → "No services recorded for this tooth."
- **Permission / Locked / Role:** document access rules are the backend's (403 → failure state; target: show
  "You don't have access to this document" when the API says so — doc 06 request-access flow).

## 7. Interactions and secondary flows
- **Tap row** → opens inline; tapping another row switches the viewer (re-download); tapping the open row does
  nothing today (target: collapse).
- **Pinch / double-tap** zoom on images; PDF pinch zoom and page scroll — note the viewer sits inside the vertically
  scrolling thread: vertical drags inside a zoomed PDF scroll the PDF, outside it the thread (gesture conflict —
  design a clear frame and a "Full screen" affordance; target: expand icon opens the document viewer of doc 06).
- **Dentition toggle** (adult / primary) on the chart.
- **Tooth tap** → services under the chart.
- **Retry** → re-download.
- Long-press: none (target: share/download → doc 06 viewer).

## 8. Edge cases
- Document is neither image nor PDF (docx, zip) → PDF renderer fails → "Could not load document" (target: show file
  type + "Open in viewer").
- Very large PDF (50+ MB) → slow download without progress; target: progress and a size hint.
- Image EXIF rotation → must be honoured.
- Scanned multi-page PDF → page viewer; first page shown; vertical paging.
- 12+ documents → only 12 rows; target "+N more" → patient documents tab.
- `autoOpenDocumentId` not in the list → nothing opens; list only.
- Same document opened twice in one thread (two cards) → two viewers; memory — target: lazy render when visible.
- Restored card → viewer re-downloads on each render (data cost); target: cache by document id for the session.
- Dental chart with no `toothServiceCounts` but with documentation → counts derived from teeth entries? Today counts
  only from `toothServiceCounts`; teeth without counts show no badge. Flag for backend.
- Primary dentition payload → child chart.
- Tooth key formats ("36" vs "FDI-36") → normalised.
- Follow-latest scroll after a 360 dp viewer appears → jumps to the bottom; the top of the document may be off
  screen. Target: scroll to the card top.
- Language switch → titles for known categories and date re-render; file content unchanged.
- 200 % font: rows wrap; the 360 dp viewer height is fixed — fine.
- Screen reader: viewer labelled with the document title; chart teeth need labels "Tooth 36, 2 services"; the
  dentition toggle labelled.
- Access revoked after listing → viewer failure with the 403 message.

## 9. Validation and messages
| Condition | Message | Where |
|---|---|---|
| Download failed / empty / unsupported | Could not load document · Retry | viewer box |
| Missing ids | backend `error` / `spokenHint` | card body |
| No documents | No documents on file. (or backend hint) | card body |
| No tooth services | No services recorded for this tooth. | under chart |
| **NEW** no access | You don't have access to this document. Request access from the patient's file. | viewer box |

## 10. Navigation out
- None today. Target: "Open full screen" → documents viewer (doc 06); "All documents" → patient documents tab.

## 11. Copy
| Key | English |
|---|---|
| `intelCardDocuments` / `intelCardDocumentViewer` / `intelCardDentalChart` | Patient documents / Document / Dental diagram |
| `intelNoDocuments` | No documents on file. |
| `couldNotLoadDocument` / `retry` | Could not load document / Retry |
| `intelDocument` / `intelPatient` / `intelService` | Document / Patient / Service |
| `dentalToothServices` | Services for tooth |
| `intelNoToothServices` | No services recorded for this tooth. |
| `dentalDentitionPermanent` / `dentalDentitionPrimary` | Adult teeth / Primary (child) teeth |
| backend sentences (translated) | I found 1 document for {name}: {title}. Opening it in chat. · I found {count} documents for {name}. They are listed in the chat. · {name} has no viewable documents yet. · Opening {title} for {name} in the chat. · Which document for {name} should I open? They are listed in the chat. · Here is the dental diagram for {name} from {when} — {count} teeth have recorded services. · No visit dental chart yet. Opening {title} for {name} in the chat. |
| **NEW** `intelOpenFullScreen` / `intelAllDocuments` / `intelNoDocumentAccess` | Open full screen / All documents / You don't have access to this document. Request access from the patient's file. |

## 12. Known problems in the current app (do not carry over)
- No full-screen or share path from the inline viewer; gesture conflicts between the zoomable viewer and the thread.
- Unsupported file types fail silently as "Could not load document".
- Viewer re-downloads on every render of a restored card.
- Auto-scroll hides the top of a freshly opened document.
- Spinner instead of a skeleton.

## 13. Open questions for design
- Inline viewer height: fixed 360 dp or aspect-fit up to 60 % of the screen?
- Should tapping a document open the full viewer directly on phones (inline only as a thumbnail)?
- Dental chart inline on a phone is cramped — thumbnail + "Open chart" instead?
