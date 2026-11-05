# Shifa AI & Intelligence

Area 02 §3–§8 (flows 03/*, boards `DaIntel`). Tickets AI-01…10, INTEL-01…10 (M10). AI-03, AI-05, AI-06 and AI-11 were
built with the visit workspace (see visits.md).

## AI-01 · AI HTTP client

Built 2026-11-05. Spec 02 §3.2, §8, §9, ticket AI-01. Data only; INTEL-04, AI-08, AI-09 and AI-10 put it on screen.

- **Endpoints** (dev `/v3/api-docs`, the backend's `DoctorAiController` / `PatientBriefingController`, and real calls
  as the seed doctor): `POST /ai/morning/stream` and `POST /ai/stream` (SSE, `DoctorAiRequest`: `messages`,
  `language` EN/RU/UZ, `question?`, `patientId?`, `consultationId?`, `conversationId?` ≤ 128), `POST /ai/speak`,
  `POST /ai/patient-briefing/{id}` (`{language}` en/uz/ru), `GET /ai/visit-facts/appointment/{id}`,
  `GET /ai/visit-facts/patient/{id}/timeline` (dev: `{"timeline":[],"patterns":[]}` for the seed patients),
  `POST /ai/visit-facts/commit`. Draft confirm / discard were already in `ConsultationRepository` (AI-11) and are
  reused. No paid AI call was made on dev: a stream without messages fails validation before the model runs, which
  showed the real framing (`event:error\ndata:{"code":"VALIDATION",…}`, HTTP 200, no space after `data:`).
- **SSE** (`data/api/Sse.kt`): `data:` is kept verbatim (Spring writes no space, so a token's leading space is part of
  the text; the old reader stripped it and glued words), lines join with `\n`, `id:` / `retry:` / comments ignored, an
  event the stream ends in is still delivered with its type. `error` events are typed by code (`VALIDATION` →
  ValidationError, `RATE_LIMIT(ED)` → RateLimit, else ServerError with the code). Callbacks suspend, and cancelling the
  caller closes the connection (tested).
- **Bug fixed:** `streamEvents` called `build()` inside the request builder, which resolved to
  `HttpRequestBuilder.build()` instead of the caller's block, so every stream went out as a bare GET. No screen used
  it yet; the parameter is now `configure`.
- **Repository** (`data/ai/AiRepository.kt`): `stream(request, morning, onEvent)` → `Token` / `Tool(name, payload)` /
  `Draft` (a nameless card or a draft without an id is dropped and logged without content); one turn gets 75 s
  (02 §3.3), after which it fails as a timeout `NetworkError`. `speak` cuts the text as Flutter did (560 chars, last
  `.!?` at 40+, else the last space past 40), 25 s, returns bytes + type (`audio/mpeg` when missing). `patientBriefing`
  and the visit facts keep failures typed (Flutter turned a failed fact read into an exception with the body text);
  fact rows are read one by one. Models in `data/model/AiModels.kt` (`VisitFacts.keptDrafts` / `dropped` as Flutter).
  Nothing logs AI text, tokens or transcripts.
- **Demo:** `DemoAi`: the morning stream sends a `get_today_schedule` card built from today's demo calendar, then a
  greeting in the request's language, word by word with leading spaces; `/ai/stream` ends in a draft (confirmable
  when tied to a demo visit); a question containing "fail" ends in an `AI_UNAVAILABLE` error event. Speech is half a
  second of silent WAV; the briefing is fixed; each demo visit has two kept drafts and one dropped fact, and committing
  marks them, drafts a task id for the medication and counts patterns. Unknown visits and any other `ai/` path
  answer a local 404, never the real backend (Codex review).
- **Tests:** `AiRepositoryTest` (a recorded morning fixture with leading spaces, cards, a nameless card, an empty data
  line, a draft, a two-line token, ignored lines and an EOF tail; request bodies; error event and non-2xx codes;
  cancel closes the connection; speak type and cut; briefing; facts, timeline, commit), `SseTest` updated,
  `DemoBackendTest` (the demo AI end to end, nothing forwarded).
- **Not checked on a phone:** there is no screen yet; the emulator check comes with INTEL-04.

## AI-02 · AI text renderer

Built 2026-11-05. Spec 02 §3.2 helpers, Flutter `core/widgets/ai_response_text.dart`, ticket AI-02. Board `DaIntel`
(an answer is plain 14/22 text there; headings and bullets are not drawn on the boards, so their look follows
Flutter's in the design's tokens: not in the design).

- **Widget** (`ui/widgets/AiResponseText.kt`): `#`–`###` headings and a line that is only `**…**` → a heading
  (`panelTitle`, `Text.BrandStrong`, a 3 dp `Border.Brand` bar on the left); `-`, `*`, `•` or `1.` → a bullet row
  (numbers become a dot, as in Flutter); `**bold**` inside lines (non-greedy; a lone `**` stays as typed); a run of
  blank lines → one 10 dp gap; anything else a paragraph. One line over 220 characters with 3+ sentences is split
  into a paragraph per sentence. Whitespace in these patterns is Dart's (no-break spaces count; Kotlin's `\s` is
  ASCII-only: Codex review). The whole answer is one `SelectionContainer`. Text style `aiText` (14/22, new in
  `ShifaTextStyles`, from DaIntel); callers can pass another (the visit briefing keeps `bodyText`).
- **Used by:** the visit briefing sheet (APPT-06), whose `ponytail:` plain-text stand-in is gone; next the
  Intelligence thread (INTEL-04) and the patient briefing (AI-09).
- **Tests:** `AiResponseTextTest` (Flutter's two tests plus wrapped headings, numbered and star bullets, blank runs,
  `**Reason:** text` staying a paragraph, the sentence split and line endings); screenshots `Display_ai_text`,
  `Briefing_ready` re-recorded.

## AI-09 · Patient briefing

Built 2026-11-05. Spec 02 §6, flow 03/20, ticket AI-09. Board `DaPatients` (the header's "AI summary" and the
"brief" sheet: title "Patient briefing", the patient's name under it, the text, "AI summary — verify against the
originals.", Copy). Flutter never showed this on Android or iOS.

- **Trigger:** a sparkles icon button "AI summary" under the favourite star in the patient header (the board's
  labelled button doesn't fit beside the name on a phone; the flow asks for an icon button). Shown only when the plan
  includes `PATIENT_BRIEFING` and the user isn't clinic staff; hidden until the plan is known. The board's
  more-actions sheet doesn't exist in the app yet, so the header is the only entry.
- **Sheet** (`ui/patients/PatientBriefing.kt`): loading = "Reading documents and generating briefing..." (a polite
  live region) over a heading + 6 line skeleton; ready = sources line (hidden when both counts are 0), the text through
  `AiResponseText`, the disclaimer, Copy (→ "Copied") and Regenerate; empty = "No information to brief on yet." with
  Retry briefing; failure = an error banner with the request id and Retry briefing; a failed Regenerate keeps the
  last text under the banner.
- **Rules:** the request goes in the app language at the time of the tap. The ViewModel is kept per patient in the
  shell store, so the briefing stays for the session and reopening shows it at once; a request still running when
  the sheet closes finishes and shows on reopen (Flutter dropped it and paid twice). One request at a time (Flutter
  restarted on a second tap). 60 s timeout (flow target). The backend's 400 means "no documents, visits, forms or
  notes" (`PatientBriefingService`), shown as the empty state, not an error (it replaces an older text, Codex
  review); 429 → "daily AI usage limit"; the 60 s running out → "Shifa took too long to reply"; 403
  refreshes the profile so the button goes if the plan changed. No server text is shown.
- **Kept:** the sources line uses the existing translated keys with "document(s)" (proper plurals would need new
  ru/uz forms; D-17).
- **Shared fix:** `codeBanner`'s dismiss is optional now; without one the banner has no ✕. 24 other screens still
  pass a no-op, fixed separately.
- **Checked:** emulator, demo mode: the button, the sheet with the demo briefing, Copy → Copied, close and reopen
  without a new request. Not run on dev (a briefing is a paid model call). iOS compiled, not run.
- **Tests:** `PatientBriefingViewModelTest` (first open in the app language, reopen without a request, one request at
  a time, 400 and blank → empty, 429 message and request id, a failed Regenerate keeps the text); screenshots
  `PatientBriefing_loading`, `_ready`, `_empty` (with the limit error), `PatientDetail_overview` (button shown).

## AI-10 · Visit facts panel and clinical timeline

Built 2026-11-05. Spec 02 §3.2 and §9, flow 03/21, ticket AI-10. Boards `DaVisit` (video room "Visit facts from the
call": kind pill, text, Keep / Reject chips, rejected at 55 % and struck through, "Document everything",
"already in the patient timeline") and `DaPatients` ("Clinical timeline": dot and line per fact, when).

- **Panel** (`ui/visit/VisitFacts.kt`, `VisitFactsViewModel` per visit): in the visit workspace above the notes, for
  in-person and video visits (Flutter had it only in the video call). Read on open and on every return (the scribe may
  have finished meanwhile), plus a refresh icon (flow §3). Kept drafts with the quote (italic) and schedule hint;
  Keep / Reject are local until committed and survive a reload (Flutter lost them). "Dropped (not said)" lists what
  the model dropped, with its reason as sent. Top 3 clinic patterns. "Document everything" sends the kept ids as
  `acceptFactIds` and the rejected ones as `rejectFactIds` (the backend rejects the rest of the drafts when an accept
  list is sent), is disabled while documenting or with nothing kept (Flutter documented 0), then reads again; the
  result reads "Documented {n} facts." (plural) or "Facts documented: n. Medication tasks drafted: m." in a polite
  live region. A failed commit says so with the board's copy.
- **Notes in step (Codex review):** the backend also appends the documented facts to the visit's manual note. The
  notes are saved before the commit (no commit if they can't be), and the same text is added to the open editor as
  already saved (`VisitNotes.appendSaved`), so the next autosave keeps it. The commit's answer is applied at once, so
  the drafts can't be documented twice. While the scribe is working and no facts have come, the panel reads again
  every 10 s while on screen.
- **When it shows:** only while there are facts (kept, dropped or documented), a first read in flight, or a failed
  first read (an error row with Retry; Flutter hid failures). Without the AI notes plan the panel isn't there (the
  board's "Visit facts need the Pro plan." line is left out: it would show in every visit for BASIC doctors, and the
  in-person boards have no panel at all).
- **Timeline** (`ui/patients/ClinicalTimeline.kt`, `ClinicalTimelineViewModel` per patient): on the overview under
  the clinical summary, read on every return to the chart. "Medication · text", the quote, and when it was
  documented as "22 October 2026 · 10:42" in practice time (Flutter showed the raw ISO string), then the top 4
  patterns. "No documented visit facts yet." when empty; an error row with Retry on a first failure; a failed re-read
  keeps what is shown. AI notes plan, doctors only.
- **Shared:** `factTypeLabel` (Symptom / Diagnosis / Therapy / Medication, others as sent) and `Patterns` live in
  `VisitFacts.kt` and are used by both.
- **Checked:** emulator, demo mode, visit 102 (6 Nov): three facts (one dropped), Reject the symptom, Document
  everything → "Facts documented: 1. Medication tasks drafted: 1.", the dropped one stays, then Patients → Jasur
  Karimov → the timeline lists the medication at 13:45 with the pattern. Dev has no visits, so no dev read of an
  appointment's facts (the timeline read was checked in AI-01). iOS compiled, not run.
- **Not built:** tapping a timeline row to its visit, per-fact edit.
- **Tests:** `VisitFactsViewModelTest` (read, a rejection surviving a reload, commit body and the read after, nothing
  kept can't commit, a failed commit, a failed first read, empty, the timeline keeping its rows on a failed re-read);
  screenshots `VisitFacts_ready`, `VisitFacts_done` (message, already documented, error row), `ClinicalTimeline`.

## INTEL-01 · Chat models and session store

Built 2026-11-05. Spec 02 §4.2, §4.3, §3.9, §0 (`detectReplyLanguage`), ticket INTEL-01. Data only; INTEL-02 puts it
on screen.

- **Models** (`data/intelligence/IntelligenceChat.kt`): `ChatMessage` (role, content, time, the tool name and card
  payload an assistant turn showed), `ChatSession` (`displayTitle`: the doctor's title when renamed, else the first
  user message as a short title, else "New chat"; `preview`: the latest message, 90 characters; `matchesQuery`;
  `patientMark`). Stored as Flutter stored them (ISO times, `titleCustom` optional); a bad chat or message is skipped
  instead of losing the whole history, a non-object card is dropped, a missing role is the doctor's.
- **ChatTitles:** the starter-prompt map (EN / UZ / RU), wake phrases ("Hey / Salom / Assalomu alaykum / Привет
  Shifa" and the name's mishears), `stripWakePhrase`, `shortTitle` (48, cut on a word past 24), `previewLine` (90, past
  40), and `detectReplyLanguage`: Uzbek Cyrillic letters → UZ, other Cyrillic → RU, Uzbek marker words → UZ, two
  English function words → EN, else keep the chat's language.
- **ChatContext:** a chat is about a patient from a patient card; clinic-wide cards (schedule, free slots, patterns,
  most visited) never link one, a search names the patient only for a single match, and "Patient" isn't a name.
- **Store** (`ChatSessionStore`, key `shifa_intelligence_chats_v1:<doctorId>`): a cold start opens on an empty chat
  (made if none), history kept; create, goToWelcome, select, delete (the last one leaves a new empty chat; deleting
  the open one opens the newest), rename (keeps `updatedAt`; blank ignored), replaceMessages (moves the chat to the
  top). Another doctor signing in reads their own key. Chat ids get a counter after the time, so two chats in the
  same millisecond can't share the backend's `conversationId`. A turn finishing after its chat was deleted (or another doctor signed in) changes
  nothing; stored messages are a copy of the caller's list; a stored chat without an id gets its own (Codex review).
  Whitespace in the ported patterns is Dart's (`utils/DartWhitespace.kt`, shared with `AiResponseText`).
- **Fix (ticket):** the chats name patients, so they are stored in the encrypted settings (Keychain /
  EncryptedSharedPreferences, the token's store) instead of plain preferences, and capped at 50 chats (oldest go
  first, never the open one; Flutter kept every chat). Flutter's plain-preference history isn't migrated: it was
  written by the web build, which isn't ported.
- **Tests:** `IntelligenceChatTest`: Flutter's seven tests (reply language, wake phrases, starter titles, long titles
  and previews, renamed titles kept, patient link, legacy JSON) plus Russian / Uzbek-Cyrillic detection, skipped bad
  rows, the store's semantics per doctor and the cap.
