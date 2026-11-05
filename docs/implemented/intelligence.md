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
